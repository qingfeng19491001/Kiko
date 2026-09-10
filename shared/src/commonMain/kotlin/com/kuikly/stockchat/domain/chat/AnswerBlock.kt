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

    /** 章节标题（序号徽标 + 标题 + 可选副标题） */
    data class SectionHeader(
        val index: Int,
        val title: String,
        val subtitle: String = "",
    ) : AnswerBlock()

    /** 核心结论高亮区（左侧色条 + 引言文本） */
    data class SummaryCallout(val text: String) : AnswerBlock()

    /** 柱状图卡片 */
    data class BarChartCard(
        val title: String,
        val subtitle: String = "",
        val bars: List<BarEntry>,
        val unit: String = "",
    ) : AnswerBlock()

    /** 关键价位表 */
    data class KeyLevelsCard(
        val title: String,
        val levels: List<KeyLevel>,
    ) : AnswerBlock()

    /** 仪表盘卡片（情绪指数 / 技术评分 / 估值分位） */
    data class GaugeCard(
        val title: String,
        val value: Float,
        /** 0..100 */
        val max: Float = 100f,
        val label: String,
        val description: String = "",
    ) : AnswerBlock()

    /** 市场广度卡片 */
    data class MarketBreadthCard(
        val title: String,
        val advancing: Int,
        val declining: Int,
        val limitUp: Int,
        val limitDown: Int,
        val halted: Int,
        val limitUpRate: String,
    ) : AnswerBlock()

    /** 连板梯队卡片 */
    data class LimitUpLadderCard(
        val title: String,
        val maxLevel: Int,
        val levels: List<LadderLevel>,
    ) : AnswerBlock()

    /** 资金流向卡片 */
    data class CapitalFlowCard(
        val title: String,
        val flows: List<CapitalFlow>,
    ) : AnswerBlock()
}

data class BarEntry(
    val label: String,
    val value: Double,
    val color: BarColor = BarColor.NEUTRAL,
)

enum class BarColor { UP, DOWN, NEUTRAL }

data class KeyLevel(
    val label: String,
    val value: String,
    val note: String,
    val tone: TagTone = TagTone.NEUTRAL,
)

data class LadderLevel(
    val level: Int,
    val stocks: List<LadderStock>,
)

data class LadderStock(
    val name: String,
    val code: String,
    val changePct: String,
    val marketCap: String,
)

data class CapitalFlow(
    val label: String,
    val netInflow: String,
    val pct: String,
    val tone: TagTone,
)

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
