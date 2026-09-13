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

fun ViewContainer<*, *>.StockCardView(
    block: AnswerBlock.StockCard,
    contentWidth: Float,
    onOpen: (key: String) -> Unit,
) {
    val q = block.quote
    val ins = q.instrument
    InstrumentCache.put(ins)
    val changeColor = AppTheme.changeColor(q.change)
    val sparkW = 96f
    Card(padding = 14f, onClick = { onOpen(ins.key) }) {
        attr { marginTop(8f); marginBottom(4f) }
        // 头部：头像 + 名称 + 代码 + 状态
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            InstrumentAvatar(ins, 38f)
            View {
                attr { flex(1f); marginLeft(10f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text(ins.name)
                            fontSize(16f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                        }
                    }
                    View {
                        attr {
                            marginLeft(6f)
                            backgroundColor(AppTheme.surfaceMuted)
                            borderRadius(4f)
                            paddingLeft(5f); paddingRight(5f); paddingTop(1f); paddingBottom(1f)
                        }
                        Text {
                            attr {
                                text(ins.displayCode)
                                fontSize(10f)
                                color(AppTheme.textSecondary)
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text(q.tradingStatus + " · " + q.updateTime)
                        fontSize(11f)
                        color(AppTheme.textTertiary)
                        marginTop(3f)
                    }
                }
            }
            Icon(IconKind.CHEVRON_RIGHT, 18f, AppTheme.textTertiary)
        }
        // 价格行 + 迷你走势
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginTop(12f) }
            View {
                attr { flex(1f) }
                View {
                    attr { flexDirectionRow(); alignItemsFlexEnd() }
                    Text {
                        attr {
                            text(NumberFormat.price(q.price))
                            fontSize(28f)
                            fontWeight700()
                            color(changeColor)
                        }
                    }
                    Text {
                        attr {
                            text(ins.market.currency)
                            fontSize(11f)
                            color(AppTheme.textTertiary)
                            marginLeft(4f)
                            marginBottom(5f)
                        }
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(4f) }
                    Text {
                        attr {
                            text(NumberFormat.signed(q.change))
                            fontSize(13f)
                            fontWeight500()
                            color(changeColor)
                            marginRight(6f)
                        }
                    }
                    ChangeBadge(q.change, NumberFormat.signedPct(q.changePct), filled = true)
                }
            }
            if (block.sparkline.size >= 2) {
                SparklineChart(
                    values = block.sparkline,
                    width = sparkW,
                    height = 44f,
                    color = changeColor,
                    fillAlphaColor = if (q.isDown) Color(0x33E5484DL) else Color(0x3316A34AL),
                )
            }
        }
        // 指标行
        Divider(vertical = 12f)
        View {
            attr { flexDirectionRow(); justifyContentSpaceBetween() }
            if (ins.isIndex) {
                LabelValue("今开", NumberFormat.price(q.open))
                LabelValue("最高", NumberFormat.price(q.high), AppTheme.up)
                LabelValue("最低", NumberFormat.price(q.low), AppTheme.down)
                LabelValue("成交额", NumberFormat.compact(q.turnover), alignRight = true)
            } else {
                LabelValue("成交额", NumberFormat.compact(q.turnover))
                LabelValue("市盈率", NumberFormat.ratio(q.pe))
                LabelValue("市值", NumberFormat.capFromYi(q.totalMarketCap ?: q.marketCap))
                LabelValue("振幅", NumberFormat.pct(q.amplitude), alignRight = true)
            }
        }
        // 技术标签
        block.analysis?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            View {
                attr { flexDirectionRow(); flexWrapWrap(); marginTop(12f) }
                tags.forEach { TagChip(it) }
            }
        }
        if (q.isMock) {
            Text {
                attr {
                    text("离线演示数据，仅供体验")
                    fontSize(10f)
                    color(AppTheme.textTertiary)
                    marginTop(4f)
                }
            }
        }
    }
}
