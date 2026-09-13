package com.tencent.kuiklybase.chart.stock

import com.tencent.kuiklybase.chart.config.ChartTheme
import com.tencent.kuiklybase.chart.config.StockAuxiliaryIndicator
import com.tencent.kuiklybase.chart.config.StockMainIndicator
import com.tencent.kuiklybase.chart.config.StockPriceDisplayMode
import com.tencent.kuiklybase.chart.config.StockRenderConfig
import com.tencent.kuiklybase.chart.core.StockLineData
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickCandidate
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickDomain
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickPlanInput
import com.tencent.kuiklybase.chart.core.cartesian.CartesianScale
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayoutEngine
import com.tencent.kuiklybase.chart.core.cartesian.PlotRect
import com.tencent.kuiklybase.chart.core.cartesian.PlannedAxisTick
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.OhlcPoint
import com.tencent.kuiklybase.chart.model.hasValidOhlcSemantics
import kotlin.math.abs

internal data class StockPlots(
    val price: PlotRect,
    val volume: PlotRect?,
)

internal data class StockPricePaneSections(
    val header: PlotRect,
    val content: PlotRect,
    val xAxis: PlotRect,
)

internal data class StockOverlayBuilders(
    val toolbar: Boolean = false,
    val mainHeader: Boolean = false,
    val firstHeader: Boolean = false,
    val secondHeader: Boolean = false,
)

internal data class StockOverlaySlot(
    val mounted: Boolean,
    val visible: Boolean,
    val rect: PlotRect,
)

internal data class StockOverlayState(
    val revision: Int,
    val toolbar: StockOverlaySlot,
    val mainHeader: StockOverlaySlot,
    val firstHeader: StockOverlaySlot,
    val secondHeader: StockOverlaySlot,
)

internal fun resolveStockOverlayState(
    plot: PlotRect,
    config: StockRenderConfig,
    builders: StockOverlayBuilders,
): StockOverlayState {
    val composite = config.firstPane.show || config.secondPane.show
    val firstVisible = composite && config.firstPane.show && !config.panesCollapsed
    val secondVisible = composite && config.secondPane.show && !config.panesCollapsed
    val plots = splitCompositeStockPlots(
        plot = plot,
        firstVisible = firstVisible,
        secondVisible = secondVisible,
        firstRatio = config.firstPane.heightRatio,
        secondRatio = config.secondPane.heightRatio,
        toolbarHeight = stockCompositeToolbarHeight(builders.toolbar),
    )
    val finitePositiveLayout = plot.width.isFinite() && plot.height.isFinite() && plot.width > 0f && plot.height > 0f
    val hiddenRect = PlotRect(plots.price.left, plots.price.top, plots.price.left, plots.price.top)
    fun slot(mounted: Boolean, visible: Boolean, rect: PlotRect?): StockOverlaySlot {
        val resolvedVisible = mounted && visible && finitePositiveLayout && rect != null && rect.width > 0f && rect.height > 0f
        return StockOverlaySlot(mounted, resolvedVisible, if (resolvedVisible) rect!! else hiddenRect)
    }
    val mainHeader = splitStockPricePane(
        plots.price,
        headerHeight = if (config.mainIndicator == StockMainIndicator.BARE_K) 18f else 34f,
        xAxisHeight = 18f,
    ).header
    return StockOverlayState(
        revision = config.revision,
        toolbar = slot(builders.toolbar, composite, plots.toolbar),
        mainHeader = slot(builders.mainHeader, composite, mainHeader),
        firstHeader = slot(
            builders.firstHeader,
            firstVisible,
            plots.first?.let { splitStockPricePane(it).header },
        ),
        secondHeader = slot(
            builders.secondHeader,
            secondVisible,
            plots.second?.let { splitStockPricePane(it).header },
        ),
    )
}

internal data class StockAxisFramePlan(
    val gridTicks: List<PlannedAxisTick>,
    val priceAxisTicks: List<PlannedAxisTick>,
    val volumeAxisTicks: List<PlannedAxisTick>,
)

