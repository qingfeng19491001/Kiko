package com.tencent.kuiklybase.chart.line

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuiklybase.chart.config.LineChartAttr
import com.tencent.kuiklybase.chart.config.ChartTooltipContext
import com.tencent.kuiklybase.chart.config.ChartTooltipItem
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.withPlotClip
import com.tencent.kuiklybase.chart.core.cartesian.CartesianChartView
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayout
import com.tencent.kuiklybase.chart.core.cartesian.cartesianChartView
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport
import kotlin.math.abs

class LineChartView(seriesProvider: () -> ObservableList<ChartSeries>) :
    CartesianChartView<LineChartAttr>(seriesProvider) {

    override fun createAttr() = LineChartAttr()

    override fun buildTooltipText(
        data: List<ChartSeries>,
        seriesIndex: Int,
        pointIndex: Int,
    ): String {
        val context = buildLineTooltipContext(data, seriesIndex, pointIndex, attr.tooltip.sharedByX)
        return attr.tooltip.format(context) ?: defaultLineTooltipText(context)
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
        val hasExtras = attr.thresholds.isNotEmpty() ||
            attr.annotations.isNotEmpty() ||
            attr.fillBelow ||
            !attr.connectNulls ||
            ChartCanvasRenderer.containsNonFinitePoint(series)
        if (!hasExtras) {
            ChartCanvasRenderer.drawGrid(context, layout, viewport, theme, config.grid.show)
            drawChartAxes(context, layout, viewport, series)
            context.withPlotClip(layout.plot) {
                ChartCanvasRenderer.drawLineSeries(
                    context, layout, viewport, series, theme, selection,
                    showPoints = config.showPoints,
                    pointRadius = config.pointRadius,
                    smooth = config.smooth,
                )
            }
            return
        }
        ChartCanvasRenderer.drawGrid(context, layout, viewport, theme, config.grid.show)
        drawChartAxes(context, layout, viewport, series)
        context.withPlotClip(layout.plot) {
            ChartCanvasRenderer.drawLineSeriesEnhanced(
                context,
                layout,
                viewport,
                series,
                theme,
                selection,
                config.smooth,
                config.showPoints,
                config.pointRadius,
                config.connectNulls,
                config.fillBelow,
                config.thresholds,
                config.annotations,
            )
        }
    }
}

internal fun buildLineTooltipContext(
    series: List<ChartSeries>,
    selectedSeriesIndex: Int,
    selectedPointIndex: Int,
    sharedByX: Boolean,
): ChartTooltipContext {
    val selected = series[selectedSeriesIndex].points[selectedPointIndex]
    val items = if (sharedByX) {
        series.mapIndexedNotNull { seriesIndex, item ->
            val pointIndex = item.points.indexOfFirst { point ->
                point.x.isFinite() && point.y.isFinite() && abs(point.x - selected.x) <= 0.0001f
            }
            if (pointIndex < 0) null else ChartTooltipItem(
                seriesName = item.name,
                point = item.points[pointIndex],
                seriesIndex = seriesIndex,
                pointIndex = pointIndex,
            )
        }
    } else {
        listOf(
            ChartTooltipItem(
                seriesName = series[selectedSeriesIndex].name,
                point = selected,
                seriesIndex = selectedSeriesIndex,
                pointIndex = selectedPointIndex,
            ),
        )
    }
    return ChartTooltipContext(
        label = selected.label.ifEmpty { selected.x.toString() },
        x = selected.x,
        items = items,
    )
}

internal fun defaultLineTooltipText(context: ChartTooltipContext): String {
    val item = context.items.singleOrNull()
    if (item != null) {
        return if (item.seriesName.isNotEmpty()) {
            "${item.seriesName} · ${context.label}: ${item.point.y}"
        } else {
            "${context.label}: ${item.point.y}"
        }
    }
    return buildString {
        append(context.label)
        context.items.forEach { tooltipItem ->
            append('\n')
            append(tooltipItem.seriesName)
            append(": ")
            append(tooltipItem.point.y)
        }
    }
}

fun ViewContainer<*, *>.LineChart(
    seriesProvider: () -> ObservableList<ChartSeries>,
    init: LineChartView.() -> Unit,
) {
    cartesianChartView(LineChartView(seriesProvider), init)
}
