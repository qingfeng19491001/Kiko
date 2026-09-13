package com.tencent.kuiklybase.chart.bar

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuiklybase.chart.config.BarChartAttr
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.cartesian.CartesianChartView
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayout
import com.tencent.kuiklybase.chart.core.cartesian.cartesianChartView
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport

class BarChartView(seriesProvider: () -> ObservableList<ChartSeries>) :
    CartesianChartView<BarChartAttr>(seriesProvider) {

    override fun createAttr() = BarChartAttr()

    init {
        isCategoryX = true
        useCategoryHit = true
    }

    override fun computeDefaultViewport(data: List<ChartSeries>): ChartViewport {
        return ChartViewport.fromSeries(
            data,
            isCategoryX = true,
            stacked = config.stacked,
            horizontal = config.horizontal,
        )
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
        useHorizontalHit = config.horizontal
        useCategoryHit = !config.horizontal
        useStackedHit = config.stacked
        val theme = config.theme.resolved()
        ChartCanvasRenderer.drawGrid(context, layout, viewport, theme, config.grid.show)
        drawChartAxes(context, layout, viewport, series, horizontal = config.horizontal)
        when {
            config.horizontal -> ChartCanvasRenderer.drawHorizontalBarSeries(
                context, layout, viewport, series, theme, selection,
                showLabel = config.label.show,
                stacked = config.stacked,
                showTotalLabel = config.showTotalLabel,
            )
            config.stacked -> ChartCanvasRenderer.drawStackedBarSeries(
                context, layout, viewport, series, theme, selection,
                showTotalLabel = config.showTotalLabel,
            )
            else -> ChartCanvasRenderer.drawBarSeries(
                context, layout, viewport, series, theme, selection,
                showLabel = config.label.show,
            )
        }
    }
}

fun ViewContainer<*, *>.BarChart(
    seriesProvider: () -> ObservableList<ChartSeries>,
    init: BarChartView.() -> Unit,
) {
    cartesianChartView(BarChartView(seriesProvider), init)
}
