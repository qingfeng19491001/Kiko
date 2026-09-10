package com.kuikly.stockchat.domain.prediction

import com.kuikly.stockchat.domain.model.KLineBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredictionChartMapperTest {
    private val history = listOf(
        KLineBar("2026-04-09", 99.0, 100.0, 101.0, 98.0, 1.0),
        KLineBar("2026-04-10", 100.0, 100.0, 102.0, 99.0, 1.0),
    )

    @Test
    fun appendsFutureLineBars() {
        val prediction = StockPrediction(
            direction = "偏多",
            confidence = 0.4f,
            rationale = "均线",
            sourceUpdatedAt = "t",
            historyPointCount = 2,
            points = listOf(
                PredictionPoint("2026-04-13", 101.0, 100.0, 102.0),
                PredictionPoint("2026-04-14", 102.0),
            ),
        )
        val bars = PredictionChartMapper.toForecastBars(history, prediction)
        assertEquals(2, bars.size)
        assertEquals("2026-04-13", bars[0].date)
        assertEquals(101.0, bars[0].close)
        assertEquals(101.0, bars[0].open)
        assertEquals(0.0, bars[0].volume)
    }

    @Test
    fun rejectsDateNotAfterHistory() {
        val prediction = StockPrediction(
            "中性", 0.2f, "x", "t", 2,
            listOf(PredictionPoint("2026-04-10", 100.0)),
        )
        assertTrue(PredictionChartMapper.toForecastBars(history, prediction).isEmpty())
    }

    @Test
    fun rejectsJumpOverThreshold() {
        val prediction = StockPrediction(
            "偏多", 0.9f, "x", "t", 2,
            listOf(PredictionPoint("2026-04-13", 160.0)),
        )
        assertTrue(PredictionChartMapper.toForecastBars(history, prediction).isEmpty())
    }
}
