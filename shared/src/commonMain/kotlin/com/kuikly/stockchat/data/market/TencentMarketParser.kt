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
    const val MKLINE_URL = "https://ifzq.gtimg.cn/appstock/app/kline/mkline"
    const val MINUTE_URL = "https://web.ifzq.gtimg.cn/appstock/app/minute/query"

    fun quoteUrl(instruments: List<Instrument>): String =
        QUOTE_BASE + instruments.flatMap { quoteRequestKeys(it) }.distinct().joinToString(",")

    /**
     * 实际请求键不能带交易所后缀：`usAAPL.OQ` 会回 `v_pv_none_match`，
     * 必须打 `usAAPL`。回包匹配仍用 [quoteLookupKeys]。
     */
    fun quoteRequestKeys(instrument: Instrument): List<String> {
        val keys = quoteLookupKeys(instrument).filter { '.' !in it }
        return keys.ifEmpty { quoteLookupKeys(instrument) }
    }

    fun klineUrl(period: KLinePeriod): String =
        if (period.isMinuteBar) MKLINE_URL else KLINE_URL

    fun klineParam(instrument: Instrument, period: KLinePeriod, count: Int): String {
        val fq = if (instrument.isIndex || period.isMinuteBar) "" else "qfq"
        return "${klineSymbol(instrument)},${period.apiKey},,,$count,$fq"
    }

    /**
     * 美股指数日 K 必须打 `us.DJI`：`usDJI` 的 fqkline 往往只回当天一根，面积图会空。
     */
    fun klineSymbol(instrument: Instrument): String =
        if (instrument.market == Market.US && instrument.isIndex) "us.${instrument.code}"
        else instrument.tencentSymbol.substringBefore('.')

    // region 实时行情

    /**
     * 解析 `v_xxx="..."` 文本，返回 symbol → 字段数组。
     */
    fun splitQuoteResponse(raw: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        val source = extractQuoteLines(raw)
        source.split(";").forEach { segment ->
            val line = segment.trim().trimStart('{', '"').trim()
            val vIndex = line.indexOf("v_")
            if (vIndex < 0) return@forEach
            val normalized = line.substring(vIndex)
            val eq = normalized.indexOf('=')
            if (eq < 0) return@forEach
            val symbol = normalized.substring(2, eq).trim().trim('"')
            val body = normalized.substring(eq + 1).trim()
                .removePrefix("\\\"")
                .trim('"')
                .removeSuffix("\\\"")
                .trim('"')
            if (body.isEmpty() || symbol.isEmpty()) return@forEach
            result[symbol] = body.split("~")
        }
        return result
    }

    /** 鸿蒙 NetworkModule 可能把整段行情塞进 JSON，`v_` 不在行首。 */
    private fun extractQuoteLines(raw: String): String {
        val start = raw.indexOf("v_")
        return if (start > 0) raw.substring(start) else raw
    }

    /**
     * 腾讯美股回包键名是 `usTSLA`，目录里的代码却是 `usTSLA.OQ`，必须同时兼容。
     */
    fun quoteLookupKeys(instrument: Instrument): List<String> {
        val primary = instrument.tencentSymbol
        val byCode = instrument.market.tencentPrefix + instrument.code
        val withoutDot = primary.substringBefore('.')
        val dottedUs = if (instrument.market == Market.US) "us.${instrument.code}" else null
        return listOfNotNull(primary, byCode, withoutDot, dottedUs).distinct()
    }

    fun quoteFields(raw: String, instrument: Instrument): List<String>? {
        val map = splitQuoteResponse(raw)
        quoteLookupKeys(instrument).forEach { key ->
            map[key]?.let { return alignQuoteFields(it, instrument) }
        }
        val needle = instrument.code.lowercase()
        val matches = map.entries.filter { entry ->
            val key = entry.key.lowercase()
            key.contains(needle) && !key.startsWith("s_")
        }
        val picked = matches.firstOrNull()
            ?: map.entries.firstOrNull { it.key.lowercase().contains(needle) }
        return picked?.value?.let { alignQuoteFields(it, instrument) }
    }

    /**
     * 腾讯 GBK 名在 UTF-8 误解码时会多出 `~`，代码不再落在 [2]。
     * 另有实现会丢掉现价，后续字段整体左移一位（昨收出现在 [3]）。
     */
    fun alignQuoteFields(fields: List<String>, instrument: Instrument): List<String> {
        val codeIdx = fields.indexOfFirst { isQuoteCodeField(it, instrument) }
        val aligned = if (codeIdx > 2) {
            val name = fields.subList(1, codeIdx).filter { it.isNotBlank() }.joinToString("")
            listOf(fields[0], name, fields[codeIdx]) + fields.drop(codeIdx + 1)
        } else {
            fields
        }
        return recoverDroppedLastPrice(aligned)
    }

    private fun isQuoteCodeField(raw: String, instrument: Instrument): Boolean {
        val field = raw.trim().lowercase().substringBefore('.')
        if (field.isEmpty()) return false
        val code = instrument.code.lowercase()
        val bare = code.trimStart('0').ifEmpty { code }
        return field == code ||
            field == bare ||
            field == instrument.tencentSymbol.lowercase() ||
            field == instrument.tencentSymbol.lowercase().substringBefore('.')
    }

    private fun recoverDroppedLastPrice(fields: List<String>): List<String> {
        if (fields.size < 6) return fields
        val listed = fields[3].trim().toDoubleOrNull() ?: return fields
        val next = fields[4].trim().toDoubleOrNull() ?: return fields
        val maybeVolume = fields[5].trim().toDoubleOrNull() ?: return fields
        val looksLikeVolumeAsOpen = listed > 0 && maybeVolume > listed * 50
        val nextLooksLikePrice = next > 0 && next < listed * 5 && next > listed / 5
        if (!looksLikeVolumeAsOpen || !nextLooksLikePrice) return fields
        val window = (27..34).mapNotNull { fields.getOrNull(it)?.trim()?.toDoubleOrNull() }
        var fromPair: Double? = null
        for (i in 0 until window.lastIndex) {
            val change = window[i]
            val pct = window[i + 1]
            if (kotlin.math.abs(pct) < 40 &&
                kotlin.math.abs(listed * pct / 100.0 - change) <= 0.2
            ) {
                fromPair = listed + change
                break
            }
        }
        val expected = fromPair
        val hinted = fields.drop(6).mapNotNull { it.trim().toDoubleOrNull() }
            .firstOrNull { candidate ->
                expected != null && kotlin.math.abs(candidate - expected) <= 0.2
            }
        val recovered = hinted ?: fromPair ?: listed
        return fields.take(3) + recovered.toString() + fields.drop(3)
    }

    fun parseQuote(instrument: Instrument, fields: List<String>): Quote? {
        val aligned = alignQuoteFields(fields, instrument)
        if (aligned.size < 6) return null
        fun d(index: Int): Double? = aligned.getOrNull(index)?.trim()?.toDoubleOrNull()
        val price = d(3) ?: return null
        val prevClose = d(4) ?: return null
        if (price <= 0 || prevClose <= 0) return null
        val rawOpen = d(5) ?: prevClose
        val open = if (rawOpen > 0 && rawOpen < price * 5 && rawOpen > price / 5) rawOpen else prevClose
        val computedChange = price - prevClose
        val computedPct = if (prevClose > 0) computedChange / prevClose * 100 else 0.0
        val change = d(31)?.takeIf { kotlin.math.abs(it - computedChange) <= maxTolerance(computedChange) }
            ?: computedChange
        val changePct = d(32)?.takeIf { kotlin.math.abs(it - computedPct) <= 0.35 } ?: computedPct
        val bandHigh = maxOf(price, open, prevClose)
        val bandLow = minOf(price, open, prevClose)
        val high = listOfNotNull(d(33), d(35), d(9)).firstOrNull { it >= bandHigh * 0.98 && it <= bandHigh * 1.15 }
            ?: bandHigh
        val low = listOfNotNull(d(34), d(5)).firstOrNull { it > 0 && it <= bandLow * 1.02 && it >= bandLow * 0.8 }
            ?: bandLow
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
        val time = formatTime(aligned.getOrNull(30) ?: "")
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

    private fun maxTolerance(change: Double): Double = maxOf(0.05, kotlin.math.abs(change) * 0.2)

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
        val node = quoteLookupKeys(instrument).firstNotNullOfOrNull { data.optJSONObject(it) } ?: return null
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
        val date = normalizeBarDate(row.optString(0) ?: return null) ?: return null
        val open = row.optString(1)?.toDoubleOrNull() ?: return null
        val close = row.optString(2)?.toDoubleOrNull() ?: return null
        val high = row.optString(3)?.toDoubleOrNull() ?: return null
        val low = row.optString(4)?.toDoubleOrNull() ?: return null
        val volume = row.optString(5)?.toDoubleOrNull() ?: 0.0
        if (open <= 0 || close <= 0) return null
        return KLineBar(date, open, close, high, low, volume)
    }

    /**
     * 统一 K 线日期格式：
     * - 日/周/月接口返回 "yyyy-MM-dd"
     * - 分钟级接口（m1/m5/...）返回 "yyyyMMddHHmm" 紧凑格式
     * 统一转成 DateUtil 可解析的 "yyyy-MM-dd[ HH:mm]"。
     */
    private fun normalizeBarDate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('-')) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        return when (digits.length) {
            8 -> "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            12 -> "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)} " +
                "${digits.substring(8, 10)}:${digits.substring(10, 12)}"
            else -> null
        }
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
