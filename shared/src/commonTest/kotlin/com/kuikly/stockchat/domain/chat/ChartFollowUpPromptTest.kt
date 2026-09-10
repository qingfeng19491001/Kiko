package com.kuikly.stockchat.domain.chat

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
}
