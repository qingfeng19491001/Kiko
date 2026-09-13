package com.kuikly.stockchat.data.kline

import com.kuikly.stockchat.domain.model.KLineBar
import kotlin.test.Test
import kotlin.test.assertTrue

class MarketKLineDataSourceTest {
    @Test
    fun barsJsonContainsOhlcvFields() {
        val json = MarketKLineDataSource.barsJson(
            listOf(
                KLineBar(
                    date = "2026-04-10",
                    open = 100.0,
                    high = 102.0,
                    low = 99.0,
                    close = 101.0,
                    volume = 1234.0,
                ),
            ),
        )
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"open\":100.0"))
        assertTrue(json.contains("\"close\":101.0"))
        assertTrue(json.contains("\"volume\":1234.0"))
    }
}
