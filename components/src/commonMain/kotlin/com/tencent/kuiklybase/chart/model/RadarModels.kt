package com.tencent.kuiklybase.chart.model

data class RadarDimension(
    val label: String,
    val maxValue: Float,
)

data class RadarSeries(
    val name: String,
    val values: List<Float>,
    val color: Long,
)
