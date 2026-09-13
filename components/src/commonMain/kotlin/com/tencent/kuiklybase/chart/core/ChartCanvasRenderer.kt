package com.tencent.kuiklybase.chart.core

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuiklybase.chart.config.ChartAnnotationConfig
import com.tencent.kuiklybase.chart.config.ChartTheme
import com.tencent.kuiklybase.chart.config.StockAverageLine
import com.tencent.kuiklybase.chart.config.ChartThresholdConfig
import com.tencent.kuiklybase.chart.config.AreaMode
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayout
import com.tencent.kuiklybase.chart.core.cartesian.CartesianScale
import com.tencent.kuiklybase.chart.core.cartesian.PlotRect
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickCandidate
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickDomain
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickPlanInput
import com.tencent.kuiklybase.chart.core.cartesian.AxisTickPlanner
import com.tencent.kuiklybase.chart.core.cartesian.PlannedAxisTick
import com.tencent.kuiklybase.chart.core.cartesian.numericNiceStep
import com.tencent.kuiklybase.chart.core.polar.PolarScale
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartSlice
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.OhlcPoint
import com.tencent.kuiklybase.chart.model.RadarDimension
import com.tencent.kuiklybase.chart.model.RadarSeries
import com.tencent.kuiklybase.chart.model.hasValidOhlcSemantics
import com.tencent.kuiklybase.chart.stock.StockLineResult
import com.tencent.kuiklybase.chart.stock.stockLineData
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/** 轴刻度：数据坐标 + 展示文案（类别轴优先用 [ChartDataPoint.label]）。 */
internal data class AxisTick(val value: Float, val text: String)

internal data class StockHistogramBar(val x: Float, val value: Float)

