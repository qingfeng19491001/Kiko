package com.tencent.kuiklybase.chart.horizontalbar

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuiklybase.chart.bar.BarChart
import com.tencent.kuiklybase.chart.bar.BarChartView
import com.tencent.kuiklybase.chart.model.ChartSeries

/** 水平条形图兼容入口（等价于 `BarChart { horizontal = true }`）。 */
fun ViewContainer<*, *>.HorizontalBarChart(
    seriesProvider: () -> ObservableList<ChartSeries>,
    init: BarChartView.() -> Unit,
) {
    BarChart(seriesProvider) {
        attr { horizontal = true }
        init()
    }
}
