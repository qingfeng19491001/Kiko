package com.tencent.kuiklybase.chart.config

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSlice
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.RadarDimension

enum class ChartViewportCommand {
    NONE,
    ZOOM_IN,
    ZOOM_OUT,
    PAN_LEFT,
    PAN_RIGHT,
    RESET,
}

data class ChartViewportRequest(
    val sequence: Int = 0,
    val command: ChartViewportCommand = ChartViewportCommand.NONE,
)

open class CartesianChartAttr : ComposeAttr() {
    var title by observable("")
    var viewportRequest by observable(ChartViewportRequest())
    val xAxis = ChartAxisConfig()
    val yAxis = ChartAxisConfig()
    val grid = ChartGridConfig()
    val theme = ChartThemeOptions()
    val interaction = ChartInteractionConfig()

    fun xAxis(block: ChartAxisConfig.() -> Unit) = xAxis.apply(block)
    fun yAxis(block: ChartAxisConfig.() -> Unit) = yAxis.apply(block)
    fun grid(block: ChartGridConfig.() -> Unit) = grid.apply(block)
    fun theme(block: ChartThemeOptions.() -> Unit) = theme.apply(block)
    fun interaction(block: ChartInteractionConfig.() -> Unit) = interaction.apply(block)
}

/** 带系列图例的笛卡尔图 Attr（折线 / 柱 / 面积 / 散点）。K 线不继承本类。 */
open class SeriesCartesianChartAttr : CartesianChartAttr() {
    val legend = ChartLegendConfig()

    fun legend(block: ChartLegendConfig.() -> Unit) = legend.apply(block)
}

/** Tooltip 中的一条系列数据。 */
data class ChartTooltipItem(
    val seriesName: String,
    val point: ChartDataPoint,
    val seriesIndex: Int,
    val pointIndex: Int,
)

/** Tooltip 格式化上下文；多系列模式下 [items] 包含同一 X 坐标的可见系列。 */
data class ChartTooltipContext(
    val label: String,
    val x: Float,
    val items: List<ChartTooltipItem>,
)

/** 折线图 Tooltip DSL 配置。 */
class ChartTooltipConfig {
    /** 多系列折线图是否聚合同一 X 坐标下的全部系列。 */
    var sharedByX: Boolean = true
    private var formatterHandler: ((ChartTooltipContext) -> String)? = null

    fun formatter(handler: (ChartTooltipContext) -> String) {
        formatterHandler = handler
    }

    internal fun format(context: ChartTooltipContext): String? = formatterHandler?.invoke(context)
}

class LineChartAttr : SeriesCartesianChartAttr() {
    /** 平滑曲线（三次贝塞尔近似）。 */
    var smooth: Boolean = false
    /** 是否绘制数据点标记。 */
    var showPoints: Boolean = true
    /** 数据点半径。 */
    var pointRadius: Float = 4f
    /** 是否连接缺失值（NaN / Float.NEGATIVE_INFINITY 视作断点）。默认 true。 */
    var connectNulls: Boolean = true
    /** 是否在折线下方填充区域（线性填充）。 */
    var fillBelow: Boolean = false
    /**
     * 阈值参考线集合。每条为 Y=value 处的水平虚线 + 可选标签。
     * 渲染顺序：网格 → 阈值 → 填充 → 折线 → 数据点 → 注释。
     */
    val thresholds = mutableListOf<ChartThresholdConfig>()
    /**
     * 文本注释集合。每个注释固定在 (dataX, dataY) 位置，可选连接线/锚点。
     */
    val annotations = mutableListOf<ChartAnnotationConfig>()
    val tooltip = ChartTooltipConfig()

    fun thresholds(block: MutableList<ChartThresholdConfig>.() -> Unit) = thresholds.apply(block)
    fun annotations(block: MutableList<ChartAnnotationConfig>.() -> Unit) = annotations.apply(block)
    fun tooltip(block: ChartTooltipConfig.() -> Unit) = tooltip.apply(block)
}

