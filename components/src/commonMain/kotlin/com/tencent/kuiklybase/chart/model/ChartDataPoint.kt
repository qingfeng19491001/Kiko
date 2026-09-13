package com.tencent.kuiklybase.chart.model

data class ChartDataPoint(
    val label: String,
    val x: Float,
    val y: Float,
    /** 单点/单柱颜色；为空时回退到系列色。 */
    val color: Long? = null,
) {
    fun resolveColor(seriesColor: Long): Long = color ?: seriesColor
}
