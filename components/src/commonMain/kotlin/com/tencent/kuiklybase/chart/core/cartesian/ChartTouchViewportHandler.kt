package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuiklybase.chart.config.ChartInteractionConfig
import kotlin.math.hypot

/**
 * 手势分工：
 * - 单指平移：优先走 Android 原生 [pan]（会 requestDisallowIntercept，避免被父级吃掉）
 * - 双指捏合：走 touch（Android 无可靠原生 pinch）
 * - 长按框选：longPress 武装后由 touch/pan 继续拖动
 */
internal class ChartTouchViewportHandler(
    private val interaction: ChartInteractionConfig,
    private val controllerProvider: () -> ChartGestureController?,
    private val scaleProvider: () -> CartesianScale,
    private val onBrushRangeChanged: (ClosedFloatingPointRange<Float>?) -> Unit,
    private val onBrushFinished: (ClosedFloatingPointRange<Float>?) -> Unit,
    private val onCrosshair: (Float?, Float?) -> Unit,
    /** true：单指平移交给原生 pan，touch 只处理双指/框选续拖。 */
    private val preferNativePan: Boolean = true,
) {
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var pinchStartDistance = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private enum class Mode { NONE, PAN, PINCH, BRUSH }

    fun onTouchDown(params: TouchParams) = onDown(params)

    fun onTouchMove(params: TouchParams) = onMove(params)

    fun onTouchUp(params: TouchParams) = onUp(params)

    /** Android 原生 pan：start/move/end。 */
    fun onNativePan(state: String, x: Float, y: Float) {
        // 框选由 longPress 武装后继续复用 pan 坐标；它不应依赖普通平移开关。
        if (!interaction.enablePan && mode != Mode.BRUSH) return
        if (mode == Mode.PINCH) return
        val controller = controllerProvider() ?: return
        val scale = scaleProvider()
        when (state) {
            "start" -> {
                if (mode == Mode.BRUSH) {
                    controller.onBrushMove(x, scale)
                    onBrushRangeChanged(controller.dragSelectRange())
                    onCrosshair(x, null)
                    moved = true
                    return
                }
                mode = Mode.PAN
                lastX = x
                lastY = y
                downX = x
                downY = y
                moved = true
                controller.onPanStart(x, y, scale)
            }
            "move" -> {
                when (mode) {
                    Mode.BRUSH -> {
                        controller.onBrushMove(x, scale)
                        onBrushRangeChanged(controller.dragSelectRange())
                        onCrosshair(x, null)
                        moved = true
                    }
                    Mode.PAN -> {
                        controller.onPanMove(x, y, scale)
                        moved = true
                    }
                    Mode.NONE -> {
                        // 部分机型会先 move 再补 start
                        mode = Mode.PAN
                        controller.onPanStart(lastX.takeIf { it != 0f } ?: x, lastY.takeIf { it != 0f } ?: y, scale)
                        controller.onPanMove(x, y, scale)
                        moved = true
                    }
                    else -> Unit
                }
                lastX = x
                lastY = y
            }
            "end" -> {
                when (mode) {
                    Mode.BRUSH -> {
                        val range = controller.onBrushEnd()
                        onBrushRangeChanged(null)
                        onBrushFinished(range)
                    }
                    Mode.PAN -> controller.onPanEnd()
                    else -> Unit
                }
                mode = Mode.NONE
                controller.disarmBrush()
            }
        }
    }

    /** 由 longPress start 调用，进入框选模式。 */
    fun beginBrush(x: Float) {
        if (!interaction.enableDragSelect) return
        val controller = controllerProvider() ?: return
        val scale = scaleProvider()
        mode = Mode.BRUSH
        moved = true
        controller.onBrushStart(x, scale)
        onBrushRangeChanged(controller.dragSelectRange())
        onCrosshair(x, null)
    }

    fun wasMoved(): Boolean = moved

    private fun onDown(params: TouchParams) {
        if (params.touches.size >= 2 && interaction.enableScale) {
            onPinchStart(params)
            return
        }
        downX = params.x
        downY = params.y
        lastX = params.x
        lastY = params.y
        moved = false
        mode = Mode.NONE
        val controller = controllerProvider() ?: return
        if (controller.isBrushArmed() && interaction.enableDragSelect) {
            beginBrush(params.x)
        }
    }

    private fun onMove(params: TouchParams) {
        if (params.touches.size >= 2 && interaction.enableScale) {
            if (mode != Mode.PINCH) onPinchStart(params) else onPinchMove(params)
            return
        }
        if (mode == Mode.PINCH && params.touches.size < 2) {
            mode = Mode.NONE
            return
        }

        val controller = controllerProvider() ?: return
        val scale = scaleProvider()
        val dist = hypot(params.x - downX, params.y - downY)

        if (mode == Mode.NONE) {
            // 单指平移交给原生 pan，避免与 GestureDetector/父级拦截打架
            if (!preferNativePan && dist > 6f && interaction.enablePan) {
                mode = Mode.PAN
                controller.onPanStart(lastX, lastY, scale)
                controller.onPanMove(params.x, params.y, scale)
                moved = true
            }
            lastX = params.x
            lastY = params.y
            return
        }

        when (mode) {
            Mode.PAN -> {
                if (!preferNativePan) {
                    controller.onPanMove(params.x, params.y, scale)
                    moved = true
                }
            }
            Mode.BRUSH -> {
                controller.onBrushMove(params.x, scale)
                onBrushRangeChanged(controller.dragSelectRange())
                onCrosshair(params.x, null)
                moved = true
            }
            else -> Unit
        }
        lastX = params.x
        lastY = params.y
    }

    private fun onPinchStart(params: TouchParams) {
        if (!interaction.enableScale) return
        val dist = touchDistance(params) ?: return
        pinchStartDistance = dist.coerceAtLeast(1f)
        mode = Mode.PINCH
        moved = true
        controllerProvider()?.onPinchStart()
        onBrushRangeChanged(null)
    }

    private fun onPinchMove(params: TouchParams) {
        val controller = controllerProvider() ?: return
        val dist = touchDistance(params) ?: return
        val factor = (dist / pinchStartDistance).coerceIn(0.05f, 40f)
        val focal = touchFocal(params) ?: return
        controller.onPinch(factor, focal.first, focal.second, scaleProvider())
    }

    private fun onUp(params: TouchParams) {
        val controller = controllerProvider()
        when (mode) {
            Mode.BRUSH -> {
                val range = controller?.onBrushEnd()
                onBrushRangeChanged(null)
                onBrushFinished(range)
            }
            Mode.PAN -> {
                if (!preferNativePan) {
                    controller?.onPanEnd()
                }
            }
            Mode.PINCH, Mode.NONE -> Unit
        }
        if (mode != Mode.PAN || !preferNativePan) {
            mode = Mode.NONE
            controller?.disarmBrush()
        }
    }

    private fun touchDistance(params: TouchParams): Float? {
        if (params.touches.size < 2) return null
        val a = params.touches[0]
        val b = params.touches[1]
        return hypot(a.x - b.x, a.y - b.y)
    }

    private fun touchFocal(params: TouchParams): Pair<Float, Float>? {
        if (params.touches.size < 2) return null
        val a = params.touches[0]
        val b = params.touches[1]
        return (a.x + b.x) / 2f to (a.y + b.y) / 2f
    }
}