class BarChartAttr : SeriesCartesianChartAttr() {
    val label = ChartLabelConfig()
    /** 堆叠柱状图模式。 */
    var stacked: Boolean = false
    /** 堆叠时是否显示累计标签。 */
    var showTotalLabel: Boolean = true
    /** 水平条形图。 */
    var horizontal: Boolean = false

    fun label(block: ChartLabelConfig.() -> Unit) = label.apply(block)
}

class AreaChartAttr : SeriesCartesianChartAttr() {
    var mode: AreaMode = AreaMode.BASIC
    var gradientFill: Boolean = true
    var smooth: Boolean = false
    var showPoints: Boolean = false
    var pointRadius: Float = 4f
}

enum class AreaMode {
    BASIC, STACKED, PERCENT_STACKED, SPLINE, RANGE, STEP, STREAM, OVERLAPPED, POLAR, RIDGELINE,
}

class ScatterChartAttr : SeriesCartesianChartAttr() {
    var pointRadius: Float = 5f
}

data class StockAverageLine(
    val period: Int,
    val color: Long,
    val label: String = "MA$period",
) {
    init {
        require(period > 0) { "period must be positive" }
    }
}

enum class StockPriceDisplayMode { CANDLE, LINE }

enum class StockMainIndicator { BARE_K, MA, BOLL, EXPMA, BBI, ENE }

enum class StockAuxiliaryIndicator { NONE, VOLUME, AMOUNT, MACD, KDJ, RSI, WR, BBD }

enum class StockPaneSlot { FIRST, SECOND }

internal data class StockPaneRenderConfig(
    val show: Boolean,
    val heightRatio: Float,
    val indicator: StockAuxiliaryIndicator,
)

internal data class StockRenderConfig(
    val revision: Int,
    val priceDisplayMode: StockPriceDisplayMode,
    val mainIndicator: StockMainIndicator,
    val firstPane: StockPaneRenderConfig,
    val secondPane: StockPaneRenderConfig,
    val panesCollapsed: Boolean,
    val candleWidthRatio: Float,
    val preset: StockThemePreset,
)

fun StockPaneSlot.allowedIndicators(): Set<StockAuxiliaryIndicator> = when (this) {
    StockPaneSlot.FIRST -> setOf(
        StockAuxiliaryIndicator.VOLUME,
        StockAuxiliaryIndicator.MACD,
        StockAuxiliaryIndicator.AMOUNT,
    )
    StockPaneSlot.SECOND -> setOf(
        StockAuxiliaryIndicator.MACD,
        StockAuxiliaryIndicator.KDJ,
        StockAuxiliaryIndicator.RSI,
        StockAuxiliaryIndicator.WR,
        StockAuxiliaryIndicator.BBD,
    )
}

class StockAuxiliaryPaneConfig(private val slot: StockPaneSlot) {
    internal var onRenderConfigChanged: (() -> Unit)? = null

    private var showValue = false
    var show: Boolean
        get() = showValue
        set(value) {
            if (value == showValue) return
            showValue = value
            onRenderConfigChanged?.invoke()
        }

    private var heightRatioValue = 0.2f
    var heightRatio: Float
        get() = heightRatioValue
        set(value) {
            val sanitized = if (!value.isFinite() || value <= 0f) 0.2f else value.coerceIn(0.05f, 0.45f)
            if (sanitized == heightRatioValue) return
            heightRatioValue = sanitized
            onRenderConfigChanged?.invoke()
        }

    private var indicatorValue = StockAuxiliaryIndicator.NONE
    var indicator: StockAuxiliaryIndicator
        get() = indicatorValue
        set(value) {
            val sanitized = if (value == StockAuxiliaryIndicator.NONE || value in slot.allowedIndicators()) {
                value
            } else {
                StockAuxiliaryIndicator.NONE
            }
            if (sanitized == indicatorValue) return
            indicatorValue = sanitized
            onRenderConfigChanged?.invoke()
        }

    internal fun renderConfig(): StockPaneRenderConfig = StockPaneRenderConfig(show, heightRatio, indicator)
}