internal fun stockAxisFramePlan(ticks: List<PlannedAxisTick>): StockAxisFramePlan =
    StockAxisFramePlan(ticks, ticks, ticks)

internal class StockComputationCache {
    private var snapshot: List<OhlcPoint>? = null
    private val values = linkedMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> resolve(data: List<OhlcPoint>, family: String, compute: () -> T): T {
        if (snapshot != data) {
            snapshot = data.toList()
            values.clear()
        }
        if (values.containsKey(family)) return values[family] as T
        return compute().also { values[family] = it }
    }

    fun clear() {
        snapshot = null
        values.clear()
    }
}

internal enum class CompositePriceRenderDecision { EMPTY, DATA }

internal fun compositePriceRenderDecision(data: List<OhlcPoint>): CompositePriceRenderDecision =
    if (data.any(OhlcPoint::hasValidOhlcSemantics)) CompositePriceRenderDecision.DATA
    else CompositePriceRenderDecision.EMPTY

internal fun splitStockPricePane(
    plot: PlotRect,
    headerHeight: Float = 18f,
    xAxisHeight: Float = 0f,
): StockPricePaneSections {
    val left = plot.left.takeIf(Float::isFinite) ?: 0f
    val top = plot.top.takeIf(Float::isFinite) ?: 0f
    val right = (plot.right.takeIf(Float::isFinite) ?: left).coerceAtLeast(left)
    val bottom = (plot.bottom.takeIf(Float::isFinite) ?: top).coerceAtLeast(top)
    val height = (bottom.toDouble() - top.toDouble()).coerceAtLeast(0.0)
    val reservedHeader = (headerHeight.takeIf(Float::isFinite)?.toDouble() ?: 18.0).coerceIn(0.0, height)
    val reservedXAxis = (xAxisHeight.takeIf(Float::isFinite)?.toDouble() ?: 0.0)
        .coerceIn(0.0, height - reservedHeader)
    val headerBottom = finiteFloat(top.toDouble() + reservedHeader).coerceIn(top, bottom)
    val xAxisTop = finiteFloat(bottom.toDouble() - reservedXAxis).coerceIn(headerBottom, bottom)
    return StockPricePaneSections(
        header = PlotRect(left, top, right, headerBottom),
        content = PlotRect(left, headerBottom, right, xAxisTop),
        xAxis = PlotRect(left, xAxisTop, right, bottom),
    )
}

internal fun splitStockPaneHeader(plot: PlotRect, headerHeight: Float = 18f): StockPricePaneSections =
    splitStockPricePane(plot, headerHeight)

internal fun stockPaneHeaderValueRect(
    header: PlotRect,
    hasCustomBuilder: Boolean,
    customBuilderWidth: Float = 76f,
    gap: Float = 4f,
): PlotRect {
    if (!hasCustomBuilder) return header
    val inset = (customBuilderWidth.takeIf(Float::isFinite) ?: 76f) +
        (gap.takeIf(Float::isFinite) ?: 4f)
    return header.copy(left = (header.left + inset).coerceAtMost(header.right))
}

internal data class CompositeStockPlots(
    val price: PlotRect,
    val toolbar: PlotRect,
    val first: PlotRect?,
    val second: PlotRect?,
)

internal sealed class AuxiliaryPaneData {
    data object Empty : AuxiliaryPaneData()
    data class Volume(val values: List<Float?>, val average: List<Float?>) : AuxiliaryPaneData()
    data class Amount(val values: List<Float?>) : AuxiliaryPaneData()
    data class Macd(val result: MacdResult) : AuxiliaryPaneData()
    data class Kdj(val result: KdjResult) : AuxiliaryPaneData()
    data class Rsi(val lines: List<StockLineResult>) : AuxiliaryPaneData()
    data class Wr(val values: List<Float?>) : AuxiliaryPaneData()
    data class Bbd(val values: List<Float?>, val avg5: List<Float?>) : AuxiliaryPaneData()
}

internal data class StockCompositeCrosshairPlan(
    val x: Float,
    val verticalTop: Float,
    val verticalBottom: Float,
    val horizontalLeft: Float,
    val horizontalRight: Float,
    val horizontalY: Float?,
)

