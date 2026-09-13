package com.tencent.kuiklybase.chart.stock

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.ChartTheme
import com.tencent.kuiklybase.chart.config.StockAuxiliaryIndicator
import com.tencent.kuiklybase.chart.config.StockChartAttr
import com.tencent.kuiklybase.chart.config.StockMainIndicator
import com.tencent.kuiklybase.chart.config.StockPriceDisplayMode
import com.tencent.kuiklybase.chart.config.StockRenderConfig
import com.tencent.kuiklybase.chart.config.resolveStockTheme
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.AxisLabelWidthCache
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickPlanner
import com.tencent.kuiklybase.chart.core.cartesian.CartesianInteractiveView
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayout
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayoutEngine
import com.tencent.kuiklybase.chart.core.cartesian.CartesianOverlayRenderer
import com.tencent.kuiklybase.chart.core.cartesian.CartesianScale
import com.tencent.kuiklybase.chart.core.cartesian.PlotRect
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.OhlcPoint

class StockChartView(
    private val ohlcProvider: () -> ObservableList<OhlcPoint>,
    private val toolbarContent: ViewBuilder?,
) : CartesianInteractiveView<StockChartAttr>() {

    constructor(ohlcProvider: () -> ObservableList<OhlcPoint>) : this(ohlcProvider, null)

    private var lastSnapshot: List<OhlcPoint>? = null
    private var lastPricePlot: PlotRect? = null
    private var lastPriceViewport: ChartViewport? = null
    private var lastCompositePlots: CompositeStockPlots? = null
    private val axisLabelWidthCache = AxisLabelWidthCache(256)
    private val computationCache = StockComputationCache()
    private var mainHeaderContent: ViewBuilder? = null
    private var firstPaneHeaderContent: ViewBuilder? = null
    private var secondPaneHeaderContent: ViewBuilder? = null
    private var overlayState by observable(
        StockOverlayState(
            revision = 0,
            toolbar = StockOverlaySlot(false, false, PlotRect(0f, 0f, 0f, 0f)),
            mainHeader = StockOverlaySlot(false, false, PlotRect(0f, 0f, 0f, 0f)),
            firstHeader = StockOverlaySlot(false, false, PlotRect(0f, 0f, 0f, 0f)),
            secondHeader = StockOverlaySlot(false, false, PlotRect(0f, 0f, 0f, 0f)),
        ),
    )

    /**
     * 头部插槽布局变化回调：依次为主图、第一副图、第二副图的头部区域
     * （Chart 视图内部坐标，槽位隐藏时对应参数为 null）。
     * 弹出菜单等超出插槽边界的内容应在 Chart 外部按此锚定展示，
     * 超出插槽边界的子视图无法响应触摸。
     */
    var onHeaderSlotsChange: ((mainHeader: PlotRect?, firstPaneHeader: PlotRect?, secondPaneHeader: PlotRect?) -> Unit)? =
        null

    fun mainHeader(builder: ViewBuilder) {
        mainHeaderContent = builder
    }

    fun firstPaneHeader(builder: ViewBuilder) {
        firstPaneHeaderContent = builder
    }

    fun secondPaneHeader(builder: ViewBuilder) {
        secondPaneHeaderContent = builder
    }

    override fun createAttr() = StockChartAttr()

    override fun renderCanvasOverlay(parent: ViewContainer<*, *>) {
        refreshOverlayState()
        val ctx = this
        parent.apply {
            fun renderSlot(content: ViewBuilder?, resolve: (StockOverlayState) -> StockOverlaySlot) {
                if (content == null) return
                View {
                    attr {
                        // 必须在 attr 块内读取 observable 的 overlayState，
                        // 否则布局完成后槽位从隐藏变为可见时不会触发重绘。
                        val slot = resolve(ctx.overlayState)
                        positionAbsolute()
                        left(slot.rect.left)
                        top(slot.rect.top)
                        width(slot.rect.width)
                        height(slot.rect.height)
                        visibility(slot.visible)
                        touchEnable(slot.visible)
                    }
                    content.invoke(this)
                }
            }
            renderSlot(this@StockChartView.toolbarContent) { it.toolbar }
            renderSlot(this@StockChartView.mainHeaderContent) { it.mainHeader }
            renderSlot(this@StockChartView.firstPaneHeaderContent) { it.firstHeader }
            renderSlot(this@StockChartView.secondPaneHeaderContent) { it.secondHeader }
        }
    }

    private fun overlayBuilders(): StockOverlayBuilders = StockOverlayBuilders(
        toolbar = toolbarContent != null,
        mainHeader = mainHeaderContent != null,
        firstHeader = firstPaneHeaderContent != null,
        secondHeader = secondPaneHeaderContent != null,
    )

    private fun refreshOverlayState(
        plot: PlotRect = CartesianLayoutEngine.compute(canvasWidth, canvasHeight).plot,
        config: StockRenderConfig = attr.renderConfig,
    ) {
        val next = resolveStockOverlayState(plot, config, overlayBuilders())
        if (next == overlayState) return
        overlayState = next
        notifyHeaderSlotsChange(next)
    }

    private fun notifyHeaderSlotsChange(state: StockOverlayState) {
        val listener = onHeaderSlotsChange ?: return
        fun visibleRect(slot: StockOverlaySlot): PlotRect? = if (slot.visible) slot.rect else null
        listener(visibleRect(state.mainHeader), visibleRect(state.firstHeader), visibleRect(state.secondHeader))
    }

    override fun resolvedTheme(): ChartTheme = resolveStockTheme(attr.theme, attr.renderConfig.preset)

    override fun onBeforeResetViewport() {
        lastSnapshot = null
        computationCache.clear()
    }

    override fun syncDataFromProvider() {
        val data = ohlcProvider().toList()
        val bounds = ChartViewport.fromOhlc(data)
        val changed = data != lastSnapshot
        if (stockDataChanged(lastSnapshot, data)) clearSelection()
        lastSnapshot = data
        applyViewportBounds(bounds, changed)
    }

    override fun drawPlot(
        context: ContextApi,
        width: Float,
        height: Float,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
    ) {
        val data = ohlcProvider().toList()
        val renderConfig = attr.renderConfig
        refreshOverlayState(layout.plot, renderConfig)
        val composite = renderConfig.firstPane.show || renderConfig.secondPane.show
        if (!composite) {
            lastCompositePlots = null
            drawLegacy(context, layout, viewport, selection, data, renderConfig)
            return
        }
        drawComposite(context, layout, viewport, selection, data, renderConfig)
    }

    private fun drawComposite(
        context: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
        data: List<OhlcPoint>,
        renderConfig: StockRenderConfig,
    ) {
        val theme = resolvedTheme()
        val plots = splitCompositeStockPlots(
            plot = layout.plot,
            firstVisible = renderConfig.firstPane.show && !renderConfig.panesCollapsed,
            secondVisible = renderConfig.secondPane.show && !renderConfig.panesCollapsed,
            firstRatio = renderConfig.firstPane.heightRatio,
            secondRatio = renderConfig.secondPane.heightRatio,
            toolbarHeight = stockCompositeToolbarHeight(toolbarContent != null),
        )
        val visibleX = viewport.xMin..viewport.xMax
        val mainLines = computationCache.resolve(data, "MAIN:${renderConfig.mainIndicator}") {
            resolveMainIndicatorLines(data, renderConfig.mainIndicator)
        }
        val currentPrice = visibleCurrentPrice(data, visibleX)
        val priceBounds = visibleCompositePriceBounds(data, mainLines, visibleX, currentPrice)
        val priceViewport = ChartViewport(viewport.xMin, viewport.xMax, priceBounds.start, priceBounds.endInclusive)
        val priceSections = splitStockPricePane(
            plots.price,
            headerHeight = if (mainLines.isEmpty()) 18f else 34f,
            xAxisHeight = 18f,
        )
        val priceLayout = CartesianLayout(priceSections.content)
        context.font(theme.fontSize)
        val measuredWidths = data.associate { point ->
            val text = point.label.ifBlank { point.x.toString() }
            point.x to measuredAxisLabelWidth(context, text)
        }
        val ellipsisWidth = measuredAxisLabelWidth(context, "…")
        val xPlan = AxisTickPlanner.plan(
            stockAxisTickInput(data, priceViewport, priceSections.content, theme, measuredWidths, ellipsisWidth),
        )
        if (compositePriceRenderDecision(data) == CompositePriceRenderDecision.EMPTY) {
            ChartCanvasRenderer.drawPaneEmptyState(context, priceSections.content, "暂无数据", theme)
        } else {
            ChartCanvasRenderer.drawGrid(context, priceLayout, priceViewport, theme, attr.grid.show, xPlan)
            ChartCanvasRenderer.drawAxes(
                context, priceLayout, priceViewport, theme,
                showX = attr.xAxis.show,
                showY = attr.yAxis.show,
                plannedXTicks = xPlan,
            )
            when (resolvePriceRenderMode(renderConfig.priceDisplayMode)) {
                StockPriceDisplayMode.CANDLE -> ChartCanvasRenderer.drawCandlesticks(
                    context, priceLayout, priceViewport, data, theme, selection, renderConfig.candleWidthRatio,
                )
                StockPriceDisplayMode.LINE -> ChartCanvasRenderer.drawStockCloseLine(
                    context, priceLayout, priceViewport, data, theme,
                )
            }
            ChartCanvasRenderer.drawStockLines(
                context, priceLayout, priceViewport, mainLines, theme, source = data,
            )
        }
        val displayIndex = stockDisplayIndex(
            data,
            priceViewport,
            (selection as? ChartSelection.Cartesian)?.itemIndex,
        )
        displayIndex?.let { index ->
            val summarySplit = (priceSections.header.top + 18f).coerceAtMost(priceSections.header.bottom)
            ChartCanvasRenderer.drawPaneHeader(
                context,
                stockPaneHeaderValueRect(priceSections.header.copy(bottom = summarySplit), mainHeaderContent != null),
                if (mainHeaderContent == null) renderConfig.mainIndicator.name else "",
                ohlcSummaries(data, index),
                theme,
            )
            if (mainLines.isNotEmpty()) {
                ChartCanvasRenderer.drawPaneHeader(
                    context,
                    priceSections.header.copy(top = summarySplit),
                    "",
                    lineSummaries(mainLines, index),
                    theme,
                )
            }
        }
        currentPrice?.let { price ->
            if (attr.currentPriceLine.show) {
                ChartCanvasRenderer.drawCurrentPriceLine(
                    context,
                    priceSections.content,
                    priceViewport,
                    price,
                    attr.currentPriceLine.color ?: theme.primaryColor,
                    attr.currentPriceLine.lineWidth,
                    attr.currentPriceLine.dashLength,
                    attr.currentPriceLine.dashGap,
                    attr.currentPriceLine.showLabel,
                    formatStockValue(price),
                    theme.fontSize,
                )
            }
        }

        plots.first?.let { pane ->
            drawAuxiliaryPane(
                context, pane, priceViewport, data,
                renderConfig.firstPane.indicator, xPlan, displayIndex, firstPaneHeaderContent != null,
                renderConfig.candleWidthRatio, theme,
            )
        }
        plots.second?.let { pane ->
            drawAuxiliaryPane(
                context, pane, priceViewport, data,
                renderConfig.secondPane.indicator, xPlan, displayIndex, secondPaneHeaderContent != null,
                renderConfig.candleWidthRatio, theme,
            )
        }
        lastPricePlot = priceSections.content
        lastPriceViewport = priceViewport
        lastCompositePlots = plots.copy(
            price = priceSections.content,
            first = plots.first?.let { splitStockPricePane(it).content },
            second = plots.second?.let { splitStockPricePane(it).content },
        )
    }

    private fun drawAuxiliaryPane(
        context: ContextApi,
        plot: PlotRect,
        sharedViewport: ChartViewport,
        data: List<OhlcPoint>,
        indicator: StockAuxiliaryIndicator,
        xPlan: List<com.tencent.kuiklybase.chart.core.cartesian.PlannedAxisTick>,
        displayIndex: Int?,
        hasCustomHeader: Boolean,
        candleWidthRatio: Float,
        theme: ChartTheme,
    ) {
        val paneData = computationCache.resolve(data, "AUX:$indicator") {
            resolveAuxiliaryPaneData(data, indicator)
        }
        val sections = splitStockPricePane(plot)
        val bounds = auxiliaryPaneBounds(data, paneData, sharedViewport.xMin..sharedViewport.xMax)
        val paneViewport = ChartViewport(sharedViewport.xMin, sharedViewport.xMax, bounds.start, bounds.endInclusive)
        val paneLayout = CartesianLayout(sections.content)
        ChartCanvasRenderer.drawGrid(context, paneLayout, paneViewport, theme, attr.grid.show, xPlan)
        ChartCanvasRenderer.drawAxes(
            context, paneLayout, paneViewport, theme,
            showX = false,
            showY = attr.yAxis.show,
            plannedXTicks = xPlan,
        )
        when (paneData) {
            AuxiliaryPaneData.Empty -> ChartCanvasRenderer.drawPaneEmptyState(context, sections.content, "暂无数据", theme)
            is AuxiliaryPaneData.Volume -> {
                ChartCanvasRenderer.drawStockVolumes(
                    context, paneLayout, paneViewport, data, candleWidthRatio, theme,
                )
                ChartCanvasRenderer.drawStockLines(
                    context, paneLayout, paneViewport,
                    listOf(line("VMA5", paneData.average)), theme, source = data,
                )
            }
            is AuxiliaryPaneData.Amount -> ChartCanvasRenderer.drawStockHistogram(
                context, paneLayout, paneViewport, paneData.values,
                theme.upColor, theme.downColor, data.map { it.x },
            )
            is AuxiliaryPaneData.Macd -> {
                ChartCanvasRenderer.drawStockHistogram(
                    context, paneLayout, paneViewport, paneData.result.histogram,
                    theme.upColor, theme.downColor, data.map { it.x },
                )
                ChartCanvasRenderer.drawStockLines(
                    context, paneLayout, paneViewport,
                    listOf(
                        line("DIFF", paneData.result.diff),
                        line("DEA", paneData.result.dea),
                    ),
                    theme, source = data,
                )
            }
            is AuxiliaryPaneData.Kdj -> ChartCanvasRenderer.drawStockLines(
                context, paneLayout, paneViewport,
                listOf(
                    line("K", paneData.result.k),
                    line("D", paneData.result.d),
                    line("J", paneData.result.j),
                ),
                theme, source = data,
            )
            is AuxiliaryPaneData.Rsi -> ChartCanvasRenderer.drawStockLines(
                context, paneLayout, paneViewport, paneData.lines, theme, source = data,
            )
            is AuxiliaryPaneData.Wr -> ChartCanvasRenderer.drawStockLines(
                context, paneLayout, paneViewport,
                listOf(line("WR", paneData.values)), theme, source = data,
            )
            is AuxiliaryPaneData.Bbd -> ChartCanvasRenderer.drawStockLines(
                context, paneLayout, paneViewport,
                listOf(
                    line("BBD", paneData.values),
                    line("BBD5", paneData.avg5),
                ),
                theme, source = data,
            )
        }
        displayIndex?.let { index ->
            ChartCanvasRenderer.drawPaneHeader(
                context,
                stockPaneHeaderValueRect(sections.header, hasCustomHeader),
                if (hasCustomHeader) "" else indicator.name,
                auxiliarySummaries(paneData, index),
                theme,
            )
        }
    }

    private fun drawLegacy(
        context: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
        data: List<OhlcPoint>,
        renderConfig: StockRenderConfig,
    ) {
        val theme = resolvedTheme()
        val showLegend = attr.movingAverages.show && attr.movingAverages.lines.isNotEmpty()
        val contentPlot = if (showLegend) layout.plot.copy(top = layout.plot.top + 18f) else layout.plot
        val showVolume = shouldShowVolumePanel(data, attr.volumePanel.show)
        val plots = splitStockPlots(contentPlot, showVolume, attr.volumePanel.heightRatio)
        val priceLayout = CartesianLayout(plots.price)
        context.font(theme.fontSize)
        val measuredWidths = data.associate { point ->
            val text = point.label.ifBlank { point.x.toString() }
            point.x to measuredAxisLabelWidth(context, text)
        }
        val ticks = AxisTickPlanner.plan(
            stockAxisTickInput(
                data, viewport, plots.price, theme, measuredWidths,
                measuredAxisLabelWidth(context, "…"),
            ),
        )
        val axisFrame = stockAxisFramePlan(ticks)
        ChartCanvasRenderer.drawGrid(
            context, priceLayout, viewport, theme, attr.grid.show, axisFrame.gridTicks,
        )
        ChartCanvasRenderer.drawAxes(
            context, priceLayout, viewport, theme,
            attr.xAxis.show && !showVolume, attr.yAxis.show,
            plannedXTicks = axisFrame.priceAxisTicks,
        )
        ChartCanvasRenderer.drawCandlesticks(
            context, priceLayout, viewport, data, theme, selection, renderConfig.candleWidthRatio,
        )
        if (attr.movingAverages.show) {
            attr.movingAverages.lines.forEach { average ->
                val values = computationCache.resolve(data, "MA:${average.period}") {
                    stockMovingAverage(data, average.period)
                }
                ChartCanvasRenderer.drawStockAverageLine(
                    context, priceLayout, viewport,
                    stockAveragePoints(data, values),
                    average.color, theme,
                )
            }
        }
        if (showLegend) {
            val selectedIndex = (selection as? ChartSelection.Cartesian)?.itemIndex ?: data.lastIndex
            ChartCanvasRenderer.drawStockLegend(
                context, layout.plot.left, layout.plot.top + theme.fontSize,
                attr.movingAverages.lines,
                attr.movingAverages.lines.associate { average ->
                    val values = computationCache.resolve(data, "MA:${average.period}") {
                        stockMovingAverage(data, average.period)
                    }
                    average.period to values.getOrNull(selectedIndex)
                },
                theme,
            )
        }
        plots.volume?.let { volumePlot ->
            val maximum = stockVolumeBounds(data).endInclusive.coerceAtLeast(1f)
            val volumeViewport = ChartViewport(viewport.xMin, viewport.xMax, 0f, maximum * 1.08f)
            val volumeLayout = CartesianLayout(volumePlot)
            ChartCanvasRenderer.drawGrid(
                context, volumeLayout, volumeViewport, theme, attr.grid.show, axisFrame.gridTicks,
            )
            ChartCanvasRenderer.drawAxes(
                context, volumeLayout, volumeViewport, theme,
                attr.xAxis.show, attr.yAxis.show,
                plannedXTicks = axisFrame.volumeAxisTicks,
            )
            ChartCanvasRenderer.drawStockVolumes(
                context, volumeLayout, volumeViewport, data, renderConfig.candleWidthRatio, theme,
            )
            attr.volumePanel.averageLines.forEach { average ->
                val values = computationCache.resolve(data, "VMA:${average.period}") {
                    stockVolumeMovingAverage(data, average.period)
                }
                ChartCanvasRenderer.drawStockAverageLine(
                    context, volumeLayout, volumeViewport,
                    data.zip(values).mapNotNull { (point, value) -> value?.let { point.x to it } },
                    average.color, theme,
                )
            }
        }
        lastPricePlot = plots.price
        lastPriceViewport = viewport
    }

    private fun measuredAxisLabelWidth(context: ContextApi, text: String): Float {
        return axisLabelWidthCache.resolve(resolvedTheme().fontSize, text) { context.measureText(it).width }
    }

    private fun ohlcSummaries(data: List<OhlcPoint>, index: Int): List<Pair<String, Long>> {
        val point = data.getOrNull(index) ?: return emptyList()
        return listOf(
            "O:${formatStockValue(point.open)}" to 0L,
            "H:${formatStockValue(point.high)}" to 0L,
            "L:${formatStockValue(point.low)}" to 0L,
            "C:${formatStockValue(point.close)}" to 0L,
        )
    }

    private fun lineSummaries(lines: List<StockLineResult>, index: Int): List<Pair<String, Long>> =
        lines.mapNotNull { line ->
            line.values.getOrNull(index)?.takeIf(Float::isFinite)?.let { value ->
                "${line.name}:${formatStockValue(value)}" to line.color
            }
        }

    private fun auxiliarySummaries(data: AuxiliaryPaneData, index: Int): List<Pair<String, Long>> = when (data) {
        AuxiliaryPaneData.Empty -> emptyList()
        is AuxiliaryPaneData.Volume -> listOfNotNull(
            summary("VOL", data.values.getOrNull(index)),
            summary("VMA5", data.average.getOrNull(index)),
        )
        is AuxiliaryPaneData.Amount -> listOf(summary("AMT", data.values.getOrNull(index)))
        is AuxiliaryPaneData.Macd -> listOf(
            summary("DIFF", data.result.diff.getOrNull(index)),
            summary("DEA", data.result.dea.getOrNull(index)),
            summary("MACD", data.result.histogram.getOrNull(index)),
        ).filterNotNull()
        is AuxiliaryPaneData.Kdj -> listOf(
            summary("K", data.result.k.getOrNull(index)),
            summary("D", data.result.d.getOrNull(index)),
            summary("J", data.result.j.getOrNull(index)),
        ).filterNotNull()
        is AuxiliaryPaneData.Rsi -> lineSummaries(data.lines, index)
        is AuxiliaryPaneData.Wr -> listOf(summary("WR", data.values.getOrNull(index)))
        is AuxiliaryPaneData.Bbd -> listOfNotNull(
            summary("BBD", data.values.getOrNull(index)),
            summary("BBD5", data.avg5.getOrNull(index)),
        )
    }.filterNotNull()

    private fun summary(name: String, value: Float?): Pair<String, Long>? =
        value?.takeIf(Float::isFinite)?.let { "$name:${formatStockValue(it)}" to stockIndicatorColor(name) }

    private fun formatStockValue(value: Float): String = ((value * 100f).toInt() / 100f).toString()

    override fun selectionCrosshair(
        layout: CartesianLayout,
        viewport: ChartViewport,
    ): Pair<Float, Float>? {
        val selected = selection as? ChartSelection.Cartesian ?: return null
        val point = ohlcProvider().getOrNull(selected.itemIndex) ?: return null
        val plot = lastPricePlot ?: return null
        val priceViewport = lastPriceViewport ?: return null
        if (point.x !in priceViewport.xMin..priceViewport.xMax) return null
        val scale = CartesianScale(plot, priceViewport)
        return scale.toPixelX(point.x) to scale.toPixelY(point.close)
    }

    override fun isSelectionVisible(viewport: ChartViewport): Boolean {
        val selected = selection as? ChartSelection.Cartesian ?: return true
        val point = ohlcProvider().getOrNull(selected.itemIndex) ?: return false
        return point.x.isFinite() && point.x in viewport.xMin..viewport.xMax
    }

    override fun drawCrosshairOverlay(
        context: ContextApi,
        layout: CartesianLayout,
        crosshairX: Float?,
        crosshairY: Float?,
    ) {
        val plots = lastCompositePlots
        if (plots == null || crosshairX == null) {
            super.drawCrosshairOverlay(context, layout, crosshairX, crosshairY)
            return
        }
        val plan = stockCompositeCrosshairPlan(plots, crosshairX, crosshairY) ?: return
        CartesianOverlayRenderer.drawCrosshairLines(
            context,
            attr.interaction.enableCrosshair,
            plan.x,
            plan.verticalTop,
            plan.verticalBottom,
            plan.horizontalLeft,
            plan.horizontalRight,
            plan.horizontalY,
            resolvedTheme().primaryColor,
        )
    }

    override fun onPlotClick(x: Float, y: Float) {
        // 落在 toolbar / 各 pane header 上的点击属于 overlay 子树，
        // Kuikly 事件冒泡会将其传递到 Canvas 的 click handler。
        // 此时应当让 overlay 内的 click handler（菜单/指标切换）优先处理，
        // 而不要触发下方的 K 线选中。
        if (isInOverlay(x, y)) return
        val data = ohlcProvider().toList()
        if (data.isEmpty() || canvasWidth <= 0f) return
        val pricePlot = lastPricePlot ?: CartesianLayoutEngine.compute(canvasWidth, canvasHeight).plot
        val priceViewport = lastPriceViewport ?: viewport
        val bestIndex = nearestVisibleStockIndex(data, priceViewport, pricePlot, x, y, 24f) ?: return
        val currentIndex = (selection as? ChartSelection.Cartesian)?.itemIndex
        // 通用单击交互：再次单击已选中的同一根 K 线取消选中。
        if (currentIndex != null && currentIndex == bestIndex) {
            clearSelection()
            event.onSelectionChange?.invoke(null)
            return
        }
        val candle = data[bestIndex]
        val scale = CartesianScale(pricePlot, priceViewport)
        selection = ChartSelection.Cartesian(0, bestIndex, candle.label)
        event.onSelectionChange?.invoke(selection)
        event.onPointClick?.invoke(ChartDataPoint(candle.label, candle.x, candle.close), 0, bestIndex)
        showSelectionTooltip(
            text = buildString {
                append(candle.label.ifEmpty { candle.x.toString() })
                append("  O:${candle.open} H:${candle.high} L:${candle.low} C:${candle.close}")
                candle.volume?.let { append("  VOL:$it") }
            },
            localX = x,
            localY = y,
            crossX = scale.toPixelX(candle.x),
            crossY = scale.toPixelY(candle.close),
        )
    }

    private fun isInOverlay(x: Float, y: Float): Boolean {
        // overlay view 使用 positionAbsolute() 定位，left/top 基于 Canvas 内部坐标
        // slot.rect 是 Canvas 内部坐标
        // 点击坐标 (x, y) 是相对于事件处理 View（Canvas 的父容器）的坐标
        // 需要转换到 Canvas 内部坐标系：减去 canvasOffset
        val localX = x - canvasOffsetX
        val localY = y - canvasOffsetY
        val state = overlayState
        
        // 如果 overlayState 还是初始值（所有 slot 都不可见），说明还未刷新，
        // 此时应该用最新的 canvasWidth/canvasHeight 重新计算
        if (!state.mainHeader.visible && !state.firstHeader.visible && !state.secondHeader.visible && canvasWidth > 0f) {
            refreshOverlayState()
            val refreshedState = overlayState
            return containsPoint(refreshedState.toolbar, localX, localY) ||
                    containsPoint(refreshedState.mainHeader, localX, localY) ||
                    containsPoint(refreshedState.firstHeader, localX, localY) ||
                    containsPoint(refreshedState.secondHeader, localX, localY)
        }
        return containsPoint(state.toolbar, localX, localY) ||
                containsPoint(state.mainHeader, localX, localY) ||
                containsPoint(state.firstHeader, localX, localY) ||
                containsPoint(state.secondHeader, localX, localY)
    }

    private fun containsPoint(slot: StockOverlaySlot, x: Float, y: Float): Boolean {
        if (!slot.visible) return false
        val rect = slot.rect
        return x in rect.left..rect.right && y in rect.top..rect.bottom
    }
}

fun ViewContainer<*, *>.StockChart(
    ohlcProvider: () -> ObservableList<OhlcPoint>,
    init: StockChartView.() -> Unit,
) = StockChart(ohlcProvider, null, init)

fun ViewContainer<*, *>.StockChart(
    ohlcProvider: () -> ObservableList<OhlcPoint>,
    toolbar: ViewBuilder?,
    init: StockChartView.() -> Unit,
) {
    addChild(StockChartView(ohlcProvider, toolbar), init)
}
