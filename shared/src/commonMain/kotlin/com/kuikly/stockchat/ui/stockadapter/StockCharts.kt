package com.kuikly.stockchat.ui.stockadapter

import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.BarColor
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.*
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.bar.BarChart
import com.tencent.kuiklybase.chart.line.LineChart
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSeries as KuiklyChartSeries

class StockChartState {
    var series by observableList<KuiklyChartSeries>()

    fun ensure(next: List<KuiklyChartSeries>) {
        if (series.isEmpty()) series.addAll(next)
    }
}

fun seriesChartData(block: AnswerBlock.SeriesChartCard): List<KuiklyChartSeries> =
    block.series.map { line ->
        KuiklyChartSeries(
            name = line.name,
            color = line.colorArgb,
            points = block.categories.mapIndexed { index, label ->
                val raw = line.values.getOrNull(index)
                ChartDataPoint(
                    label = label,
                    x = index.toFloat(),
                    y = raw?.toFloat() ?: Float.NaN,
                )
            },
        )
    }

fun barChartData(block: AnswerBlock.BarChartCard): List<KuiklyChartSeries> {
    val points = block.bars.mapIndexed { index, bar ->
        val color = when (bar.color) {
            BarColor.UP -> COLOR_UP
            BarColor.DOWN -> COLOR_DOWN
            BarColor.NEUTRAL -> COLOR_ACCENT
        }
        ChartDataPoint(bar.label, index.toFloat(), bar.value.toFloat(), color)
    }
    return listOf(KuiklyChartSeries(name = block.title, color = COLOR_ACCENT, points = points))
}

fun ViewContainer<*, *>.StockLineChart(
    state: StockChartState,
    width: Float,
    height: Float,
    title: String,
    unit: String,
) {
    View {
        attr { width(width); height(height) }
        LineChart({ state.series }) {
            attr {
                flex(1f)
                this.title = title
                showPoints = false
                pointRadius = 2.5f
                smooth = false
                xAxis { show = true }
                yAxis { show = true }
                grid { show = true }
                legend { show = true; interactive = true }
                theme {
                    primaryColor = COLOR_ACCENT
                    axisColor = COLOR_TERTIARY
                    gridColor = 0xFFEEF1F5L
                    textColor = COLOR_SECONDARY
                    backgroundColor = COLOR_WHITE
                    fontSize = 10f
                    lineWidth = 1.6f
                    upColor = COLOR_UP
                    downColor = COLOR_DOWN
                }
                interaction {
                    enableTap = true
                    enableLongPressInspect = true
                    enableScale = true
                    enablePan = true
                    enableReset = true
                    enableCrosshair = true
                    lockY = true
                    clampToData = true
                    initialVisibleRatio = 1f
                }
                tooltip {
                    sharedByX = true
                    formatter { context ->
                        buildString {
                            append(context.label)
                            context.items.forEach { item ->
                                append('\n')
                                append(item.seriesName)
                                append("  ")
                                append(formatChartValue(item.point.y.toDouble(), unit))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun ViewContainer<*, *>.StockBarChart(
    state: StockChartState,
    width: Float,
    height: Float,
    title: String,
    unit: String,
) {
    View {
        attr { width(width); height(height) }
        BarChart({ state.series }) {
            attr {
                flex(1f)
                this.title = title
                xAxis { show = true }
                yAxis { show = true }
                grid { show = true }
                legend { show = true; interactive = true }
                theme {
                    primaryColor = COLOR_ACCENT
                    axisColor = COLOR_TERTIARY
                    gridColor = 0xFFEEF1F5L
                    textColor = COLOR_SECONDARY
                    backgroundColor = COLOR_WHITE
                    fontSize = 10f
                    upColor = COLOR_UP
                    downColor = COLOR_DOWN
                }
                interaction {
                    enableTap = true
                    enableLongPressInspect = true
                    enableScale = true
                    enablePan = true
                    enableReset = true
                    enableCrosshair = true
                    lockY = true
                    clampToData = true
                }
            }
        }
    }
}

fun formatChartValue(value: Double?, unit: String): String {
    if (value == null || value.isNaN()) return "--"
    return if (unit == "%") NumberFormat.signedPct(value) else NumberFormat.fixed(value, 2) + unit
}