internal fun stockCompositeCrosshairPlan(
    plots: CompositeStockPlots,
    x: Float,
    priceY: Float?,
): StockCompositeCrosshairPlan? {
    if (!x.isFinite() || x !in plots.price.left..plots.price.right) return null
    val horizontalY = priceY?.takeIf { it.isFinite() && it in plots.price.top..plots.price.bottom }
    val verticalBottom = plots.second?.bottom ?: plots.first?.bottom ?: plots.price.bottom
    return StockCompositeCrosshairPlan(
        x = x,
        verticalTop = plots.price.top,
        verticalBottom = verticalBottom,
        horizontalLeft = plots.price.left,
        horizontalRight = plots.price.right,
        horizontalY = horizontalY,
    )
}

internal fun resolveAuxiliaryPaneData(
    data: List<OhlcPoint>,
    indicator: StockAuxiliaryIndicator,
): AuxiliaryPaneData = when (indicator) {
    StockAuxiliaryIndicator.NONE -> AuxiliaryPaneData.Empty
    StockAuxiliaryIndicator.VOLUME -> data
        .map { point -> point.volume?.takeIf { point.hasValidOhlcSemantics() && it.isFinite() } }
        .let { values ->
            if (values.noneFinite()) AuxiliaryPaneData.Empty
            else AuxiliaryPaneData.Volume(values, stockVolumeMovingAverage(data, 5))
    }
    StockAuxiliaryIndicator.AMOUNT -> stockAmount(data).let { values ->
        if (values.all { it == null }) AuxiliaryPaneData.Empty else AuxiliaryPaneData.Amount(values)
    }
    StockAuxiliaryIndicator.MACD -> stockMacd(data).let { result ->
        if ((result.diff + result.dea + result.histogram).noneFinite()) AuxiliaryPaneData.Empty
        else AuxiliaryPaneData.Macd(result)
    }
    StockAuxiliaryIndicator.KDJ -> stockKdj(data).let { result ->
        if ((result.k + result.d + result.j).noneFinite()) AuxiliaryPaneData.Empty
        else AuxiliaryPaneData.Kdj(result)
    }
    StockAuxiliaryIndicator.RSI -> stockRsiLines(data).let { lines ->
        if (lines.flatMap { it.values }.noneFinite()) AuxiliaryPaneData.Empty
        else AuxiliaryPaneData.Rsi(lines)
    }
    StockAuxiliaryIndicator.WR -> wr(data).let { values ->
        if (values.noneFinite()) AuxiliaryPaneData.Empty else AuxiliaryPaneData.Wr(values)
    }
    StockAuxiliaryIndicator.BBD -> bbd(data).let { values ->
        val avg5 = ema(values.filterNotNull(), 5)
        if (values.noneFinite()) AuxiliaryPaneData.Empty else AuxiliaryPaneData.Bbd(values, avg5)
    }
}

private fun List<Float?>.noneFinite(): Boolean = none { it?.isFinite() == true }

internal fun resolveMainIndicatorLines(
    data: List<OhlcPoint>,
    indicator: StockMainIndicator,
): List<StockLineResult> = when (indicator) {
    StockMainIndicator.BARE_K -> emptyList()
    StockMainIndicator.MA -> stockMaLines(data)
    StockMainIndicator.BOLL -> stockBollLines(data)
    StockMainIndicator.EXPMA -> stockExpmaLines(data)
    StockMainIndicator.BBI -> stockBbiLines(data)
    StockMainIndicator.ENE -> stockEneLines(data)
}

internal fun resolvePriceRenderMode(mode: StockPriceDisplayMode): StockPriceDisplayMode = when (mode) {
    StockPriceDisplayMode.CANDLE -> StockPriceDisplayMode.CANDLE
    StockPriceDisplayMode.LINE -> StockPriceDisplayMode.LINE
}

