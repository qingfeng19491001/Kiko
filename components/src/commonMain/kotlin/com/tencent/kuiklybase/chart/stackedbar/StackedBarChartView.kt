package com.tencent.kuiklybase.chart.stackedbar

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuiklybase.chart.bar.BarChart
import com.tencent.kuiklybase.chart.bar.BarChartView
import com.tencent.kuiklybase.chart.model.ChartSeries

/** 堆叠柱状图兼容入口（等价于 `BarChart { stacked = true }`）。 */
fun ViewContainer<*, *>.StackedBarChart(
    seriesProvider: () -> ObservableList<ChartSeries>,
    init: BarChartView.() -> Unit,
) {
    BarChart(seriesProvider) {
        attr { stacked = true }
        init()
    }
}
