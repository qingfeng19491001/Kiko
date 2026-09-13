package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.SeriesCartesianChartAttr
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.toChartColor
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport

abstract class CartesianChartView<A : SeriesCartesianChartAttr>(
    protected val seriesProvider: () -> ObservableList<ChartSeries>,
) : CartesianInteractiveView<A>() {
    protected var isCategoryX: Boolean = false
    protected var useCategoryHit: Boolean = false
    protected var useHorizontalHit: Boolean = false
    protected var useStackedHit: Boolean = false

    private var lastSeriesSnapshot: List<ChartSeries>? = null
    private var hiddenSeriesNames by observable(emptySet<String>())

    private fun visibleSeries(): List<ChartSeries> =
        filterVisibleSeries(seriesProvider().toList(), hiddenSeriesNames)

    protected open fun computeDefaultViewport(data: List<ChartSeries>): ChartViewport {
        return ChartViewport.fromSeries(data, isCategoryX = isCategoryX)
    }

    override fun onBeforeResetViewport() {
        lastSeriesSnapshot = null
    }

    override fun syncDataFromProvider() {
        val data = visibleSeries()
        val bounds = computeDefaultViewport(data)
        val changed = data != lastSeriesSnapshot
        lastSeriesSnapshot = data
        applyViewportBounds(bounds, changed)
    }

    protected fun drawChartAxes(
        context: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        horizontal: Boolean = false,
        preferCategoryLabels: Boolean = isCategoryX || horizontal,
    ) {
        val labeled = series.any { s -> s.points.any { it.label.isNotEmpty() } }
        val categoryTicks = when {
            preferCategoryLabels || labeled -> ChartCanvasRenderer.axisTicksFromSeries(series)
            else -> null
        }
        ChartCanvasRenderer.drawAxes(
            context,
            layout,
            viewport,
            config.theme.resolved(),
            config.xAxis.show,
            config.yAxis.show,
            xTicks = if (horizontal) null else categoryTicks,
            yTicks = if (horizontal) categoryTicks else null,
        )
    }

    override fun renderBelowCanvas(parent: ViewContainer<*, *>) {
        val ctx = this
        parent.apply {
            vif({ ctx.attr.legend.show }) {
                View {
                    attr {
                        flexDirection(FlexDirection.ROW)
                        justifyContent(FlexJustifyContent.CENTER)
                        alignItems(FlexAlign.CENTER)
                        padding(8f)
                        flexWrapWrap()
                    }
                    vfor({ ctx.seriesProvider() }) { series ->
                        View {
                            attr {
                                flexDirection(FlexDirection.ROW)
                                alignItems(FlexAlign.CENTER)
                                marginRight(12f)
                                marginBottom(4f)
                                if (ctx.attr.legend.interactive) {
                                    padding(4f, 8f, 4f, 4f)
                                    borderRadius(4f)
                                    val hidden = ctx.hiddenSeriesNames.contains(series.name)
                                    backgroundColor(Color(if (hidden) 0xFFE5E7EB else 0xFFF5F6FA))
                                    opacity(if (hidden) 0.45f else 1f)
                                }
                            }
                            View {
                                attr {
                                    size(10f, 10f)
                                    borderRadius(5f)
                                    backgroundColor(series.color.toChartColor())
                                    marginRight(4f)
                                }
                            }
                            Text {
                                attr {
                                    text(series.name)
                                    fontSize(ctx.attr.theme.fontSize)
                                    color(ctx.attr.theme.textColor.toChartColor())
                                }
                            }
                            if (ctx.attr.legend.interactive) {
                                event {
                                    click {
                                        ctx.onLegendToggle(series.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onLegendToggle(seriesName: String) {
        hiddenSeriesNames = toggleHiddenSeries(hiddenSeriesNames, seriesName)
        selection = null
        showTooltip = false
        event.onSelectionChange?.invoke(null)
        syncDataFromProvider()
    }

    override fun drawPlot(
        context: ContextApi,
        width: Float,
        height: Float,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
    ) {
        drawChart(
            context,
            width,
            height,
            layout,
            viewport,
            selection,
            visibleSeries(),
        )
    }

    override fun selectionCrosshair(
        layout: CartesianLayout,
        viewport: ChartViewport,
    ): Pair<Float, Float>? = resolveCartesianSelectionCrosshair(
        visibleSeries(),
        selection,
        layout.plot,
        viewport,
    )

    override fun onPlotClick(x: Float, y: Float) {
        val data = visibleSeries()
        if (data.isEmpty() || canvasWidth <= 0f) {
            clearSelection()
            return
        }
        val scale = currentScale()
        val hit = when {
            useHorizontalHit -> CartesianHitTester.nearestHorizontalBar(
                data, scale, x, y, stacked = useStackedHit,
            )
            useCategoryHit -> CartesianHitTester.nearestBar(
                data, scale, x, y, stacked = useStackedHit, grouped = !useStackedHit,
            )
            else -> CartesianHitTester.nearestPoint(data, scale, x, y)
        }
        if (hit == null) {
            clearSelection()
            return
        }
        val point = data[hit.seriesIndex].points[hit.pointIndex]
        selection = ChartSelection.Cartesian(hit.seriesIndex, hit.pointIndex, point.label)
        event.onSelectionChange?.invoke(selection)
        event.onPointClick?.invoke(point, hit.seriesIndex, hit.pointIndex)
        val seriesName = data[hit.seriesIndex].name
        showSelectionTooltip(
            text = buildTooltipText(data, hit.seriesIndex, hit.pointIndex),
            localX = x,
            localY = y,
            crossX = scale.toPixelX(point.x),
            crossY = scale.toPixelY(point.y),
        )
    }

    protected open fun buildTooltipText(
        data: List<ChartSeries>,
        seriesIndex: Int,
        pointIndex: Int,
    ): String {
        val seriesName = data[seriesIndex].name
        val point = data[seriesIndex].points[pointIndex]
        val label = point.label.ifEmpty { point.x.toString() }
        return if (seriesName.isNotEmpty()) "$seriesName · $label: ${point.y}" else "$label: ${point.y}"
    }

    protected abstract fun drawChart(
        context: ContextApi,
        width: Float,
        height: Float,
        layout: CartesianLayout,
        viewport: ChartViewport,
        selection: ChartSelection?,
        series: List<ChartSeries>,
    )
}

internal fun filterVisibleSeries(
    series: List<ChartSeries>,
    hiddenSeriesNames: Set<String>,
): List<ChartSeries> = series.filterNot { hiddenSeriesNames.contains(it.name) }

internal fun toggleHiddenSeries(hiddenSeriesNames: Set<String>, seriesName: String): Set<String> =
    if (hiddenSeriesNames.contains(seriesName)) hiddenSeriesNames - seriesName
    else hiddenSeriesNames + seriesName

internal fun resolveViewportAfterDataChange(
    current: ChartViewport,
    newDefault: ChartViewport,
    hasUserViewportOverride: Boolean,
    newBounds: ChartViewport = newDefault,
    clampToData: Boolean = false,
    lockY: Boolean = false,
): ChartViewport {
    if (!hasUserViewportOverride) return newDefault
    return if (clampToData) {
        ChartGestureController.clampToBounds(current, newBounds, lockY)
    } else {
        current
    }
}

internal fun resolveCartesianSelectionCrosshair(
    series: List<ChartSeries>,
    selection: ChartSelection?,
    plot: PlotRect,
    viewport: ChartViewport,
): Pair<Float, Float>? {
    val cartesian = selection as? ChartSelection.Cartesian ?: return null
    val point = series.getOrNull(cartesian.seriesIndex)
        ?.points
        ?.getOrNull(cartesian.itemIndex)
        ?: return null
    if (!point.x.isFinite() || !point.y.isFinite()) return null
    val scale = CartesianScale(plot, viewport)
    val x = scale.toPixelX(point.x)
    val y = scale.toPixelY(point.y)
    if (x !in plot.left..plot.right || y !in plot.top..plot.bottom) return null
    return x to y
}

/** Root-relative tooltip position from canvas offset + local click point. */
internal fun resolveTooltipPosition(
    canvasOffsetX: Float,
    canvasOffsetY: Float,
    localX: Float,
    localY: Float,
    yOffset: Float = 28f,
    containerWidth: Float? = null,
    tooltipWidth: Float = 0f,
    horizontalGap: Float = 8f,
    horizontalPadding: Float = 8f,
): Pair<Float, Float> {
    val anchorX = canvasOffsetX + localX
    val left = if (containerWidth == null || tooltipWidth <= 0f) {
        anchorX
    } else {
        val minLeft = canvasOffsetX + horizontalPadding
        val maxRight = canvasOffsetX + containerWidth - horizontalPadding
        val rightCandidate = anchorX + horizontalGap
        if (rightCandidate + tooltipWidth <= maxRight) {
            rightCandidate
        } else {
            (anchorX - horizontalGap - tooltipWidth).coerceAtLeast(minLeft)
        }
    }
    return left to canvasOffsetY + localY - yOffset
}

internal fun estimateTooltipWidth(text: String, containerWidth: Float): Float {
    val contentWidth = text.split('\n').maxOfOrNull { line ->
        line.fold(0) { width, char -> width + if (char.code <= 0x7F) 7 else 12 }
    }?.toFloat() ?: 0f
    return (contentWidth + 20f).coerceIn(72f, (containerWidth - 16f).coerceAtLeast(72f))
}

internal fun <A : SeriesCartesianChartAttr, T : CartesianChartView<A>> ViewContainer<*, *>.cartesianChartView(
    view: T,
    init: T.() -> Unit,
) {
    addChild(view, init)
}
