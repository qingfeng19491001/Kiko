package com.kuikly.stockchat.ui.market

import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.max

private val mintBar = Color(0xFFB6E3CFL)
private val trackGray = Color(0xFFE8EAEDL)
private val areaUp = Color(0x6612924AL)
private val areaDown = Color(0x66D93B3BL)

internal fun ViewContainer<*, *>.MarketIndexOverview(host: MarketListScreenHost) {
    val snaps = host.viewModel.overviewSnaps.toList()
    View {
        attr {
            paddingTop(10f)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(Color(0xFFF3F6F8L), 0f),
                ColorStop(Color(0xFFF5F6F8L), 1f),
            )
        }
        MarketSectionTitle(MarketBoardOverview.indicesTitle(host.viewModel.board))
        View {
            attr {
                flexDirectionRow()
                paddingTop(12f)
                paddingBottom(12f)
                paddingLeft(8f)
                paddingRight(8f)
                backgroundColor(Color.WHITE)
            }
            if (snaps.isEmpty()) {
                repeat(3) { MarketIndexColumnSkeleton() }
            } else {
                snaps.forEach { snap ->
                    MarketIndexColumn(snap) { host.onOpenInstrument(snap.quote.instrument) }
                }
            }
        }
    }
    val stats = host.viewModel.overviewStats
    if (stats != null && !stats.isEmpty) {
        MarketStatsOverview(host, stats)
    }
}

private fun ViewContainer<*, *>.MarketIndexColumn(snap: MarketSnapshot, onClick: () -> Unit) {
    val quote = snap.quote
    val color = AppTheme.changeColor(quote.change)
    View {
        attr { flex(1f); alignItemsCenter(); paddingLeft(4f); paddingRight(4f) }
        event { click { onClick() } }
        Text {
            attr {
                text(quote.instrument.name)
                fontSize(12f)
                color(Color(0xFF8B8F97L))
                lines(1)
            }
        }
        Text {
            attr {
                text(NumberFormat.price(quote.price))
                fontSize(15f)
                fontWeight600()
                color(color)
                marginTop(6f)
                lines(1)
            }
        }
        Text {
            attr {
                text(NumberFormat.signedPct(quote.changePct))
                fontSize(12f)
                color(color)
                marginTop(2f)
            }
        }
        View {
            attr { height(40f); marginTop(8f) }
            if (snap.sparkline.size >= 2) {
                SparklineChart(
                    values = snap.sparkline,
                    width = 78f,
                    height = 40f,
                    color = color,
                    fillAlphaColor = if (quote.change >= 0) areaUp else areaDown,
                )
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketStatsOverview(host: MarketListScreenHost, stats: MarketOverviewStats) {
    View {
        attr {
            paddingTop(14f)
            backgroundColor(Color(0xFFF5F6F8L))
        }
        MarketSectionTitle(MarketBoardOverview.statsTitle(host.viewModel.board))
        View {
            attr { flexDirectionRow(); alignItemsStretch(); backgroundColor(Color.WHITE) }
            if (stats.hasTurnover) {
                MarketStatCard {
                    event { click { host.onOpenInstrument(stats.turnoverInstrument) } }
                    MarketStatTitle("成交额")
                    Text {
                        attr {
                            text(stats.turnoverPrefix)
                            fontSize(12f)
                            color(Color(0xFF8B8F97L))
                            marginTop(10f)
                        }
                    }
                    Text {
                        attr {
                            text(formatYi(stats.turnoverYuan))
                            fontSize(22f)
                            fontWeight700()
                            color(Color(0xFF191919L))
                            marginTop(2f)
                            lines(1)
                        }
                    }
                    View { attr { flex(1f); minHeight(16f) } }
                    TurnoverBars()
                }
            }
            if (stats.hasTurnover && stats.hasSize) {
                View { attr { width(0.5f); backgroundColor(AppTheme.divider) } }
            }
            if (stats.hasSize) {
                MarketStatCard {
                    event { click { host.onOpenInstrument(stats.sizeInstrument) } }
                    MarketStatTitle(stats.sizeTitle)
                    Text {
                        attr {
                            text(stats.sizeHeadline)
                            fontSize(22f)
                            fontWeight700()
                            color(Color(0xFF191919L))
                            marginTop(10f)
                            lines(1)
                        }
                    }
                    View { attr { flex(1f); minHeight(12f) } }
                    View {
                        attr { flexDirectionRow(); alignItemsFlexEnd(); height(70f) }
                        SizeBar(stats.largeLabel, stats.largePct)
                        SizeBar(stats.midLabel, stats.midPct)
                        SizeBar(stats.smallLabel, stats.smallPct)
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.MarketSectionTitle(title: String) {
    Text {
        attr {
            text(title)
            fontSize(16f)
            fontWeight600()
            color(Color(0xFF191919L))
            marginLeft(16f)
            marginRight(16f)
            marginBottom(10f)
        }
    }
}

private fun formatYi(value: Double?): String {
    if (value == null || value <= 0) return "--"
    return NumberFormat.fixed(value / 1_0000_0000.0, 0) + "亿"
}

private fun ViewContainer<*, *>.TurnoverBars() {
    val heights = listOf(22f, 34f, 28f, 42f, 52f)
    View {
        attr { flexDirectionRow(); alignItemsFlexEnd(); height(52f) }
        heights.forEachIndexed { index, height ->
            if (index > 0) View { attr { width(7f) } }
            View {
                attr {
                    width(10f)
                    height(height)
                    borderRadius(5f)
                    backgroundColor(if (index == heights.lastIndex) AppTheme.down else trackGray)
                }
            }
        }
        View { attr { flex(1f) } }
    }
}

private fun ViewContainer<*, *>.MarketStatCard(content: ViewContainer<*, *>.() -> Unit) {
    View {
        attr {
            flex(1f)
            minHeight(168f)
            paddingTop(16f)
            paddingBottom(14f)
            paddingLeft(16f)
            paddingRight(16f)
            backgroundColor(Color.WHITE)
        }
        content()
    }
}

private fun ViewContainer<*, *>.MarketStatTitle(title: String) {
    Text {
        attr {
            text(title)
            fontSize(13f)
            color(Color(0xFF8B8F97L))
        }
    }
}

private fun ViewContainer<*, *>.SizeBar(label: String, pct: Double?) {
    val color = pct?.let { AppTheme.changeColor(it) } ?: AppTheme.textTertiary
    val barH = if (pct == null) 32f else max(32f, (32f + abs(pct).toFloat() / 2.2f * 20f).coerceAtMost(50f))
    View {
        attr { flex(1f); alignItemsCenter(); justifyContentFlexEnd() }
        Text {
            attr {
                text(if (pct == null) "--" else NumberFormat.signedPct(pct))
                fontSize(10f)
                fontWeight500()
                color(color)
            }
        }
        View {
            attr {
                width(26f)
                height(barH)
                marginTop(6f)
                borderRadius(7f)
                backgroundColor(mintBar)
            }
        }
        Text {
            attr {
                text(label)
                fontSize(10f)
                color(Color(0xFF8B8F97L))
                marginTop(6f)
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketIndexColumnSkeleton() {
    View {
        attr { flex(1f); height(96f); alignItemsCenter(); padding(8f) }
        View { attr { width(48f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted) } }
        View { attr { width(64f); height(16f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(8f) } }
        View { attr { width(40f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(6f) } }
    }
}
