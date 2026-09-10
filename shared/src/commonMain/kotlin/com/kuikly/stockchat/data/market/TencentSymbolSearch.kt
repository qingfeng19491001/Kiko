package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentType
import com.kuikly.stockchat.domain.model.Market

/**
 * 腾讯证券联想搜索。回包形如：
 * `v_hint="sh~600436~片仔癀~pzh~GP-A^hk~00700~腾讯控股~txkg~GP"`
 */
class TencentSymbolSearch(private val http: HttpClient) {

    fun search(query: String, callback: (List<Instrument>) -> Unit) {
        val q = query.trim()
        if (q.isEmpty()) {
            callback(emptyList())
            return
        }
        http.get(SEARCH_URL, mapOf("v" to "2", "t" to "all", "q" to q)) { text, _ ->
            callback(parse(text.orEmpty()))
        }
    }

    companion object {
        const val SEARCH_URL = "https://smartbox.gtimg.cn/s3/"

        fun parse(raw: String): List<Instrument> {
            val body = unwrapHint(raw) ?: return emptyList()
            if (body.equals("N", ignoreCase = true) || body.isBlank()) return emptyList()
            return body.split('^').mapNotNull(::parseRecord)
        }

        private fun unwrapHint(raw: String): String? {
            val trimmed = raw.trim()
            val marker = "v_hint="
            val idx = trimmed.indexOf(marker)
            val payload = if (idx >= 0) trimmed.substring(idx + marker.length).trim() else trimmed
            return payload.trim().trim('"').trimEnd(';').trim().trim('"').takeIf { it.isNotEmpty() }
        }

        private fun parseRecord(record: String): Instrument? {
            val parts = record.split('~')
            if (parts.size < 3) return null
            val marketKey = parts[0].trim().lowercase()
            val rawCode = parts[1].trim()
            val name = unescapeUnicode(parts[2].trim())
            val kind = parts.getOrNull(4)?.trim()?.uppercase().orEmpty()
            if (name.isEmpty() || rawCode.isEmpty()) return null
            if (kind == "QZ" || kind == "JJ" || kind == "ZQ") return null
            val market = when (marketKey) {
                "sh" -> Market.SH
                "sz" -> Market.SZ
                "hk" -> Market.HK
                "us" -> Market.US
                else -> return null
            }
            val type = if (kind == "ZS") InstrumentType.INDEX else InstrumentType.STOCK
            val (code, override) = normalizeCode(market, rawCode)
            if (code.isEmpty()) return null
            return Instrument(
                code = code,
                name = name,
                market = market,
                type = type,
                tencentCodeOverride = override,
            )
        }

        private fun normalizeCode(market: Market, rawCode: String): Pair<String, String?> {
            val code = rawCode.trim()
            return when (market) {
                Market.US -> {
                    val upper = code.uppercase()
                    val ticker = upper.substringBefore('.')
                    ticker to if (upper.contains('.')) upper else null
                }
                Market.HK, Market.SH, Market.SZ -> code.uppercase() to null
            }
        }

        internal fun unescapeUnicode(text: String): String {
            if (!text.contains("\\u", ignoreCase = true)) return text
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                if (i + 5 < text.length && text[i] == '\\' && (text[i + 1] == 'u' || text[i + 1] == 'U')) {
                    val hex = text.substring(i + 2, i + 6)
                    val cp = hex.toIntOrNull(16)
                    if (cp != null) {
                        out.append(cp.toChar())
                        i += 6
                        continue
                    }
                }
                out.append(text[i])
                i += 1
            }
            return out.toString()
        }
    }
}
