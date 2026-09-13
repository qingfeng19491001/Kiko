package com.tencent.kuiklybase.chart.core.cartesian

data class PlotRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class CartesianLayout(
    val plot: PlotRect,
)

object CartesianLayoutEngine {
    private const val AXIS_PADDING_LEFT = 50f
    private const val AXIS_PADDING_BOTTOM = 28f
    private const val AXIS_PADDING_TOP = 8f
    private const val AXIS_PADDING_RIGHT = 12f

    fun compute(width: Float, height: Float): CartesianLayout {
        return CartesianLayout(
            plot = PlotRect(
                left = AXIS_PADDING_LEFT,
                top = AXIS_PADDING_TOP,
                right = width - AXIS_PADDING_RIGHT,
                bottom = height - AXIS_PADDING_BOTTOM,
            ),
        )
    }
}
