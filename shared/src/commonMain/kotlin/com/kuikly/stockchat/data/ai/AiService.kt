package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.attachment.AttachmentLoader
import com.kuikly.stockchat.data.attachment.FileContentReader
import com.kuikly.stockchat.data.attachment.LoadedAttachment
import com.kuikly.stockchat.data.market.DerivedMarketRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.chat.AiAnswer
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.AnswerComposer
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.IntentParser
import com.kuikly.stockchat.domain.chat.ParsedIntent
import com.kuikly.stockchat.domain.chat.ResearchProgress
import com.kuikly.stockchat.domain.chat.ResearchSource
import com.kuikly.stockchat.domain.chat.ResearchSourceKind
import com.kuikly.stockchat.domain.chat.ResearchStage
import com.kuikly.stockchat.domain.model.DerivedMarketData
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.InstrumentResolver
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.PeerUniverse
import com.tencent.kuikly.core.timer.setTimeout

/**
 * AI 回答的流式事件监听。所有回调都在 Kuikly 线程触发，可直接更新 UI 状态。
 */
interface AnswerListener {
    /** 研究链路的真实进度，用于展示“理解问题 → 读取行情 → 生成结论”。 */
    fun onProgress(progress: ResearchProgress) = Unit

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
    fun generate(
        parsed: ParsedIntent,
        snapshots: List<MarketSnapshot>,
        derived: DerivedMarketData,
        attachments: List<LoadedAttachment>,
        callback: (AiAnswer) -> Unit,
    )
}

class LocalAiEngine : AiEngine {
    override fun generate(
        parsed: ParsedIntent,
        snapshots: List<MarketSnapshot>,
        derived: DerivedMarketData,
        attachments: List<LoadedAttachment>,
        callback: (AiAnswer) -> Unit,
    ) {
        callback(AnswerComposer.compose(parsed, snapshots, derived))
    }
}

/**
 * 问答服务：意图识别 → 行情检索 → 回答生成 → 模拟 token 级流式输出。
 */
