package com.kuikly.stockchat.domain.model

import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.ParsedIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstrumentResolverTest {
    @Test
    fun catalogIsFastPath() {
        val hits = InstrumentResolver().resolveLocal("腾讯控股后市如何")
        assertEquals(StockCatalog.tencent.key, hits.single().key)
    }

    @Test
    fun parsesAShareCode() {
        val hits = InstrumentResolver.parseCodes("看看600436今天怎么走")
        assertEquals("600436", hits.single().code)
        assertEquals(Market.SH, hits.single().market)
    }

    @Test
    fun pickBestPrefersStockOverIndex() {
        val resolver = InstrumentResolver()
        val picked = resolver.pickBest(
            "腾讯",
            listOf(
                Instrument("000847", "腾讯济安", Market.SH, InstrumentType.INDEX),
                Instrument("00700", "腾讯控股", Market.HK),
            ),
        )
        assertEquals("00700", picked?.code)
    }

    @Test
    fun enrichSearchesUnresolvedName() {
        val pzh = Instrument("600436", "片仔癀", Market.SH)
        val resolver = InstrumentResolver { _, callback -> callback(listOf(pzh)) }
        var result: ParsedIntent? = null
        resolver.enrich(
            ParsedIntent(Intent.STOCK_ANALYSIS, emptyList(), "片仔癀后市如何", unresolvedNames = listOf("片仔癀")),
        ) { result = it }
        assertEquals(pzh.key, result?.instruments?.single()?.key)
    }

    @Test
    fun enrichSkipsGreeting() {
        val resolver = InstrumentResolver { _, callback -> callback(listOf(StockCatalog.tencent)) }
        var result: ParsedIntent? = null
        resolver.enrich(ParsedIntent(Intent.GREETING, emptyList(), "你好")) { result = it }
        assertTrue(result?.instruments.isNullOrEmpty())
    }
}