internal fun stockAxisTickInput(
    data: List<OhlcPoint>,
    viewport: ChartViewport,
    plot: PlotRect,
    theme: ChartTheme,
    measuredWidths: Map<Float, Float>,
    compactEllipsisWidth: Float,
): AxisTickPlanInput = AxisTickPlanInput(
    visibleMin = viewport.xMin,
    visibleMax = viewport.xMax,
    plotLeft = plot.left,
    plotWidth = plot.width,
    fontSize = theme.fontSize,
    domain = AxisTickDomain.CATEGORY_INDEX,
    candidates = data.map { point ->
        AxisTickCandidate(
            value = point.x,
            text = point.label.ifBlank { point.x.toString() },
            measuredWidth = measuredWidths[point.x] ?: theme.fontSize * 4f,
        )
    },
    fallbackText = "…",
    fallbackMeasuredWidth = compactEllipsisWidth,
)

internal fun visibleCompositePriceBounds(
    points: List<OhlcPoint>,
    lines: List<StockLineResult>,
    visibleX: ClosedFloatingPointRange<Float>,
    currentPrice: Float? = null,
): ClosedFloatingPointRange<Float> {
    val values = mutableListOf<Float>()
    points.forEach { point ->
        if (!point.hasValidOhlcSemantics() || point.x !in visibleX) return@forEach
        values += point.low
        values += point.high
    }
    lines.forEach { line ->
        val count = minOf(points.size, line.values.size)
        for (index in 0 until count) {
            val point = points[index]
            if (point.hasValidOhlcSemantics() && point.x in visibleX) {
                line.values[index]?.takeIf(Float::isFinite)?.let(values::add)
            }
        }
    }
    currentPrice?.takeIf(Float::isFinite)?.let(values::add)
    return paddedValues(values)
}

internal fun stockLineData(source: List<OhlcPoint>, values: List<Float?>): StockLineData =
    StockLineData(
        source.map { it.x },
        source.indices.map { index -> values.getOrNull(index)?.takeIf { source[index].hasValidOhlcSemantics() } },
    )

internal fun visibleCurrentPrice(
    points: List<OhlcPoint>,
    visibleX: ClosedFloatingPointRange<Float>,
): Float? = points.lastOrNull()?.let { latest ->
    latest.close.takeIf { latest.hasValidOhlcSemantics() && latest.x in visibleX }
}

internal fun stockDisplayIndex(
    data: List<OhlcPoint>,
    viewport: ChartViewport,
    selectedIndex: Int?,
): Int? {
    fun isVisibleValid(index: Int): Boolean = data.getOrNull(index)?.let { point ->
        point.hasValidOhlcSemantics() && point.x in viewport.xMin..viewport.xMax
    } == true
    if (selectedIndex != null && isVisibleValid(selectedIndex)) return selectedIndex
    return data.indices.lastOrNull(::isVisibleValid)
}

internal fun auxiliaryPaneBounds(
    source: List<OhlcPoint>,
    pane: AuxiliaryPaneData,
    visibleX: ClosedFloatingPointRange<Float>,
): ClosedFloatingPointRange<Float> {
    val values = when (pane) {
        AuxiliaryPaneData.Empty -> emptyList()
        is AuxiliaryPaneData.Volume -> pane.values + pane.average
        is AuxiliaryPaneData.Amount -> pane.values
        is AuxiliaryPaneData.Macd -> pane.result.diff + pane.result.dea + pane.result.histogram
        is AuxiliaryPaneData.Kdj -> pane.result.k + pane.result.d + pane.result.j
        is AuxiliaryPaneData.Rsi -> pane.lines.flatMap { it.values }
        is AuxiliaryPaneData.Wr -> pane.values
        is AuxiliaryPaneData.Bbd -> pane.values + pane.avg5
    }
    val seriesCount = when (pane) {
        is AuxiliaryPaneData.Volume -> 2
        is AuxiliaryPaneData.Macd -> 3
        is AuxiliaryPaneData.Kdj -> 3
        is AuxiliaryPaneData.Rsi -> pane.lines.size
        is AuxiliaryPaneData.Bbd -> 2
        else -> 1
    }.coerceAtLeast(1)
    val visibleValues = mutableListOf<Float>()
    repeat(seriesCount) { seriesIndex ->
        val offset = seriesIndex * source.size
        source.indices.forEach { index ->
            val point = source[index]
            if (point.hasValidOhlcSemantics() && point.x in visibleX) {
                values.getOrNull(offset + index)?.takeIf(Float::isFinite)?.let(visibleValues::add)
            }
        }
    }
    if (pane is AuxiliaryPaneData.Volume || pane is AuxiliaryPaneData.Amount || pane is AuxiliaryPaneData.Macd) {
        visibleValues += 0f
    }
    return if (pane is AuxiliaryPaneData.Volume || pane is AuxiliaryPaneData.Amount) {
        zeroBasedPaddedValues(visibleValues)
    } else {
        paddedValues(visibleValues)
    }
}

