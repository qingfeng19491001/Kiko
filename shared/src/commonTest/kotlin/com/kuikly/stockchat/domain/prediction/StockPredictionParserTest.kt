package com.kuikly.stockchat.domain.prediction

import com.kuikly.stockchat.data.parser.StockPredictionParser
import com.kuikly.stockchat.domain.util.DateUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockPredictionParserTest {
    @Test
    fun acceptsMatchingForecast() {
        val dates = DateUtil.nextWeekdays("2026-04-10", 5)
        val json = """
            {"direction":"偏多","confidence":0.42,"rationale":"均线多头","sourceUpdatedAt":"04-10 16:00","historyPointCount":8,
             "forecast":[${dates.joinToString(",") { """{"date":"$it","price":100.5,"low":99.0,"high":102.0}""" }}]}
        """.trimIndent()
        val parsed = StockPredictionParser.parse(json, "04-10 16:00", 8, dates, 100.0)
        assertEquals("偏多", parsed?.direction)
        assertEquals(5, parsed?.points?.size)
        assertEquals(dates.first(), parsed?.points?.first()?.date)
    }

    @Test
    fun rejectsWrongDateOrCount() {
        val dates = DateUtil.nextWeekdays("2026-04-10", 5)
        val json = """{"direction":"中性","confidence":0.2,"rationale":"震荡","sourceUpdatedAt":"04-10 16:00","historyPointCount":8,"forecast":[]}"""
        assertNull(StockPredictionParser.parse(json, "04-10 16:00", 8, dates, 100.0))
    }

    @Test
    fun rejectsImplausiblePrice() {
        val dates = DateUtil.nextWeekdays("2026-04-10", 1)
        val json = """{"direction":"偏多","confidence":0.9,"rationale":"暴涨","sourceUpdatedAt":"t","historyPointCount":8,
            "forecast":[{"date":"${dates.first()}","price":99999.0}]}"""
        assertNull(StockPredictionParser.parse(json, "t", 8, dates, 100.0))
    }

    @Test
    fun nextWeekdaysSkipsWeekend() {
        val dates = DateUtil.nextWeekdays("2026-04-10", 1)
        assertTrue(dates.isNotEmpty())
        assertTrue(!DateUtil.isWeekend(dates.first()))
    }
}
