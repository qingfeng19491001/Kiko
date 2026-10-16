package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Market
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 腾讯美股排行 `rank/us/getList` 未登录时常回空列表。
 * 名单改用新浪美股全市场列表（约 1.8 万只），按市值取前列；报价仍走腾讯 `qt`。
 */
object SinaUsMarketList {

    const val LIST_URL = "https://stock.finance.sina.com.cn/usstock/api/jsonp.php/cb/US_CategoryService.getList"

    val PAGE_HEADERS: Map<String, String> = mapOf(
        "Referer" to "https://finance.sina.com.cn/",
    )

    fun params(count: Int = 50): Map<String, String> = mapOf(
        "page" to "1",
        "num" to count.toString(),
        "sort" to "mktcap",
        "asc" to "0",
        "market" to "",
        "id" to "",
    )

    fun parse(raw: String): List<Instrument> {
        val json = runCatching { JSONObject(TencentBoardList.extractJson(raw)) }.getOrNull() ?: return emptyList()
        val list = json.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Instrument>(list.length())
        for (i in 0 until list.length()) {
            val row = list.optJSONObject(i) ?: continue
            val symbol = row.optString("symbol").orEmpty().trim().uppercase()
            if (symbol.isEmpty()) continue
            val name = row.optString("cname").orEmpty().ifBlank { row.optString("name").orEmpty() }.ifBlank { symbol }
            val venue = row.optString("market").orEmpty().uppercase()
            val override = when {
                "NASDAQ" in venue -> "$symbol.OQ"
                "NYSE" in venue -> "$symbol.N"
                "AMEX" in venue -> "$symbol.A"
                else -> null
            }
            out += Instrument(
                code = symbol.substringBefore('.'),
                name = name,
                market = Market.US,
                tencentCodeOverride = override,
            )
        }
        return out
    }
}
