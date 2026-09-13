package com.kuikly.stockchat.domain.model

import com.kuikly.stockchat.data.market.TencentMarketParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KLinePeriodTest {

    @Test
    fun fiveDayDoesNotShareMinuteApiKey() {
        assertEquals("day", KLinePeriod.FIVE_DAY.apiKey)
        assertEquals("m5", KLinePeriod.MIN_5.apiKey)
        assertEquals("day", KLinePeriod.FIVE_DAY.unit)
        assertTrue(KLinePeriod.FIVE_DAY.line)
    }

    @Test
    fun moreMenuPeriodsMatchFullChartDemo() {
        assertEquals(
            listOf("1分", "5分", "15分", "30分", "60分", "120分", "季K", "年K"),
            KLinePeriod.extendedTabs.map { it.label },
        )
    }

    @Test
    fun minuteBarsHitMkline() {
        assertEquals(TencentMarketParser.MKLINE_URL, TencentMarketParser.klineUrl(KLinePeriod.MIN_15))
        assertEquals(TencentMarketParser.KLINE_URL, TencentMarketParser.klineUrl(KLinePeriod.DAY))
        assertEquals(TencentMarketParser.KLINE_URL, TencentMarketParser.klineUrl(KLinePeriod.FIVE_DAY))
        assertTrue(KLinePeriod.MIN_60.isMinuteBar)
        assertFalse(KLinePeriod.FIVE_DAY.isMinuteBar)
    }
}
