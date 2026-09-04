package com.kuikly.stockchat.domain.model

/** K 线周期 */
enum class KLinePeriod(val label: String, val apiKey: String) {
    MINUTE("分时", "minute"),
    DAY("日K", "day"),
    WEEK("周K", "week"),
    MONTH("月K", "month");

    val isIntraday: Boolean get() = this == MINUTE
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