class StockCurrentPriceLineConfig {
    var show: Boolean = true
    var showLabel: Boolean = true
    var color: Long? = null
    var lineWidth: Float = 1f
        set(value) {
            field = if (!value.isFinite() || value <= 0f) 1f else value.coerceAtMost(12f)
        }
    var dashLength: Float = 4f
        set(value) {
            field = if (!value.isFinite() || value <= 0f) 4f else value.coerceAtMost(100f)
        }
    var dashGap: Float = 3f
        set(value) {
            field = if (!value.isFinite() || value < 0f) 3f else value.coerceAtMost(100f)
        }
}

class StockMovingAverageConfig {
    var show: Boolean = false
    val lines = mutableListOf<StockAverageLine>()

    fun line(period: Int, color: Long, label: String = "MA$period") {
        require(period > 0) { "period must be positive" }
        lines.removeAll { it.period == period }
        lines += StockAverageLine(period, color, label)
    }
}

class StockVolumePanelConfig {
    var show: Boolean = false
    var heightRatio: Float = 0.24f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0.16f, 0.4f) else 0.24f
        }
    val averageLines = mutableListOf<StockAverageLine>()

    fun average(period: Int, color: Long, label: String = "VMA$period") {
        require(period > 0) { "period must be positive" }
        averageLines.removeAll { it.period == period }
        averageLines += StockAverageLine(period, color, label)
    }
}

class StockChartAttr : CartesianChartAttr() {
    private var candleWidthRatioValue = 0.6f
    var candleWidthRatio: Float
        get() = candleWidthRatioValue
        set(value) {
            val sanitized = if (value.isFinite()) value.coerceIn(0.1f, 1f) else 0.6f
            if (sanitized == candleWidthRatioValue) return
            candleWidthRatioValue = sanitized
            refreshRenderConfig()
        }

    private var presetValue = StockThemePreset.LIGHT
    var preset: StockThemePreset
        get() = presetValue
        set(value) {
            if (value == presetValue) return
            presetValue = value
            refreshRenderConfig()
        }

    private var priceDisplayModeValue = StockPriceDisplayMode.CANDLE
    var priceDisplayMode: StockPriceDisplayMode
        get() = priceDisplayModeValue
        set(value) {
            if (value == priceDisplayModeValue) return
            priceDisplayModeValue = value
            refreshRenderConfig()
        }

    private var mainIndicatorValue = StockMainIndicator.BARE_K
    var mainIndicator: StockMainIndicator
        get() = mainIndicatorValue
        set(value) {
            if (value == mainIndicatorValue) return
            mainIndicatorValue = value
            refreshRenderConfig()
        }

    val firstPane = StockAuxiliaryPaneConfig(StockPaneSlot.FIRST)
    val secondPane = StockAuxiliaryPaneConfig(StockPaneSlot.SECOND)

    private var panesCollapsedValue = false
    var panesCollapsed: Boolean
        get() = panesCollapsedValue
        set(value) {
            if (value == panesCollapsedValue) return
            panesCollapsedValue = value
            refreshRenderConfig()
        }

    internal var renderConfig by observable(buildRenderConfig(0))
        private set
    val currentPriceLine = StockCurrentPriceLineConfig()
    val movingAverages = StockMovingAverageConfig()
    val volumePanel = StockVolumePanelConfig()

    fun firstPane(block: StockAuxiliaryPaneConfig.() -> Unit) = firstPane.apply(block)

    fun secondPane(block: StockAuxiliaryPaneConfig.() -> Unit) = secondPane.apply(block)

    fun currentPriceLine(block: StockCurrentPriceLineConfig.() -> Unit) = currentPriceLine.apply(block)

    fun movingAverages(block: StockMovingAverageConfig.() -> Unit) = movingAverages.apply(block)

    fun volumePanel(block: StockVolumePanelConfig.() -> Unit) = volumePanel.apply(block)

