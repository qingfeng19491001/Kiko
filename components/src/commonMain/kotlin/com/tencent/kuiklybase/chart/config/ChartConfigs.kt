package com.tencent.kuiklybase.chart.config

class ChartAxisConfig {
    var show: Boolean = true
}

class ChartGridConfig {
    var show: Boolean = true
}

class ChartLegendConfig {
    var show: Boolean = true
    /**
     * 是否允许点击图例切换系列显隐。仅 SeriesCartesianChartAttr 系列生效；
     * 组件内部维护隐藏集合，不会修改调用方传入的 seriesProvider。
     */
    var interactive: Boolean = false
}

class ChartLabelConfig {
    var show: Boolean = false
}

/**
 * 阈值参考线：在 Y = value 处绘制一条水平虚线及可选标签，用于强调阈值/均值/警戒线。
 */
class ChartThresholdConfig(
    var value: Float,
    var label: String = "",
    var color: Long = 0xFFFAAD14,
    var dashWidth: Float = 4f,
    var showLabel: Boolean = true,
)

/**
 * 文本注释：固定坐标 (dataX, dataY) 处渲染一段文案，可选辅助点、连线、引线偏移。
 *
 * - [text]            注释内容
 * - [dataX]/[dataY]   数据坐标系锚点
 * - [dx]/[dy]         像素级文本偏移（正方向：右/下）
 * - [connector]       是否绘制从锚点到文本的连接线
 * - [anchorPoint]     是否绘制锚点小圆点
 */
class ChartAnnotationConfig(
    var text: String,
    var dataX: Float,
    var dataY: Float,
    var dx: Float = 0f,
    var dy: Float = 0f,
    var color: Long = 0xFF2C3542,
    var fontSize: Float = 11f,
    var connector: Boolean = false,
    var anchorPoint: Boolean = true,
    var connectorColor: Long = 0xFF2C3542,
)

/** DSL 可变主题配置。
 * 渲染请使用 [resolved] 得到不可变快照；运行时改字段需重建图表节点。
 */
class ChartThemeOptions {
    /** 强调色（雷达选中轴高亮等）。 */
    var primaryColor: Long = 0xFF1677FF
    var axisColor: Long = 0xFFBFBFBF
    var gridColor: Long = 0xFFF0F0F0
    var textColor: Long = 0xFF262626
    var backgroundColor: Long = 0xFFFFFFFF
    var fontSize: Float = 11f
    var lineWidth: Float = 2f
    /** 上涨 K 线实体色。 */
    var upColor: Long = 0xFFE74C3C
    /** 下跌 K 线实体色。 */
    var downColor: Long = 0xFF27AE60

    fun resolved(): ChartTheme = ChartTheme(
        primaryColor = primaryColor,
        axisColor = axisColor,
        gridColor = gridColor,
        textColor = textColor,
        backgroundColor = backgroundColor,
        fontSize = fontSize,
        lineWidth = lineWidth,
        upColor = upColor,
        downColor = downColor,
    )
}

/** 渲染用不可变主题快照。 */
data class ChartTheme(
    val primaryColor: Long = 0xFF1677FF,
    val axisColor: Long = 0xFFBFBFBF,
    val gridColor: Long = 0xFFF0F0F0,
    val textColor: Long = 0xFF262626,
    val backgroundColor: Long = 0xFFFFFFFF,
    val fontSize: Float = 11f,
    val lineWidth: Float = 2f,
    val upColor: Long = 0xFFE74C3C,
    val downColor: Long = 0xFF27AE60,
)

enum class StockThemePreset {
    LIGHT,
    DARK,
}

fun resolveStockTheme(
    options: ChartThemeOptions,
    preset: StockThemePreset,
): ChartTheme {
    val defaults = ChartTheme()
    val base = when (preset) {
        StockThemePreset.LIGHT -> ChartTheme(
            primaryColor = 0xFF1677FF,
            axisColor = 0xFFB8C0CC,
            gridColor = 0xFFEDF0F4,
            textColor = 0xFF262626,
            backgroundColor = 0xFFFFFFFF,
            upColor = 0xFFE74C3C,
            downColor = 0xFF27AE60,
        )
        StockThemePreset.DARK -> ChartTheme(
            primaryColor = 0xFF4C8DFF,
            axisColor = 0xFF455066,
            gridColor = 0xFF263243,
            textColor = 0xFFD8DFEA,
            backgroundColor = 0xFF101620,
            upColor = 0xFFE15A5A,
            downColor = 0xFF28AD78,
        )
    }
    return base.copy(
        primaryColor = options.primaryColor.customOr(base.primaryColor, defaults.primaryColor),
        axisColor = options.axisColor.customOr(base.axisColor, defaults.axisColor),
        gridColor = options.gridColor.customOr(base.gridColor, defaults.gridColor),
        textColor = options.textColor.customOr(base.textColor, defaults.textColor),
        backgroundColor = options.backgroundColor.customOr(base.backgroundColor, defaults.backgroundColor),
        fontSize = options.fontSize.customOr(base.fontSize, defaults.fontSize),
        lineWidth = options.lineWidth.customOr(base.lineWidth, defaults.lineWidth),
        upColor = options.upColor.customOr(base.upColor, defaults.upColor),
        downColor = options.downColor.customOr(base.downColor, defaults.downColor),
    )
}

private fun Long.customOr(preset: Long, default: Long): Long = if (this != default) this else preset

private fun Float.customOr(preset: Float, default: Float): Float = if (this != default) this else preset

/**
 * 笛卡尔图交互。
 *
 * 点选与 Tooltip 默认开启；平移、缩放、双击复位和框选均由具体图表按需显式开启。
 */
class ChartInteractionConfig {
    var enableTap: Boolean = true
    /** Long press inspects the nearest data item without entering brush selection. */
    var enableLongPressInspect: Boolean = false
    /** 长按拖动框选；默认关闭，松手后是否缩放见 [brushZoom]。 */
    var enableDragSelect: Boolean = false
    /** 双指捏合缩放；适合时间序列、K 线和密集数据，默认关闭。 */
    var enableScale: Boolean = false
    /** 单指拖动平移；适合已缩放或局部窗口图表，默认关闭。 */
    var enablePan: Boolean = false
    /** 双击复位到初始窗口；应与平移或缩放配套开启，默认关闭。 */
    var enableReset: Boolean = false
    /** 选中/框选时绘制十字准星；仅供明确需要的分析场景开启，默认关闭。 */
    var enableCrosshair: Boolean = false
    /** 框选结束是否缩放到 X 选区。 */
    var brushZoom: Boolean = true
    /** 锁定 Y 轴，仅沿 X 平移/缩放（时间序列/K 线习惯）。 */
    var lockY: Boolean = false
    /** 视口限制在数据全量范围内，避免拖出空白。 */
    var clampToData: Boolean = true
    /**
     * 初始可见 X 范围占全量的比例（0.15–1）。
     * 默认 1（全貌）；小于 1 时开局即可单指平移浏览局部。
     */
    var initialVisibleRatio: Float = 1f
    /**
     * 初始窗口锚点。默认 CENTER。
     * END 适合股票最新价靠右。
     */
    var initialVisibleAnchor: VisibleAnchor = VisibleAnchor.CENTER
}

enum class VisibleAnchor {
    START,
    CENTER,
    END,
}

class TapInteractionConfig {
    var enableTap: Boolean = true
}
