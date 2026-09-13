package com.kuikly.stockchat.ui.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketListFilterTest {

    @Test
    fun allBoardKeepsEveryCatalogName() {
        assertTrue(MarketListFilter.matches(StockCatalog.tencent, MarketBoard.ALL, "", emptySet()))
        assertTrue(MarketListFilter.matches(StockCatalog.sse, MarketBoard.ALL, "", emptySet()))
    }

    @Test
    fun watchBoardOnlyKeepsStarredKeys() {
        val tencent = StockCatalog.tencent
        assertFalse(MarketListFilter.matches(tencent, MarketBoard.WATCH, "", emptySet()))
        assertTrue(MarketListFilter.matches(tencent, MarketBoard.WATCH, "", setOf(tencent.key)))
    }

    @Test
    fun marketBoardsSplitCnHkUsAndIndex() {
        assertTrue(MarketListFilter.matches(StockCatalog.moutai, MarketBoard.CN, "", emptySet()))
        assertFalse(MarketListFilter.matches(StockCatalog.tencent, MarketBoard.CN, "", emptySet()))
        assertTrue(MarketListFilter.matches(StockCatalog.tencent, MarketBoard.HK, "", emptySet()))
        assertTrue(MarketListFilter.matches(StockCatalog.apple, MarketBoard.US, "", emptySet()))
        assertTrue(MarketListFilter.matches(StockCatalog.sse, MarketBoard.INDEX, "", emptySet()))
        assertFalse(MarketListFilter.matches(StockCatalog.moutai, MarketBoard.INDEX, "", emptySet()))
        assertTrue(MarketListFilter.matches(StockCatalog.tencent, MarketBoard.HK_CONNECT, "", emptySet()))
        assertFalse(MarketListFilter.matches(StockCatalog.hsi, MarketBoard.HK_CONNECT, "", emptySet()))
        assertFalse(MarketListFilter.matches(StockCatalog.moutai, MarketBoard.HK_CONNECT, "", emptySet()))
    }

    @Test
    fun overviewIndicesFollowSelectedBoard() {
        assertEquals(
            listOf(StockCatalog.sse, StockCatalog.hsi, StockCatalog.dji),
            MarketBoardOverview.indices(MarketBoard.ALL),
        )
        assertEquals(
            listOf(StockCatalog.sse, StockCatalog.szse, StockCatalog.chinext),
            MarketBoardOverview.indices(MarketBoard.CN),
        )
        assertEquals(
            listOf(StockCatalog.hsi, StockCatalog.hstech, StockCatalog.hscei),
            MarketBoardOverview.indices(MarketBoard.HK),
        )
        assertEquals(
            MarketBoardOverview.indices(MarketBoard.HK),
            MarketBoardOverview.indices(MarketBoard.HK_CONNECT),
        )
        assertEquals(
            listOf(StockCatalog.dji, StockCatalog.ixic, StockCatalog.ndx),
            MarketBoardOverview.indices(MarketBoard.US),
        )
        assertTrue(MarketBoardOverview.indices(MarketBoard.WATCH).isEmpty())
    }

    @Test
    fun aShareStatsUseCsiSizeAndTwoMarketTurnover() {
        assertTrue(MarketBoardOverview.showsStats(MarketBoard.ALL))
        assertTrue(MarketBoardOverview.showsStats(MarketBoard.CN))
        assertTrue(MarketBoardOverview.showsStats(MarketBoard.HK))
        assertFalse(MarketBoardOverview.showsStats(MarketBoard.US))
        val built = MarketOverviewStats.fromAShare(
            mapOf(
                StockCatalog.sse.key to fakeQuote(StockCatalog.sse, 9_581_8634_0000.0),
                StockCatalog.szse.key to fakeQuote(StockCatalog.szse, 1_0137_1215_0000.0),
                StockCatalog.csi300.key to fakeQuote(StockCatalog.csi300, 0.0, -0.84),
                StockCatalog.csi500.key to fakeQuote(StockCatalog.csi500, 0.0, -1.78),
                StockCatalog.csi1000.key to fakeQuote(StockCatalog.csi1000, 0.0, -2.12),
            ),
        )
        assertEquals(9_581_8634_0000.0 + 1_0137_1215_0000.0, built.turnoverYuan)
        assertEquals("小盘股领跌", built.sizeHeadline)
        val hk = MarketOverviewStats.fromHongKong(
            mapOf(
                StockCatalog.hsi.key to fakeQuote(StockCatalog.hsi, 2_284_3055.0, -0.60),
                StockCatalog.hstech.key to fakeQuote(StockCatalog.hstech, 0.0, -0.23),
                StockCatalog.hscei.key to fakeQuote(StockCatalog.hscei, 0.0, -0.34),
            ),
        )
        assertEquals(2_284_3055.0 * 10_000.0, hk.turnoverYuan)
        assertEquals("恒指领跌", hk.sizeHeadline)
        assertEquals("指数对比", hk.sizeTitle)
    }

    private fun fakeQuote(instrument: Instrument, turnover: Double, changePct: Double = 0.0): Quote {
        return Quote(
            instrument = instrument,
            price = 1.0,
            prevClose = 1.0,
            open = 1.0,
            high = 1.0,
            low = 1.0,
            change = changePct,
            changePct = changePct,
            volume = 0.0,
            turnover = turnover,
            updateTime = "",
            tradingStatus = "",
        )
    }

    @Test
    fun queryMatchesNameCodeAndAlias() {
        val tencent = StockCatalog.tencent
        assertTrue(MarketListFilter.matches(tencent, MarketBoard.ALL, "00700", emptySet()))
        assertTrue(MarketListFilter.matches(tencent, MarketBoard.ALL, "腾讯", emptySet()))
        assertFalse(MarketListFilter.matches(tencent, MarketBoard.ALL, "茅台", emptySet()))
    }
}
