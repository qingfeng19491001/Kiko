package com.kuikly.stockchat.domain.analysis

import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailActionPlanTest {

    @Test
    fun strongScoreUsesWatchStanceAndKeepsSupportResistance() {
        val plan = DetailActionPlan.from(quote(428.4, 0.66), analysis(score = 72, rsi = 58.0))
        assertEquals("逢低关注", plan.stance)
        assertEquals(TagTone.POSITIVE, plan.stanceTone)
        assertEquals(listOf("关注区", "现价", "压力区"), plan.levels.map { it.label })
        assertTrue(plan.signals.any { it.title == "短期" && it.value == "偏多" })
        assertTrue(plan.operationTip.contains("关注区"))
    }

    @Test
    fun weakScoreAsksToCutSize() {
        val plan = DetailActionPlan.from(quote(80.0, -2.1), analysis(score = 30, rsi = 28.0, short = TrendBias.BEARISH))
        assertEquals("控制仓位", plan.stance)
        assertEquals(TagTone.NEGATIVE, plan.stanceTone)
        assertTrue(plan.signals.any { it.title == "RSI" && it.hint.contains("超卖") })
    }

    private fun quote(price: Double, changePct: Double): Quote {
        val prev = price / (1 + changePct / 100)
        return Quote(
            instrument = StockCatalog.tencent,
            price = price,
            prevClose = prev,
            open = prev,
            high = price,
            low = prev,
            change = price - prev,
            changePct = changePct,
            volume = 1.0,
            turnover = 1.0,
            updateTime = "09-13 16:00",
            tradingStatus = "港股已收盘",
        )
    }

    private fun analysis(
        score: Int,
        rsi: Double?,
        short: TrendBias = TrendBias.BULLISH,
    ): TechnicalAnalysis = TechnicalAnalysis(
        ma5 = 420.0,
        ma10 = 418.0,
        ma20 = 410.0,
        ma60 = 400.0,
        rsi14 = rsi,
        support = 411.0,
        resistance = 430.8,
        shortTermTrend = short,
        midTermTrend = TrendBias.NEUTRAL,
        volatilityPct = 18.0,
        volumeRatio = 1.4,
        change5dPct = 1.2,
        change20dPct = 3.0,
        biasToMa20Pct = 2.0,
        score = score,
        tags = emptyList(),
    )
}