internal data class StockHistogramPixelBar(
    val sourceX: Float,
    val value: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class StockLineData(
    val sourceX: List<Float>,
    val values: List<Float?>,
)

internal data class StockCandlestickPixel(
    val sourceIndex: Int,
    val centerX: Float,
    val highY: Float,
    val lowY: Float,
    val openY: Float,
    val closeY: Float,
    val bodyWidth: Float,
    val isUp: Boolean,
)

internal data class StockVolumePixel(
    val sourceIndex: Int,
    val sourceX: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val isUp: Boolean,
)

internal data class CurrentPriceLinePlan(
    val y: Float,
    val lineWidth: Float,
    val dashPattern: List<Float>,
)

internal data class CurrentPriceLabelPlan(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val baseline: Float,
)

internal data class CurrentPriceRenderPlan(
    val line: CurrentPriceLinePlan,
    val label: CurrentPriceLabelPlan?,
)

internal data class PaneHeaderItem(
    val text: String,
    val color: Long,
    val x: Float,
    val estimatedWidth: Float,
)

internal class AxisLabelWidthCache(private val maxEntries: Int = 128) {
    private data class Key(val fontSize: Float, val text: String)

    private val widths = linkedMapOf<Key, Float>()

    val size: Int get() = widths.size

    fun resolve(fontSize: Float, text: String, measureText: (String) -> Float): Float {
        val key = Key(fontSize, text)
        widths[key]?.let { return it }
        val measured = (measureText(text) * 1.05f)
            .takeIf { it.isFinite() && it > 0f }
            ?: fontSize.coerceAtLeast(1f)
        if (widths.size >= maxEntries.coerceAtLeast(1)) {
            widths.keys.firstOrNull()?.let(widths::remove)
        }
        widths[key] = measured
        return measured
    }
}

private val cartesianAxisLabelWidthCache = AxisLabelWidthCache()

internal fun planCartesianXAxisTicks(
    viewport: ChartViewport,
    plot: PlotRect,
    fontSize: Float,
    xTicks: List<AxisTick>?,
    widthCache: AxisLabelWidthCache? = null,
    measureText: (String) -> Float,
): List<PlannedAxisTick> {
    val domain: AxisTickDomain
    val ticks = if (!xTicks.isNullOrEmpty()) {
        domain = AxisTickDomain.CATEGORY_INDEX
        xTicks
    } else {
        domain = AxisTickDomain.NUMERIC
        generateNumericXAxisTicks(viewport.xMin, viewport.xMax)
    }
    return AxisTickPlanner.plan(
        AxisTickPlanInput(
            visibleMin = viewport.xMin,
            visibleMax = viewport.xMax,
            plotLeft = plot.left,
            plotWidth = plot.width,
            fontSize = fontSize,
            domain = domain,
            fallbackText = "…",
            fallbackMeasuredWidth = widthCache?.resolve(fontSize, "…", measureText)
                ?: (measureText("…") * 1.05f).takeIf { it.isFinite() && it > 0f }
                ?: fontSize.coerceAtLeast(1f),
            candidates = ticks.map { tick ->
                val visible = tick.value in minOf(viewport.xMin, viewport.xMax)..maxOf(viewport.xMin, viewport.xMax)
                AxisTickCandidate(
                    value = tick.value,
                    text = tick.text,
                    measuredWidth = if (visible) {
                        widthCache?.resolve(fontSize, tick.text, measureText)
                            ?: (measureText(tick.text) * 1.05f).takeIf { it.isFinite() && it > 0f }
                            ?: fontSize.coerceAtLeast(1f)
                    } else {
                        1f
                    },
                )
            },
        ),
    )
}

internal fun resolveCartesianXAxisTicks(
    plannedXTicks: List<PlannedAxisTick>?,
    viewport: ChartViewport,
    plot: PlotRect,
    fontSize: Float,
    xTicks: List<AxisTick>?,
    widthCache: AxisLabelWidthCache? = null,
    measureText: (String) -> Float,
): List<PlannedAxisTick> = plannedXTicks ?: planCartesianXAxisTicks(
    viewport = viewport,
    plot = plot,
    fontSize = fontSize,
    xTicks = xTicks,
    widthCache = widthCache,
    measureText = measureText,
)

internal fun generateNumericXAxisTicks(first: Float, second: Float): List<AxisTick> {
    if (!first.isFinite() || !second.isFinite() || first == second) return emptyList()
    val lo = minOf(first.toDouble(), second.toDouble())
    val hi = maxOf(first.toDouble(), second.toDouble())
    val span = hi - lo
    if (!span.isFinite() || span <= 0.0) return emptyList()
    val step = numericNiceStep(span / 6.0)
    if (!step.isFinite() || step <= 0.0) return emptyList()
    val startMultiple = ceil(lo / step)
    val endMultiple = floor(hi / step)
    if (!startMultiple.isFinite() || !endMultiple.isFinite() || endMultiple < startMultiple) {
        val value = (lo + span / 2.0).toFloat()
        return listOf(AxisTick(value, formatAxisValue(value, step)))
    }
    val ticks = mutableListOf<AxisTick>()
    repeat(64) { index ->
        val multiple = startMultiple + index.toDouble()
        if (multiple > endMultiple) return@repeat
        val value = (multiple * step).toFloat()
        if (value.isFinite() && value.toDouble() in lo..hi && ticks.lastOrNull()?.value != value) {
            val formatted = formatAxisValue(value, step)
            val text = if (ticks.any { it.text == formatted }) value.toString() else formatted
            ticks += AxisTick(value, text)
        }
    }
    if (ticks.isNotEmpty()) return ticks
    val midpoint = (lo + span / 2.0).toFloat()
    return if (midpoint.isFinite()) {
        listOf(AxisTick(midpoint, formatAxisValue(midpoint, step)))
    } else {
        emptyList()
    }
}

private fun formatAxisValue(value: Float, step: Double): String {
    val valueDouble = value.toDouble()
    if (valueDouble in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble() &&
        value == value.toLong().toFloat()
    ) return value.toLong().toString()
    val stepExponent = floor(kotlin.math.log10(kotlin.math.abs(step))).toInt()
    if (-stepExponent > 6) return value.toString()
    val decimalPlaces = (-stepExponent).coerceAtLeast(0) + 1
    val factor = 10.0.pow(decimalPlaces.toDouble())
    val rounded = round(valueDouble * factor) / factor
    return if (rounded.isFinite()) rounded.toString() else value.toString()
}

internal fun formatChartValue(value: Float): String {
    if (!value.isFinite()) return value.toString()
    val valueDouble = value.toDouble()
    if (valueDouble in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble() &&
        value == value.toLong().toFloat()
    ) return value.toLong().toString()
    val rounded = round(valueDouble * 10.0) / 10.0
    return if (rounded.isFinite()) rounded.toString() else value.toString()
}

internal inline fun ContextApi.withPlotClip(plot: PlotRect, draw: () -> Unit) {
    save()
    beginPath()
    moveTo(plot.left, plot.top)
    lineTo(plot.right, plot.top)
    lineTo(plot.right, plot.bottom)
    lineTo(plot.left, plot.bottom)
    closePath()
    clipPathIntersect()
    try {
        draw()
    } finally {
        restore()
    }
}

internal fun resolveAnnotationTextPosition(
    desiredX: Float,
    desiredBaselineY: Float,
    textWidth: Float,
    textAscent: Float,
    textDescent: Float,
    plot: PlotRect,
    padding: Float = 2f,
): Pair<Float, Float> {
    val minX = plot.left + padding
    val maxX = maxOf(minX, plot.right - textWidth.coerceAtLeast(0f) - padding)
    val minBaselineY = plot.top + textAscent.coerceAtLeast(0f) + padding
    val maxBaselineY = maxOf(
        minBaselineY,
        plot.bottom - textDescent.coerceAtLeast(0f) - padding,
    )
    return desiredX.coerceIn(minX, maxX) to
        desiredBaselineY.coerceIn(minBaselineY, maxBaselineY)
}

internal object ChartCanvasRenderer {
    fun axisTicksFromSeries(series: List<ChartSeries>): List<AxisTick> {
        val points = series.firstOrNull { it.points.isNotEmpty() }?.points.orEmpty()
        if (points.isEmpty()) return emptyList()
        return points
            .asSequence()
            .filter { it.x.isFinite() }
            .distinctBy { it.x }
            .map { AxisTick(it.x, it.label.ifBlank { formatValue(it.x) }) }
            .toList()
    }

    fun axisTicksFromOhlc(points: List<OhlcPoint>): List<AxisTick> {
        if (points.isEmpty()) return emptyList()
        return points
            .asSequence()
            .filter { it.x.isFinite() }
            .map { AxisTick(it.x, it.label.ifBlank { formatValue(it.x) }) }
            .toList()
    }

    fun drawGrid(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        theme: ChartTheme,
        show: Boolean,
        plannedXTicks: List<PlannedAxisTick>? = null,
    ) {
        if (!show) return
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        ctx.strokeStyle(theme.gridColor.toChartColor())
        ctx.lineWidth(1f)
        val yTicks = 5
        for (i in 0..yTicks) {
            val y = plot.top + plot.height * i / yTicks
            ctx.beginPath()
            ctx.moveTo(plot.left, y)
            ctx.lineTo(plot.right, y)
            ctx.stroke()
        }
        val plannedXs = plannedXTicks?.let { plannedGridXs(plot, it) }
        if (plannedXs != null) {
            plannedXs.forEach { x ->
                ctx.beginPath()
                ctx.moveTo(x, plot.top)
                ctx.lineTo(x, plot.bottom)
                ctx.stroke()
            }
        } else {
            val xTicks = 5
            for (i in 0..xTicks) {
                val dataX = viewport.xMin + (viewport.xMax - viewport.xMin) * i / xTicks
                val x = scale.toPixelX(dataX)
                ctx.beginPath()
                ctx.moveTo(x, plot.top)
                ctx.lineTo(x, plot.bottom)
                ctx.stroke()
            }
        }
    }

    internal fun plannedGridXs(plot: PlotRect, ticks: List<PlannedAxisTick>): List<Float> =
        ticks.asSequence()
            .map { it.x }
            .filter { it.isFinite() && it in plot.left..plot.right }
            .distinct()
            .toList()

    fun drawAxes(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        theme: ChartTheme,
        showX: Boolean,
        showY: Boolean,
        xTicks: List<AxisTick>? = null,
        yTicks: List<AxisTick>? = null,
        plannedXTicks: List<PlannedAxisTick>? = null,
    ) {
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        ctx.font(theme.fontSize)
        ctx.fillStyle(theme.axisColor.toChartColor())
        ctx.textAlign(TextAlign.CENTER)
        if (showX) {
            ctx.beginPath()
            ctx.strokeStyle(theme.axisColor.toChartColor())
            ctx.lineWidth(1f)
            ctx.moveTo(plot.left, plot.bottom)
            ctx.lineTo(plot.right, plot.bottom)
            ctx.stroke()
        }
        if (showY) {
            ctx.beginPath()
            ctx.strokeStyle(theme.axisColor.toChartColor())
            ctx.moveTo(plot.left, plot.top)
            ctx.lineTo(plot.left, plot.bottom)
            ctx.stroke()
            ctx.textAlign(TextAlign.RIGHT)
            val ticks = resolveVisibleTicks(yTicks, viewport.yMin, viewport.yMax)
            if (ticks != null) {
                ticks.forEach { tick ->
                    val y = scale.toPixelY(tick.value)
                    if (y in plot.top..plot.bottom) {
                        ctx.fillText(tick.text, plot.left - 6f, y + theme.fontSize * 0.35f)
                    }
                }
            } else {
                val count = 5
                for (i in 0..count) {
                    val ratio = i.toFloat() / count
                    val value = viewport.yMin + (viewport.yMax - viewport.yMin) * (1f - ratio)
                    val y = plot.top + plot.height * ratio
                    ctx.fillText(formatValue(value), plot.left - 6f, y + theme.fontSize * 0.35f)
                }
            }
        }
        if (showX) {
            ctx.font(theme.fontSize)
            ctx.textAlign(TextAlign.CENTER)
            resolveCartesianXAxisTicks(
                plannedXTicks = plannedXTicks,
                viewport = viewport,
                plot = plot,
                fontSize = theme.fontSize,
                xTicks = xTicks,
                widthCache = cartesianAxisLabelWidthCache,
                measureText = { text -> ctx.measureText(text).width },
            ).forEach { tick ->
                ctx.fillText(tick.text, tick.x, plot.bottom + theme.fontSize + 4f)
            }
        }
    }

    /** 视口内可见刻度；过多时均匀抽样。null 表示走数值轴。 */
    private fun resolveVisibleTicks(
        ticks: List<AxisTick>?,
        minValue: Float,
        maxValue: Float,
        maxCount: Int = 6,
    ): List<AxisTick>? {
        if (ticks.isNullOrEmpty()) return null
        val lo = minOf(minValue, maxValue)
        val hi = maxOf(minValue, maxValue)
        val visible = ticks.filter { it.value in lo..hi }
        if (visible.isEmpty()) return emptyList()
        if (visible.size <= maxCount) return visible
        val step = (visible.size - 1).toFloat() / (maxCount - 1).coerceAtLeast(1)
        return (0 until maxCount).map { i -> visible[(i * step).toInt().coerceIn(0, visible.lastIndex)] }
            .distinctBy { it.value }
    }

    fun drawLineSeries(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
        showPoints: Boolean = true,
        pointRadius: Float = 4f,
        smooth: Boolean = false,
    ) {
        val scale = CartesianScale(layout.plot, viewport)
        series.forEachIndexed { sIdx, s ->
            if (s.points.isEmpty()) return@forEachIndexed
            val pixels = s.points.map { scale.toPixelX(it.x) to scale.toPixelY(it.y) }
            val color = s.color.toChartColor()
            ctx.beginPath()
            ctx.strokeStyle(color)
            ctx.lineWidth(theme.lineWidth)
            pathThroughPoints(ctx, pixels, smooth)
            ctx.stroke()
            if (showPoints) {
                s.points.forEachIndexed { pIdx, p ->
                    val (px, py) = pixels[pIdx]
                    val selected = selection is ChartSelection.Cartesian &&
                        selection.seriesIndex == sIdx && selection.itemIndex == pIdx
                    val fill = p.resolveColor(s.color)
                    drawMarker(ctx, px, py, fill, if (selected) pointRadius + 2f else pointRadius, selected)
                }
            }
        }
    }

    internal fun containsNonFinitePoint(series: List<ChartSeries>): Boolean =
        series.any { item -> item.points.any { point -> !point.x.isFinite() || !point.y.isFinite() } }

    internal fun lineSegments(
        points: List<Pair<Float, Float>?>,
        connectNulls: Boolean,
    ): List<List<Pair<Float, Float>>> {
        if (connectNulls) {
            return points.filterNotNull().takeIf { it.isNotEmpty() }?.let(::listOf) ?: emptyList()
        }
        val segments = mutableListOf<List<Pair<Float, Float>>>()
        var current = mutableListOf<Pair<Float, Float>>()
        points.forEach { point ->
            if (point == null) {
                if (current.isNotEmpty()) {
                    segments += current
                    current = mutableListOf()
                }
            } else {
                current += point
            }
        }
        if (current.isNotEmpty()) segments += current
        return segments
    }

    /**
     * 增强版折线绘制：支持阈值参考线、缺失值断点、折线下方填充、文本注释。
     * 渲染顺序：
     *   1) 阈值参考线
     *   2) 缺失值跳过
     *   3) 折线下方填充（可选）
     *   4) 折线主描边
     *   5) 数据点标记
     *   6) 文本注释（含可选连接线/锚点）
     */
    fun drawLineSeriesEnhanced(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
        smooth: Boolean,
        showPoints: Boolean,
        pointRadius: Float,
        connectNulls: Boolean,
        fillBelow: Boolean,
        thresholds: List<ChartThresholdConfig>,
        annotations: List<ChartAnnotationConfig>,
    ) {
        val scale = CartesianScale(layout.plot, viewport)
        val plot = layout.plot

        thresholds.forEach { t ->
            drawThreshold(ctx, scale, plot, theme, t)
        }

        series.forEachIndexed { sIdx, s ->
            if (s.points.isEmpty()) return@forEachIndexed
            val pixels = s.points.map { p ->
                val px = if (!p.x.isFinite() || !p.y.isFinite()) null
                else scale.toPixelX(p.x) to scale.toPixelY(p.y)
                p to px
            }
            val baseColor = s.color.toChartColor()

            if (fillBelow) {
                drawLineFillBelow(ctx, plot, pixels, s.color, connectNulls)
            }

            ctx.beginPath()
            ctx.strokeStyle(baseColor)
            ctx.lineWidth(theme.lineWidth)
            drawLinePath(ctx, pixels, smooth = smooth, connectNulls = connectNulls)
            ctx.stroke()

            if (showPoints) {
                pixels.forEachIndexed { pIdx, entry ->
                    val pos = entry.second ?: return@forEachIndexed
                    val p = entry.first
                    val selected = selection is ChartSelection.Cartesian &&
                        selection.seriesIndex == sIdx && selection.itemIndex == pIdx
                    val fill = p.resolveColor(s.color)
                    drawMarker(
                        ctx,
                        pos.first,
                        pos.second,
                        fill,
                        if (selected) pointRadius + 2f else pointRadius,
                        selected,
                    )
                }
            }
        }

        annotations.forEach { a ->
            drawAnnotation(ctx, scale, plot, a)
        }
    }

    /**
     * 描绘折线主路径：缺失值断点由 [connectNulls] 控制；[smooth] 切换到三次贝塞尔。
     */
    private fun drawLinePath(
        ctx: ContextApi,
        pixels: List<Pair<ChartDataPoint, Pair<Float, Float>?>>,
        smooth: Boolean,
        connectNulls: Boolean,
    ) {
        lineSegments(pixels.map { it.second }, connectNulls).forEach { segment ->
            if (smooth) {
                pathThroughPoints(ctx, segment, smooth = true)
            } else {
                ctx.moveTo(segment.first().first, segment.first().second)
                segment.drop(1).forEach { point -> ctx.lineTo(point.first, point.second) }
            }
        }
    }

    /**
     * 描绘折线下方区域填充：缺失段自动断开成多个填充区域。
     */
    private fun drawLineFillBelow(
        ctx: ContextApi,
        plot: PlotRect,
        pixels: List<Pair<ChartDataPoint, Pair<Float, Float>?>>,
        seriesColor: Long,
        connectNulls: Boolean,
    ) {
        lineSegments(pixels.map { it.second }, connectNulls).forEach { segment ->
            if (segment.isEmpty()) return@forEach
            ctx.beginPath()
            ctx.moveTo(segment.first().first, plot.bottom)
            segment.forEach { point -> ctx.lineTo(point.first, point.second) }
            ctx.lineTo(segment.last().first, plot.bottom)
            ctx.closePath()
            val gradient = ctx.createLinearGradient(0f, plot.top, 0f, plot.bottom)
            gradient.addColorStop(0f, Color(seriesColor.withAlpha(0xAA)))
            gradient.addColorStop(1f, Color(seriesColor.withAlpha(0x05)))
            ctx.fillStyle(gradient)
            ctx.fill()
        }
    }

    private fun drawThreshold(
        ctx: ContextApi,
        scale: CartesianScale,
        plot: PlotRect,
        theme: ChartTheme,
        cfg: ChartThresholdConfig,
    ) {
        val y = scale.toPixelY(cfg.value)
        if (y !in plot.top..plot.bottom) return
        ctx.beginPath()
        ctx.strokeStyle(cfg.color.toChartColor())
        ctx.lineWidth(1f)
        if (cfg.dashWidth > 0f) {
            var x = plot.left
            while (x < plot.right) {
                val end = minOf(x + cfg.dashWidth, plot.right)
                ctx.beginPath()
                ctx.moveTo(x, y)
                ctx.lineTo(end, y)
                ctx.stroke()
                x += cfg.dashWidth * 2f
            }
        } else {
            ctx.moveTo(plot.left, y)
            ctx.lineTo(plot.right, y)
            ctx.stroke()
        }
        if (cfg.showLabel && cfg.label.isNotEmpty()) {
            ctx.font(theme.fontSize)
            ctx.fillStyle(cfg.color.toChartColor())
            ctx.textAlign(TextAlign.LEFT)
            ctx.fillText(cfg.label, plot.left + 4f, y - 3f)
        }
    }

    private fun drawAnnotation(
        ctx: ContextApi,
        scale: CartesianScale,
        plot: PlotRect,
        cfg: ChartAnnotationConfig,
    ) {
        val ax = scale.toPixelX(cfg.dataX)
        val ay = scale.toPixelY(cfg.dataY)
        ctx.font(cfg.fontSize)
        ctx.textAlign(TextAlign.LEFT)
        val metrics = ctx.measureText(cfg.text)
        val (tx, ty) = resolveAnnotationTextPosition(
            desiredX = ax + cfg.dx,
            desiredBaselineY = ay + cfg.dy,
            textWidth = metrics.width,
            textAscent = metrics.actualBoundingBoxAscent,
            textDescent = metrics.actualBoundingBoxDescent,
            plot = plot,
        )

        if (cfg.connector) {
            ctx.beginPath()
            ctx.strokeStyle(cfg.connectorColor.toChartColor())
            ctx.lineWidth(1f)
            ctx.moveTo(ax, ay)
            ctx.lineTo(tx, ty)
            ctx.stroke()
        }
        if (cfg.anchorPoint) {
            ctx.beginPath()
            ctx.fillStyle(cfg.connectorColor.toChartColor())
            ctx.arc(ax, ay, 2.5f, 0f, (2 * PI).toFloat(), false)
            ctx.fill()
        }

        ctx.fillStyle(cfg.color.toChartColor())
        ctx.fillText(cfg.text, tx, ty)
    }

    fun drawAreaSeries(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        gradientFill: Boolean,
        selection: ChartSelection?,
        smooth: Boolean = false,
        showPoints: Boolean = false,
        pointRadius: Float = 4f,
        mode: AreaMode = AreaMode.BASIC,
    ) {
        if (mode == AreaMode.POLAR) {
            drawPolarArea(ctx, layout, viewport, series, theme, selection)
            return
        }
        if (mode == AreaMode.RANGE) {
            drawRangeArea(ctx, layout, viewport, series, theme)
            return
        }
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        val totals = if (mode == AreaMode.PERCENT_STACKED || mode == AreaMode.STREAM) {
            val n = series.maxOfOrNull { it.points.size } ?: 0
            (0 until n).map { i ->
                series.sumOf { it.points.getOrNull(i)?.y?.toDouble() ?: 0.0 }.toFloat()
            }
        } else emptyList()
        series.forEachIndexed { sIdx, s ->
            if (s.points.isEmpty()) return@forEachIndexed
            val stacked = mode == AreaMode.STACKED || mode == AreaMode.PERCENT_STACKED || mode == AreaMode.STREAM
            val values = s.points.mapIndexed { i, p -> when (mode) {
                    AreaMode.PERCENT_STACKED -> p.y / totals.getOrElse(i) { 1f } * 100f
                    else -> p.y
                }
            }
            val pixels = values.mapIndexed { i, value ->
                val lower = if (stacked) {
                    val prefix = series.take(sIdx)
                        .sumOf { it.points.getOrNull(i)?.y?.toDouble() ?: 0.0 }
                        .toFloat()
                    when (mode) {
                        AreaMode.PERCENT_STACKED -> prefix / totals.getOrElse(i) { 1f }.coerceAtLeast(1e-6f) * 100f
                        AreaMode.STREAM -> prefix - totals.getOrElse(i) { 0f } / 2f
                        else -> prefix
                    }
                } else 0f
                scale.toPixelX(s.points[i].x) to scale.toPixelY(value + lower)
            }
            val basePixels = values.mapIndexed { i, _ ->
                val lower = if (stacked) {
                    val prefix = series.take(sIdx)
                        .sumOf { it.points.getOrNull(i)?.y?.toDouble() ?: 0.0 }
                        .toFloat()
                    when (mode) {
                        AreaMode.PERCENT_STACKED -> prefix / totals.getOrElse(i) { 1f }.coerceAtLeast(1e-6f) * 100f
                        AreaMode.STREAM -> prefix - totals.getOrElse(i) { 0f } / 2f
                        else -> prefix
                    }
                } else 0f
                scale.toPixelX(s.points[i].x) to scale.toPixelY(lower)
            }
            val color = s.color.toChartColor()
            ctx.beginPath()
            val first = pixels.first()
            ctx.moveTo(first.first, if (stacked) basePixels.first().second else plot.bottom)
            if (mode == AreaMode.STEP) {
                ctx.lineTo(pixels.first().first, pixels.first().second)
                for (i in 1 until pixels.size) {
                    ctx.lineTo(pixels[i].first, pixels[i - 1].second)
                    ctx.lineTo(pixels[i].first, pixels[i].second)
                }
            } else if (smooth && pixels.size >= 2) {
                ctx.lineTo(first.first, first.second)
                pathThroughPoints(ctx, pixels, smooth = true, startFromIndex = 1)
            } else {
                pixels.forEach { (px, py) -> ctx.lineTo(px, py) }
            }
            val last = pixels.last()
            ctx.lineTo(last.first, if (stacked) basePixels.last().second else plot.bottom)
            if (stacked) {
                for (i in basePixels.lastIndex - 1 downTo 0) ctx.lineTo(basePixels[i].first, basePixels[i].second)
            }
            ctx.closePath()
            if (gradientFill) {
                val gradient = ctx.createLinearGradient(0f, plot.top, 0f, plot.bottom)
                gradient.addColorStop(0f, Color(s.color.withAlpha(0xAA)))
                gradient.addColorStop(1f, Color(s.color.withAlpha(0x11)))
                ctx.fillStyle(gradient)
            } else {
                ctx.fillStyle(Color(s.color.withAlpha(0x55)))
            }
            ctx.fill()
            ctx.beginPath()
            ctx.strokeStyle(color)
            ctx.lineWidth(theme.lineWidth)
            if (mode == AreaMode.STEP) {
                ctx.moveTo(pixels.first().first, pixels.first().second)
                for (i in 1 until pixels.size) {
                    ctx.lineTo(pixels[i].first, pixels[i - 1].second)
                    ctx.lineTo(pixels[i].first, pixels[i].second)
                }
            } else pathThroughPoints(ctx, pixels, smooth || mode == AreaMode.SPLINE)
            ctx.stroke()
            s.points.forEachIndexed { pIdx, p ->
                val selected = selection is ChartSelection.Cartesian &&
                    selection.seriesIndex == sIdx && selection.itemIndex == pIdx
                if (showPoints || selected) {
                    val (px, py) = pixels[pIdx]
                    val fill = p.resolveColor(s.color)
                    drawMarker(ctx, px, py, fill, if (selected) pointRadius + 2f else pointRadius, selected)
                }
            }
        }
    }

    private fun drawPolarArea(
        ctx: ContextApi, layout: CartesianLayout, viewport: ChartViewport,
        series: List<ChartSeries>, theme: ChartTheme, selection: ChartSelection?,
    ) {
        val plot = layout.plot
        val cx = (plot.left + plot.right) / 2f
        val cy = (plot.top + plot.bottom) / 2f
        val radius = min(plot.width, plot.height) * 0.38f
        val values = series.firstOrNull()?.points.orEmpty()
        val max = values.maxOfOrNull { it.y }?.coerceAtLeast(1f) ?: return
        val step = (2 * PI).toFloat() / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, p ->
            val a0 = -PI.toFloat() / 2f + i * step + 0.03f
            val a1 = a0 + step - 0.06f
            val r = radius * (p.y / max).coerceIn(0f, 1f)
            ctx.beginPath(); ctx.moveTo(cx, cy)
            ctx.lineTo(cx + r * cos(a0), cy + r * sin(a0))
            ctx.arc(cx, cy, r, a0, a1, false); ctx.closePath()
            ctx.fillStyle(Color(p.resolveColor(series.first().color).withAlpha(0xAA))); ctx.fill()
            ctx.strokeStyle(theme.backgroundColor.toChartColor()); ctx.lineWidth(2f); ctx.stroke()
        }
    }

    private fun drawRangeArea(
        ctx: ContextApi, layout: CartesianLayout, viewport: ChartViewport,
        series: List<ChartSeries>, theme: ChartTheme,
    ) {
        if (series.size < 2) return
        val scale = CartesianScale(layout.plot, viewport)
        val lower = series[0].points.map { scale.toPixelX(it.x) to scale.toPixelY(it.y) }
        val upper = series[1].points.map { scale.toPixelX(it.x) to scale.toPixelY(it.y) }
        if (lower.isEmpty() || upper.isEmpty()) return
        ctx.beginPath(); ctx.moveTo(lower.first().first, lower.first().second)
        lower.drop(1).forEach { ctx.lineTo(it.first, it.second) }
        upper.asReversed().forEach { ctx.lineTo(it.first, it.second) }
        ctx.closePath(); ctx.fillStyle(Color(series[0].color.withAlpha(0x44))); ctx.fill()
        ctx.beginPath(); lower.forEachIndexed { i, p -> if (i == 0) ctx.moveTo(p.first, p.second) else ctx.lineTo(p.first, p.second) }
        ctx.strokeStyle(series[0].color.toChartColor()); ctx.lineWidth(theme.lineWidth); ctx.stroke()
        ctx.beginPath(); upper.forEachIndexed { i, p -> if (i == 0) ctx.moveTo(p.first, p.second) else ctx.lineTo(p.first, p.second) }
        ctx.strokeStyle(series[1].color.toChartColor()); ctx.lineWidth(theme.lineWidth); ctx.stroke()
    }

    fun drawBarSeries(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
        showLabel: Boolean,
        grouped: Boolean = true,
    ) {
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        if (series.isEmpty()) return
        val categories = series.first().points.size
        if (categories == 0) return
        val groupWidth = plot.width / categories
        val barCount = if (grouped) series.size else 1
        val barWidth = groupWidth * 0.7f / barCount
        series.forEachIndexed { sIdx, s ->
            s.points.forEachIndexed { pIdx, p ->
                val cx = scale.toPixelX(p.x)
                val groupLeft = cx - groupWidth * 0.35f
                val barLeft = if (grouped) {
                    groupLeft + barWidth * sIdx + groupWidth * 0.15f / barCount
                } else {
                    cx - barWidth / 2f
                }
                val barTop = scale.toPixelY(p.y)
                val barBottom = scale.toPixelY(0f.coerceAtLeast(viewport.yMin))
                val selected = selection is ChartSelection.Cartesian &&
                    selection.seriesIndex == sIdx && selection.itemIndex == pIdx
                val fill = p.resolveColor(s.color)
                ctx.fillStyle(fill.toChartColor())
                ctx.fillRect(barLeft, barTop, barWidth, (barBottom - barTop).coerceAtLeast(1f))
                if (selected) {
                    strokeRect(ctx, barLeft, barTop, barWidth, (barBottom - barTop).coerceAtLeast(1f), fill)
                }
                if (showLabel) {
                    ctx.font(theme.fontSize - 1f)
                    ctx.fillStyle(theme.textColor.toChartColor())
                    ctx.textAlign(TextAlign.CENTER)
                    ctx.fillText(formatValue(p.y), barLeft + barWidth / 2f, barTop - 4f)
                }
            }
        }
    }

    fun drawStackedBarSeries(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
        showTotalLabel: Boolean,
    ) {
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        if (series.isEmpty()) return
        val categories = series.first().points.size
        val barWidth = plot.width / categories * 0.6f
        for (cIdx in 0 until categories) {
            var stackBase = 0f
            var total = 0f
            series.forEachIndexed { sIdx, s ->
                val p = s.points.getOrNull(cIdx) ?: return@forEachIndexed
                val cx = scale.toPixelX(p.x)
                val barLeft = cx - barWidth / 2f
                val barBottom = scale.toPixelY(stackBase)
                stackBase += p.y
                total += p.y
                val barTop = scale.toPixelY(stackBase)
                val selected = selection is ChartSelection.Cartesian &&
                    selection.seriesIndex == sIdx && selection.itemIndex == cIdx
                val fill = p.resolveColor(s.color)
                ctx.fillStyle(fill.toChartColor())
                ctx.fillRect(barLeft, barTop, barWidth, (barBottom - barTop).coerceAtLeast(1f))
                if (selected) {
                    strokeRect(ctx, barLeft, barTop, barWidth, (barBottom - barTop).coerceAtLeast(1f), fill)
                }
            }
            if (showTotalLabel && categories > 0) {
                val p = series.first().points[cIdx]
                val cx = scale.toPixelX(p.x)
                ctx.font(theme.fontSize - 1f)
                ctx.fillStyle(theme.textColor.toChartColor())
                ctx.textAlign(TextAlign.CENTER)
                ctx.fillText(formatValue(total), cx, scale.toPixelY(stackBase) - 4f)
            }
        }
    }

    fun drawHorizontalBarSeries(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
        showLabel: Boolean,
        stacked: Boolean = false,
        showTotalLabel: Boolean = true,
    ) {
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        if (series.isEmpty()) return
        val categories = series.first().points.size
        if (categories == 0) return
        val groupHeight = plot.height / categories
        if (stacked) {
            for (cIdx in 0 until categories) {
                var stackBase = 0f
                var total = 0f
                val category = series.first().points[cIdx].x
                val cy = scale.toPixelY(category)
                val barTop = cy - groupHeight * 0.3f
                val barHeight = groupHeight * 0.6f
                series.forEachIndexed { sIdx, s ->
                    val p = s.points.getOrNull(cIdx) ?: return@forEachIndexed
                    val barLeft = scale.toPixelX(stackBase)
                    stackBase += p.y
                    total += p.y
                    val barRight = scale.toPixelX(stackBase)
                    val selected = selection is ChartSelection.Cartesian &&
                        selection.seriesIndex == sIdx && selection.itemIndex == cIdx
                    val fill = p.resolveColor(s.color)
                    ctx.fillStyle(fill.toChartColor())
                    ctx.fillRect(barLeft, barTop, (barRight - barLeft).coerceAtLeast(1f), barHeight)
                    if (selected) {
                        strokeRect(ctx, barLeft, barTop, (barRight - barLeft).coerceAtLeast(1f), barHeight, fill)
                    }
                }
                if (showTotalLabel) {
                    ctx.font(theme.fontSize - 1f)
                    ctx.fillStyle(theme.textColor.toChartColor())
                    ctx.textAlign(TextAlign.LEFT)
                    ctx.fillText(
                        formatValue(total),
                        scale.toPixelX(stackBase) + 4f,
                        cy + theme.fontSize * 0.35f,
                    )
                }
            }
            return
        }
        val barCount = series.size
        val barHeight = groupHeight * 0.7f / barCount
        series.forEachIndexed { sIdx, s ->
            s.points.forEachIndexed { pIdx, p ->
                val cy = scale.toPixelY(p.x)
                val groupTop = cy - groupHeight * 0.35f
                val barTop = groupTop + barHeight * sIdx + groupHeight * 0.15f / barCount
                val barLeft = scale.toPixelX(0f.coerceAtLeast(viewport.xMin))
                val barRight = scale.toPixelX(p.y)
                val selected = selection is ChartSelection.Cartesian &&
                    selection.seriesIndex == sIdx && selection.itemIndex == pIdx
                val fill = p.resolveColor(s.color)
                ctx.fillStyle(fill.toChartColor())
                ctx.fillRect(barLeft, barTop, (barRight - barLeft).coerceAtLeast(1f), barHeight)
                if (selected) {
                    strokeRect(ctx, barLeft, barTop, (barRight - barLeft).coerceAtLeast(1f), barHeight, fill)
                }
                if (showLabel) {
                    ctx.font(theme.fontSize - 1f)
                    ctx.fillStyle(theme.textColor.toChartColor())
                    ctx.textAlign(TextAlign.LEFT)
                    ctx.fillText(formatValue(p.y), barRight + 4f, barTop + barHeight * 0.7f)
                }
            }
        }
    }

    fun drawScatterSeries(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        series: List<ChartSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
        pointRadius: Float,
    ) {
        val plot = layout.plot
        val scale = CartesianScale(plot, viewport)
        series.forEachIndexed { sIdx, s ->
            s.points.forEachIndexed { pIdx, p ->
                val px = scale.toPixelX(p.x)
                val py = scale.toPixelY(p.y)
                val selected = selection is ChartSelection.Cartesian &&
                    selection.seriesIndex == sIdx && selection.itemIndex == pIdx
                val radius = if (selected) pointRadius + 2f else pointRadius
                val fill = p.resolveColor(s.color)
                drawMarker(ctx, px, py, fill, radius, selected)
            }
        }
    }

    internal fun stockLineSegments(
        sourceX: List<Float>,
        values: List<Float?>,
        visibleX: ClosedFloatingPointRange<Float>,
    ): List<List<Pair<Float, Float>>> {
        val aligned = ArrayList<Pair<Float, Float>?>(minOf(sourceX.size, values.size))
        for (index in 0 until minOf(sourceX.size, values.size)) {
            val x = sourceX[index]
            val value = values[index]
            aligned += if (x.isFinite() && value != null && value.isFinite()) x to value else null
        }
        return lineSegments(aligned, connectNulls = false).mapNotNull { segment ->
            val firstVisible = segment.indexOfFirst { it.first in visibleX }
            if (firstVisible >= 0) {
                val lastVisible = segment.indexOfLast { it.first in visibleX }
                return@mapNotNull segment.subList(
                    (firstVisible - 1).coerceAtLeast(0),
                    (lastVisible + 2).coerceAtMost(segment.size),
                )
            }
            val crossingIndex = (0 until segment.lastIndex).firstOrNull { index ->
                val firstX = segment[index].first
                val secondX = segment[index + 1].first
                maxOf(firstX, secondX) >= visibleX.start &&
                    minOf(firstX, secondX) <= visibleX.endInclusive
            }
            crossingIndex?.let { segment.subList(it, it + 2) }
        }
    }

    internal fun stockDataToPixel(
        plot: PlotRect,
        viewport: ChartViewport,
        dataX: Float,
        dataY: Float,
    ): Pair<Float, Float>? {
        if (!dataX.isFinite() || !dataY.isFinite()) return null
        val endpoints = listOf(
            plot.left, plot.top, plot.right, plot.bottom,
            viewport.xMin, viewport.xMax, viewport.yMin, viewport.yMax,
        )
        if (endpoints.any { !it.isFinite() }) return null
        val plotWidth = plot.right.toDouble() - plot.left.toDouble()
        val plotHeight = plot.bottom.toDouble() - plot.top.toDouble()
        val xRange = viewport.xMax.toDouble() - viewport.xMin.toDouble()
        val yRange = viewport.yMax.toDouble() - viewport.yMin.toDouble()
        if (!plotWidth.isFinite() || plotWidth <= 0.0 || !plotHeight.isFinite() || plotHeight <= 0.0 ||
            !xRange.isFinite() || xRange <= 0.0 || !yRange.isFinite() || yRange <= 0.0
        ) return null
        val pixelX = plot.left.toDouble() +
            (dataX.toDouble() - viewport.xMin.toDouble()) / xRange * plotWidth
        val pixelY = plot.bottom.toDouble() -
            (dataY.toDouble() - viewport.yMin.toDouble()) / yRange * plotHeight
        if (!pixelX.isFinite() || !pixelY.isFinite()) return null
        val resultX = pixelX.toFloat()
        val resultY = pixelY.toFloat()
        return if (resultX.isFinite() && resultY.isFinite()) resultX to resultY else null
    }

    internal fun stockLinePixelSegments(
        plot: PlotRect,
        viewport: ChartViewport,
        segments: List<List<Pair<Float, Float>>>,
    ): List<List<Pair<Float, Float>>> = buildList {
        segments.forEach { segment ->
            val pixels = ArrayList<Pair<Float, Float>>(segment.size)
            var valid = true
            segment.forEach { point ->
                val pixel = stockDataToPixel(plot, viewport, point.first, point.second)
                if (pixel == null) {
                    valid = false
                } else {
                    pixels += pixel
                }
            }
            if (valid && pixels.isNotEmpty()) add(pixels)
        }
    }

    internal fun stockHistogramBars(
        sourceX: List<Float>,
        values: List<Float?>,
        visibleX: ClosedFloatingPointRange<Float>,
    ): List<StockHistogramBar> {
        val bars = ArrayList<StockHistogramBar>(minOf(sourceX.size, values.size))
        for (index in 0 until minOf(sourceX.size, values.size)) {
            val x = sourceX[index]
            val value = values[index]
            if (x.isFinite() && x in visibleX && value != null && value.isFinite()) {
                bars += StockHistogramBar(x, value)
            }
        }
        return bars
    }

    internal fun stockHistogramBounds(bars: List<StockHistogramBar>): ClosedFloatingPointRange<Float> {
        if (bars.isEmpty()) return -1f..1f
        var minimum = 0f
        var maximum = 0f
        bars.forEach { bar ->
            minimum = minOf(minimum, bar.value)
            maximum = maxOf(maximum, bar.value)
        }
        if (minimum == maximum) {
            val padding = (kotlin.math.abs(minimum) * 0.05f).coerceAtLeast(1e-3f)
            return (minimum - padding)..(maximum + padding)
        }
        return minimum..maximum
    }

    internal fun stockHistogramPixelBars(
        plot: PlotRect,
        viewport: ChartViewport,
        sourceX: List<Float>,
        values: List<Float?>,
    ): List<StockHistogramPixelBar> {
        if (!isFinitePositivePlot(plot) || !isFiniteOrderedViewport(viewport)) return emptyList()
        val xScale = plot.width.toDouble() / (viewport.xMax.toDouble() - viewport.xMin.toDouble())
        val yScale = plot.height.toDouble() / (viewport.yMax.toDouble() - viewport.yMin.toDouble())
        if (!xScale.isFinite() || xScale <= 0.0 || !yScale.isFinite() || yScale <= 0.0) return emptyList()
        val baseline = (plot.bottom.toDouble() - (0.0 - viewport.yMin.toDouble()) * yScale)
            .coerceIn(plot.top.toDouble(), plot.bottom.toDouble())
        val count = minOf(sourceX.size, values.size)
        return buildList {
            for (index in 0 until count) {
                val x = sourceX[index]
                val value = values[index]
                if (!x.isFinite() || x !in viewport.xMin..viewport.xMax || value == null ||
                    !value.isFinite() || value == 0f
                ) continue
                val spacing = localFiniteXSpacing(sourceX, index)
                    ?: ((viewport.xMax.toDouble() - viewport.xMin.toDouble()) / count.coerceAtLeast(1))
                val center = plot.left.toDouble() + (x.toDouble() - viewport.xMin.toDouble()) * xScale
                val rawWidth = (spacing * xScale * 0.6).coerceIn(
                    minOf(1.0, plot.width.toDouble()),
                    plot.width.toDouble(),
                )
                val rawLeft = center - rawWidth / 2.0
                val clippedLeft = rawLeft.coerceAtLeast(plot.left.toDouble())
                val clippedRight = (rawLeft + rawWidth).coerceAtMost(plot.right.toDouble())
                if (!center.isFinite() || clippedRight <= clippedLeft) continue
                val valueY = (plot.bottom.toDouble() -
                    (value.toDouble() - viewport.yMin.toDouble()) * yScale)
                    .coerceIn(plot.top.toDouble(), plot.bottom.toDouble())
                val rawHeight = kotlin.math.abs(baseline - valueY)
                if (!rawHeight.isFinite() || rawHeight <= 0.0) continue
                val desiredHeight = rawHeight.coerceAtLeast(1.0)
                val top = if (value > 0f) {
                    (baseline - desiredHeight).coerceAtLeast(plot.top.toDouble())
                } else {
                    baseline
                }
                val bottom = if (value > 0f) {
                    baseline
                } else {
                    (baseline + desiredHeight).coerceAtMost(plot.bottom.toDouble())
                }
                if (bottom <= top) continue
                add(
                    StockHistogramPixelBar(
                        sourceX = x,
                        value = value,
                        left = clippedLeft.toFloat(),
                        top = top.toFloat(),
                        width = (clippedRight - clippedLeft).toFloat(),
                        height = (bottom - top).toFloat(),
                    ),
                )
            }
        }
    }

    private fun localFiniteXSpacing(sourceX: List<Float>, index: Int): Double? {
        val x = sourceX[index]
        var previous: Float? = null
        for (candidate in index - 1 downTo 0) {
            if (sourceX[candidate].isFinite()) {
                previous = sourceX[candidate]
                break
            }
        }
        var next: Float? = null
        for (candidate in index + 1 until sourceX.size) {
            if (sourceX[candidate].isFinite()) {
                next = sourceX[candidate]
                break
            }
        }
        return listOfNotNull(previous, next)
            .map { kotlin.math.abs(it.toDouble() - x.toDouble()) }
            .filter { it.isFinite() && it > 0.0 }
            .minOrNull()
    }

    internal fun paneHeaderItems(
        plot: PlotRect,
        title: String,
        summaries: List<Pair<String, Long>>,
        fontSize: Float,
        measureText: (String) -> Float,
    ): List<PaneHeaderItem> {
        if (!isFinitePositivePlot(plot) || !fontSize.isFinite() || fontSize <= 0f) return emptyList()
        val items = ArrayList<PaneHeaderItem>(summaries.size + 1)
        var x = plot.left + 4f
        var lastWasEllipsized = false
        fun fittedText(text: String, availableWidth: Float): Pair<String, Float>? {
            val measured = measureText(text)
            if (measured.isFinite() && measured > 0f && measured <= availableWidth) return text to measured
            if (availableWidth <= 0f) return null
            for (length in text.length - 1 downTo 0) {
                val candidate = text.take(length) + "\u2026"
                val width = measureText(candidate)
                if (width.isFinite() && width > 0f && width <= availableWidth) return candidate to width
            }
            return null
        }
        fun append(text: String, color: Long): Boolean {
            if (text.isBlank()) return true
            val fitted = fittedText(text, plot.right - 4f - x) ?: return false
            // 容纳不下时直接放弃，避免末尾出现孤立的 "..." 占位。
            if (fitted.first != text) return false
            items += PaneHeaderItem(fitted.first, color, x, fitted.second)
            lastWasEllipsized = false
            val width = fitted.second
            x += width + 10f
            return true
        }
        if (!append(title, 0L)) return emptyList()
        if (lastWasEllipsized) return items
        for ((text, color) in summaries) {
            if (!append(text, color)) break
        }
        return items
    }

    internal fun currentPriceLineY(
        plot: PlotRect,
        viewport: ChartViewport,
        price: Float,
    ): Float? {
        if (!price.isFinite() || !isFinitePositivePlot(plot) || !isFiniteOrderedViewport(viewport)) return null
        if (price !in viewport.yMin..viewport.yMax) return null
        val y = plot.bottom.toDouble() - plot.height.toDouble() *
            (price.toDouble() - viewport.yMin.toDouble()) /
            (viewport.yMax.toDouble() - viewport.yMin.toDouble())
        return y.toFloat().takeIf { it.isFinite() && it in plot.top..plot.bottom }
    }

    internal fun currentPriceLinePlan(
        plot: PlotRect,
        viewport: ChartViewport,
        price: Float,
        lineWidth: Float,
        dashLength: Float,
        dashGap: Float,
    ): CurrentPriceLinePlan? = currentPriceLineY(plot, viewport, price)?.let { y ->
        CurrentPriceLinePlan(
            y = y,
            lineWidth = lineWidth.takeIf { it.isFinite() && it > 0f } ?: 1f,
            dashPattern = listOf(
                dashLength.takeIf { it.isFinite() && it > 0f } ?: 4f,
                dashGap.takeIf { it.isFinite() && it >= 0f } ?: 3f,
            ),
        )
    }

    internal fun currentPriceRenderPlan(
        plot: PlotRect,
        viewport: ChartViewport,
        price: Float,
        lineWidth: Float,
        dashLength: Float,
        dashGap: Float,
        showLabel: Boolean,
        labelText: String,
        fontSize: Float,
        measuredLabelWidth: Float,
    ): CurrentPriceRenderPlan? {
        val line = currentPriceLinePlan(plot, viewport, price, lineWidth, dashLength, dashGap) ?: return null
        if (!showLabel) return CurrentPriceRenderPlan(line, null)
        if (labelText.isBlank() || !fontSize.isFinite() || fontSize <= 0f ||
            !measuredLabelWidth.isFinite() || measuredLabelWidth <= 0f
        ) return CurrentPriceRenderPlan(line, null)
        val horizontalPadding = 4f
        val verticalPadding = 2f
        val width = (measuredLabelWidth + horizontalPadding * 2f).coerceAtMost(plot.width)
        val height = (fontSize + verticalPadding * 2f).coerceAtMost(plot.height)
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            return CurrentPriceRenderPlan(line, null)
        }
        val left = plot.right - width
        val top = (line.y - height / 2f).coerceIn(plot.top, plot.bottom - height)
        return CurrentPriceRenderPlan(
            line,
            CurrentPriceLabelPlan(
                text = labelText,
                left = left,
                top = top,
                right = plot.right,
                bottom = top + height,
                baseline = (top + verticalPadding + fontSize).coerceAtMost(top + height),
            ),
        )
    }

    internal fun currentPriceLineDashReset(): List<Float> = emptyList()

    internal fun stockCloseLineData(points: List<OhlcPoint>): StockLineData = StockLineData(
        sourceX = points.map { it.x },
        values = points.map { point -> point.close.takeIf { point.hasValidOhlcSemantics() } },
    )

    internal fun stockCandlestickPixelPlan(
        plot: PlotRect,
        viewport: ChartViewport,
        points: List<OhlcPoint>,
        candleWidthRatio: Float,
    ): List<StockCandlestickPixel> {
        if (!isFinitePositivePlot(plot) || !isFiniteOrderedViewport(viewport)) return emptyList()
        val visible = points.withIndex().filter { (_, point) ->
            point.hasValidOhlcSemantics() && point.x in viewport.xMin..viewport.xMax
        }
        if (visible.isEmpty()) return emptyList()
        val bodyWidth = plot.width / visible.size * candleWidthRatio.coerceIn(0.2f, 0.9f)
        if (!bodyWidth.isFinite() || bodyWidth <= 0f) return emptyList()
        return visible.mapNotNull { indexed ->
            val point = indexed.value
            val center = stockDataToPixel(plot, viewport, point.x, point.close) ?: return@mapNotNull null
            val high = stockDataToPixel(plot, viewport, point.x, point.high) ?: return@mapNotNull null
            val low = stockDataToPixel(plot, viewport, point.x, point.low) ?: return@mapNotNull null
            val open = stockDataToPixel(plot, viewport, point.x, point.open) ?: return@mapNotNull null
            StockCandlestickPixel(
                sourceIndex = indexed.index,
                centerX = center.first,
                highY = high.second,
                lowY = low.second,
                openY = open.second,
                closeY = center.second,
                bodyWidth = bodyWidth,
                isUp = point.close >= point.open,
            ).takeIf { candle ->
                listOf(candle.centerX, candle.highY, candle.lowY, candle.openY, candle.closeY, candle.bodyWidth)
                    .all(Float::isFinite)
            }
        }
    }

    internal fun stockVolumePixelPlan(
        plot: PlotRect,
        viewport: ChartViewport,
        points: List<OhlcPoint>,
        candleWidthRatio: Float,
    ): List<StockVolumePixel> {
        if (!isFinitePositivePlot(plot) || !isFiniteOrderedViewport(viewport)) return emptyList()
        val visible = points.withIndex().filter { (_, point) ->
            point.hasValidOhlcSemantics() && point.x in viewport.xMin..viewport.xMax &&
                point.volume?.isFinite() == true
        }
        if (visible.isEmpty()) return emptyList()
        val plotLeft = plot.left.toDouble()
        val plotTop = plot.top.toDouble()
        val plotRight = plot.right.toDouble()
        val plotBottom = plot.bottom.toDouble()
        val xRange = viewport.xMax.toDouble() - viewport.xMin.toDouble()
        val yRange = viewport.yMax.toDouble() - viewport.yMin.toDouble()
        val xScale = (plotRight - plotLeft) / xRange
        val yScale = (plotBottom - plotTop) / yRange
        val bodyWidth = (plotRight - plotLeft) / visible.size *
            candleWidthRatio.coerceIn(0.2f, 0.9f).toDouble()
        val baselineY = (plotBottom - (0.0 - viewport.yMin.toDouble()) * yScale)
            .coerceIn(plotTop, plotBottom)
        if (!xScale.isFinite() || xScale <= 0.0 || !yScale.isFinite() || yScale <= 0.0 ||
            !bodyWidth.isFinite() || bodyWidth <= 0.0 || !baselineY.isFinite()
        ) return emptyList()
        return visible.mapNotNull { indexed ->
            val point = indexed.value
            val volume = point.volume ?: return@mapNotNull null
            val centerX = plotLeft + (point.x.toDouble() - viewport.xMin.toDouble()) * xScale
            val rawLeft = centerX - bodyWidth / 2.0
            val clippedLeft = rawLeft.coerceAtLeast(plotLeft)
            val clippedRight = (rawLeft + bodyWidth).coerceAtMost(plotRight)
            val valueY = (plotBottom -
                (volume.toDouble().coerceIn(viewport.yMin.toDouble(), viewport.yMax.toDouble()) -
                    viewport.yMin.toDouble()) * yScale)
                .coerceIn(plotTop, plotBottom)
            val top = minOf(valueY, baselineY)
            val bottom = maxOf(valueY, baselineY)
            if (!centerX.isFinite() || clippedRight <= clippedLeft || bottom <= top) return@mapNotNull null
            StockVolumePixel(
                indexed.index,
                point.x,
                clippedLeft.toFloat(),
                top.toFloat(),
                (clippedRight - clippedLeft).toFloat(),
                (bottom - top).toFloat(),
                point.close >= point.open,
            )
                .takeIf { bar -> listOf(bar.left, bar.top, bar.width, bar.height).all(Float::isFinite) }
        }
    }

    internal fun shouldDrawPaneEmptyState(plot: PlotRect, text: String): Boolean =
        text.isNotBlank() && isFinitePositivePlot(plot)

    internal fun paneEmptyStateCenter(plot: PlotRect): Pair<Float, Float> =
        midpoint(plot.left, plot.right) to midpoint(plot.top, plot.bottom)

    private fun midpoint(first: Float, second: Float): Float =
        (first.toDouble() + (second.toDouble() - first.toDouble()) / 2.0).toFloat()

    private fun isFinitePositivePlot(plot: PlotRect): Boolean {
        if (!plot.left.isFinite() || !plot.top.isFinite() || !plot.right.isFinite() || !plot.bottom.isFinite()) {
            return false
        }
        val width = plot.right.toDouble() - plot.left.toDouble()
        val height = plot.bottom.toDouble() - plot.top.toDouble()
        return width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0 &&
            width <= Float.MAX_VALUE.toDouble() && height <= Float.MAX_VALUE.toDouble()
    }

    private fun isFiniteOrderedViewport(viewport: ChartViewport): Boolean =
        viewport.xMin.isFinite() && viewport.xMax.isFinite() && viewport.xMax > viewport.xMin &&
            viewport.yMin.isFinite() && viewport.yMax.isFinite() && viewport.yMax > viewport.yMin

    fun drawCurrentPriceLine(
        ctx: ContextApi,
        plot: PlotRect,
        viewport: ChartViewport,
        price: Float,
        color: Long,
        lineWidth: Float = 1f,
        dashLength: Float = 4f,
        dashGap: Float = 3f,
        showLabel: Boolean = false,
        labelText: String = price.toString(),
        fontSize: Float = 12f,
    ) {
        ctx.font(fontSize)
        val measuredWidth = if (showLabel) ctx.measureText(labelText).width else 0f
        val plan = currentPriceRenderPlan(
            plot, viewport, price, lineWidth, dashLength, dashGap,
            showLabel, labelText, fontSize, measuredWidth,
        ) ?: return
        ctx.withPlotClip(plot) {
            try {
                ctx.beginPath()
                ctx.strokeStyle(color.toChartColor())
                ctx.lineWidth(plan.line.lineWidth)
                ctx.setLineDash(plan.line.dashPattern)
                ctx.moveTo(plot.left, plan.line.y)
                ctx.lineTo(plot.right, plan.line.y)
                ctx.stroke()
            } finally {
                ctx.setLineDash(currentPriceLineDashReset())
            }
            plan.label?.let { label ->
                ctx.fillStyle(color.toChartColor())
                ctx.fillRect(label.left, label.top, label.right - label.left, label.bottom - label.top)
                ctx.fillStyle(Color.WHITE)
                ctx.textAlign(TextAlign.LEFT)
                ctx.fillText(label.text, label.left + 4f, label.baseline)
            }
        }
    }

    fun drawStockCloseLine(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        points: List<OhlcPoint>,
        theme: ChartTheme,
    ) {
        val data = stockCloseLineData(points)
        drawStockSegments(
            ctx,
            layout,
            viewport,
            stockLineSegments(data.sourceX, data.values, viewport.xMin..viewport.xMax),
            theme.primaryColor,
            theme.lineWidth,
        )
    }

    fun drawStockLines(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        lines: List<StockLineResult>,
        theme: ChartTheme,
        sourceX: List<Float>? = null,
        source: List<OhlcPoint>? = null,
    ) {
        lines.forEach { line ->
            val data = source?.let { stockLineData(it, line.values) }
            val xValues = data?.sourceX ?: sourceX ?: List(line.values.size) { it.toFloat() }
            val values = data?.values ?: line.values
            drawStockSegments(
                ctx,
                layout,
                viewport,
                stockLineSegments(xValues, values, viewport.xMin..viewport.xMax),
                line.color,
                theme.lineWidth,
            )
        }
    }

    private fun drawStockSegments(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        segments: List<List<Pair<Float, Float>>>,
        color: Long,
        lineWidth: Float,
    ) {
        if (segments.isEmpty()) return
        val pixelSegments = stockLinePixelSegments(layout.plot, viewport, segments)
        if (pixelSegments.isEmpty()) return
        ctx.withPlotClip(layout.plot) {
            ctx.strokeStyle(color.toChartColor())
            ctx.lineWidth(lineWidth)
            pixelSegments.forEach { segment ->
                if (segment.isEmpty()) return@forEach
                ctx.beginPath()
                ctx.moveTo(segment[0].first, segment[0].second)
                for (index in 1 until segment.size) {
                    ctx.lineTo(segment[index].first, segment[index].second)
                }
                ctx.stroke()
            }
        }
    }

    fun drawStockHistogram(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        values: List<Float?>,
        positiveColor: Long,
        negativeColor: Long,
        sourceX: List<Float>? = null,
    ) {
        val bars = stockHistogramPixelBars(
            layout.plot,
            viewport,
            sourceX ?: List(values.size) { it.toFloat() },
            values,
        )
        if (bars.isEmpty()) return
        ctx.withPlotClip(layout.plot) {
            bars.forEach { bar ->
                ctx.fillStyle((if (bar.value >= 0f) positiveColor else negativeColor).toChartColor())
                ctx.fillRect(bar.left, bar.top, bar.width, bar.height)
            }
        }
    }

    fun drawPaneHeader(
        ctx: ContextApi,
        plot: PlotRect,
        title: String,
        summaries: List<Pair<String, Long>>,
        theme: ChartTheme,
    ) {
        val fontSize = theme.fontSize.coerceAtMost(plot.height.coerceAtLeast(0f))
        if (!fontSize.isFinite() || fontSize <= 0f) return
        ctx.withPlotClip(plot) {
            ctx.font(fontSize)
            val items = paneHeaderItems(plot, title, summaries, fontSize) { text ->
                ctx.measureText(text).width
            }
            if (items.isEmpty()) return@withPlotClip
            ctx.textAlign(TextAlign.LEFT)
            val baseline = (plot.top + fontSize + 3f).coerceAtMost(plot.bottom)
            items.forEach { item ->
                ctx.fillStyle((if (item.color == 0L) theme.textColor else item.color).toChartColor())
                ctx.fillText(item.text, item.x, baseline)
            }
        }
    }

    fun drawPaneEmptyState(
        ctx: ContextApi,
        plot: PlotRect,
        text: String,
        theme: ChartTheme,
    ) {
        if (!shouldDrawPaneEmptyState(plot, text)) return
        val center = paneEmptyStateCenter(plot)
        if (!center.first.isFinite() || !center.second.isFinite()) return
        ctx.withPlotClip(plot) {
            ctx.font(theme.fontSize.coerceAtMost(plot.height))
            ctx.fillStyle(theme.axisColor.toChartColor())
            ctx.textAlign(TextAlign.CENTER)
            ctx.fillText(text, center.first, center.second)
        }
    }

    fun drawCandlesticks(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        points: List<OhlcPoint>,
        theme: ChartTheme,
        selection: ChartSelection?,
        candleWidthRatio: Float,
    ) {
        val plot = layout.plot
        val candles = stockCandlestickPixelPlan(plot, viewport, points, candleWidthRatio)
        if (candles.isEmpty()) return
        ctx.withPlotClip(plot) {
            candles.forEach { candle ->
                val fill = if (candle.isUp) theme.upColor else theme.downColor
                val selected = selection is ChartSelection.Cartesian &&
                    selection.seriesIndex == 0 && selection.itemIndex == candle.sourceIndex
                ctx.beginPath()
                ctx.strokeStyle(fill.toChartColor())
                ctx.lineWidth(1f)
                ctx.moveTo(candle.centerX, candle.highY)
                ctx.lineTo(candle.centerX, candle.lowY)
                ctx.stroke()
                val top = minOf(candle.openY, candle.closeY)
                val height = (kotlin.math.abs(candle.closeY - candle.openY)).coerceAtLeast(1f)
                ctx.fillStyle(fill.toChartColor())
                ctx.fillRect(candle.centerX - candle.bodyWidth / 2f, top, candle.bodyWidth, height)
                if (selected) {
                    strokeRect(ctx, candle.centerX - candle.bodyWidth / 2f, top, candle.bodyWidth, height, fill)
                }
            }
        }
    }

    fun drawStockAverageLine(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        points: List<Pair<Float, Float>>,
        color: Long,
        theme: ChartTheme,
    ) {
        if (points.isEmpty()) return
        drawLineSeries(
            ctx = ctx,
            layout = layout,
            viewport = viewport,
            series = listOf(
                ChartSeries(
                    name = "",
                    color = color,
                    points = points.map { (x, y) -> ChartDataPoint("", x, y) },
                ),
            ),
            theme = theme,
            selection = null,
            showPoints = false,
        )
    }

    fun drawStockVolumes(
        ctx: ContextApi,
        layout: CartesianLayout,
        viewport: ChartViewport,
        points: List<OhlcPoint>,
        candleWidthRatio: Float,
        theme: ChartTheme,
    ) {
        val bars = stockVolumePixelPlan(layout.plot, viewport, points, candleWidthRatio)
        if (bars.isEmpty()) return
        ctx.withPlotClip(layout.plot) {
            bars.forEach { bar ->
                val color = if (bar.isUp) theme.upColor else theme.downColor
                ctx.fillStyle(color.toChartColor())
                ctx.fillRect(bar.left, bar.top, bar.width, bar.height)
            }
        }
    }

    fun drawStockLegend(
        ctx: ContextApi,
        left: Float,
        baseline: Float,
        lines: List<StockAverageLine>,
        values: Map<Int, Float?>,
        theme: ChartTheme,
    ) {
        var x = left
        ctx.font(theme.fontSize)
        ctx.textAlign(TextAlign.LEFT)
        lines.forEach { line ->
            val value = values[line.period]
            val text = if (value == null) line.label else "${line.label}:${formatValue(value)}"
            ctx.fillStyle(line.color.toChartColor())
            ctx.fillText(text, x, baseline)
            x += text.length * theme.fontSize * 0.58f + 12f
        }
    }

    fun drawPieSlices(
        ctx: ContextApi,
        centerX: Float,
        centerY: Float,
        outerRadius: Float,
        innerRadius: Float,
        slices: List<ChartSlice>,
        startAngleDeg: Float,
        selection: ChartSelection?,
        theme: ChartTheme,
        showPercent: Boolean,
    ) {
        val total = slices.sumOf { it.value.coerceAtLeast(0f).toDouble() }
            .toFloat()
            .coerceAtLeast(1e-6f)
        var angle = startAngleDeg * PI.toFloat() / 180f
        slices.forEachIndexed { idx, slice ->
            val safeValue = slice.value.coerceAtLeast(0f)
            val sweep = safeValue / total * (2 * PI).toFloat()
            val selected = selection is ChartSelection.Slice && selection.sliceIndex == idx
            val radius = if (selected) outerRadius + 6f else outerRadius
            ctx.beginPath()
            ctx.moveTo(
                centerX + innerRadius * cos(angle),
                centerY + innerRadius * sin(angle),
            )
            ctx.arc(centerX, centerY, radius, angle, angle + sweep, false)
            ctx.arc(centerX, centerY, innerRadius, angle + sweep, angle, true)
            ctx.closePath()
            ctx.fillStyle(slice.color.toChartColor())
            ctx.fill()
            if (selected) {
                ctx.strokeStyle(Color.WHITE)
                ctx.lineWidth(2.5f)
                ctx.stroke()
            }
            if (showPercent) {
                val mid = angle + sweep / 2f
                val labelR = (radius + innerRadius) / 2f
                val lx = centerX + labelR * cos(mid)
                val ly = centerY + labelR * sin(mid)
                ctx.font(theme.fontSize)
                ctx.fillStyle(theme.textColor.toChartColor())
                ctx.textAlign(TextAlign.CENTER)
                val percent = (safeValue / total * 100f).let { formatValue(it) }
                ctx.fillText("$percent%", lx, ly)
            }
            angle += sweep
        }
    }

    fun drawRadar(
        ctx: ContextApi,
        centerX: Float,
        centerY: Float,
        radius: Float,
        dimensions: List<RadarDimension>,
        series: List<RadarSeries>,
        theme: ChartTheme,
        selection: ChartSelection?,
    ) {
        if (dimensions.isEmpty()) return
        val count = dimensions.size
        val angleStep = (2 * PI).toFloat() / count
        val startAngle = -PI.toFloat() / 2f
        val selectedDim = (selection as? ChartSelection.Radar)?.dimensionIndex
        val selectedSeries = (selection as? ChartSelection.Radar)?.seriesIndex

        for (level in 1..4) {
            val r = radius * level / 4f
            ctx.beginPath()
            for (i in 0 until count) {
                val angle = startAngle + angleStep * i
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)
                if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
            }
            ctx.closePath()
            ctx.strokeStyle(theme.gridColor.toChartColor())
            ctx.lineWidth(1f)
            ctx.stroke()
        }
        dimensions.forEachIndexed { i, dim ->
            val angle = startAngle + angleStep * i
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            val axisSelected = selectedDim == i
            ctx.beginPath()
            ctx.moveTo(centerX, centerY)
            ctx.lineTo(x, y)
            ctx.strokeStyle(
                if (axisSelected) theme.primaryColor.toChartColor() else theme.gridColor.toChartColor(),
            )
            ctx.lineWidth(if (axisSelected) 2f else 1f)
            ctx.stroke()
            ctx.font(theme.fontSize)
            ctx.fillStyle(
                if (axisSelected) theme.primaryColor.toChartColor() else theme.textColor.toChartColor(),
            )
            ctx.textAlign(TextAlign.CENTER)
            ctx.fillText(dim.label, x + 12f * cos(angle), y + 12f * sin(angle))
        }
        series.forEachIndexed { sIdx, s ->
            val isSelectedSeries = selectedSeries == sIdx
            val dimOthers = selectedSeries != null && !isSelectedSeries
            val fillAlpha = when {
                isSelectedSeries -> 0x66
                dimOthers -> 0x18
                else -> 0x44
            }
            val strokeAlpha = if (dimOthers) 0x66 else 0xFF
            ctx.beginPath()
            s.values.forEachIndexed { i, value ->
                val dim = dimensions.getOrNull(i) ?: return@forEachIndexed
                val ratio = PolarScale.radarValueRatio(value, dim.maxValue)
                val angle = startAngle + angleStep * i
                val x = centerX + radius * ratio * cos(angle)
                val y = centerY + radius * ratio * sin(angle)
                if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
            }
            ctx.closePath()
            ctx.fillStyle(Color(s.color.withAlpha(fillAlpha)))
            ctx.fill()
            ctx.strokeStyle(Color(s.color.withAlpha(strokeAlpha)))
            ctx.lineWidth(if (isSelectedSeries) theme.lineWidth + 1f else theme.lineWidth)
            ctx.stroke()
            s.values.forEachIndexed { i, value ->
                val dim = dimensions.getOrNull(i) ?: return@forEachIndexed
                val ratio = PolarScale.radarValueRatio(value, dim.maxValue)
                val angle = startAngle + angleStep * i
                val x = centerX + radius * ratio * cos(angle)
                val y = centerY + radius * ratio * sin(angle)
                val selected = selection is ChartSelection.Radar &&
                    selection.seriesIndex == sIdx && selection.dimensionIndex == i
                drawMarker(ctx, x, y, s.color, if (selected) 7f else 4f, selected)
            }
        }
    }

    private fun pathThroughPoints(
        ctx: ContextApi,
        points: List<Pair<Float, Float>>,
        smooth: Boolean,
        startFromIndex: Int = 0,
    ) {
        if (points.isEmpty()) return
        if (startFromIndex == 0) {
            ctx.moveTo(points[0].first, points[0].second)
        }
        if (!smooth || points.size < 3) {
            val from = if (startFromIndex == 0) 1 else startFromIndex
            for (i in from until points.size) {
                ctx.lineTo(points[i].first, points[i].second)
            }
            return
        }
        val start = if (startFromIndex == 0) 0 else startFromIndex - 1
        for (i in start until points.size - 1) {
            val p0 = points[if (i == 0) 0 else i - 1]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[if (i + 2 < points.size) i + 2 else i + 1]
            val cp1x = p1.first + (p2.first - p0.first) / 6f
            val cp1y = p1.second + (p2.second - p0.second) / 6f
            val cp2x = p2.first - (p3.first - p1.first) / 6f
            val cp2y = p2.second - (p3.second - p1.second) / 6f
            ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.first, p2.second)
        }
    }

    /** 选中态保持自身颜色，外扩 + 白色描边强调。 */
    private fun drawMarker(
        ctx: ContextApi,
        px: Float,
        py: Float,
        color: Long,
        radius: Float,
        selected: Boolean,
    ) {
        if (selected) {
            ctx.beginPath()
            ctx.fillStyle(Color(color.withAlpha(0x33)))
            ctx.arc(px, py, radius + 4f, 0f, (2 * PI).toFloat(), false)
            ctx.fill()
        }
        ctx.beginPath()
        ctx.fillStyle(color.toChartColor())
        ctx.arc(px, py, radius, 0f, (2 * PI).toFloat(), false)
        ctx.fill()
        if (selected) {
            ctx.beginPath()
            ctx.strokeStyle(Color.WHITE)
            ctx.lineWidth(2f)
            ctx.arc(px, py, radius, 0f, (2 * PI).toFloat(), false)
            ctx.stroke()
        }
    }

    private fun strokeRect(
        ctx: ContextApi,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        color: Long,
    ) {
        ctx.beginPath()
        ctx.moveTo(left, top)
        ctx.lineTo(left + width, top)
        ctx.lineTo(left + width, top + height)
        ctx.lineTo(left, top + height)
        ctx.closePath()
        ctx.strokeStyle(Color.WHITE)
        ctx.lineWidth(2f)
        ctx.stroke()
        ctx.beginPath()
        ctx.moveTo(left, top)
        ctx.lineTo(left + width, top)
        ctx.lineTo(left + width, top + height)
        ctx.lineTo(left, top + height)
        ctx.closePath()
        ctx.strokeStyle(color.toChartColor())
        ctx.lineWidth(1.5f)
        ctx.stroke()
    }

    private fun formatValue(value: Float): String {
        return formatChartValue(value)
    }

    private fun ContextApi.fillRect(left: Float, top: Float, width: Float, height: Float) {
        beginPath()
        moveTo(left, top)
        lineTo(left + width, top)
        lineTo(left + width, top + height)
        lineTo(left, top + height)
        closePath()
        fill()
    }
}
