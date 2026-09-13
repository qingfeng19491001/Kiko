package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuiklybase.chart.model.ChartViewport
import kotlin.math.max

class CartesianScale(
    val plot: PlotRect,
    private val viewport: ChartViewport,
) {
    fun toPixelX(dataX: Float): Float {
        val range = max(viewport.xMax - viewport.xMin, 1e-6f)
        return plot.left + (dataX - viewport.xMin) / range * plot.width
    }

    fun toPixelY(dataY: Float): Float {
        val range = max(viewport.yMax - viewport.yMin, 1e-6f)
        return plot.bottom - (dataY - viewport.yMin) / range * plot.height
    }

    fun toDataX(pixelX: Float): Float {
        val range = max(viewport.xMax - viewport.xMin, 1e-6f)
        return viewport.xMin + (pixelX - plot.left) / plot.width * range
    }

    fun toDataY(pixelY: Float): Float {
        val range = max(viewport.yMax - viewport.yMin, 1e-6f)
        return viewport.yMin + (plot.bottom - pixelY) / plot.height * range
    }
}
