package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.domain.chat.AiAnswer
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.AnswerComposer
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.IntentParser
import com.kuikly.stockchat.domain.chat.ParsedIntent
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.tencent.kuikly.core.timer.setTimeout

/**
 * AI 回答的流式事件监听。所有回调都在 Kuikly 线程触发，可直接更新 UI 状态。
 */
interface AnswerListener {
    /** 意图已识别，开始检索数据 */
    fun onThinking(parsed: ParsedIntent)

    /** 追加一个完整的非文本块（卡片 / 图表 / 标签等） */
    fun onBlock(index: Int, block: AnswerBlock)

    /** Markdown 块开始输出 */
    fun onMarkdownStart(index: Int)

    /** Markdown 块增量文本（累计全文） */
    fun onMarkdownDelta(index: Int, text: String, finished: Boolean)

    fun onComplete(answer: AiAnswer)

    fun onError(message: String)
}

/**
 * AI 引擎抽象：输入意图与行情，输出结构化回答。
 * 默认为本地规则引擎，可替换为远端大模型。
 */
interface AiEngine {
    fun generate(parsed: ParsedIntent, snapshots: List<MarketSnapshot>, callback: (AiAnswer) -> Unit)
}

class LocalAiEngine : AiEngine {
    override fun generate(parsed: ParsedIntent, snapshots: List<MarketSnapshot>, callback: (AiAnswer) -> Unit) {
        callback(AnswerComposer.compose(parsed, snapshots))
    }
}

/**
 * 问答服务：意图识别 → 行情检索 → 回答生成 → 模拟 token 级流式输出。
 */
class AiService(
    private val pagerId: String,
    private val marketRepository: MarketRepository,
    private val engine: AiEngine = LocalAiEngine(),
) {
    /** 每个 tick 输出的字符数 */
    var charsPerTick: Int = 4
    /** tick 间隔毫秒 */
    var tickIntervalMs: Int = 24
    /** 非文本块之间的停顿 */
    var blockPauseMs: Int = 160

    private var generation = 0

    fun cancel() {
        generation += 1
    }

    fun ask(text: String, contextInstruments: List<Instrument>, listener: AnswerListener) {
        val myGeneration = ++generation
        val parsed = IntentParser.parse(text, contextInstruments)
        listener.onThinking(parsed)

        val needed = when (parsed.intent) {
            Intent.KNOWLEDGE, Intent.GREETING, Intent.UNKNOWN -> emptyList()
            Intent.COMPARE -> parsed.instruments.take(2)
            else -> parsed.instruments.take(1)
        }
        // 兜底：风险意图但没有标的时也不请求
        marketRepository.loadSnapshots(needed) { snapshots ->
            if (myGeneration != generation) return@loadSnapshots
            engine.generate(parsed, snapshots) { answer ->
                if (myGeneration != generation) return@generate
                // 让“思考中”至少可见片刻，体验更自然
                setTimeout(pagerId, 350) {
                    if (myGeneration != generation) return@setTimeout
                    streamBlocks(answer, 0, myGeneration, listener)
                }
            }
        }
    }

    private fun streamBlocks(answer: AiAnswer, index: Int, myGeneration: Int, listener: AnswerListener) {
        if (myGeneration != generation) return
        if (index >= answer.blocks.size) {
            listener.onComplete(answer)
            return
        }
        val block = answer.blocks[index]
        if (block is AnswerBlock.Markdown) {
            listener.onMarkdownStart(index)
            streamMarkdown(block.text, index, 0, myGeneration, listener) {
                setTimeout(pagerId, blockPauseMs / 2) { streamBlocks(answer, index + 1, myGeneration, listener) }
            }
        } else {
            listener.onBlock(index, block)
            setTimeout(pagerId, blockPauseMs) { streamBlocks(answer, index + 1, myGeneration, listener) }
        }
    }

    private fun streamMarkdown(
        full: String,
        index: Int,
        cursor: Int,
        myGeneration: Int,
        listener: AnswerListener,
        onDone: () -> Unit,
    ) {
        if (myGeneration != generation) return
        val next = nextCursor(full, cursor)
        val finished = next >= full.length
        listener.onMarkdownDelta(index, full.substring(0, next), finished)
        if (finished) {
            onDone()
            return
        }
        setTimeout(pagerId, tickIntervalMs) {
            streamMarkdown(full, index, next, myGeneration, listener, onDone)
        }
    }

    /**
     * 计算下一次输出的位置：默认按字符数推进，遇到 Markdown 结构（表格行、代码块）时整行输出，
     * 避免半截语法导致渲染抖动。
     */
    private fun nextCursor(full: String, cursor: Int): Int {
        var next = minOf(cursor + charsPerTick, full.length)
        val lineStart = if (cursor > 0) full.lastIndexOf('\n', cursor - 1) + 1 else 0
        val lineEnd = full.indexOf('\n', cursor).let { if (it < 0) full.length else it }
        if (lineEnd < lineStart) return next
        val line = full.substring(lineStart, lineEnd)
        if (line.trimStart().startsWith("|") || line.trimStart().startsWith("```") || line.trimStart().startsWith("#")) {
            next = minOf(lineEnd + 1, full.length)
        }
        return next
    }
}
