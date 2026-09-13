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

fun ViewContainer<*, *>.LimitUpLadderCardView(block: AnswerBlock.LimitUpLadderCard) {
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.TREND_UP, 16f, AppTheme.ink)
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
                    backgroundColor(AppTheme.ink)
                    borderRadius(4f)
                    paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                }
                Text {
                    attr {
                        text("最高 ${block.maxLevel} 连板")
                        fontSize(11f)
                        fontWeight600()
                        color(Color.WHITE)
                    }
                }
            }
        }
        block.levels.forEach { level ->
            View {
                attr { flexDirectionRow(); marginTop(10f) }
                View {
                    attr {
                        width(44f)
                        height(22f)
                        borderRadius(4f)
                        backgroundColor(if (level.level >= block.maxLevel) AppTheme.downSoft else AppTheme.surfaceMuted)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("${level.level}板")
                            fontSize(11f)
                            fontWeight600()
                            color(if (level.level >= block.maxLevel) AppTheme.down else AppTheme.textSecondary)
                        }
                    }
                }
                View {
                    attr { flex(1f); flexDirectionRow(); flexWrapWrap(); marginLeft(8f) }
                    level.stocks.forEach { stock ->
                        View {
                            attr {
                                backgroundColor(AppTheme.surfaceMuted)
                                borderRadius(6f)
                                paddingLeft(8f); paddingRight(8f); paddingTop(5f); paddingBottom(5f)
                                marginRight(6f); marginBottom(6f)
                                flexDirectionRow(); alignItemsCenter()
                            }
                            Text { attr { text(stock.name); fontSize(12f); fontWeight600(); color(AppTheme.textPrimary) } }
                            Text {
                                attr {
                                    text(stock.changePct)
                                    fontSize(11f)
                                    fontWeight500()
                                    color(AppTheme.up)
                                    marginLeft(5f)
                                }
                            }
                            Text {
                                attr {
                                    text(stock.marketCap)
                                    fontSize(10f)
                                    color(AppTheme.textTertiary)
                                    marginLeft(5f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region 资金流向卡片
