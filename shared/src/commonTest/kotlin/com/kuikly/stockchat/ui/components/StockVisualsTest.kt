package com.kuikly.stockchat.ui.stockadapter

import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.BarColor
import com.kuikly.stockchat.domain.chat.BarEntry
import com.kuikly.stockchat.domain.chat.ChartSeries
import com.kuikly.stockchat.domain.chat.PeerRow
import com.kuikly.stockchat.domain.chat.SeriesChartKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockVisualsTest {

    @Test
    fun peerRowsKeepClickableNameAndChangeColor() {
        val rows = peerTableRows(
            listOf(
                PeerRow("hk:00700", "腾讯控股", "400.00", "+1.20%", 1.2, "80亿", "18.0", "3.2万亿"),
                PeerRow("hk:09988", "阿里巴巴", "80.00", "-0.80%", -0.8, "20亿", "16.0", "1.6万亿"),
            ),
        )
        assertEquals(2, rows.size)
        assertEquals("hk:00700", rows[0].id)
        val name = rows[0].cells["name"] as StockCell
        assertEquals("腾讯控股", name.text)
        assertEquals(COLOR_ACCENT, name.colorArgb)
        val down = rows[1].cells["changePct"] as StockCell
        assertEquals(COLOR_DOWN, down.colorArgb)
        assertEquals(6, peerTableColumns().size)
    }

    @Test
    fun seriesChartMapsAlignedPoints() {
        val card = AnswerBlock.SeriesChartCard(
            title = "腾讯、阿里近 3 个交易日相对走势",
            unit = "",
            kind = SeriesChartKind.LINE,
            categories = listOf("09-01", "09-02", "09-03"),
            series = listOf(
                ChartSeries("腾讯", "hk:00700", 0xFF1677FF, listOf(100.0, 101.0, null)),
            ),
        )
        val series = seriesChartData(card)
        assertEquals(1, series.size)
        assertEquals(3, series[0].points.size)
        assertEquals(100f, series[0].points[0].y)
        assertTrue(series[0].points[2].y.isNaN())
    }

    @Test
    fun barChartUsesEntryColor() {
        val card = AnswerBlock.BarChartCard(
            title = "涨跌",
            bars = listOf(BarEntry("9月", -2.4, BarColor.DOWN)),
            unit = "%",
        )
        val series = barChartData(card)
        assertEquals(COLOR_DOWN, series[0].points[0].color)
    }
}
