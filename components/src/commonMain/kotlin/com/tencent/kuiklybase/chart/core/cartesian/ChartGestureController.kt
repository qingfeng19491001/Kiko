package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuiklybase.chart.config.ChartInteractionConfig
import com.tencent.kuiklybase.chart.model.ChartViewport
import kotlin.math.abs
import kotlin.math.max

/**
 * 地图式视口手势：平移 / 捏合缩放 / 长按框选缩放。
 */
internal class ChartGestureController(
    private val interaction: ChartInteractionConfig,
    private var viewport: ChartViewport,
    private val defaultViewport: ChartViewport,
    private val onViewportChanged: (ChartViewport) -> Unit,
) {
    private var panMode: PanMode = PanMode.NONE
    private var panStartX = 0f
    private var panStartY = 0f
    private var dragStartDataX = 0f
    private var dragEndDataX = 0f
    private var viewportAtPinchStart = viewport
    private var brushArmed = false

    internal enum class PanMode { NONE, PAN, DRAG_SELECT }

    fun armBrush() {
        if (interaction.enableDragSelect) {
            brushArmed = true
        }
    }

    fun disarmBrush() {
        brushArmed = false
    }

    fun isBrushArmed(): Boolean = brushArmed

    fun onPanStart(x: Float, y: Float, scale: CartesianScale) {
        panStartX = x
        panStartY = y
        panMode = when {
            brushArmed && interaction.enableDragSelect -> PanMode.DRAG_SELECT
            interaction.enablePan -> PanMode.PAN
            interaction.enableDragSelect -> PanMode.DRAG_SELECT
            else -> PanMode.NONE
        }
        if (panMode == PanMode.DRAG_SELECT) {
            dragStartDataX = scale.toDataX(x)
            dragEndDataX = dragStartDataX
        }
    }

    fun onBrushStart(x: Float, scale: CartesianScale) {
        if (!interaction.enableDragSelect) return
        brushArmed = true
        panMode = PanMode.DRAG_SELECT
        panStartX = x
        dragStartDataX = scale.toDataX(x)
        dragEndDataX = dragStartDataX
    }

    fun onBrushMove(x: Float, scale: CartesianScale) {
        if (panMode != PanMode.DRAG_SELECT && !brushArmed) return
        panMode = PanMode.DRAG_SELECT
        dragEndDataX = scale.toDataX(x)
    }

    fun onPanMove(x: Float, y: Float, scale: CartesianScale) {
        when (panMode) {
            PanMode.PAN -> {
                val dx = x - panStartX
                val dy = y - panStartY
                panStartX = x
                panStartY = y
                val xRange = viewport.xMax - viewport.xMin
                val yRange = viewport.yMax - viewport.yMin
                val plot = scale.plot
                val dataDx = -dx / max(plot.width, 1f) * xRange
                val dataDy = if (interaction.lockY) {
                    0f
                } else {
                    dy / max(plot.height, 1f) * yRange
                }
                applyViewport(
                    ChartViewport(
                        xMin = viewport.xMin + dataDx,
                        xMax = viewport.xMax + dataDx,
                        yMin = viewport.yMin + dataDy,
                        yMax = viewport.yMax + dataDy,
                    ),
                )
            }
            PanMode.DRAG_SELECT -> {
                dragEndDataX = scale.toDataX(x)
            }
            PanMode.NONE -> Unit
        }
    }

    /**
     * @return 框选区间；若 [ChartInteractionConfig.brushZoom] 为 true 已应用到视口。
     */
    fun onPanEnd(): ClosedFloatingPointRange<Float>? {
        val range = dragSelectRange()
        val wasBrush = panMode == PanMode.DRAG_SELECT
        panMode = PanMode.NONE
        brushArmed = false
        if (wasBrush && range != null && interaction.brushZoom) {
            zoomToXRange(range)
        }
        return range
    }

    fun onBrushEnd(): ClosedFloatingPointRange<Float>? = onPanEnd()

    fun dragSelectRange(): ClosedFloatingPointRange<Float>? {
        if (!interaction.enableDragSelect) return null
        if (panMode != PanMode.DRAG_SELECT && !brushArmed) return null
        if (dragStartDataX == dragEndDataX) return null
        return if (dragStartDataX < dragEndDataX) {
            dragStartDataX..dragEndDataX
        } else {
            dragEndDataX..dragStartDataX
        }
    }

    fun onPinchStart() {
        if (!interaction.enableScale) return
        viewportAtPinchStart = viewport
    }

    fun onPinch(scaleFactor: Float, focalX: Float, focalY: Float, cartesianScale: CartesianScale) {
        if (!interaction.enableScale) return
        val start = viewportAtPinchStart
        val startScale = CartesianScale(cartesianScale.plot, start)
        val dataX = startScale.toDataX(focalX)
        val dataY = startScale.toDataY(focalY)
        val xRange = start.xMax - start.xMin
        val yRange = start.yMax - start.yMin
        val safeScale = scaleFactor.coerceIn(0.05f, 40f)
        val newXRange = (xRange / safeScale).coerceAtLeast(minXSpan())
        val newYRange = if (interaction.lockY) {
            yRange
        } else {
            (yRange / safeScale).coerceAtLeast(minYSpan())
        }
        val xRatio = (dataX - start.xMin) / max(xRange, 1e-6f)
        val yRatio = (dataY - start.yMin) / max(yRange, 1e-6f)
        applyViewport(
            ChartViewport(
                xMin = dataX - newXRange * xRatio,
                xMax = dataX + newXRange * (1f - xRatio),
                yMin = if (interaction.lockY) start.yMin else dataY - newYRange * yRatio,
                yMax = if (interaction.lockY) start.yMax else dataY + newYRange * (1f - yRatio),
            ),
        )
    }

    fun zoomBy(factor: Float) {
        if (!interaction.enableScale || !factor.isFinite()) return
        val safeFactor = factor.coerceIn(0.5f, 2f)
        val xRange = viewport.xMax - viewport.xMin
        val yRange = viewport.yMax - viewport.yMin
        val xCenter = (viewport.xMin + viewport.xMax) * 0.5f
        val yCenter = (viewport.yMin + viewport.yMax) * 0.5f
        val newXRange = (xRange / safeFactor).coerceAtLeast(minXSpan())
        val newYRange = if (interaction.lockY) yRange else (yRange / safeFactor).coerceAtLeast(minYSpan())
        applyViewport(
            ChartViewport(
                xMin = xCenter - newXRange * 0.5f,
                xMax = xCenter + newXRange * 0.5f,
                yMin = if (interaction.lockY) viewport.yMin else yCenter - newYRange * 0.5f,
                yMax = if (interaction.lockY) viewport.yMax else yCenter + newYRange * 0.5f,
            ),
        )
    }

    fun panByRatio(ratio: Float) {
        if (!interaction.enablePan || !ratio.isFinite()) return
        val xRange = viewport.xMax - viewport.xMin
        val delta = xRange * ratio.coerceIn(-1f, 1f)
        applyViewport(
            viewport.copy(
                xMin = viewport.xMin + delta,
                xMax = viewport.xMax + delta,
            ),
        )
    }

    fun zoomToXRange(range: ClosedFloatingPointRange<Float>) {
        val span = abs(range.endInclusive - range.start)
        if (span < minXSpan() * 0.5f) return
        val pad = span * 0.04f
        applyViewport(
            ChartViewport(
                xMin = range.start - pad,
                xMax = range.endInclusive + pad,
                yMin = viewport.yMin,
                yMax = viewport.yMax,
            ),
        )
    }

    fun reset() {
        if (!interaction.enableReset) return
        applyViewport(defaultViewport, clamp = false)
    }

    fun currentViewport(): ChartViewport = viewport

    private fun applyViewport(next: ChartViewport, clamp: Boolean = interaction.clampToData) {
        val resolved = if (clamp) clampToBounds(next, defaultViewport, interaction.lockY) else next
        if (resolved == viewport) return
        viewport = resolved
        onViewportChanged(resolved)
    }

    private fun minXSpan(): Float = max((defaultViewport.xMax - defaultViewport.xMin) * 0.02f, 1e-3f)

    private fun minYSpan(): Float = max((defaultViewport.yMax - defaultViewport.yMin) * 0.02f, 1e-3f)

    companion object {
        fun clampToBounds(
            viewport: ChartViewport,
            bounds: ChartViewport,
            lockY: Boolean,
        ): ChartViewport {
            var xMin = viewport.xMin
            var xMax = viewport.xMax
            var yMin = viewport.yMin
            var yMax = viewport.yMax
            val xSpan = max(xMax - xMin, 1e-6f)
            val ySpan = max(yMax - yMin, 1e-6f)
            val boundXSpan = max(bounds.xMax - bounds.xMin, 1e-6f)
            val boundYSpan = max(bounds.yMax - bounds.yMin, 1e-6f)

            if (xSpan >= boundXSpan) {
                xMin = bounds.xMin
                xMax = bounds.xMax
            } else {
                if (xMin < bounds.xMin) {
                    xMin = bounds.xMin
                    xMax = xMin + xSpan
                }
                if (xMax > bounds.xMax) {
                    xMax = bounds.xMax
                    xMin = xMax - xSpan
                }
            }

            if (!lockY) {
                if (ySpan >= boundYSpan) {
                    yMin = bounds.yMin
                    yMax = bounds.yMax
                } else {
                    if (yMin < bounds.yMin) {
                        yMin = bounds.yMin
                        yMax = yMin + ySpan
                    }
                    if (yMax > bounds.yMax) {
                        yMax = bounds.yMax
                        yMin = yMax - ySpan
                    }
                }
            }

            return ChartViewport(xMin, xMax, yMin, yMax)
        }
    }
}
