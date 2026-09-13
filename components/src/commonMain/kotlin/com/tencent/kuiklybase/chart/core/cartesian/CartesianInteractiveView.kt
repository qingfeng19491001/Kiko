package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.layoutFrameDidChange
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.CartesianChartAttr
import com.tencent.kuiklybase.chart.config.CartesianChartEvent
import com.tencent.kuiklybase.chart.config.ChartTheme
import com.tencent.kuiklybase.chart.config.ChartViewportCommand
import com.tencent.kuiklybase.chart.config.ChartViewportRequest
import com.tencent.kuiklybase.chart.core.toChartColor
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartViewport

/**
 * 笛卡尔图交互壳：视口 / 手势 / Tooltip / 框选与十字准星。
 * 系列图与 K 线共用，子类只负责数据同步、绘制与点击命中。
 */
abstract class CartesianInteractiveView<A : CartesianChartAttr> :
    ComposeView<A, CartesianChartEvent>() {

    var viewport by observable(ChartViewport(0f, 1f, 0f, 1f))
        private set
    var dataBounds by observable(ChartViewport(0f, 1f, 0f, 1f))
        private set
    var homeViewport by observable(ChartViewport(0f, 1f, 0f, 1f))
        private set
    var selection by observable<ChartSelection?>(null)
        protected set

    protected var dragSelection by observable<ClosedFloatingPointRange<Float>?>(null)
    protected var crosshairX by observable<Float?>(null)
    protected var crosshairY by observable<Float?>(null)
    protected var tooltipText by observable("")
    protected var tooltipX by observable(0f)
    protected var tooltipY by observable(0f)
    protected var tooltipWidth by observable(0f)
    protected var showTooltip by observable(false)
    private var selectionOverlayVisible by observable(false)
    protected var canvasWidth by observable(0f)
    protected var canvasHeight by observable(0f)
    protected var canvasOffsetX by observable(0f)
    protected var canvasOffsetY by observable(0f)

    private var gestureController: ChartGestureController? = null
    private var touchHandler: ChartTouchViewportHandler? = null
    private var hasUserViewportOverride = false
    private val viewportCommandDrainScheduler = ViewportCommandDrainScheduler(
        setTimeout = { callback -> this.setTimeout(0) { callback() } },
        clearTimeout = { timeoutRef -> this.clearTimeout(timeoutRef) },
    )
    private var viewportCommandQueue: ViewportCommandQueue? = null
    private var handledViewportCommandSequence: Int? = null

    private fun createViewportCommandQueue(): ViewportCommandQueue {
        val executor = ViewportCommandExecutor(
            zoomBy = { factor ->
                ensureGestureReady()
                gestureController?.zoomBy(factor)
            },
            panByRatio = { ratio ->
                ensureGestureReady()
                gestureController?.panByRatio(ratio)
            },
            reset = ::resetViewport,
        )
        return ViewportCommandQueue(
            scheduleDrain = viewportCommandDrainScheduler::schedule,
            execute = executor::execute,
            initialAcceptedSequence = handledViewportCommandSequence,
            onAcceptedSequence = { handledViewportCommandSequence = it },
        )
    }

    protected val config: A
        get() = attr

    protected open fun resolvedTheme(): ChartTheme = attr.theme.resolved()

    override fun createEvent() = CartesianChartEvent()

    protected fun applyViewportBounds(bounds: ChartViewport, fingerprintChanged: Boolean) {
        val home = bounds.focusedXWindow(
            ratio = attr.interaction.initialVisibleRatio,
            anchor = attr.interaction.initialVisibleAnchor,
        )
        if (!fingerprintChanged && bounds == dataBounds && home == homeViewport) return
        dataBounds = bounds
        homeViewport = home
        val resolved = resolveViewportAfterDataChange(
            viewport,
            home,
            hasUserViewportOverride,
            bounds,
            attr.interaction.clampToData,
            attr.interaction.lockY,
        )
        val changed = resolved != viewport
        viewport = resolved
        if (changed) reconcileSelectionOverlay(resolved)
        bindGestureController()
    }

    protected fun resetViewport() {
        hasUserViewportOverride = false
        onBeforeResetViewport()
        syncDataFromProvider()
        viewport = homeViewport
        dragSelection = null
        crosshairX = null
        crosshairY = null
        showTooltip = false
        selectionOverlayVisible = false
        selection = null
        bindGestureController()
        event.onViewportChange?.invoke(viewport)
        event.onSelectionChange?.invoke(null)
    }

    protected fun clearSelection() {
        val hadSelection = selection != null || showTooltip
        selection = null
        showTooltip = false
        selectionOverlayVisible = false
        crosshairX = null
        crosshairY = null
        if (hadSelection) event.onSelectionChange?.invoke(null)
    }

    protected open fun onBeforeResetViewport() {}

    protected fun ensureGestureReady() {
        if (gestureController == null || touchHandler == null) {
            bindGestureController()
        }
    }

    private fun bindGestureController() {
        gestureController = ChartGestureController(
            interaction = attr.interaction,
            viewport = viewport,
            defaultViewport = dataBounds,
            onViewportChanged = { newViewport ->
                hasUserViewportOverride = true
                viewport = newViewport
                reconcileSelectionOverlay(newViewport)
                event.onViewportChange?.invoke(newViewport)
            },
        )
        if (touchHandler == null) {
            touchHandler = ChartTouchViewportHandler(
                interaction = attr.interaction,
                controllerProvider = { gestureController },
                scaleProvider = { currentScale() },
                onBrushRangeChanged = { dragSelection = it },
                onBrushFinished = { range ->
                    if (range != null) event.onDragSelect?.invoke(range)
                },
                onCrosshair = { x, y ->
                    crosshairX = x
                    crosshairY = y
                },
                preferNativePan = true,
            )
        }
    }

    protected fun currentScale(): CartesianScale {
        val w = canvasWidth.coerceAtLeast(1f)
        val h = canvasHeight.coerceAtLeast(1f)
        return CartesianScale(CartesianLayoutEngine.compute(w, h).plot, viewport)
    }

    protected fun showSelectionTooltip(
        text: String,
        localX: Float,
        localY: Float,
        crossX: Float?,
        crossY: Float? = null,
    ) {
        tooltipText = text
        val anchorX = crossX ?: localX
        val anchorY = crossY ?: localY
        tooltipWidth = estimateTooltipWidth(text, canvasWidth)
        val tip = resolveTooltipPosition(
            canvasOffsetX = canvasOffsetX,
            canvasOffsetY = canvasOffsetY,
            localX = anchorX,
            localY = anchorY,
            containerWidth = canvasWidth,
            tooltipWidth = tooltipWidth,
        )
        tooltipX = tip.first
        tooltipY = tip.second
        showTooltip = true
        selectionOverlayVisible = true
        if (attr.interaction.enableCrosshair) {
            crosshairX = crossX
            crosshairY = crossY
        }
    }

    override fun created() {
        super.created()
        syncDataFromProvider()
    }

    override fun viewWillLoad() {
        super.viewWillLoad()
        viewportCommandDrainScheduler.activate()
        viewportCommandQueue = createViewportCommandQueue()
    }

    override fun viewWillUnload() {
        viewportCommandDrainScheduler.deactivate()
        viewportCommandQueue?.close()
        viewportCommandQueue = null
        super.viewWillUnload()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                val theme = ctx.resolvedTheme()
                attr {
                    flex(1f)
                    flexDirection(FlexDirection.COLUMN)
                    backgroundColor(theme.backgroundColor.toChartColor())
                }
                vif({ ctx.attr.title.isNotEmpty() }) {
                    Text {
                        attr {
                            text(ctx.attr.title)
                            fontSize(16f)
                            fontWeightSemiBold()
                            color(theme.textColor.toChartColor())
                            marginBottom(4f)
                            marginLeft(12f)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                    }
                    event {
                        layoutFrameDidChange { frame ->
                            ctx.canvasOffsetX = frame.x
                            ctx.canvasOffsetY = frame.y
                            if (frame.width > 0f) ctx.canvasWidth = frame.width
                            if (frame.height > 0f) ctx.canvasHeight = frame.height
                        }
                        click { params ->
                            if (!ctx.attr.interaction.enableTap) return@click
                            if (ctx.touchHandler?.wasMoved() == true) return@click
                            ctx.onPlotClick(params.x, params.y)
                        }
                        doubleClick {
                            if (!ctx.attr.interaction.enableReset) return@doubleClick
                            ctx.resetViewport()
                        }
                        longPress { params ->
                            if (params.state != "start") return@longPress
                            when (resolveLongPressAction(ctx.attr.interaction)) {
                                LongPressAction.BRUSH -> {
                                    ctx.ensureGestureReady()
                                    ctx.gestureController?.armBrush()
                                    ctx.touchHandler?.beginBrush(params.x)
                                }
                                LongPressAction.INSPECT -> ctx.onPlotClick(params.x, params.y)
                                LongPressAction.NONE -> Unit
                            }
                        }
                        pan { params ->
                            if (!ctx.attr.interaction.enablePan &&
                                !ctx.attr.interaction.enableDragSelect
                            ) return@pan
                            ctx.ensureGestureReady()
                            ctx.touchHandler?.onNativePan(params.state, params.x, params.y)
                        }
                        touchDown { params ->
                            ctx.ensureGestureReady()
                            ctx.touchHandler?.onTouchDown(params)
                        }
                        touchMove { params ->
                            ctx.touchHandler?.onTouchMove(params)
                        }
                        touchUp { params ->
                            ctx.touchHandler?.onTouchUp(params)
                        }
                    }
                    Canvas({ attr { flex(1f) } }) { context, width, height ->
                        ctx.canvasWidth = width
                        ctx.canvasHeight = height
                        ctx.syncDataFromProvider()
                        ctx.viewportCommandQueue?.offer(ctx.attr.viewportRequest)
                        val layout = CartesianLayoutEngine.compute(width, height)
                        val currentViewport = ctx.viewport
                        ctx.drawPlot(context, width, height, layout, currentViewport, ctx.selection)
                        CartesianOverlayRenderer.drawBrush(
                            context, layout, currentViewport, ctx.dragSelection,
                            ctx.resolvedTheme().primaryColor,
                        )
                        val selectedCrosshair = if (ctx.selectionOverlayVisible) {
                            ctx.selectionCrosshair(layout, currentViewport)
                        } else null
                        val crosshairX = if (ctx.selectionOverlayVisible) selectedCrosshair?.first else ctx.crosshairX
                        val crosshairY = if (ctx.selectionOverlayVisible) selectedCrosshair?.second else ctx.crosshairY
                        ctx.drawCrosshairOverlay(
                            context,
                            layout,
                            crosshairX,
                            crosshairY,
                        )
                    }
                    ctx.renderCanvasOverlay(this)
                }
                ctx.renderBelowCanvas(this)
                vif({ ctx.showTooltip }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(ctx.tooltipX)
                            top(ctx.tooltipY)
                            backgroundColor(Color(0xD9000000))
                            borderRadius(6f)
                            width(ctx.tooltipWidth)
                            padding(8f, 10f, 8f, 10f)
                        }
                        Text {
                            attr {
                                text(ctx.tooltipText)
                                fontSize(12f)
                                color(Color.WHITE)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 图例等画布下方内容；默认无。 */
    protected open fun renderBelowCanvas(parent: ViewContainer<*, *>) {}

    /** Content positioned over the canvas coordinate space. */
    protected open fun renderCanvasOverlay(parent: ViewContainer<*, *>) {}

    protected open fun selectionCrosshair(
        layout: CartesianLayout,
        viewport: ChartViewport,
    ): Pair<Float, Float>? = null

    protected open fun isSelectionVisible(viewport: ChartViewport): Boolean = true

    private fun reconcileSelectionOverlay(viewport: ChartViewport) {
        val visible = selection == null || isSelectionVisible(viewport)
        if (shouldHideSelectionOverlay(selection != null, visible)) {
            showTooltip = false
            selectionOverlayVisible = false
            crosshairX = null
            crosshairY = null
        } else {
            selectionOverlayVisible = resolveSelectionOverlayVisibility(selectionOverlayVisible, visible)
        }
    }

    protected open fun drawCrosshairOverlay(
        context: ContextApi,
        layout: CartesianLayout,
        crosshairX: Float?,
        crosshairY: Float?,
    ) {
        CartesianOverlayRenderer.drawCrosshair(
            context,
            layout,
            attr.interaction.enableCrosshair,
            crosshairX,
            crosshairY,
            resolvedTheme().primaryColor,
        )
    }

    protected abstract fun syncDataFromProvider()

    protected abstract fun drawPlot(
        context: ContextApi,
        width: Float,
        height: Float,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
    )

    protected abstract fun onPlotClick(x: Float, y: Float)
}

internal fun shouldHideSelectionOverlay(hasSelection: Boolean, selectionVisible: Boolean): Boolean =
    hasSelection && !selectionVisible

internal fun resolveSelectionOverlayVisibility(wasVisible: Boolean, selectionVisible: Boolean): Boolean =
    wasVisible && selectionVisible

internal data class ViewportCommandDispatch(
    val handledSequence: Int,
    val command: ChartViewportCommand?,
)

internal fun resolveViewportCommandDispatch(
    handledSequence: Int?,
    request: ChartViewportRequest,
): ViewportCommandDispatch = if (handledSequence != null && request.sequence <= handledSequence) {
    ViewportCommandDispatch(handledSequence, null)
} else {
    ViewportCommandDispatch(
        handledSequence = request.sequence,
        command = request.command.takeUnless { it == ChartViewportCommand.NONE },
    )
}

internal class ViewportCommandExecutor(
    private val zoomBy: (Float) -> Unit,
    private val panByRatio: (Float) -> Unit,
    private val reset: () -> Unit,
) {
    fun execute(command: ChartViewportCommand) {
        when (command) {
            ChartViewportCommand.ZOOM_IN -> zoomBy(1.25f)
            ChartViewportCommand.ZOOM_OUT -> zoomBy(0.8f)
            ChartViewportCommand.PAN_LEFT -> panByRatio(-0.2f)
            ChartViewportCommand.PAN_RIGHT -> panByRatio(0.2f)
            ChartViewportCommand.RESET -> reset()
            ChartViewportCommand.NONE -> Unit
        }
    }
}

internal class ViewportCommandQueue(
    private val scheduleDrain: (() -> Unit) -> Unit,
    private val execute: (ChartViewportCommand) -> Unit,
    initialAcceptedSequence: Int? = null,
    private val onAcceptedSequence: (Int) -> Unit = {},
) {
    private var acceptedSequence: Int? = initialAcceptedSequence
    private val pending = mutableListOf<PendingViewportCommand>()
    private var drainScheduled = false
    private var closed = false

    fun offer(request: ChartViewportRequest) {
        if (closed) return
        val decision = resolveViewportCommandDispatch(acceptedSequence, request)
        if (decision.handledSequence == acceptedSequence) return
        acceptedSequence = decision.handledSequence
        onAcceptedSequence(decision.handledSequence)
        val command = decision.command ?: return
        pending += PendingViewportCommand(decision.handledSequence, command)
        if (drainScheduled) return
        drainScheduled = true
        scheduleDrain(::drain)
    }

    fun close() {
        closed = true
        pending.clear()
        drainScheduled = false
    }

    private fun drain() {
        if (closed) return
        drainScheduled = false
        val batch = pending.toList()
        pending.clear()
        batch.forEach { pendingCommand -> execute(pendingCommand.command) }
    }
}

private data class PendingViewportCommand(
    val sequence: Int,
    val command: ChartViewportCommand,
)

internal class ViewportCommandDrainScheduler(
    private val setTimeout: (() -> Unit) -> String,
    private val clearTimeout: (String) -> Unit,
) {
    private var active = false
    private var generation = 0
    private var timeoutRef: String? = null

    fun activate() {
        deactivate()
        active = true
        generation += 1
    }

    fun deactivate() {
        active = false
        generation += 1
        timeoutRef?.let(clearTimeout)
        timeoutRef = null
    }

    fun schedule(drain: () -> Unit) {
        if (!active || timeoutRef != null) return
        val scheduledGeneration = generation
        timeoutRef = setTimeout {
            if (!active || generation != scheduledGeneration) return@setTimeout
            timeoutRef = null
            drain()
        }
    }
}

internal enum class LongPressAction {
    NONE,
    INSPECT,
    BRUSH,
}

internal fun resolveLongPressAction(interaction: com.tencent.kuiklybase.chart.config.ChartInteractionConfig): LongPressAction =
    when {
        interaction.enableDragSelect -> LongPressAction.BRUSH
        interaction.enableLongPressInspect -> LongPressAction.INSPECT
        else -> LongPressAction.NONE
    }

internal object CartesianOverlayRenderer {
    fun drawBrush(
        context: ContextApi,
        layout: CartesianLayout,
        currentViewport: ChartViewport,
        range: ClosedFloatingPointRange<Float>?,
        primaryColor: Long,
    ) {
        if (range == null) return
        val scale = CartesianScale(layout.plot, currentViewport)
        val left = scale.toPixelX(range.start).coerceIn(layout.plot.left, layout.plot.right)
        val right = scale.toPixelX(range.endInclusive).coerceIn(layout.plot.left, layout.plot.right)
        val l = minOf(left, right)
        val r = maxOf(left, right)
        val plot = layout.plot
        context.beginPath()
        context.moveTo(plot.left, plot.top)
        context.lineTo(l, plot.top)
        context.lineTo(l, plot.bottom)
        context.lineTo(plot.left, plot.bottom)
        context.closePath()
        context.fillStyle(Color(0x33101827))
        context.fill()
        context.beginPath()
        context.moveTo(r, plot.top)
        context.lineTo(plot.right, plot.top)
        context.lineTo(plot.right, plot.bottom)
        context.lineTo(r, plot.bottom)
        context.closePath()
        context.fillStyle(Color(0x33101827))
        context.fill()
        context.beginPath()
        context.moveTo(l, plot.top)
        context.lineTo(r, plot.top)
        context.lineTo(r, plot.bottom)
        context.lineTo(l, plot.bottom)
        context.closePath()
        context.fillStyle(Color(primaryColor.withAlpha(0x33)))
        context.fill()
        context.beginPath()
        context.strokeStyle(Color(primaryColor.withAlpha(0xFF)))
        context.lineWidth(1.5f)
        context.moveTo(l, plot.top)
        context.lineTo(l, plot.bottom)
        context.moveTo(r, plot.top)
        context.lineTo(r, plot.bottom)
        context.stroke()
    }

    fun drawCrosshair(
        context: ContextApi,
        layout: CartesianLayout,
        enabled: Boolean,
        cx: Float?,
        cy: Float?,
        primaryColor: Long,
    ) {
        if (!enabled) return
        if (cx == null && cy == null) return
        val plot = layout.plot
        context.strokeStyle(Color(primaryColor.withAlpha(0x99)))
        context.lineWidth(1f)
        if (cx != null) {
            val x = cx.coerceIn(plot.left, plot.right)
            context.beginPath()
            context.moveTo(x, plot.top)
            context.lineTo(x, plot.bottom)
            context.stroke()
        }
        if (cy != null) {
            val y = cy.coerceIn(plot.top, plot.bottom)
            context.beginPath()
            context.moveTo(plot.left, y)
            context.lineTo(plot.right, y)
            context.stroke()
        }
    }

    fun drawCrosshairLines(
        context: ContextApi,
        enabled: Boolean,
        x: Float,
        verticalTop: Float,
        verticalBottom: Float,
        horizontalLeft: Float,
        horizontalRight: Float,
        horizontalY: Float?,
        primaryColor: Long,
    ) {
        if (!enabled || listOf(x, verticalTop, verticalBottom, horizontalLeft, horizontalRight).any { !it.isFinite() }) return
        context.strokeStyle(Color(primaryColor.withAlpha(0x99)))
        context.lineWidth(1f)
        context.beginPath()
        context.moveTo(x, verticalTop)
        context.lineTo(x, verticalBottom)
        context.stroke()
        if (horizontalY?.isFinite() == true) {
            context.beginPath()
            context.moveTo(horizontalLeft, horizontalY)
            context.lineTo(horizontalRight, horizontalY)
            context.stroke()
        }
    }
}

private fun Long.withAlpha(alpha: Int): Long =
    (this and 0x00FFFFFFL) or (alpha.toLong() shl 24)
