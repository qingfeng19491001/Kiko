package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.domain.prediction.PredictionPoint
import com.kuikly.stockchat.domain.prediction.StockPrediction
import kotlin.test.Test
import kotlin.test.assertTrue

class ChartFollowUpPromptTest {
    @Test
    fun includesNameAndForecast() {
        val prompt = ChartFollowUpPrompt.build(
            StockCatalog.moutai,
            SelectedChartPoint("2026-09-12", 1400.0, 1410.0, 1390.0, 1405.0, isForecast = true, changePct = 1.2),
            StockPrediction("偏多", 0.41f, "均线", "t", 8, listOf(PredictionPoint("2026-09-12", 1405.0))),
        )
        assertTrue(prompt.contains("贵州茅台"))
        assertTrue(prompt.contains("2026-09-12"))
        assertTrue(prompt.contains("情景预测"))
        assertTrue(prompt.contains("这一点怎么看"))
    }

    @Test
    fun fromBarsUsesSelectedClose() {
        val bars = listOf(
            KLineBar("2026-09-10", 10.0, 11.0, 12.0, 9.0, 1.0),
            KLineBar("2026-09-11", 11.0, 12.0, 13.0, 10.0, 1.0),
        )
        val prompt = ChartFollowUpPrompt.fromBars("腾讯控股", bars, 1)
        assertTrue(prompt != null && prompt.contains("腾讯控股"))
        assertTrue(prompt.contains("2026-09-11"))
        assertTrue(prompt.contains("这一点怎么看"))
    }
}
