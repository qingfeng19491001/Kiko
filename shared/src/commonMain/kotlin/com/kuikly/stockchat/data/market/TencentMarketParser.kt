package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.MinuteTick
import com.kuikly.stockchat.domain.model.Quote
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 腾讯证券公开行情接口解析器。
 *
 * - 实时行情：`https://qt.gtimg.cn/q=hk00700` → `v_hk00700="100~腾讯控股~00700~price~...";`
 * - K 线：`https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=hk00700,day,,,320,qfq`
 * - 分时：`https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=hk00700`
 *
 * 所有解析都以“容错优先”为原则：任何字段缺失返回 null，由上层决定是否降级到演示数据。
 */
object TencentMarketParser {

    const val QUOTE_BASE = "https://qt.gtimg.cn/q="
    const val KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
    const val MINUTE_URL = "https://web.ifzq.gtimg.cn/appstock/app/minute/query"

    fun quoteUrl(instruments: List<Instrument>): String =
        QUOTE_BASE + instruments.joinToString(",") { it.tencentSymbol }

    fun klineParam(instrument: Instrument, period: KLinePeriod, count: Int): String {
        val fq = if (instrument.isIndex) "" else "qfq"
        return "${instrument.tencentSymbol},${period.apiKey},,,$count,$fq"
    }

    // region 实时行情

    /**
     * 解析 `v_xxx="..."` 文本，返回 symbol → 字段数组。
     */
    fun splitQuoteResponse(raw: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        raw.split(";").forEach { segment ->
            val line = segment.trim()
            if (!line.startsWith("v_")) return@forEach
            val eq = line.indexOf('=')
            if (eq < 0) return@forEach
            val symbol = line.substring(2, eq).trim()
            val body = line.substring(eq + 1).trim().trim('"')
            if (body.isEmpty()) return@forEach
            result[symbol] = body.split("~")
        }
        return result
    }

    fun parseQuote(instrument: Instrument, fields: List<String>): Quote? {
        if (fields.size < 40) return null
        fun d(index: Int): Double? = fields.getOrNull(index)?.trim()?.toDoubleOrNull()
        val price = d(3) ?: return null
        val prevClose = d(4) ?: return null
        if (price <= 0 || prevClose <= 0) return null
        val open = d(5) ?: prevClose
        val change = d(31) ?: (price - prevClose)
        val changePct = d(32) ?: (change / prevClose * 100)
        val high = d(33) ?: price
        val low = d(34) ?: price
        val isAShare = instrument.market == Market.SH || instrument.market == Market.SZ
        val volumeRaw = d(36) ?: d(6) ?: 0.0
        // A 股成交量单位为“手”，港美股为“股”
        val volume = if (isAShare) volumeRaw * 100 else volumeRaw
        // A 股成交额单位为“万”，港美股为原币种金额
        val turnover = (d(37) ?: 0.0) * (if (isAShare) 1_0000.0 else 1.0)
        val amplitude = d(43)?.takeIf { it >= 0 }
        val pe = d(39)?.takeIf { it > 0 }
        val marketCap = d(44)?.takeIf { it > 0 }
        val totalCap = d(45)?.takeIf { it > 0 }
        // 市净率：A 股在 46；港股在 58；美股接口无稳定字段
        val pb = when (instrument.market) {
            Market.SH, Market.SZ -> d(46)
            Market.HK -> d(58)
            else -> null
        }?.takeIf { it > 0 && it < 500 }
        val turnoverRate = d(38)?.takeIf { it > 0 && it < 100 }
        // 52 周高低：A 股在 67/68（47/48 为涨跌停价）；港美股在 48/49
        val high52 = (if (isAShare) d(67) else d(48))?.takeIf { it > 0 }
        val low52 = (if (isAShare) d(68) else d(49))?.takeIf { it > 0 }
        val time = formatTime(fields.getOrNull(30) ?: "")
        return Quote(
            instrument = instrument,
            price = price,
            prevClose = prevClose,
            open = open,
            high = high,
            low = low,
            change = change,
            changePct = changePct,
            volume = volume,
            turnover = turnover,
            pe = if (instrument.isIndex) null else pe,
            pb = if (instrument.isIndex) null else pb,
            marketCap = if (instrument.isIndex) null else marketCap,
            totalMarketCap = if (instrument.isIndex) null else totalCap,
            amplitude = amplitude,
            turnoverRate = if (instrument.isIndex) null else turnoverRate,
            high52w = high52,
            low52w = low52,
            updateTime = time,
            tradingStatus = tradingStatus(instrument.market, time),
            isMock = false,
        )
    }

