package com.tencent.kuiklybase.chart.model

data class ChartSeries(
    val name: String,
    val points: List<ChartDataPoint>,
    val color: Long,
)
