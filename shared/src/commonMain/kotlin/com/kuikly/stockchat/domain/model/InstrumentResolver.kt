package com.kuikly.stockchat.domain.model

import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.ParsedIntent

/**
 * 标的解析：目录与代码规则为快路径，未命中时走异步搜索。
 */
class InstrumentResolver(
    private val search: ((query: String, callback: (List<Instrument>) -> Unit) -> Unit)? = null,
) {
    fun resolveLocal(text: String): List<Instrument> {
        val fromCatalog = StockCatalog.extract(text)
        if (fromCatalog.isNotEmpty()) return fromCatalog
        return parseCodes(text)
    }

    fun parseToken(token: String): Instrument? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        StockCatalog.resolveName(trimmed)?.let { return it }
        return parseCodes(trimmed).firstOrNull()
    }

    fun enrich(parsed: ParsedIntent, callback: (ParsedIntent) -> Unit) {
        val local = parsed.instruments.ifEmpty { resolveLocal(parsed.rawText) }.toMutableList()
        val queries = buildQueries(parsed, local)
        if (queries.isEmpty() || search == null) {
            callback(parsed.copy(instruments = local.distinctBy { it.key }))
            return
        }
        val found = ArrayList<Instrument>()
        fun next(index: Int) {
            if (index >= queries.size) {
                val merged = (found + local).distinctBy { it.key }
                val intent = if (parsed.intent == Intent.UNKNOWN && merged.isNotEmpty()) Intent.STOCK_ANALYSIS else parsed.intent
                callback(parsed.copy(intent = intent, instruments = merged, unresolvedNames = emptyList()))
                return
            }
            search.invoke(queries[index]) { matches ->
                pickBest(queries[index], matches)?.let { found += it }
                next(index + 1)
            }
        }
        next(0)
    }

    private fun buildQueries(parsed: ParsedIntent, local: List<Instrument>): List<String> {
        if (!shouldSearch(parsed.intent)) return emptyList()
        if (parsed.unresolvedNames.isNotEmpty()) return parsed.unresolvedNames.map { it.trim() }.filter { it.isNotEmpty() }
        val unnamed = local.filter { it.name == it.code }.map { it.code }
        if (unnamed.isNotEmpty()) return unnamed
        if (local.isNotEmpty()) return emptyList()
        val text = parsed.rawText.trim()
        if (text.length < 2) return emptyList()
        return listOf(text.take(20))
    }

    private fun shouldSearch(intent: Intent): Boolean = when (intent) {
        Intent.GREETING, Intent.KNOWLEDGE, Intent.LIMIT_UP_LADDER, Intent.MARKET_OVERVIEW -> false
        else -> true
    }

    fun pickBest(query: String, matches: List<Instrument>): Instrument? {
        if (matches.isEmpty()) return null
        val q = query.trim()
        val ranked = matches.sortedWith(
            compareBy<Instrument> { nameScore(q, it) }
                .thenBy { typeScore(it) }
                .thenBy { marketScore(it) },
        )
        return ranked.firstOrNull()
    }

    private fun nameScore(query: String, instrument: Instrument): Int {
        val q = query.lowercase()
        val name = instrument.name.lowercase()
        val code = instrument.code.lowercase()
        return when {
            name == q || code == q || instrument.displayCode.equals(query, ignoreCase = true) -> 0
            name.startsWith(q) || q.startsWith(name) -> 1
            name.contains(q) || q.contains(name) -> 2
            else -> 3
        }
    }

    private fun typeScore(instrument: Instrument): Int = if (instrument.isIndex) 1 else 0

    private fun marketScore(instrument: Instrument): Int = when (instrument.market) {
        Market.SH, Market.SZ, Market.HK -> 0
        Market.US -> 1
    }

    companion object {
        private val aShare = Regex("""(?<![0-9A-Za-z])([036]\d{5})(?![0-9A-Za-z])""")
        private val hkCode = Regex("""(?<![0-9A-Za-z])(\d{5})(?![0-9A-Za-z])""")

        fun parseCodes(text: String): List<Instrument> {
            val hits = mutableListOf<Instrument>()
            aShare.findAll(text).forEach { match ->
                val code = match.groupValues[1]
                val market = if (code.startsWith("6") || code.startsWith("9")) Market.SH else Market.SZ
                hits += Instrument(code = code, name = code, market = market)
            }
            if (hits.isNotEmpty()) return hits.distinctBy { it.key }
            hkCode.findAll(text).forEach { match ->
                val code = match.groupValues[1]
                if (code.startsWith("0") || code.startsWith("1")) {
                    hits += Instrument(code = code, name = code, market = Market.HK)
                }
            }
            return hits.distinctBy { it.key }
        }
    }
}
