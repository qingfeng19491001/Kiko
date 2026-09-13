package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TencentQuoteParserTest {

    @Test
    fun usQuoteLooksUpBareSymbolInsteadOfOqSuffix() {
        val raw = """v_usTSLA="200~Tesla~TSLA.OQ~367.81~368.16~368.25~1~0~0~366.92~80~0~0~0~0~0~0~0~0~367.30~280~0~0~0~0~0~0~0~0~~20260909160001~-0.35~-0.10~375.44~366.00";"""
        val tesla = StockCatalog.tesla
        assertEquals("usTSLA.OQ", tesla.tencentSymbol)
        assertTrue("usTSLA" in TencentMarketParser.quoteLookupKeys(tesla))
        assertTrue(TencentMarketParser.quoteUrl(listOf(tesla)).contains("usTSLA"))
        val fields = TencentMarketParser.quoteFields(raw, tesla)
        assertNotNull(fields)
        val quote = TencentMarketParser.parseQuote(tesla, fields)
        assertNotNull(quote)
        assertEquals(367.81, quote!!.price, 0.001)
        assertFalse(quote.isMock)
    }

    @Test
    fun quoteUrlBatchesMultipleSymbols() {
        val url = TencentMarketParser.quoteUrl(listOf(StockCatalog.tencent, StockCatalog.moutai, StockCatalog.apple))
        assertTrue(url.startsWith("https://qt.gtimg.cn/q="))
        assertTrue(url.contains("hk00700"))
        assertTrue(url.contains("sh600519"))
        assertTrue(url.contains("usAAPL") || url.contains("AAPL"))
        assertFalse(url.contains("usAAPL.OQ"))
    }

    @Test
    fun usIndexKlineUsesDottedSymbol() {
        val dji = StockCatalog.dji
        assertEquals("us.DJI", TencentMarketParser.klineSymbol(dji))
        assertTrue("us.DJI" in TencentMarketParser.quoteLookupKeys(dji))
        assertTrue(TencentMarketParser.klineParam(dji, KLinePeriod.DAY, 40).startsWith("us.DJI,"))
        assertEquals("usAAPL", TencentMarketParser.klineSymbol(StockCatalog.apple))
    }

    @Test
    fun hongKongQuoteStillMatchesPrefixedCode() {
        val raw = """v_hk01211="100~比亚迪股份~01211~79.550~81.800~80.500";"""
        val fields = TencentMarketParser.quoteFields(raw, StockCatalog.byd)
        assertNotNull(fields)
        val quote = TencentMarketParser.parseQuote(StockCatalog.byd, fields!!)
        assertNotNull(quote)
        assertEquals(79.55, quote!!.price, 0.001)
    }
}
