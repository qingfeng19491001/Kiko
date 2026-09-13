package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.ChatMessage
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.Role
import com.kuikly.stockchat.domain.chat.ResearchProgress
import com.kuikly.stockchat.domain.chat.ResearchReport
import com.kuikly.stockchat.domain.chat.ResearchSource
import com.kuikly.stockchat.domain.chat.ResearchStage
import com.kuikly.stockchat.domain.chat.ResearchState
import com.kuikly.stockchat.domain.chat.elapsedText
import com.kuikly.stockchat.ui.stockadapter.StockChartState
import com.kuikly.stockchat.ui.stockadapter.StockTableState
import com.kuikly.stockchat.ui.components.StreamingMarkdownModel
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.views.DivView

/**
 * 消息中的一个可渲染块。
 * Markdown 块持有流式模型；其他块一次性渲染。
 */
sealed class UiBlock(val index: Int) {
    var revealed by observable(false)
    class Markdown(index: Int, val model: StreamingMarkdownModel) : UiBlock(index)
    class Card(index: Int, val block: AnswerBlock) : UiBlock(index)
}

/** 对话内图表十字线选中下标，必须挂在 Pager 消息模型上才能驱动重绘。 */
class ChartProbe {
    var index by observable(-1)
}

/**
 * 聊天消息的响应式 UI 模型。
 */
