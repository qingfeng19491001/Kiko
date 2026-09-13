package com.kuikly.stockchat.ui.answer

import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.ChartFollowUpPrompt
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
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.kuikly.stockchat.domain.chat.SeriesChartKind
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.ChartCardView(
    block: AnswerBlock.ChartCard,
    contentWidth: Float,
    onOpen: (key: String) -> Unit,
    onAskBar: (text: String) -> Unit,
    probe: ChartProbe? = null,
) {
    val chartW = contentWidth - 28f
    val selected = probe?.index?.takeIf { it in block.bars.indices } ?: block.bars.lastIndex
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.CHART, 16f, AppTheme.ink)
            View {
                attr { flex(1f); marginLeft(6f) }
                Text { attr { text(block.title); fontSize(14f); fontWeight600(); color(AppTheme.textPrimary) } }
                Text { attr { text(block.subtitle); fontSize(11f); color(AppTheme.textTertiary); marginTop(2f) } }
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter(); paddingLeft(8f); paddingTop(4f); paddingBottom(4f) }
                event { click { onOpen(block.instrumentKey) } }
                Text { attr { text("详情"); fontSize(12f); color(AppTheme.accent) } }
                Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.accent)
            }
        }
        View {
            attr { marginTop(10f) }
            CandleChart(
                bars = block.bars,
                width = chartW,
                height = 190f,
                selectedIndex = selected,
                onSelectIndex = { index -> probe?.index = index },
            )
        }
        block.bars.getOrNull(selected)?.let { bar ->
            View {
                attr { flexDirectionRow(); justifyContentSpaceBetween(); marginTop(8f) }
                LabelValue("日", bar.date.take(10), valueSize = 12f)
                LabelValue("开", NumberFormat.price(bar.open), valueSize = 12f)
                LabelValue("高", NumberFormat.price(bar.high), AppTheme.up, valueSize = 12f)
                LabelValue("低", NumberFormat.price(bar.low), AppTheme.down, valueSize = 12f)
                LabelValue("收", NumberFormat.price(bar.close), AppTheme.changeColor(bar.close - bar.open), alignRight = true, valueSize = 12f)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(10f)
                    paddingTop(8f)
                }
                event {
                    click {
                        val name = InstrumentCache.get(block.instrumentKey)?.name ?: block.title
                        ChartFollowUpPrompt.fromBars(name, block.bars, selected)?.let(onAskBar)
                    }
                }
                Text {
                    attr {
                        text("问这一根")
                        fontSize(13f)
                        fontWeight600()
                        color(AppTheme.accent)
                    }
                }
                Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.accent)
            }
        }
    }
}

// endregion
