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
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.kuikly.stockchat.domain.chat.SeriesChartKind
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.MarketBreadthCardView(block: AnswerBlock.MarketBreadthCard) {
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.COMPARE, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                    flex(1f)
                }
            }
            View {
                attr {
                    backgroundColor(AppTheme.upSoft)
                    borderRadius(4f)
                    paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                }
                Text {
                    attr {
                        text("涨停率 ${block.limitUpRate}")
                        fontSize(11f)
                        fontWeight600()
                        color(AppTheme.up)
                    }
                }
            }
        }
        // 涨跌对比条
        val total = block.advancing + block.declining
        View {
            attr {
                flexDirectionRow()
                height(8f)
                borderRadius(4f)
                overflow(true)
                backgroundColor(AppTheme.surfaceMuted)
                marginTop(12f)
            }
            if (total > 0) {
                View { attr { flex(block.advancing.toFloat()); backgroundColor(AppTheme.up) } }
                View { attr { flex(block.declining.toFloat()); backgroundColor(AppTheme.down) } }
            }
        }
        View {
            attr { flexDirectionRow(); justifyContentSpaceBetween(); marginTop(12f) }
            LabelValue("上涨", "${block.advancing}", AppTheme.up, valueSize = 15f)
            LabelValue("下跌", "${block.declining}", AppTheme.down, valueSize = 15f)
            LabelValue("涨停", "${block.limitUp}", AppTheme.up, valueSize = 15f)
            LabelValue("跌停", "${block.limitDown}", AppTheme.down, valueSize = 15f)
            LabelValue("停牌", "${block.halted}", AppTheme.textSecondary, alignRight = true, valueSize = 15f)
        }
    }
}

// endregion

// region 连板梯队卡片