private fun zeroBasedPaddedValues(values: List<Float>): ClosedFloatingPointRange<Float> {
    val maximum = values.asSequence().filter(Float::isFinite).maxOrNull()?.coerceAtLeast(0f) ?: 0f
    val padding = if (maximum > 0f) (maximum.toDouble() * 0.05).coerceAtLeast(1e-3) else 1e-3
    val upper = (maximum.toDouble() + padding).coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    return 0f..upper
}

private fun paddedValues(values: List<Float>): ClosedFloatingPointRange<Float> {
    val finite = values.filter(Float::isFinite)
    if (finite.isEmpty()) return 0f..1f
    return paddedStockBounds(finite.minOrNull()!!, finite.maxOrNull()!!)
}

internal fun nearestVisibleStockIndex(
    data: List<OhlcPoint>,
    viewport: ChartViewport,
    pricePlot: PlotRect,
    x: Float,
    y: Float,
    hitRadius: Float,
): Int? {
    if (x !in pricePlot.left..pricePlot.right || y !in pricePlot.top..pricePlot.bottom) return null
    val scale = CartesianScale(pricePlot, viewport)
    return data.indices
        .asSequence()
        .filter {
            val point = data[it]
            point.hasValidOhlcSemantics() && point.x in viewport.xMin..viewport.xMax
        }
        .map { it to abs(scale.toPixelX(data[it].x) - x) }
        .filter { it.second <= hitRadius }
        .minWithOrNull(compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first })
        ?.first
}

internal fun stockMovingAverage(
    points: List<OhlcPoint>,
    period: Int,
): List<Float?> {
    require(period > 0) { "period must be positive" }
    var sum = 0.0
    val run = ArrayDeque<Float>()
    return points.map { point ->
        if (!point.hasValidOhlcSemantics()) {
            sum = 0.0
            run.clear()
            return@map null
        }
        run.addLast(point.close)
        sum += point.close.toDouble()
        if (run.size > period) sum -= run.removeFirst().toDouble()
        if (run.size == period) (sum / period).toFloat() else null
    }
}

internal fun stockVolumeMovingAverage(
    points: List<OhlcPoint>,
    period: Int,
): List<Float?> {
    require(period > 0) { "period must be positive" }
    val volumes = points.map { point -> point.volume?.takeIf { point.hasValidOhlcSemantics() && it.isFinite() } }
    return volumes.indices.map { index ->
        val start = index - period + 1
        if (start < 0) return@map null
        val window = volumes.subList(start, index + 1)
        if (window.any { it == null }) null else window.filterNotNull().average().toFloat()
    }
}

internal fun stockAveragePoints(
    source: List<OhlcPoint>,
    values: List<Float?>,
): List<Pair<Float, Float>> = source.zip(values).mapNotNull { (point, value) ->
    value?.let { point.x to it }
}

internal fun shouldShowVolumePanel(
    points: List<OhlcPoint>,
    configured: Boolean,
): Boolean = configured && points.any { it.volume != null }

internal fun stockDataChanged(previous: List<OhlcPoint>?, current: List<OhlcPoint>): Boolean =
    previous != null && previous != current

internal fun stockVolumeBounds(points: List<OhlcPoint>): ClosedFloatingPointRange<Float> {
    val maximum = points.asSequence()
        .filter(OhlcPoint::hasValidOhlcSemantics)
        .mapNotNull { it.volume?.takeIf(Float::isFinite) }
        .maxOrNull()
        ?.coerceAtLeast(0f) ?: 0f
    return 0f..maximum
}

