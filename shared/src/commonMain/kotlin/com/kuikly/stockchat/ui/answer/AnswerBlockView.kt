package com.kuikly.stockchat.ui.answer

import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.ui.chat.ChartProbe
import com.kuikly.stockchat.ui.stockadapter.StockChartState
import com.kuikly.stockchat.ui.stockadapter.StockTableState
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind

fun ViewContainer<*, *>.AnswerBlockView(
    block: AnswerBlock,
    contentWidth: Float,
    onOpenInstrument: (key: String) -> Unit,
    onFollowUp: (text: String) -> Unit,
    probe: ChartProbe? = null,
    tableState: StockTableState? = null,
    chartState: StockChartState? = null,
) {
    when (block) {
        is AnswerBlock.Markdown -> Unit // 由流式 Markdown 组件单独渲染
        is AnswerBlock.StockCard -> StockCardView(block, contentWidth, onOpenInstrument)
        is AnswerBlock.CompareCard -> CompareCardView(block, contentWidth, onOpenInstrument, tableState)
        is AnswerBlock.ChartCard -> vbind({ probe?.index ?: -1 }) {
            ChartCardView(block, contentWidth, onOpenInstrument, onFollowUp, probe)
        }
        is AnswerBlock.MetricGrid -> MetricGridView(block, contentWidth)
        is AnswerBlock.Tags -> TagsView(block)
        is AnswerBlock.Risk -> RiskView(block)
        is AnswerBlock.FollowUps -> FollowUpsView(block, onFollowUp)
        is AnswerBlock.SectionHeader -> SectionHeaderView(block)
        is AnswerBlock.SummaryCallout -> SummaryCalloutView(block)
        is AnswerBlock.BarChartCard -> BarChartCardView(block, contentWidth, chartState)
        is AnswerBlock.KeyLevelsCard -> KeyLevelsCardView(block, contentWidth, tableState)
        is AnswerBlock.GaugeCard -> GaugeCardView(block, contentWidth)
        is AnswerBlock.MarketBreadthCard -> MarketBreadthCardView(block)
        is AnswerBlock.LimitUpLadderCard -> LimitUpLadderCardView(block)
        is AnswerBlock.CapitalFlowCard -> CapitalFlowCardView(block, contentWidth, tableState)
        is AnswerBlock.PeerTableCard -> PeerTableCardView(block, contentWidth, onOpenInstrument, tableState)
        is AnswerBlock.SeriesChartCard -> SeriesChartCardView(block, contentWidth, chartState, probe)
        is AnswerBlock.HighlightsCard -> HighlightsCardView(block)
    }
}