    init {
        firstPane.onRenderConfigChanged = ::refreshRenderConfig
        secondPane.onRenderConfigChanged = ::refreshRenderConfig
        interaction.enableLongPressInspect = true
        interaction.enablePan = true
        interaction.enableScale = true
        interaction.enableReset = true
        interaction.enableCrosshair = true
        interaction.lockY = true
        interaction.clampToData = true
        interaction.initialVisibleRatio = 0.55f
        interaction.initialVisibleAnchor = VisibleAnchor.END
    }

    private fun buildRenderConfig(revision: Int): StockRenderConfig = StockRenderConfig(
        revision = revision,
        priceDisplayMode = priceDisplayMode,
        mainIndicator = mainIndicator,
        firstPane = firstPane.renderConfig(),
        secondPane = secondPane.renderConfig(),
        panesCollapsed = panesCollapsed,
        candleWidthRatio = candleWidthRatio,
        preset = preset,
    )

    private fun refreshRenderConfig() {
        val next = buildRenderConfig(renderConfig.revision + 1)
        if (next.copy(revision = renderConfig.revision) != renderConfig) renderConfig = next
    }
}

class CartesianChartEvent : ComposeEvent() {
    var onPointClick: ((ChartDataPoint, Int, Int) -> Unit)? = null
    var onViewportChange: ((ChartViewport) -> Unit)? = null
    var onSelectionChange: ((ChartSelection?) -> Unit)? = null
    var onDragSelect: ((ClosedFloatingPointRange<Float>) -> Unit)? = null

    fun pointClick(handler: (ChartDataPoint, Int, Int) -> Unit) {
        onPointClick = handler
    }

    fun viewportChange(handler: (ChartViewport) -> Unit) {
        onViewportChange = handler
    }

    fun selectionChange(handler: (ChartSelection?) -> Unit) {
        onSelectionChange = handler
    }

    fun dragSelect(handler: (ClosedFloatingPointRange<Float>) -> Unit) {
        onDragSelect = handler
    }
}

open class PolarChartAttr : ComposeAttr() {
    var title by observable("")
    val legend = ChartLegendConfig()
    val theme = ChartThemeOptions()
    val interaction = TapInteractionConfig()

    fun legend(block: ChartLegendConfig.() -> Unit) = legend.apply(block)
    fun theme(block: ChartThemeOptions.() -> Unit) = theme.apply(block)
    fun interaction(block: TapInteractionConfig.() -> Unit) = interaction.apply(block)
}

class PieChartAttr : PolarChartAttr() {
    var showPercentLabel: Boolean = true
    var innerRadiusRatio: Float = 0f
    var startAngle: Float = -90f
    /** 环形模式：环宽（>0 时按环绘制，优先于 [innerRadiusRatio]）。 */
    var ringWidth: Float = 0f
    /** 环心文案（仅环形有意义）。 */
    var centerText: String = ""
}

class PieChartEvent : ComposeEvent() {
    var onSliceClick: ((ChartSlice, Int) -> Unit)? = null
    var onSelectionChange: ((ChartSelection?) -> Unit)? = null

    fun sliceClick(handler: (ChartSlice, Int) -> Unit) {
        onSliceClick = handler
    }

    fun selectionChange(handler: (ChartSelection?) -> Unit) {
        onSelectionChange = handler
    }
}

class RadarChartAttr : PolarChartAttr() {
    var dimensions by observableList<RadarDimension>()

    fun dimensions(block: RadarDimensionsBuilder.() -> Unit) {
        dimensions.clear()
        dimensions.addAll(RadarDimensionsBuilder().apply(block).items)
    }
}

class RadarDimensionsBuilder {
    internal val items = mutableListOf<RadarDimension>()

    fun dimension(label: String, maxValue: Float) {
        items.add(RadarDimension(label, maxValue))
    }
}

class RadarChartEvent : ComposeEvent() {
    var onRadarClick: ((Int, Int, String?) -> Unit)? = null
    var onSelectionChange: ((ChartSelection?) -> Unit)? = null

    fun radarClick(handler: (Int, Int, String?) -> Unit) {
        onRadarClick = handler
    }

    fun selectionChange(handler: (ChartSelection?) -> Unit) {
        onSelectionChange = handler
    }
}
