package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.analysis.InsightTag
import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote

/**
 * AI 回复的结构化内容块。回复由若干块顺序组成：
 * Markdown 文本流式输出，其余卡片一次性呈现。
 */
sealed class AnswerBlock {

    /** Markdown 正文 */
    data class Markdown(val text: String) : AnswerBlock()

    /** 个股 / 指数行情卡片 */
    data class StockCard(
        val quote: Quote,
        val sparkline: List<Double>,
        val analysis: TechnicalAnalysis?,
    ) : AnswerBlock()

    /** 两只标的对比表 */
    data class CompareCard(
        val title: String,
        val leftName: String,
        val rightName: String,
        val rows: List<CompareRow>,
        val leftKey: String,
        val rightKey: String,
    ) : AnswerBlock()

    /** K 线图卡片 */
    data class ChartCard(
        val title: String,
        val subtitle: String,
        val bars: List<KLineBar>,
        val instrumentKey: String,
    ) : AnswerBlock()

    /** 指标网格 */
    data class MetricGrid(val items: List<Metric>) : AnswerBlock()

    /** 标签行 */
    data class Tags(val tags: List<InsightTag>) : AnswerBlock()

    /** 风险提示 */
    data class Risk(val title: String, val body: String) : AnswerBlock()

    /** 追问建议 */
    data class FollowUps(val items: List<String>) : AnswerBlock()
}

data class CompareRow(
    val label: String,
    val left: String,
    val right: String,
    val diff: String,
    val diffTone: TagTone,
)

data class Metric(val label: String, val value: String, val tone: TagTone = TagTone.NEUTRAL)

/**
 * 一次完整的 AI 回答（用于生成后的持久化与渲染）。
 */
data class AiAnswer(
    val intent: Intent,
    val blocks: List<AnswerBlock>,
    /** 回答涉及的标的，供上下文追问使用 */
    val relatedSnapshots: List<MarketSnapshot> = emptyList(),
)
