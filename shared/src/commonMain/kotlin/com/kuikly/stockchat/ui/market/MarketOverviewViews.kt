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

private val turnoverInk = Color(0xFF191919L)
private val turnoverMuted = Color(0xFFC5CAD3L)
private val areaUp = Color(0x3312924AL)
private val areaDown = Color(0x33D93B3BL)
private const val sizePlotHalf = 32f

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
                val chartW = ((getPager().pageData.pageViewWidth - 16f) / snaps.size - 10f).coerceIn(76f, 128f)
                snaps.forEach { snap ->
                    MarketIndexColumn(snap, chartW) { host.onOpenInstrument(snap.quote.instrument) }
                }
            }
        }
    }
    val stats = host.viewModel.overviewStats
    if (stats != null && !stats.isEmpty) {
        MarketStatsOverview(host, stats)
    }
}

private fun ViewContainer<*, *>.MarketIndexColumn(
    snap: MarketSnapshot,
    chartW: Float,
    onClick: () -> Unit,
) {
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
            attr { height(48f); marginTop(8f); alignItemsCenter() }
            if (snap.overviewLine.size >= 2) {
                SparklineChart(
                    values = snap.overviewLine,
                    width = chartW,
                    height = 48f,
                    color = color,
                    fillAlphaColor = if (quote.change >= 0) areaUp else areaDown,
                    showEndDot = true,
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
                    View { attr { flex(1f); minHeight(12f) } }
                    if (stats.hasTurnoverShares) {
                        TurnoverShareChart(stats.turnoverShares)
                    }
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
                    View { attr { flex(1f); minHeight(8f) } }
                    SizeCompareChart(stats)
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

private fun ViewContainer<*, *>.TurnoverShareChart(shares: List<MarketTurnoverShare>) {
    View {
        attr {
            height(8f)
            flexDirectionRow()
            overflow(true)
            borderRadius(4f)
            backgroundColor(AppTheme.surfaceMuted)
        }
        shares.forEachIndexed { index, share ->
            View {
                attr {
                    flex(MarketOverviewCharts.turnoverWeight(share.yuan))
                    height(8f)
                    backgroundColor(if (index == 0) turnoverInk else turnoverMuted)
                }
            }
        }
    }
    View {
        attr { flexDirectionRow(); marginTop(10f) }
        shares.forEachIndexed { index, share ->
            if (index > 0) View { attr { width(12f) } }
            View {
                attr { flex(1f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr {
                            size(6f, 6f)
                            borderRadius(3f)
                            backgroundColor(if (index == 0) turnoverInk else turnoverMuted)
                        }
                    }
                    Text {
                        attr {
                            text(share.label)
                            fontSize(11f)
                            color(Color(0xFF8B8F97L))
                            marginLeft(4f)
                        }
                    }
                }
                Text {
                    attr {
                        text(formatYi(share.yuan))
                        fontSize(12f)
                        fontWeight600()
                        color(Color(0xFF191919L))
                        marginTop(2f)
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.SizeCompareChart(stats: MarketOverviewStats) {
    val maxAbs = stats.sizeMaxAbs
    View {
        attr { height(sizePlotHalf * 2 + 36f) }
        View {
            attr {
                positionAbsolute()
                left(4f)
                right(4f)
                top(16f + sizePlotHalf)
                height(0.5f)
                backgroundColor(AppTheme.divider)
            }
        }
        View {
            attr { flexDirectionRow(); alignItemsStretch(); height(sizePlotHalf * 2 + 36f) }
            SizeBar(stats.largeLabel, stats.largePct, maxAbs)
            SizeBar(stats.midLabel, stats.midPct, maxAbs)
            SizeBar(stats.smallLabel, stats.smallPct, maxAbs)
        }
    }
}

private fun ViewContainer<*, *>.MarketStatCard(content: ViewContainer<*, *>.() -> Unit) {
    View {
        attr {
            flex(1f)
            minHeight(196f)
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

private fun ViewContainer<*, *>.SizeBar(label: String, pct: Double?, maxAbs: Double) {
    val color = pct?.let { AppTheme.changeColor(it) } ?: AppTheme.textTertiary
    val up = (pct ?: 0.0) >= 0
    val barH = MarketOverviewCharts.sizeBarHeight(pct, maxAbs, sizePlotHalf)
    View {
        attr { flex(1f); alignItemsCenter() }
        View {
            attr { height(16f); allCenter() }
            if (pct != null && up) {
                Text {
                    attr {
                        text(NumberFormat.signedPct(pct))
                        fontSize(10f)
                        fontWeight500()
                        color(color)
                    }
                }
            }
        }
        View {
            attr { width(22f); height(sizePlotHalf * 2); alignItemsCenter() }
            View {
                attr {
                    width(22f)
                    height(sizePlotHalf)
                    alignItemsCenter()
                    justifyContentFlexEnd()
                }
                if (up) {
                    View {
                        attr {
                            width(22f)
                            height(barH)
                            borderRadius(6f)
                            backgroundColor(if (pct == null) AppTheme.surfaceMuted else color)
                        }
                    }
                }
            }
            View {
                attr {
                    width(22f)
                    height(sizePlotHalf)
                    alignItemsCenter()
                    justifyContentFlexStart()
                }
                if (!up) {
                    View {
                        attr {
                            width(22f)
                            height(barH)
                            borderRadius(6f)
                            backgroundColor(color)
                        }
                    }
                }
            }
        }
        View {
            attr { height(16f); allCenter() }
            if (pct != null && !up) {
                Text {
                    attr {
                        text(NumberFormat.signedPct(pct))
                        fontSize(10f)
                        fontWeight500()
                        color(color)
                    }
                }
            }
        }
        Text {
            attr {
                text(label)
                fontSize(10f)
                color(Color(0xFF8B8F97L))
                marginTop(2f)
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketIndexColumnSkeleton() {
    View {
        attr { flex(1f); height(112f); alignItemsCenter(); padding(8f) }
        View { attr { width(48f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted) } }
        View { attr { width(64f); height(16f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(8f) } }
        View { attr { width(40f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(6f) } }
    }
}
