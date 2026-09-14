package com.kuikly.stockchat.ui.answer

import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.chat.ChartProbe
import com.kuikly.stockchat.ui.components.Card
import com.kuikly.stockchat.ui.components.ChangeBadge
import com.kuikly.stockchat.ui.components.Divider
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.InstrumentAvatar
import com.kuikly.stockchat.ui.components.LabelValue
import com.kuikly.stockchat.ui.components.TagChip
import com.kuikly.stockchat.ui.stockadapter.StockBarChart
import com.kuikly.stockchat.ui.stockadapter.StockChartState
import com.kuikly.stockchat.ui.stockadapter.StockLineChart
import com.kuikly.stockchat.ui.stockadapter.StockQuoteTable
import com.kuikly.stockchat.ui.stockadapter.StockTableState
import com.kuikly.stockchat.ui.stockadapter.barChartData
import com.kuikly.stockchat.ui.stockadapter.capitalFlowColumns
import com.kuikly.stockchat.ui.stockadapter.capitalFlowRows
import com.kuikly.stockchat.ui.stockadapter.compareTableColumns
import com.kuikly.stockchat.ui.stockadapter.compareTableRows
import com.kuikly.stockchat.ui.stockadapter.keyLevelColumns
import com.kuikly.stockchat.ui.stockadapter.keyLevelRows
import com.kuikly.stockchat.ui.stockadapter.peerTableColumns
import com.kuikly.stockchat.ui.stockadapter.peerTableRows
import com.kuikly.stockchat.ui.stockadapter.seriesChartData
import com.kuikly.stockchat.ui.components.charts.CandleChart
import com.kuikly.stockchat.ui.components.charts.GaugeChart
import com.kuikly.stockchat.ui.components.charts.SeriesChart
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.kuikly.stockchat.domain.chat.SeriesChartKind
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.SeriesChartCardView(
    block: AnswerBlock.SeriesChartCard,
    contentWidth: Float,
    chartState: StockChartState? = null,
    probe: ChartProbe? = null,
) {
    val chartW = contentWidth - 28f
    vbind({ probe?.index ?: -1 }) {
        val selected = probe?.index?.takeIf { it in block.categories.indices } ?: -1
        Card(padding = 14f) {
            attr { marginTop(8f); marginBottom(4f) }
            ChartCardHeader(block.title, block.subtitle)
            View {
                attr { flexDirectionRow(); flexWrapWrap(); marginTop(8f) }
                block.series.forEach { line ->
                    View {
                        attr { flexDirectionRow(); alignItemsCenter(); marginRight(12f); marginBottom(4f) }
                        View { attr { size(7f, 7f); borderRadius(4f); backgroundColor(Color(line.colorArgb)) } }
                        Text {
                            attr {
                                text(line.name)
                                fontSize(11f)
                                color(AppTheme.textSecondary)
                                marginLeft(4f)
                            }
                        }
                    }
                }
            }
            View {
                attr { marginTop(6f) }
                SeriesChart(
                    kind = block.kind,
                    categories = block.categories,
                    series = block.series,
                    width = chartW,
                    height = 188f,
                    unit = block.unit,
                    selectedIndex = selected,
                    onSelectIndex = { probe?.index = it },
                )
            }
            if (selected in block.categories.indices) {
                Text {
                    attr {
                        text(
                            buildString {
                                append(block.categories[selected])
                                block.series.forEach { line ->
                                    append("  ")
                                    append(line.name)
                                    append(" ")
                                    append(formatSeriesValue(line.values.getOrNull(selected), block.unit))
                                }
                            },
                        )
                        fontSize(11f)
                        color(AppTheme.textSecondary)
                        marginTop(8f)
                        lineHeight(16f)
                    }
                }
            }
        }
    }
}

private fun formatSeriesValue(value: Double?, unit: String): String {
    if (value == null) return "--"
    return if (unit == "%") NumberFormat.signedPct(value) else NumberFormat.fixed(value, 1)
}
