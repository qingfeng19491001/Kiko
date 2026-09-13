package com.kuikly.stockchat.data.chat

import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.ChartSeries
import com.kuikly.stockchat.domain.chat.PeerRow
import com.kuikly.stockchat.domain.chat.SeriesChartKind
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatCodecAnswerBlockTest {

    @Test
    fun peerTableAndSeriesRoundTrip() {
        val table = AnswerBlock.PeerTableCard(
            title = "同业",
            rows = listOf(
                PeerRow(StockCatalog.tencent.key, "腾讯控股", "400.00", "+1.20%", 1.2, "8.0亿", "18.00", "3.2万亿"),
            ),
        )
        val series = AnswerBlock.SeriesChartCard(
            title = "相对走势",
            subtitle = "100",
            unit = "",
            kind = SeriesChartKind.LINE,
            categories = listOf("01-01", "01-02"),
            series = listOf(
                ChartSeries("腾讯控股", StockCatalog.tencent.key, 0xFF2B6CF6, listOf<Double?>(100.0, 101.5)),
            ),
        )
        val highlights = AnswerBlock.HighlightsCard(items = listOf("今日腾讯更强"))
        val header = AnswerBlock.SectionHeader(2, "技术面", collapsedByDefault = true)

        fun round(block: AnswerBlock) = ChatCodec.decodeBlock(ChatCodec.encode(block))
        assertEquals(table, round(table))
        assertEquals(series, round(series))
        assertEquals(highlights, round(highlights))
        val decodedHeader = round(header) as AnswerBlock.SectionHeader
        assertTrue(decodedHeader.collapsedByDefault)
        assertEquals("技术面", decodedHeader.title)
        val expanded = round(AnswerBlock.SectionHeader(3, "阶段表现")) as AnswerBlock.SectionHeader
        assertTrue(!expanded.collapsedByDefault)
    }
}
