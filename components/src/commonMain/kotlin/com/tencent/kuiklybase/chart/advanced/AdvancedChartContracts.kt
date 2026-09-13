package com.tencent.kuiklybase.chart.advanced

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuiklybase.chart.config.ChartThemeOptions
import com.tencent.kuiklybase.chart.config.StockThemePreset
import kotlin.math.ceil
import kotlin.math.floor

enum class AdvancedChartKind {
    DUAL_AXIS_BAR,
    WATERFALL,
    HISTOGRAM,
    BULLET,
    HALF_DONUT,
    ROSE,
    SUNBURST,
    NESTED_PIE,
    OHLC,
    STOCK_AREA,
    STOCK_LINE,
    RENKO,
    KAGI,
    POINT_FIGURE,
}

data class DualAxisPoint(
    val label: String,
    val barValue: Float,
    val lineValue: Float,
    val barColor: Long? = null,
)

data class WaterfallPoint(
    val label: String,
    val value: Float,
    val isTotal: Boolean = false,
    val color: Long? = null,
)

data class HistogramBin(
    val label: String,
    val value: Float,
    val color: Long? = null,
)

data class BulletChartItem(
    val label: String,
    val actual: Float,
    val target: Float,
    val ranges: List<Float> = listOf(0.55f, 0.8f, 1f),
    val color: Long? = null,
)

data class SunburstNode(
    val label: String,
    val value: Float,
    val color: Long,
    val depth: Int,
)

enum class NestedPieRing { INNER, OUTER }

data class NestedPieSlice(
    val label: String,
    val value: Float,
    val color: Long,
    val ring: NestedPieRing,
)

data class PointFigureColumn(
    val label: String,
    val count: Int,
    val rising: Boolean,
)

data class AdvancedChartSelection(
    val kind: AdvancedChartKind,
    val index: Int,
    val label: String,
    val value: Float? = null,
    val summary: String? = null,
) {
    /** 保留新增 summary 字段前的 4 参数构造签名。 */
    constructor(
        kind: AdvancedChartKind,
        index: Int,
        label: String,
        value: Float?,
    ) : this(kind, index, label, value, null)

    @Deprecated("Binary compatibility bridge", level = DeprecationLevel.HIDDEN)
    fun copy(
        kind: AdvancedChartKind = this.kind,
        index: Int = this.index,
        label: String = this.label,
        value: Float? = this.value,
    ): AdvancedChartSelection = AdvancedChartSelection(kind, index, label, value, summary)
}

class AdvancedChartInteractionConfig {
    var enableTap: Boolean = true
    var enableLongPressInspect: Boolean = false
    var enableCrosshair: Boolean = false
    var enablePan: Boolean = false
    var enableScale: Boolean = false
    var enableReset: Boolean = false
    var clampToData: Boolean = true
    var minimumVisibleRatio: Float = 0.16f
    var initialVisibleRatio: Float = 1f
}

class AdvancedChartAttr : ComposeAttr() {
    var preset: StockThemePreset = StockThemePreset.LIGHT
    val theme = ChartThemeOptions()
    val interaction = AdvancedChartInteractionConfig()

    fun theme(block: ChartThemeOptions.() -> Unit) = theme.apply(block)
    fun interaction(block: AdvancedChartInteractionConfig.() -> Unit) = interaction.apply(block)
}

class AdvancedChartEvent : ComposeEvent() {
    var onItemClick: ((AdvancedChartSelection) -> Unit)? = null
    var onViewportChange: ((Float, Float) -> Unit)? = null
    var onSelectionChange: ((AdvancedChartSelection?) -> Unit)? = null

    fun itemClick(handler: (AdvancedChartSelection) -> Unit) {
        onItemClick = handler
    }

    fun viewportChange(handler: (Float, Float) -> Unit) {
        onViewportChange = handler
    }

    fun selectionChange(handler: (AdvancedChartSelection?) -> Unit) {
        onSelectionChange = handler
    }
}

internal fun supportsStockViewport(kind: AdvancedChartKind): Boolean = when (kind) {
    AdvancedChartKind.OHLC,
    AdvancedChartKind.STOCK_AREA,
    AdvancedChartKind.STOCK_LINE,
    AdvancedChartKind.RENKO,
    AdvancedChartKind.KAGI,
    AdvancedChartKind.POINT_FIGURE,
    -> true
    else -> false
}

internal fun normalizeStockViewport(
    start: Float,
    end: Float,
    minimumSpan: Float,
): ClosedFloatingPointRange<Float> {
    val safeMinimum = minimumSpan.coerceIn(0.01f, 1f)
    val safeStart = start.takeIf(Float::isFinite) ?: 0f
    val safeEnd = end.takeIf(Float::isFinite) ?: 1f
    val span = (safeEnd - safeStart).coerceIn(safeMinimum, 1f)
    val nextStart = safeStart.coerceIn(0f, 1f - span)
    return nextStart..(nextStart + span)
}

internal fun latestAnchoredStockViewport(
    initialVisibleRatio: Float,
    minimumVisibleRatio: Float,
): ClosedFloatingPointRange<Float> {
    val minimum = minimumVisibleRatio.coerceIn(0.01f, 1f)
    val ratio = initialVisibleRatio.coerceIn(minimum, 1f)
    return normalizeStockViewport(1f - ratio, 1f, minimum)
}

internal fun visibleStockIndexRange(
    count: Int,
    start: Float,
    end: Float,
): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val first = floor(start.coerceIn(0f, 1f) * count).toInt().coerceIn(0, count - 1)
    val last = (ceil(end.coerceIn(0f, 1f) * count).toInt() - 1).coerceIn(first, count - 1)
    return first..last
}

internal fun sourceIndexForVisibleSlot(
    slot: Int,
    count: Int,
    start: Float,
    end: Float,
): Int {
    val range = visibleStockIndexRange(count, start, end)
    if (range.isEmpty()) return -1
    return (range.first + slot).coerceIn(range.first, range.last)
}

internal fun toggleAdvancedSelection(current: Int, hit: Int): Int =
    if (hit < 0 || hit == current) -1 else hit

internal fun formatStockSelection(selection: AdvancedChartSelection): String {
    selection.summary?.let { return "${selection.label}  $it" }
    val value = selection.value ?: return selection.label
    val field = when (selection.kind) {
        AdvancedChartKind.STOCK_AREA,
        AdvancedChartKind.STOCK_LINE,
        AdvancedChartKind.RENKO,
        AdvancedChartKind.KAGI,
        -> "价格"
        AdvancedChartKind.POINT_FIGURE -> "列高"
        AdvancedChartKind.OHLC -> "收盘"
        else -> "数值"
    }
    return "${selection.label}  $field:$value"
}

internal fun applyStockInteractionDefaults(
    attr: AdvancedChartAttr,
    kind: AdvancedChartKind,
) {
    if (!supportsStockViewport(kind)) return
    attr.interaction.apply {
        enableLongPressInspect = true
        enableCrosshair = true
        enablePan = true
        enableScale = true
        enableReset = true
        clampToData = true
        minimumVisibleRatio = 0.16f
        initialVisibleRatio = 0.65f
    }
}
