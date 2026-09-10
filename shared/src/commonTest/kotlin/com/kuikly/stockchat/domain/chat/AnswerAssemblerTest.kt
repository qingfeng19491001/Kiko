package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.Quote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerAssemblerTest {

    private val quote = Quote(
        instrument = Instrument(code = "600519", name = "贵州茅台", market = Market.SH, sector = "白酒"),
        price = 1488.0,
        prevClose = 1476.0,
        open = 1476.0,
        high = 1495.0,
        low = 1472.0,
        change = 12.0,
        changePct = 0.81,
        volume = 3.2e6,
        turnover = 4.8e9,
        pe = 28.0,
        pb = 9.1,
        high52w = 1800.0,
        low52w = 1200.0,
        updateTime = "09-10 15:00",
        tradingStatus = "已收盘",
        isMock = false,
    )

    @Test
    fun merge_interleaves_model_text_before_matching_cards() {
        val base = AiAnswer(
            intent = Intent.STOCK_ANALYSIS,
            blocks = listOf(
                AnswerBlock.Markdown("本地开场"),
                AnswerBlock.SectionHeader(1, "盘面概览"),
                AnswerBlock.Markdown("本地盘面"),
                AnswerBlock.StockCard(quote, emptyList(), null),
                AnswerBlock.SectionHeader(2, "技术面"),
                AnswerBlock.Markdown("本地技术"),
                AnswerBlock.KeyLevelsCard("关键价位", listOf(KeyLevel("支撑", "10.0", "近低", TagTone.POSITIVE))),
                AnswerBlock.SectionHeader(3, "估值与规模"),
                AnswerBlock.Markdown("本地估值"),
                AnswerBlock.SectionHeader(4, "核心结论"),
                AnswerBlock.SummaryCallout("本地结论框"),
                AnswerBlock.Risk("风险提示", "免责"),
                AnswerBlock.FollowUps(listOf("追问")),
            ),
        )
        val merged = AnswerAssembler.merge(
            base,
            """
            ## 盘面概览
            今天缩量整理。
            ## 技术面
            站上五日均线。
            ## 核心结论
            短线观察箱体上沿。
            """.trimIndent(),
        )

        val types = merged.blocks.map { it::class.simpleName }
        assertEquals(
            listOf(
                "SectionHeader",
                "Markdown",
                "StockCard",
                "SectionHeader",
                "Markdown",
                "KeyLevelsCard",
                "SectionHeader",
                "Markdown",
                "SummaryCallout",
                "Risk",
                "FollowUps",
            ),
            types,
        )
        val markdowns = merged.blocks.filterIsInstance<AnswerBlock.Markdown>().map { it.text }
        assertEquals(listOf("今天缩量整理。", "站上五日均线。", "短线观察箱体上沿。"), markdowns)
        assertEquals(1488.0, merged.blocks.filterIsInstance<AnswerBlock.StockCard>().single().quote.price)
        assertTrue(!merged.blocks.filterIsInstance<AnswerBlock.StockCard>().single().quote.isMock)
    }

    @Test
    fun parse_splits_hash_and_numbered_headings() {
        val parsed = AnswerAssembler.parseModelSections(
            """
            先看盘面。
            ## 技术面
            均线多头。
            2. 核心结论
            继续持有。
            """.trimIndent(),
        )
        assertEquals("先看盘面。", parsed.sections[SectionKey.Preamble])
        assertEquals("均线多头。", parsed.sections[SectionKey.Technical])
        assertEquals("继续持有。", parsed.sections[SectionKey.Conclusion])
    }

    @Test
    fun unsectioned_model_text_lands_in_overview() {
        val base = AiAnswer(
            intent = Intent.STOCK_ANALYSIS,
            blocks = listOf(
                AnswerBlock.Markdown("本地开场"),
                AnswerBlock.SectionHeader(1, "盘面概览"),
                AnswerBlock.Markdown("本地盘面"),
                AnswerBlock.StockCard(quote, emptyList(), null),
                AnswerBlock.FollowUps(listOf("追问")),
            ),
        )
        val merged = AnswerAssembler.merge(base, "整段没有标题的模型分析。")
        val markdowns = merged.blocks.filterIsInstance<AnswerBlock.Markdown>().map { it.text }
        assertEquals(listOf("整段没有标题的模型分析。"), markdowns)
        assertEquals("StockCard", merged.blocks[2]::class.simpleName)
    }

    @Test
    fun merge_keeps_local_answer_when_model_blank() {
        val base = AiAnswer(Intent.UNKNOWN, listOf(AnswerBlock.Markdown("本地")))
        assertEquals(base, AnswerAssembler.merge(base, "   "))
    }

    @Test
    fun failure_notice_keeps_local_cards() {
        val base = AiAnswer(
            Intent.STOCK_ANALYSIS,
            listOf(AnswerBlock.StockCard(quote, emptyList(), null), AnswerBlock.Markdown("本地盘面")),
        )
        val failed = AnswerAssembler.withFailureNotice(base, "401 unauthorized")
        assertEquals("Markdown", failed.blocks.first()::class.simpleName)
        assertTrue((failed.blocks.first() as AnswerBlock.Markdown).text.contains("401"))
        assertEquals(3, failed.blocks.size)
    }
}
