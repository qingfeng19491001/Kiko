package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.InstrumentType
import com.kuikly.stockchat.domain.model.Market
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TencentSymbolSearchTest {
    @Test
    fun parsesSingleAShareRecord() {
        val raw = """v_hint="sh~600436~\u7247\u4ed4\u7640~pzh~GP-A""""
        val hits = TencentSymbolSearch.parse(raw)
        assertEquals(1, hits.size)
        assertEquals("600436", hits[0].code)
        assertEquals("片仔癀", hits[0].name)
        assertEquals(Market.SH, hits[0].market)
        assertEquals("sh600436", hits[0].key)
    }

    @Test
    fun parsesMultipleAndSkipsWarrants() {
        val raw = """v_hint="hk~00700~腾讯控股~txkg~GP^hk~13005~腾讯权证~x~QZ^sh~000847~腾讯济安~x~ZS""""
        val hits = TencentSymbolSearch.parse(raw)
        assertEquals(2, hits.size)
        assertEquals("00700", hits[0].code)
        assertEquals(InstrumentType.INDEX, hits[1].type)
    }

    @Test
    fun parsesUsTickerOverride() {
        val raw = """v_hint="us~aapl.oq~苹果~pg~GP""""
        val hit = TencentSymbolSearch.parse(raw).single()
        assertEquals("AAPL", hit.code)
        assertEquals("AAPL.OQ", hit.tencentCodeOverrideOrNull())
        assertEquals(Market.US, hit.market)
    }

    @Test
    fun emptyHint() {
        assertTrue(TencentSymbolSearch.parse("""v_hint="N";""").isEmpty())
    }
}
