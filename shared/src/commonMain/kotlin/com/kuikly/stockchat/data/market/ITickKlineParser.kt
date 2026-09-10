package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.MinuteTick
import com.kuikly.stockchat.domain.util.DateUtil
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * iTick 分钟 K：`GET /stock/kline` 或 `/indices/kline`，kType=1。
 * 把单日 1 分钟柱转成详情页 [IntradaySeries]（成交量改成累计，匹配现有分时图）。
 */
object ITickKlineParser {

    const val BASE_URL = "https://api.itick.org"
    const val KTYPE_MINUTE = "1"
    const val SESSION_LIMIT = "400"

    fun path(instrument: Instrument): String =
        if (instrument.isIndex) "/indices/kline" else "/stock/kline"

    fun region(instrument: Instrument): String = instrument.market.suffix

    /** 港股数字代码去前导零：00700 → 700，与 iTick 文档一致。 */
    fun code(instrument: Instrument): String {
        val raw = instrument.code
        return if (instrument.market == Market.HK && raw.all { it.isDigit() }) {
            raw.trimStart('0').ifEmpty { "0" }
        } else {
            raw
        }
    }

    fun query(instrument: Instrument): Map<String, String> = mapOf(
        "region" to region(instrument),
        "code" to code(instrument),
        "kType" to KTYPE_MINUTE,
        "limit" to SESSION_LIMIT,
    )

    fun parseIntraday(raw: String, instrument: Instrument, prevCloseHint: Double): IntradaySeries? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val status = json.optString("code").toIntOrNull()
        if (status != null && status != 0) return null
        val array = json.optJSONArray("data") ?: return null
        val bars = ArrayList<RawBar>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val t = jsonNumber(row, "t")?.toLong() ?: continue
            val close = jsonNumber(row, "c") ?: continue
            val volume = jsonNumber(row, "v") ?: 0.0
            if (close <= 0) continue
            val epoch = if (t < 10_000_000_000L) t * 1000L else t
            bars += RawBar(epoch, close, volume)
        }
        return toIntraday(bars, instrument.market, prevCloseHint)
    }

    internal data class RawBar(val epochMs: Long, val close: Double, val volume: Double)

    internal fun toIntraday(bars: List<RawBar>, market: Market, prevCloseHint: Double): IntradaySeries? {
        if (bars.size < 2) return null
        val offset = timezoneOffsetHours(market)
        val sorted = bars.sortedBy { it.epochMs }
        val clocks = sorted.mapNotNull { bar ->
            val clock = DateUtil.toLocalClock(bar.epochMs, offset) ?: return@mapNotNull null
            Triple(bar, clock, clock.date)
        }
        if (clocks.isEmpty()) return null
        val lastDate = clocks.last().third
        val session = clocks.filter { it.third == lastDate }
        if (session.size < 2) return null
        var cumulative = 0.0
        val ticks = session.map { (bar, clock, _) ->
            cumulative += bar.volume.coerceAtLeast(0.0)
            MinuteTick(clock.hhmm, bar.close, cumulative)
        }
        val prevClose = prevCloseHint.takeIf { it > 0 } ?: ticks.first().price
        return IntradaySeries(lastDate, prevClose, ticks)
    }

    fun timezoneOffsetHours(market: Market): Int = when (market) {
        Market.US -> -4
        Market.HK, Market.SH, Market.SZ -> 8
    }

    private fun jsonNumber(obj: JSONObject, key: String): Double? =
        obj.optString(key).trim().toDoubleOrNull()
}
