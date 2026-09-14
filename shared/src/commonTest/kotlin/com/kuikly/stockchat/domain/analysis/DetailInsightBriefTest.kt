package com.kuikly.stockchat.domain.analysis

import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.FlowItemData
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailInsightBriefTest {

    @Test
    fun strongScoreBuildsHeadlineAndCoreSections() {
        val brief = DetailInsightBrief.from(quote(428.4, 0.66), analysis(score = 72, rsi = 58.0))
        assertTrue(brief.headline.contains("偏强"))
        assertTrue(brief.headline.contains("逢低关注"))
        assertEquals(listOf("行情总结", "趋势判断", "操作提示"), brief.sections.map { it.title })
        assertEquals(listOf("今日", "近5日", "近20日", "量比"), brief.snapshotItems.map { it.title })
        assertTrue(brief.asParagraph().contains("行情") || brief.asParagraph().contains("腾讯"))
    }

    @Test
    fun flowSignalUsesMainForceWhenPresent() {
        val flow = CapitalFlowData(
            code = "00700",
            date = "2026-09-11",
            flows = listOf(
                FlowItemData("超大单", 1.0, 0.1),
                FlowItemData("主力", 12_000_000.0, 3.2),
            ),
        )
        val signal = DetailInsightBrief.flowSignal(flow)
        assertEquals("资金", signal?.title)
        assertTrue(signal?.reading?.contains("主力") == true)
        assertEquals(TagTone.POSITIVE, signal?.tone)
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
            turnover = 8_000_000_000.0,
            updateTime = "09-13 16:00",
            tradingStatus = "港股已收盘",
        )
    }

    private fun analysis(score: Int, rsi: Double?): TechnicalAnalysis = TechnicalAnalysis(
        ma5 = 420.0,
        ma10 = 418.0,
        ma20 = 410.0,
        ma60 = 400.0,
        rsi14 = rsi,
        support = 411.0,
        resistance = 430.8,
        shortTermTrend = TrendBias.BULLISH,
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
