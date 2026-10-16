package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Market
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 腾讯行情中心的真实排行名单（不是本地目录）。
 * A 股：沪深成交额；港股：主板成交额；港股通：南向标的涨跌榜。
 */
object TencentBoardList {

    const val HS_RANK_URL = "https://proxy.finance.qq.com/cgi/cgi-bin/rank/hs/getBoardRankList"
    const val HK_RANK_URL = "https://stock.gtimg.cn/data/hk_rank.php"
    const val GGT_URL = "https://web.ifzq.gtimg.cn/appstock/invest/investment/ggt"
    const val US_RANK_URL = "https://proxy.finance.qq.com/cgi/cgi-bin/rank/us/getList"

    val PAGE_HEADERS: Map<String, String> = mapOf(
        "Referer" to "https://stockapp.finance.qq.com/",
    )

    fun hsRankParams(count: Int = 60): Map<String, String> = mapOf(
        "_appver" to "11.17.0",
        "board_code" to "aStock",
        "sort_type" to "turnover",
        "direct" to "down",
        "offset" to "0",
        "count" to count.toString(),
    )

    fun hkRankParams(count: Int = 50): Map<String, String> = mapOf(
        "board" to "main_all",
        "metric" to "amount",
        "pageSize" to count.toString(),
        "reqPage" to "1",
        "order" to "desc",
        "var_name" to "list_data",
    )

    fun ggtParams(count: Int = 50): Map<String, String> = mapOf(
        "app" to "web",
        "p" to "1",
        "l" to count.toString(),
        "order" to "zdf",
        "way" to "r",
    )

    fun usRankParams(count: Int = 40): Map<String, String> = mapOf(
        "board_type" to "IMP",
        "sort_type" to "priceRatio",
        "direct" to "down",
        "offset" to "0",
        "count" to count.toString(),
    )

    fun parseAShareRank(raw: String): List<Instrument> {
        val json = runCatching { JSONObject(extractJson(raw)) }.getOrNull() ?: return emptyList()
        if (json.optInt("code", -1) != 0) return emptyList()
        val list = json.optJSONObject("data")?.optJSONArray("rank_list") ?: return emptyList()
        val out = ArrayList<Instrument>(list.length())
        for (i in 0 until list.length()) {
            val row = list.optJSONObject(i) ?: continue
            val instrument = fromPrefixedCode(row.optString("code"), row.optString("name")) ?: continue
            out += instrument
        }
        return out
    }

    fun parseHkRank(raw: String): List<Instrument> {
        val json = runCatching { JSONObject(unwrapListData(raw)) }.getOrNull() ?: return emptyList()
        if (json.has("code") && json.optInt("code") != 0) return emptyList()
        val page = json.optJSONObject("data")?.optJSONArray("page_data") ?: return emptyList()
        val out = ArrayList<Instrument>(page.length())
        for (i in 0 until page.length()) {
            val parts = page.optString(i).orEmpty().split('~')
            if (parts.size < 2) continue
            val code = parts[0].trim().padStart(5, '0')
            val name = parts[1].trim()
            if (code.isEmpty() || name.isEmpty()) continue
            out += Instrument(code = code, name = name, market = Market.HK, stockConnect = true)
        }
        return out
    }

    fun parseGgt(raw: String): List<Instrument> {
        val json = runCatching { JSONObject(extractJson(raw)) }.getOrNull() ?: return emptyList()
        if (json.optInt("code", -1) != 0) return emptyList()
        val list = json.optJSONObject("data")?.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Instrument>(list.length())
        for (i in 0 until list.length()) {
            val token = list.optString(i).orEmpty().trim()
            val symbol = token.substringBefore(' ').trim()
            val name = token.substringAfter(' ', missingDelimiterValue = symbol).trim().ifEmpty { symbol }
            val instrument = fromPrefixedCode(symbol, name, stockConnect = true) ?: continue
            if (instrument.market != Market.HK) continue
            out += instrument
        }
        return out
    }

    fun parseUsRank(raw: String): List<Instrument> {
        val json = runCatching { JSONObject(extractJson(raw)) }.getOrNull() ?: return emptyList()
        if (json.optInt("code", -1) != 0) return emptyList()
        val list = json.optJSONObject("data")?.optJSONArray("rank_list") ?: return emptyList()
        val out = ArrayList<Instrument>(list.length())
        for (i in 0 until list.length()) {
            val row = list.optJSONObject(i) ?: continue
            val instrument = fromPrefixedCode(row.optString("code"), row.optString("name")) ?: continue
            if (instrument.market != Market.US) continue
            out += instrument
        }
        return out
    }

    fun fromPrefixedCode(raw: String, name: String, stockConnect: Boolean = false): Instrument? {
        val token = raw.trim()
        if (token.isEmpty() || name.isBlank()) return null
        val lower = token.lowercase()
        return when {
            lower.startsWith("bj") -> null
            lower.startsWith("sh") -> Instrument(code = token.drop(2).uppercase(), name = name, market = Market.SH)
            lower.startsWith("sz") -> Instrument(code = token.drop(2).uppercase(), name = name, market = Market.SZ)
            lower.startsWith("hk") -> Instrument(
                code = token.drop(2).padStart(5, '0').uppercase(),
                name = name,
                market = Market.HK,
                stockConnect = stockConnect,
            )
            lower.startsWith("us") -> {
                val rest = token.drop(2).uppercase()
                val ticker = rest.substringBefore('.')
                Instrument(
                    code = ticker,
                    name = name,
                    market = Market.US,
                    tencentCodeOverride = rest.takeIf { it.contains('.') },
                )
            }
            token.all { it.isDigit() } -> Instrument(
                code = token.padStart(5, '0'),
                name = name,
                market = Market.HK,
                stockConnect = stockConnect,
            )
            else -> null
        }
    }

    internal fun unwrapListData(raw: String): String {
        val trimmed = raw.trim()
        val marker = "list_data="
        val idx = trimmed.indexOf(marker)
        val payload = if (idx >= 0) trimmed.substring(idx + marker.length).trim() else trimmed
        return payload.trim().trimEnd(';')
    }

    internal fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }
}