    /** 20240621160822 / 2024/06/21 16:08:22 → 06-21 16:08 */
    private fun formatTime(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 12) return raw
        val month = digits.substring(4, 6)
        val day = digits.substring(6, 8)
        val hour = digits.substring(8, 10)
        val minute = digits.substring(10, 12)
        return "$month-$day $hour:$minute"
    }

    fun tradingStatus(market: Market, time: String): String {
        val hm = time.substringAfter(' ', "").ifEmpty { return "${market.label}行情" }
        val hour = hm.substringBefore(':').toIntOrNull() ?: return "${market.label}行情"
        val minute = hm.substringAfter(':').toIntOrNull() ?: 0
        val hhmm = hour * 100 + minute
        return when (market) {
            Market.HK -> if (hhmm in 930..1200 || hhmm in 1300..1600) "港股交易中" else "港股已收盘"
            Market.SH, Market.SZ -> if (hhmm in 930..1130 || hhmm in 1300..1500) "A股交易中" else "A股已收盘"
            Market.US -> if (hhmm in 930..1600) "美股交易中" else "美股已收盘"
        }
    }

    // endregion

    // region K 线

    fun parseKLine(instrument: Instrument, period: KLinePeriod, json: JSONObject): List<KLineBar>? {
        val data = json.optJSONObject("data") ?: return null
        val node = data.optJSONObject(instrument.tencentSymbol) ?: return null
        val candidates = listOf("qfq${period.apiKey}", period.apiKey, "hfq${period.apiKey}")
        val array = candidates.firstNotNullOfOrNull { node.optJSONArray(it) } ?: return null
        val bars = ArrayList<KLineBar>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONArray(i) ?: continue
            val bar = parseBar(row) ?: continue
            bars += bar
        }
        return bars.takeIf { it.isNotEmpty() }
    }

    private fun parseBar(row: JSONArray): KLineBar? {
        if (row.length() < 6) return null
        val date = row.optString(0) ?: return null
        val open = row.optString(1)?.toDoubleOrNull() ?: return null
        val close = row.optString(2)?.toDoubleOrNull() ?: return null
        val high = row.optString(3)?.toDoubleOrNull() ?: return null
        val low = row.optString(4)?.toDoubleOrNull() ?: return null
        val volume = row.optString(5)?.toDoubleOrNull() ?: 0.0
        if (open <= 0 || close <= 0) return null
        return KLineBar(date, open, close, high, low, volume)
    }

    // endregion

    // region 分时

    fun parseMinute(instrument: Instrument, prevCloseHint: Double, json: JSONObject): IntradaySeries? {
        val data = json.optJSONObject("data") ?: return null
        val node = data.optJSONObject(instrument.tencentSymbol) ?: return null
        val minuteNode = node.optJSONObject("data") ?: return null
        val date = minuteNode.optString("date", "")
        val rows = minuteNode.optJSONArray("data") ?: return null
        var prevClose = prevCloseHint
        node.optJSONArray("qt")?.let { qt ->
            // qt 节点里是实时行情数组，index 4 为昨收
            qt.optJSONArray(0)?.optString(4)?.toDoubleOrNull()?.let { if (it > 0) prevClose = it }
        }
        val ticks = ArrayList<MinuteTick>(rows.length())
        for (i in 0 until rows.length()) {
            val parts = (rows.optString(i) ?: continue).trim().split(" ")
            if (parts.size < 2) continue
            val time = parts[0]
            val price = parts[1].toDoubleOrNull() ?: continue
            val volume = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            if (price <= 0) continue
            val hhmm = if (time.length == 4) time.substring(0, 2) + ":" + time.substring(2) else time
            ticks += MinuteTick(hhmm, price, volume)
        }
        if (ticks.isEmpty()) return null
        return IntradaySeries(date, prevClose, ticks)
    }

    // endregion
}
