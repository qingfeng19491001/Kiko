package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerComposerLayoutTest {

    @Test
    fun trendPutsCalloutBeforeChartAndSkipsValuation() {
        val answer = AnswerComposer.compose(
            ParsedIntent(Intent.TREND, listOf(StockCatalog.tencent), "腾讯控股后市会怎么走"),
            listOf(snapshot(StockCatalog.tencent, 400.0, 1.2)),
        )
        val types = answer.blocks.map { it::class.simpleName }
        assertEquals("SummaryCallout", types.first())
        assertTrue(types.contains("ChartCard"))
        assertTrue(types.indexOf("SummaryCallout") < types.indexOf("ChartCard"))
        assertTrue(answer.blocks.none { it is AnswerBlock.GaugeCard })
        assertTrue(answer.blocks.none { it is AnswerBlock.StockCard })
        val levels = answer.blocks.filterIsInstance<AnswerBlock.KeyLevelsCard>().single()
        assertEquals(listOf("压力", "支撑"), levels.levels.map { it.label })
        assertTrue(answer.blocks.filterIsInstance<AnswerBlock.FollowUps>().single().items.any { it.contains("这一根怎么看") || it.contains("对比") })
        assertTrue(answer.blocks.any { it is AnswerBlock.Tags })
    }

    @Test
    fun compareStartsWithConclusionThenShowsPeerEvidence() {
        val answer = AnswerComposer.compose(
            ParsedIntent(Intent.COMPARE, listOf(StockCatalog.tencent, StockCatalog.alibaba), "腾讯 vs 阿里"),
            listOf(
                snapshot(StockCatalog.tencent, 400.0, 1.2),
                snapshot(StockCatalog.alibaba, 80.0, -0.8),
            ),
        )
        val types = answer.blocks.map { it::class.simpleName }
        assertEquals("SectionHeader", types.first())
        assertEquals("同行格局", (answer.blocks.first() as AnswerBlock.SectionHeader).title)
        assertTrue(types.indexOf("SummaryCallout") < types.indexOf("PeerTableCard"))
        assertTrue(answer.blocks.none { it is AnswerBlock.StockCard })
        assertTrue(answer.blocks.any { it is AnswerBlock.SeriesChartCard })
        assertTrue(answer.blocks.any { it is AnswerBlock.HighlightsCard })
        val table = answer.blocks.filterIsInstance<AnswerBlock.PeerTableCard>().single()
        assertEquals(2, table.rows.size)
        assertTrue(table.rows.none { it.price == "--" })
    }

    @Test
    fun diagnosisKeepsTechnicalSectionExpanded() {
        val answer = AnswerComposer.compose(
            ParsedIntent(Intent.STOCK_ANALYSIS, listOf(StockCatalog.tencent), "腾讯控股全面分析一下"),
            listOf(snapshot(StockCatalog.tencent, 400.0, 0.5)),
        )
        val tech = answer.blocks.filterIsInstance<AnswerBlock.SectionHeader>().first { it.title == "技术面" }
        assertTrue(!tech.collapsedByDefault)
        assertTrue(answer.blocks.any { it is AnswerBlock.ChartCard })
        assertTrue(answer.blocks.any { it is AnswerBlock.BarChartCard })
        assertTrue(answer.blocks.any { it is AnswerBlock.Tags })
        val techMd = answer.blocks.filterIsInstance<AnswerBlock.Markdown>().joinToString("\n") { it.text }
        assertTrue(!techMd.contains("MA5"))
        assertTrue(!techMd.contains("RSI(14)"))
    }

    @Test
    fun diagnosisSkipsChartsWhenHistoryIsMock() {
        val mock = snapshot(StockCatalog.tencent, 400.0, 0.5).let { snap ->
            snap.copy(quote = snap.quote.copy(isMock = true))
        }
        val answer = AnswerComposer.compose(
            ParsedIntent(Intent.STOCK_ANALYSIS, listOf(StockCatalog.tencent), "腾讯控股全面分析一下"),
            listOf(mock),
        )
        assertTrue(answer.blocks.none { it is AnswerBlock.ChartCard })
        assertTrue(answer.blocks.none { it is AnswerBlock.BarChartCard })
        assertTrue(answer.blocks.none { it is AnswerBlock.StockCard })
        assertTrue(answer.blocks.none { it is AnswerBlock.GaugeCard })
    }

    @Test
    fun derivedLadderExplainsGatewayIsOffline() {
        val answer = AnswerComposer.compose(
            ParsedIntent(Intent.LIMIT_UP_LADDER, emptyList(), "连板梯队"),
            emptyList(),
        )
        val md = answer.blocks.filterIsInstance<AnswerBlock.Markdown>().joinToString("\n") { it.text }
        assertTrue(md.contains("当前未接入"))
        assertTrue(!md.contains("ak_gateway"))
        assertTrue(answer.blocks.any { it is AnswerBlock.FollowUps })
    }

    @Test
    fun chartHitIndexMapsLeftEdgeToFirstBar() {
        assertEquals(0, ChartHitIndex.at(0f, 10, 200f, 0f))
        assertEquals(9, ChartHitIndex.at(199f, 10, 200f, 0f))
    }
}

private fun snapshot(instrument: Instrument, price: Double, changePct: Double): MarketSnapshot {
    val bars = (0 until 60).map { i ->
        val month = 1 + i / 28
        val day = 1 + i % 28
        val close = price - 6 + i * 0.2
        KLineBar(
            date = "2026-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
            open = close - 0.3,
            close = close,
            high = close + 0.5,
            low = close - 0.6,
            volume = 1_000_000.0 + i,
        )
    }
    return MarketSnapshot(
        quote = Quote(
            instrument = instrument,
            price = price,
            prevClose = price / (1 + changePct / 100),
            open = price,
            high = price + 2,
            low = price - 2,
            change = price - price / (1 + changePct / 100),
            changePct = changePct,
            volume = 2e7,
            turnover = 8e9,
            pe = 18.0 + changePct,
            pb = 4.0,
            marketCap = 3_000.0,
            totalMarketCap = 3_200.0,
            updateTime = "09-10 16:00",
            tradingStatus = "已收盘",
            isMock = false,
        ),
        dailyBars = bars,
        intraday = null,
    )
}
