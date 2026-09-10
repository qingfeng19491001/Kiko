package com.kuikly.stockchat.domain.model

/**
 * K 线周期，与 KuiklyKLineChart 完整 Demo 的周期集合对齐。
 *
 * @param apiKey 腾讯行情接口的周期参数
 * @param span 传给 KLineChart period(value, unit) 的 value
 * @param unit 传给 KLineChart period(value, unit) 的 unit
 * @param line true 时图表用折线渲染（分时 / 五日），否则蜡烛
 */
enum class KLinePeriod(
    val label: String,
    val apiKey: String,
    val span: Int = 1,
    val unit: String = apiKey,
    val line: Boolean = false,
) {
    MINUTE("分时", "minute", 1, "minute", true),
    FIVE_DAY("五日", "m5", 5, "day", true),
    DAY("日K", "day"),
    WEEK("周K", "week"),
    MONTH("月K", "month"),
    MIN_1("1分", "m1", 1, "minute"),
    MIN_5("5分", "m5", 5, "minute"),
    MIN_15("15分", "m15", 15, "minute"),
    MIN_30("30分", "m30", 30, "minute"),
    MIN_60("60分", "m60", 60, "minute"),
    MIN_120("120分", "m120", 120, "minute"),
    QUARTER("季K", "quarter", 3, "month"),
    YEAR("年K", "year", 12, "month");

    /** 分时（当日逐笔）走独立的分时接口 */
    val isIntraday: Boolean get() = this == MINUTE

    /** 主周期栏之外的扩展周期（收进“更多”菜单） */
    val isExtended: Boolean get() = ordinal > MONTH.ordinal

    companion object {
        /** 主周期栏：分时 / 五日 / 日K / 周K / 月K */
        val mainTabs: List<KLinePeriod> = listOf(MINUTE, FIVE_DAY, DAY, WEEK, MONTH)

        /** “更多”菜单：1分 ~ 年K */
        val extendedTabs: List<KLinePeriod> = entries.filter { it.isExtended }
    }
}

/** 单根 K 线 */
data class KLineBar(
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
) {
    val isUp: Boolean get() = close >= open
}

/** 分时单点 */
data class MinuteTick(
    /** HH:mm */
    val time: String,
    val price: Double,
    /** 累计成交量 */
    val volume: Double,
)

/** 分时序列 */
data class IntradaySeries(
    val date: String,
    val prevClose: Double,
    val ticks: List<MinuteTick>,
) {
    val isEmpty: Boolean get() = ticks.isEmpty()
}

/**
 * 某标的的一次完整数据快照：行情 + 日 K + 分时。
 */
data class MarketSnapshot(
    val quote: Quote,
    val dailyBars: List<KLineBar>,
    val intraday: IntradaySeries?,
) {
    /** 近 30 个交易日收盘价，用于迷你走势图 */
    val sparkline: List<Double>
        get() = dailyBars.takeLast(30).map { it.close }
}