class ChatUiMessage(
    val id: String,
    val role: Role,
    val text: String,
    val createdAt: Long,
    initialStatus: MessageStatus,
    initialIntent: Intent = Intent.UNKNOWN,
    initialAttachments: List<Attachment> = emptyList(),
) {
    var attachments: List<Attachment> = initialAttachments
    var status by observable(initialStatus)
    var intent by observable(initialIntent)
    var errorMessage by observable("")
    var blocks by observableList<UiBlock>()
    var researchStage by observable(ResearchStage.UNDERSTANDING)
    var researchDetail by observable("正在理解问题与分析目标")
    var researchInstrumentCount by observable(0)
    var researchDataPointCount by observable(0)
    var researchElapsedMs by observable(0L)
    var researchSources by observableList<ResearchSource>()
    var researchExpanded by observable(true)
    var researchInterrupted by observable(false)
    /** -1=踩，0=未评价，1=赞；反馈只属于当前消息。 */
    var feedback by observable(0)
    var isSpeaking by observable(false)
    var selectionMenuVisible by observable(false)
    var selectionMenuX by observable(0f)
    var selectionMenuY by observable(0f)
    var selectedText = ""
    var selectionRef: ViewRef<DivView>? = null

    private val probes = mutableMapOf<Int, ChartProbe>()
    private val tables = mutableMapOf<Int, StockTableState>()
    private val charts = mutableMapOf<Int, StockChartState>()

    /** 最终的 Markdown 文本（用于持久化） */
    private val markdownTexts = mutableMapOf<Int, String>()
    private val cardBlocks = mutableMapOf<Int, AnswerBlock>()

    val isUser: Boolean get() = role == Role.USER
    val isThinking: Boolean get() = status == MessageStatus.THINKING
    val isStreaming: Boolean get() = status == MessageStatus.STREAMING

    fun updateResearch(progress: ResearchProgress) {
        applyResearchState(researchState().update(progress))
    }

    fun completeResearch(elapsedMs: Long = researchElapsedMs) {
        applyResearchState(researchState().complete(elapsedMs))
    }

    fun interruptResearch(detail: String, elapsedMs: Long = researchElapsedMs) {
        applyResearchState(researchState().interrupt(detail, elapsedMs))
    }

    private fun researchState() = ResearchState(
        stage = researchStage,
        detail = researchDetail,
        instrumentCount = researchInstrumentCount,
        dataPointCount = researchDataPointCount,
        elapsedMs = researchElapsedMs,
        sources = researchSources.toList(),
        expanded = researchExpanded,
        interrupted = researchInterrupted,
    )

    private fun applyResearchState(state: ResearchState) {
        researchStage = state.stage
        researchDetail = state.detail
        researchInstrumentCount = state.instrumentCount
        researchDataPointCount = state.dataPointCount
        researchElapsedMs = state.elapsedMs
        if (researchSources != state.sources) {
            researchSources.clear()
            researchSources.addAll(state.sources)
        }
        researchExpanded = state.expanded
        researchInterrupted = state.interrupted
    }

    fun probe(blockIndex: Int): ChartProbe = probes.getOrPut(blockIndex) { ChartProbe() }

    fun tableState(blockIndex: Int): StockTableState = tables.getOrPut(blockIndex) { StockTableState() }

    fun chartState(blockIndex: Int): StockChartState = charts.getOrPut(blockIndex) { StockChartState() }

    fun appendMarkdown(index: Int): StreamingMarkdownModel {
        val model = StreamingMarkdownModel()
        blocks.add(UiBlock.Markdown(index, model))
        return model
    }

    fun appendCard(index: Int, block: AnswerBlock) {
        cardBlocks[index] = block
        blocks.add(UiBlock.Card(index, block))
    }

    fun revealBlock(index: Int) {
        blocks.firstOrNull { it.index == index }?.revealed = true
    }

    fun revealAll() {
        blocks.forEach { it.revealed = true }
    }

    fun elapsedText(): String = elapsedText(researchElapsedMs)

    /** 朗读/系统分享使用的纯文本摘要。 */
    fun actionText(): String = blocks.sortedBy { it.index }.mapNotNull { ui ->
        when (val block = (ui as? UiBlock.Card)?.block) {
            null -> (ui as? UiBlock.Markdown)?.model?.text
            is AnswerBlock.StockCard -> "${block.quote.instrument.name}，现价 ${block.quote.price}"
            is AnswerBlock.CompareCard -> block.title
            is AnswerBlock.ChartCard -> block.title
            is AnswerBlock.MetricGrid -> block.items.joinToString("，") { "${it.label} ${it.value}" }
            is AnswerBlock.Tags -> block.tags.joinToString("，") { it.text }
            is AnswerBlock.Risk -> "${block.title}：${block.body}"
            is AnswerBlock.FollowUps -> null
            is AnswerBlock.SectionHeader -> block.title
            is AnswerBlock.SummaryCallout -> block.text
            is AnswerBlock.BarChartCard -> block.title
            is AnswerBlock.KeyLevelsCard -> block.title
            is AnswerBlock.GaugeCard -> "${block.title}：${block.label}"
            is AnswerBlock.MarketBreadthCard -> block.title
            is AnswerBlock.LimitUpLadderCard -> block.title
            is AnswerBlock.CapitalFlowCard -> block.title
            is AnswerBlock.PeerTableCard -> block.title
            is AnswerBlock.SeriesChartCard -> block.title
            is AnswerBlock.HighlightsCard -> "${block.title}：${block.items.joinToString("；")}"
            is AnswerBlock.Markdown -> block.text
        }?.takeIf { it.isNotBlank() }
    }.joinToString("\n")

    fun markdownModel(index: Int): StreamingMarkdownModel? =
        blocks.firstOrNull { it.index == index }?.let { (it as? UiBlock.Markdown)?.model }

    fun recordMarkdown(index: Int, text: String) {
        markdownTexts[index] = text
    }

    fun toDomain(): ChatMessage {
        val ordered = blocks.sortedBy { it.index }.map { block ->
            when (block) {
                is UiBlock.Markdown -> AnswerBlock.Markdown(markdownTexts[block.index] ?: block.model.text)
                is UiBlock.Card -> block.block
            }
        }
        return ChatMessage(
            id = id,
            role = role,
            text = text,
            blocks = ordered,
            status = if (status == MessageStatus.STREAMING || status == MessageStatus.THINKING) MessageStatus.DONE else status,
            intent = intent,
            createdAt = createdAt,
            errorMessage = errorMessage,
            attachments = attachments,
            researchReport = if (role == Role.ASSISTANT) ResearchReport(
                elapsedMs = researchElapsedMs,
                instrumentCount = researchInstrumentCount,
                dataPointCount = researchDataPointCount,
                sources = researchSources.toList(),
            ) else null,
        )
    }

    companion object {
        /** 从持久化消息恢复（Markdown 直接终态渲染） */
        fun fromDomain(message: ChatMessage): ChatUiMessage {
            val ui = ChatUiMessage(message.id, message.role, message.text, message.createdAt, MessageStatus.DONE, message.intent, message.attachments)
            ui.errorMessage = message.errorMessage
            if (message.role == Role.ASSISTANT && message.blocks.isNotEmpty()) {
                ui.researchStage = ResearchStage.COMPLETE
                message.researchReport?.let { report ->
                    ui.researchElapsedMs = report.elapsedMs
                    ui.researchInstrumentCount = report.instrumentCount
                    ui.researchDataPointCount = report.dataPointCount
                    ui.researchSources.addAll(report.sources)
                }
                ui.researchDetail = if (ui.researchSources.isNotEmpty()) {
                    "已检索 ${ui.researchSources.size} 类数据源 · ${ui.elapsedText()}"
                } else {
                    "已完成分析 · 可展开查看研究过程"
                }
                ui.researchExpanded = false
            }
            message.blocks.forEachIndexed { index, block ->
                if (block is AnswerBlock.Markdown) {
                    val model = ui.appendMarkdown(index)
                    model.setFinal(block.text)
                    ui.recordMarkdown(index, block.text)
                } else {
                    ui.appendCard(index, block)
                }
            }
            ui.revealAll()
            return ui
        }
    }
}