internal fun visibleStockPriceBounds(
    points: List<OhlcPoint>,
    visibleX: ClosedFloatingPointRange<Float>,
): ClosedFloatingPointRange<Float> {
    var minimum = Float.POSITIVE_INFINITY
    var maximum = Float.NEGATIVE_INFINITY
    points.forEach { point ->
        if (!point.hasValidOhlcSemantics() || point.x !in visibleX) return@forEach
        minimum = minOf(minimum, point.low)
        maximum = maxOf(maximum, point.high)
    }
    return paddedStockBounds(minimum, maximum)
}

internal fun visibleStockValueBounds(
    source: List<OhlcPoint>,
    values: List<Float?>,
    visibleX: ClosedFloatingPointRange<Float>,
): ClosedFloatingPointRange<Float> {
    var minimum = Float.POSITIVE_INFINITY
    var maximum = Float.NEGATIVE_INFINITY
    val count = minOf(source.size, values.size)
    for (index in 0 until count) {
        val point = source[index]
        val value = values[index]
        if (!point.hasValidOhlcSemantics() || point.x !in visibleX ||
            value == null || !value.isFinite()
        ) continue
        minimum = minOf(minimum, value)
        maximum = maxOf(maximum, value)
    }
    return paddedStockBounds(minimum, maximum)
}

private fun paddedStockBounds(minimum: Float, maximum: Float): ClosedFloatingPointRange<Float> {
    if (!minimum.isFinite() || !maximum.isFinite() || maximum < minimum) return 0f..1f
    val minimumDouble = minimum.toDouble()
    val maximumDouble = maximum.toDouble()
    val span = maximumDouble - minimumDouble
    val padding = if (span.isFinite() && span > 0.0) {
        (span * 0.05).coerceAtLeast(1e-3)
    } else {
        (kotlin.math.abs(minimumDouble) * 0.05).coerceAtLeast(1e-3)
    }
    val floatLimit = Float.MAX_VALUE.toDouble()
    val lower = (minimumDouble - padding).coerceIn(-floatLimit, floatLimit).toFloat()
    val upper = (maximumDouble + padding).coerceIn(-floatLimit, floatLimit).toFloat()
    return minOf(lower, upper)..maxOf(lower, upper)
}

