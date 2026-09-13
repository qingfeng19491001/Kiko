package com.kuikly.stockchat.data.kline

import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.util.DateUtil

/**
 * 真实行情 → KuiklyKLineChart `bars` JSON。换标的 / 周期由调用方丢弃过期回调。
 */
object MarketKLineDataSource {
    fun barsJson(bars: List<KLineBar>): String {
        val sb = StringBuilder("[")
        var first = true
        bars.forEach { bar ->
            val ts = DateUtil.parseToEpochMillis(bar.date) ?: return@forEach
            if (!first) sb.append(',')
            first = false
            sb.append("{\"timestamp\":").append(ts)
                .append(",\"open\":").append(bar.open)
                .append(",\"high\":").append(bar.high)
                .append(",\"low\":").append(bar.low)
                .append(",\"close\":").append(bar.close)
                .append(",\"volume\":").append(bar.volume)
                .append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    /**
     * 分时序列 → KLineChart bars。与官方 Demo 一致：每个 tick 转成 open=high=low=close 的分钟 bar。
     * mock 成交量若单调不减，按差分拆成每根增量。
     */
    fun intradayBarsJson(series: IntradaySeries, yearHint: String = "2026"): String {
        val ticks = series.ticks
        if (ticks.isEmpty()) return ""
        val digits = series.date.filter { it.isDigit() }
        val ymd = when {
            series.date.length >= 10 && series.date[4] == '-' -> series.date.substring(0, 10)
            digits.length == 8 ->
                "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            digits.length == 4 -> "$yearHint-${digits.substring(0, 2)}-${digits.substring(2, 4)}"
            else -> return ""
        }
        val isCumulative = ticks.size > 2 &&
            ticks.last().volume >= ticks.first().volume &&
            ticks.zipWithNext().all { (a, b) -> b.volume >= a.volume }
        val sb = StringBuilder("[")
        var first = true
        var prevVolume = 0.0
        ticks.forEach { tick ->
            val ts = DateUtil.parseToEpochMillis("$ymd ${tick.time}") ?: return@forEach
            val volume = if (isCumulative) (tick.volume - prevVolume).coerceAtLeast(0.0) else tick.volume
            prevVolume = tick.volume
            if (!first) sb.append(',')
            first = false
            sb.append("{\"timestamp\":").append(ts)
                .append(",\"open\":").append(tick.price)
                .append(",\"high\":").append(tick.price)
                .append(",\"low\":").append(tick.price)
                .append(",\"close\":").append(tick.price)
                .append(",\"volume\":").append(volume)
                .append('}')
        }
        sb.append(']')
        return sb.toString()
    }
}
