package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentType
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.util.DateUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ITickKlineParserTest {

    @Test
    fun hkNumericCodeDropsLeadingZeros() {
        val tencent = Instrument("00700", "腾讯控股", Market.HK)
        assertEquals("700", ITickKlineParser.code(tencent))
        assertEquals("HK", ITickKlineParser.region(tencent))
        assertEquals("/stock/kline", ITickKlineParser.path(tencent))
    }

    @Test
    fun indexUsesIndicesPath() {
        val hsi = Instrument("HSI", "恒生指数", Market.HK, type = InstrumentType.INDEX)
        assertEquals("HSI", ITickKlineParser.code(hsi))
        assertEquals("/indices/kline", ITickKlineParser.path(hsi))
    }

    @Test
    fun dateUtilRoundTripsHongKongNoon() {
        val epoch = DateUtil.parseToEpochMillis("2025-03-06 12:00", offsetHours = 8)
        assertNotNull(epoch)
        val clock = DateUtil.toLocalClock(epoch, 8)
        assertNotNull(clock)
        assertEquals("2025-03-06", clock.date)
        assertEquals("12:00", clock.hhmm)
    }

    @Test
    fun minuteBarsBecomeCumulativeIntradayForLatestSession() {
        val noon = DateUtil.parseToEpochMillis("2025-03-06 12:00", 8)!!
        val next = noon + 60_000L
        val prevDay = DateUtil.parseToEpochMillis("2025-03-05 15:00", 8)!!
        val series = ITickKlineParser.toIntraday(
            listOf(
                ITickKlineParser.RawBar(prevDay, 500.0, 9_000.0),
                ITickKlineParser.RawBar(next, 503.0, 200.0),
                ITickKlineParser.RawBar(noon, 501.0, 100.0),
            ),
            Market.HK,
            prevCloseHint = 500.0,
        )
        assertNotNull(series)
        assertEquals("2025-03-06", series.date)
        assertEquals(500.0, series.prevClose)
        assertEquals(2, series.ticks.size)
        assertEquals("12:00", series.ticks[0].time)
        assertEquals(501.0, series.ticks[0].price)
        assertEquals(100.0, series.ticks[0].volume)
        assertEquals("12:01", series.ticks[1].time)
        assertEquals(300.0, series.ticks[1].volume)
        assertTrue(series.ticks[1].volume > series.ticks[0].volume)
    }
}