internal fun splitCompositeStockPlots(
    plot: PlotRect,
    firstVisible: Boolean,
    secondVisible: Boolean,
    firstRatio: Float,
    secondRatio: Float,
    toolbarHeight: Float,
): CompositeStockPlots {
    val leftDouble = plot.left.takeIf(Float::isFinite)?.toDouble() ?: 0.0
    val topDouble = plot.top.takeIf(Float::isFinite)?.toDouble() ?: 0.0
    val rightDouble = (plot.right.takeIf(Float::isFinite)?.toDouble() ?: leftDouble)
        .coerceAtLeast(leftDouble)
    val bottomDouble = (plot.bottom.takeIf(Float::isFinite)?.toDouble() ?: topDouble)
        .coerceAtLeast(topDouble)
    val height = bottomDouble - topDouble
    val safeToolbarHeight = (toolbarHeight.takeIf(Float::isFinite)?.toDouble() ?: 30.0)
        .coerceIn(0.0, height)
    val firstFraction = firstRatio.takeIf(Float::isFinite)?.toDouble()?.coerceIn(0.14, 0.28) ?: 0.2
    val secondFraction = secondRatio.takeIf(Float::isFinite)?.toDouble()?.coerceIn(0.14, 0.28) ?: 0.2
    val visibleCount = (if (firstVisible) 1 else 0) + (if (secondVisible) 1 else 0)
    val afterToolbar = (height - safeToolbarHeight).coerceAtLeast(0.0)
    val gap = if (visibleCount == 0) 0.0 else minOf(8.0, afterToolbar / visibleCount)
    val paneCapacity = (afterToolbar - gap * visibleCount).coerceAtLeast(0.0)
    val desiredFirst = if (firstVisible) height * firstFraction else 0.0
    val desiredSecond = if (secondVisible) height * secondFraction else 0.0
    val desiredTotal = desiredFirst + desiredSecond
    val scale = if (desiredTotal > paneCapacity && desiredTotal > 0.0) paneCapacity / desiredTotal else 1.0
    val firstHeight = desiredFirst * scale
    val secondHeight = desiredSecond * scale
    val priceHeight = (height - safeToolbarHeight - gap * visibleCount - firstHeight - secondHeight)
        .coerceAtLeast(0.0)
    val priceBottomDouble = topDouble + priceHeight
    val toolbarBottomDouble = (priceBottomDouble + safeToolbarHeight).coerceAtMost(bottomDouble)
    var paneTopDouble = toolbarBottomDouble
    val firstBottomDouble = if (firstVisible) {
        paneTopDouble = (paneTopDouble + gap).coerceAtMost(bottomDouble)
        (paneTopDouble + firstHeight).coerceAtMost(bottomDouble).also { paneTopDouble = it }
    } else {
        null
    }
    val secondTopDouble = if (secondVisible) (paneTopDouble + gap).coerceAtMost(bottomDouble) else null
    val secondBottomDouble = secondTopDouble?.let { (it + secondHeight).coerceAtMost(bottomDouble) }

    val left = finiteFloat(leftDouble)
    val right = finiteFloat(rightDouble).coerceAtLeast(left)
    val top = finiteFloat(topDouble)
    val bottom = finiteFloat(bottomDouble).coerceAtLeast(top)
    val priceBottom = finiteFloat(priceBottomDouble).coerceIn(top, bottom)
    val toolbarBottom = finiteFloat(toolbarBottomDouble).coerceIn(priceBottom, bottom)
    val toolbar = PlotRect(left, priceBottom, right, toolbarBottom)
    val first = if (firstVisible) {
        val paneTop = finiteFloat(toolbarBottomDouble + gap).coerceIn(toolbarBottom, bottom)
        val paneBottom = finiteFloat(firstBottomDouble!!).coerceIn(paneTop, bottom)
        PlotRect(left, paneTop, right, paneBottom)
    } else {
        null
    }
    val second = if (secondVisible) {
        val paneTop = finiteFloat(secondTopDouble!!).coerceIn(first?.bottom ?: toolbarBottom, bottom)
        val paneBottom = finiteFloat(secondBottomDouble!!).coerceIn(paneTop, bottom)
        PlotRect(left, paneTop, right, paneBottom)
    } else {
        null
    }
    return CompositeStockPlots(
        price = PlotRect(left, top, right, priceBottom),
        toolbar = toolbar,
        first = first,
        second = second,
    )
}

internal fun stockToolbarSlotRect(
    width: Float,
    height: Float,
    firstVisible: Boolean,
    secondVisible: Boolean,
    firstRatio: Float,
    secondRatio: Float,
): PlotRect = splitCompositeStockPlots(
    CartesianLayoutEngine.compute(width, height).plot,
    firstVisible,
    secondVisible,
    firstRatio,
    secondRatio,
    toolbarHeight = stockCompositeToolbarHeight(true),
).toolbar

internal fun stockCompositeToolbarHeight(hasToolbarContent: Boolean): Float =
    if (hasToolbarContent) 30f else 0f

private fun finiteFloat(value: Double): Float =
    value.coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()

internal fun splitStockPlots(
    plot: PlotRect,
    showVolume: Boolean,
    volumeRatio: Float,
): StockPlots {
    if (!showVolume) return StockPlots(plot, null)

    val gap = 12f
    val usableHeight = (plot.height - gap).coerceAtLeast(0f)
    val volumeHeight = usableHeight * volumeRatio.coerceIn(0.16f, 0.4f)
    val priceBottom = plot.bottom - gap - volumeHeight
    return StockPlots(
        price = plot.copy(bottom = priceBottom),
        volume = PlotRect(
            left = plot.left,
            top = priceBottom + gap,
            right = plot.right,
            bottom = plot.bottom,
        ),
    )
}