class AiService(
    private val pagerId: String,
    private val marketRepository: MarketRepository,
    private val engine: AiEngine = LocalAiEngine(),
    private val derivedRepository: DerivedMarketRepository? = null,
    private val intentRecognizer: RemoteIntentRecognizer? = null,
    private val fileReader: FileContentReader? = null,
    private val instrumentResolver: InstrumentResolver = InstrumentResolver(),
    private val documents: DashScopeDocumentClient? = null,
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

    fun ask(
        text: String,
        contextInstruments: List<Instrument>,
        listener: AnswerListener,
        attachments: List<Attachment> = emptyList(),
    ) {
        val myGeneration = ++generation
        listener.onProgress(ResearchProgress(ResearchStage.UNDERSTANDING, "正在理解问题与分析目标"))
        recognize(text, contextInstruments) { parsed ->
            if (myGeneration != generation) return@recognize
            listener.onThinking(parsed)
            listener.onProgress(
                ResearchProgress(
                    ResearchStage.RESOLVING_INSTRUMENTS,
                    if (parsed.instruments.isEmpty()) "正在匹配适合的回答场景" else "已识别 ${parsed.instruments.size} 个相关标的",
                    instrumentCount = parsed.instruments.size,
                ),
            )
            AttachmentLoader.loadAll(attachments, fileReader) { loaded ->
                if (myGeneration != generation) return@loadAll
                val client = documents
                if (client == null) {
                    continueAfterAttachments(parsed, loaded, emptyList(), myGeneration, listener)
                    return@loadAll
                }
                client.enrich(loaded) { enriched, remoteIds ->
                    if (myGeneration != generation) {
                        client.deleteRemote(remoteIds)
                        return@enrich
                    }
                    continueAfterAttachments(parsed, enriched, remoteIds, myGeneration, listener)
                }
            }
        }
    }

    private fun continueAfterAttachments(
        parsed: ParsedIntent,
        loaded: List<LoadedAttachment>,
        remoteIds: List<String>,
        myGeneration: Int,
        listener: AnswerListener,
    ) {
        instrumentResolver.enrich(parsed) { resolved ->
            if (myGeneration != generation) {
                documents?.deleteRemote(remoteIds)
                return@enrich
            }
            InstrumentCache.rememberAll(resolved.instruments)
            if (resolved.instruments != parsed.instruments) listener.onThinking(resolved)
            listener.onProgress(
                ResearchProgress(
                    ResearchStage.FETCHING_MARKET_DATA,
                    if (resolved.instruments.isEmpty()) "正在整理专业知识" else "正在读取实时行情与历史走势",
                    instrumentCount = resolved.instruments.size,
                ),
            )
            continueAsk(resolved, loaded, remoteIds, myGeneration, listener)
        }
    }

    private fun recognize(text: String, context: List<Instrument>, callback: (ParsedIntent) -> Unit) {
        val recognizer = intentRecognizer
        if (recognizer == null) callback(IntentParser.parse(text, context))
        else recognizer.recognize(text, context, callback)
    }

    private fun continueAsk(
        parsed: ParsedIntent,
        loaded: List<LoadedAttachment>,
        remoteIds: List<String>,
        myGeneration: Int,
        listener: AnswerListener,
    ) {
        val needed = when (parsed.intent) {
            Intent.KNOWLEDGE, Intent.GREETING, Intent.LIMIT_UP_LADDER -> emptyList()
            Intent.UNKNOWN -> parsed.instruments.take(1)
            Intent.COMPARE -> PeerUniverse.forCompare(parsed.instruments, parsed.rawText, max = 4)
            else -> parsed.instruments.take(1)
        }
        if (needed.isNotEmpty()) InstrumentCache.rememberAll(needed)
        loadDerived(parsed) { derived ->
            if (myGeneration != generation) return@loadDerived
            marketRepository.loadSnapshots(needed) { snapshots ->
                if (myGeneration != generation) return@loadSnapshots
                val liveSnapshots = snapshots.filter { !it.quote.isMock }
                val dataPointCount = liveSnapshots.sumOf { it.dailyBars.size }
                val sources = buildSources(liveSnapshots, derived, loaded, dataPointCount)
                listener.onProgress(
                    ResearchProgress(
                        ResearchStage.SYNTHESIZING,
                        if (liveSnapshots.isEmpty()) "正在组织回答" else "行情读取完成，正在交叉分析",
                        instrumentCount = liveSnapshots.size,
                        dataPointCount = dataPointCount,
                        sources = sources,
                    ),
                )
                engine.generate(parsed, liveSnapshots, derived, loaded) { answer ->
                    documents?.deleteRemote(remoteIds)
                    if (myGeneration != generation) return@generate
                    setTimeout(pagerId, 350) {
                        if (myGeneration != generation) return@setTimeout
                        streamBlocks(answer, 0, myGeneration, listener)
                    }
                }
            }
        }
    }

    private fun buildSources(
        snapshots: List<MarketSnapshot>,
        derived: DerivedMarketData,
        loaded: List<LoadedAttachment>,
        dataPointCount: Int,
    ): List<ResearchSource> = buildList {
        if (snapshots.isNotEmpty()) add(
            ResearchSource(
                title = "实时行情快照",
                detail = "${snapshots.size} 个标的 · 最新价、涨跌幅与成交信息",
                kind = ResearchSourceKind.QUOTE,
            ),
        )
        if (dataPointCount > 0) add(
            ResearchSource(
                title = "历史日 K 数据",
                detail = "$dataPointCount 条走势数据用于趋势与区间分析",
                kind = ResearchSourceKind.HISTORY,
            ),
        )
        if (!derived.isEmpty) add(
            ResearchSource(
                title = "AKShare 市场数据",
                detail = "市场广度、连板梯队或资金流向",
                kind = ResearchSourceKind.DERIVED,
            ),
        )
        val readable = loaded.count { it.error == null && (it.hasImage || it.hasText) }
        if (readable > 0) add(
            ResearchSource(
                title = "用户提供的附件",
                detail = "$readable 份图片或文档已纳入分析",
                kind = ResearchSourceKind.ATTACHMENT,
            ),
        )
    }

    /** 按意图加载衍生数据（市场广度 / 连板梯队 / 资金流向）；网关未启动时全部为空，不影响主流程 */
    private fun loadDerived(parsed: ParsedIntent, callback: (DerivedMarketData) -> Unit) {
        val repo = derivedRepository ?: run { callback(DerivedMarketData()); return }
        when (parsed.intent) {
            Intent.MARKET_OVERVIEW -> repo.loadBreadth { b -> callback(DerivedMarketData(breadth = b)) }
            Intent.LIMIT_UP_LADDER -> repo.loadLadder { l -> callback(DerivedMarketData(ladder = l)) }
            Intent.CAPITAL_FLOW -> {
                val ins = parsed.instruments.firstOrNull()
                if (ins == null) callback(DerivedMarketData())
                else repo.loadCapitalFlow(ins) { f -> callback(DerivedMarketData(flow = f)) }
            }
            else -> callback(DerivedMarketData())
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
