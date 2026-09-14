package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeerSeriesMapperTest {

    @Test
    fun indexedClosesShareDatesAndStartAt100() {
        val left = snapshot(StockCatalog.tencent, 100.0)
        val right = snapshot(StockCatalog.alibaba, 50.0)
        val card = PeerSeriesMapper.indexedCloses(listOf(left, right), lookback = 20)
        assertTrue(card != null)
        assertEquals(SeriesChartKind.LINE, card!!.kind)
        assertEquals(2, card.series.size)
        assertEquals(100.0, card.series[0].values.first())
        assertEquals(100.0, card.series[1].values.first())
        assertTrue(card.title.contains("相对走势"))
        assertTrue(card.title.contains("腾讯"))
        assertTrue(card.series.map { it.colorArgb }.toSet().size >= 2)
    }

    @Test
    fun monthlyReturnsSkipEmptyMonthsAndUseShortLabels() {
        val left = snapshot(StockCatalog.tencent, 100.0, barCount = 160)
        val right = snapshot(StockCatalog.alibaba, 50.0, barCount = 160)
        val card = PeerSeriesMapper.monthlyReturns(listOf(left, right), months = 6)
        assertTrue(card != null)
        assertEquals(SeriesChartKind.GROUPED_BAR, card!!.kind)
        assertTrue(card.categories.all { it.length == 5 && it[2] == '-' })
        assertTrue(card.series.none { it.values.all { value -> value == null } })
    }

    @Test
    fun highlightsUseRealQuotes() {
        val items = PeerSeriesMapper.highlights(
            listOf(
                snapshot(StockCatalog.tencent, 400.0, 2.0),
                snapshot(StockCatalog.alibaba, 80.0, -1.0),
            ),
        )
        assertEquals(3, items.size)
        assertTrue(items[0].contains("腾讯"))
        assertTrue(items.none { it.contains("毛利率") })
    }
}

private fun snapshot(instrument: Instrument, price: Double, changePct: Double = 0.0, barCount: Int = 40): MarketSnapshot {
    val bars = (0 until barCount).map { i ->
        val month = 1 + (i / 20) % 12
        val day = 1 + i % 20
        val close = price + i
        KLineBar(
            "2026-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
            close, close, close + 1, close - 1, 1.0,
        )
    }
    return MarketSnapshot(
        Quote(
            instrument = instrument,
            price = price,
            prevClose = price,
            open = price,
            high = price,
            low = price,
            change = 0.0,
            changePct = changePct,
            volume = 1.0,
            turnover = 1.0,
            pe = 20.0,
            totalMarketCap = 1000.0,
            updateTime = "t",
            tradingStatus = "已收盘",
            isMock = false,
        ),
        bars,
        null,
    )
}
