package com.tencent.kuiklybase.chart.scatter

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuiklybase.chart.config.ScatterChartAttr
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.cartesian.CartesianChartView
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayout
import com.tencent.kuiklybase.chart.core.cartesian.cartesianChartView
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport

class ScatterChartView(seriesProvider: () -> ObservableList<ChartSeries>) :
    CartesianChartView<ScatterChartAttr>(seriesProvider) {

    override fun createAttr() = ScatterChartAttr()

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
        ChartCanvasRenderer.drawScatterSeries(
            context, layout, viewport, series, theme, selection,
            pointRadius = attr.pointRadius,
        )
    }
}

fun ViewContainer<*, *>.ScatterChart(
    seriesProvider: () -> ObservableList<ChartSeries>,
    init: ScatterChartView.() -> Unit,
) {
    cartesianChartView(ScatterChartView(seriesProvider), init)
}
