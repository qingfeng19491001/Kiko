package com.tencent.kuiklybase.chart.area

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuiklybase.chart.config.AreaChartAttr
import com.tencent.kuiklybase.chart.config.AreaMode
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.cartesian.CartesianChartView
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayout
import com.tencent.kuiklybase.chart.core.cartesian.cartesianChartView
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport

class AreaChartView(seriesProvider: () -> ObservableList<ChartSeries>) :
    CartesianChartView<AreaChartAttr>(seriesProvider) {

    override fun createAttr() = AreaChartAttr()

    override fun computeDefaultViewport(data: List<ChartSeries>): ChartViewport {
        val viewport = ChartViewport.fromSeries(data)
        return when (attr.mode) {
            AreaMode.STACKED -> stackedAreaViewport(data, viewport)
            AreaMode.PERCENT_STACKED -> viewport.copy(yMin = 0f, yMax = 108f)
            AreaMode.STREAM -> streamViewport(data, viewport)
            else -> viewport
        }
    }

    override fun drawChart(
        context: ContextApi,
        width: Float,
        height: Float,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
        series: List<ChartSeries>,
    ) {
        val theme = config.theme.resolved()
        ChartCanvasRenderer.drawGrid(context, layout, viewport, theme, config.grid.show)
        drawChartAxes(context, layout, viewport, series)
        ChartCanvasRenderer.drawAreaSeries(
            context, layout, viewport, series, theme,
            gradientFill = attr.gradientFill,
            selection = selection,
            smooth = attr.smooth,
            showPoints = attr.showPoints,
            pointRadius = attr.pointRadius,
            mode = attr.mode,
        )
    }
}

internal fun stackedAreaViewport(
    series: List<ChartSeries>,
    fallback: ChartViewport = ChartViewport.fromSeries(series),
): ChartViewport {
    val pointCount = series.maxOfOrNull { it.points.size } ?: return fallback
    var minPrefix = 0f
    var maxPrefix = 0f
    for (pointIndex in 0 until pointCount) {
        var prefix = 0f
        series.forEach { item ->
            val value = item.points.getOrNull(pointIndex)?.y ?: return@forEach
            if (!value.isFinite()) return@forEach
            prefix += value
            minPrefix = minOf(minPrefix, prefix)
            maxPrefix = maxOf(maxPrefix, prefix)
        }
    }
    val range = (maxPrefix - minPrefix).coerceAtLeast(1f)
    val padding = range * 0.08f
    return fallback.copy(yMin = minPrefix - padding, yMax = maxPrefix + padding)
}

internal fun streamViewport(
    series: List<ChartSeries>,
    fallback: ChartViewport = ChartViewport.fromSeries(series),
): ChartViewport {
    val pointCount = series.maxOfOrNull { it.points.size } ?: return fallback
    val maxTotal = (0 until pointCount).maxOfOrNull { pointIndex ->
        series.sumOf { item -> item.points.getOrNull(pointIndex)?.y?.toDouble() ?: 0.0 }.toFloat()
    } ?: return fallback
    val halfRange = (maxTotal / 2f).coerceAtLeast(1f)
    val padding = halfRange * 0.08f
    return fallback.copy(
        yMin = -halfRange - padding,
        yMax = halfRange + padding,
    )
}

fun ViewContainer<*, *>.AreaChart(
    seriesProvider: () -> ObservableList<ChartSeries>,
    init: AreaChartView.() -> Unit,
) {
    cartesianChartView(AreaChartView(seriesProvider), init)
}
