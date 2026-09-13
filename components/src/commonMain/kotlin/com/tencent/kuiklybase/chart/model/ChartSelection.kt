package com.tencent.kuiklybase.chart.model

sealed class ChartSelection {
    data class Cartesian(
        val seriesIndex: Int,
        val itemIndex: Int,
        val label: String? = null,
    ) : ChartSelection()

    data class Slice(
        val sliceIndex: Int,
        val label: String? = null,
    ) : ChartSelection()

    data class Radar(
        val seriesIndex: Int,
        val dimensionIndex: Int,
        val label: String? = null,
    ) : ChartSelection()
}
