package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.data.network.unwrapNetworkText
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.StockCatalog
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
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

    @Test
    fun hongKongQuotePrefersFullLineOverSimpleSPrefix() {
        val raw = """
            v_s_hk00700="100~腾讯控股~00700~428.400~2.800~0.66~15628379.0~6674835081.790~~38997.9436";
            v_hk00700="100~腾讯控股~00700~428.400~425.600~419.400~15628379.0~0~0~428.400~0~0~0~0~0~0~0~0~0~428.400~0~0~0~0~0~0~0~0~0~15628379.0~2026/09/11 16:09:02~2.800~0.66~430.800~419.400~428.400~15628379.0~6674835081.790~0~15.67";
        """.trimIndent()
        val quote = TencentMarketParser.parseQuote(StockCatalog.tencent, TencentMarketParser.quoteFields(raw, StockCatalog.tencent)!!)
        assertNotNull(quote)
        assertEquals(428.4, quote!!.price, 0.05)
        assertEquals(425.6, quote.prevClose, 0.05)
        assertEquals(2.8, quote.change, 0.05)
        assertEquals(0.66, quote.changePct, 0.05)
    }

    @Test
    fun hongKongQuoteRecoversWhenLastPriceDropped() {
        val shifted = listOf(
            "100", "腾讯控股", "00700",
            "425.600", "419.400", "15628379.0",
            "0", "0", "428.400", "0", "0", "0", "0", "0", "0", "0", "0", "0", "428.400",
            "0", "0", "0", "0", "0", "0", "0", "0", "0", "15628379.0",
            "2026/09/11 16:09:02", "2.800", "0.66", "430.800", "419.400", "428.400",
            "15628379.0", "6674835081.790", "0", "15.67",
        )
        val quote = TencentMarketParser.parseQuote(StockCatalog.tencent, shifted)
        assertNotNull(quote)
        assertEquals(428.4, quote!!.price, 0.05)
        assertEquals(425.6, quote.prevClose, 0.05)
        assertEquals(419.4, quote.open, 0.05)
        assertEquals(2.8, quote.change, 0.05)
        assertEquals(0.66, quote.changePct, 0.05)
        assertEquals(6674835081.79, quote.turnover, 1.0)
        assertTrue(quote.turnoverRate == null)
    }

    @Test
    fun hongKongQuoteMergesGarbledNameTildes() {
        val raw = """v_hk00700="100~腾~讯控股~00700~428.400~425.600~419.400~15628379.0~0~0~428.400~0~0~0~0~0~0~0~0~0~428.400~0~0~0~0~0~0~0~0~0~15628379.0~2026/09/11 16:09:02~2.800~0.66~430.800~419.400~428.400~15628379.0~6674835081.790~0~15.67";"""
        val quote = TencentMarketParser.parseQuote(StockCatalog.tencent, TencentMarketParser.quoteFields(raw, StockCatalog.tencent)!!)
        assertNotNull(quote)
        assertEquals(428.4, quote!!.price, 0.05)
        assertEquals(0.66, quote.changePct, 0.05)
    }

    @Test
    fun quoteFieldsSurviveJsonWrapperWithExtraKeys() {
        val raw = """{"data":"v_hk00700=\"100~腾讯控股~00700~428.400~425.600~419.400~1~0~0~428.400~0~0~0~0~0~0~0~0~0~428.400~0~0~0~0~0~0~0~0~0~1~2026/09/11 16:09:02~2.800~0.66~430.800~419.400~428.400~1~6674835081.790~0~15.67\";","httpCode":200}"""
        val quote = TencentMarketParser.parseQuote(StockCatalog.tencent, TencentMarketParser.quoteFields(raw, StockCatalog.tencent)!!)
        assertNotNull(quote)
        assertEquals(428.4, quote!!.price, 0.05)
    }

    @Test
    fun networkUnwrapKeepsPlainQuoteWhenExtraKeysPresent() {
        val json = JSONObject().apply {
            put("data", """v_hk00700="100~腾讯控股~00700~428.400~425.600";""")
            put("httpCode", 200)
        }
        val text = unwrapNetworkText(json)
        assertTrue(text.startsWith("v_hk00700="))
        assertFalse(text.startsWith("{"))
    }
}
