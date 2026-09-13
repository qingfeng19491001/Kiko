package com.kuikly.stockchat.ui.components.charts

import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.chat.ChartHitIndex
import com.kuikly.stockchat.domain.chat.ChartSeries
import com.kuikly.stockchat.domain.chat.SeriesChartKind
import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * 迷你走势图：一条平滑折线 + 渐变填充，用于行情卡片右侧。
 */
fun ViewContainer<*, *>.SparklineChart(
    values: List<Double>,
    width: Float,
    height: Float,
    color: Color,
    fillAlphaColor: Color,
) {
    Canvas({
        attr { size(width, height) }
    }) { ctx, w, h ->
        ChartPainter.sparkline(ctx, values, w, h, color, fillAlphaColor)
    }
}

/**
 * 分时图：价格折线（相对昨收着色）+ 昨收虚线 + 底部成交量柱。
 */
fun ViewContainer<*, *>.IntradayChart(
    series: IntradaySeries,
    width: Float,
    height: Float,
) {
    Canvas({
        attr { size(width, height) }
    }) { ctx, w, h ->
        ChartPainter.intraday(ctx, series, w, h)
    }
}

/**
 * K 线图：蜡烛 + MA5 / MA10 / MA20 + 成交量 + 价格刻度。
 */
fun ViewContainer<*, *>.CandleChart(
    bars: List<KLineBar>,
    width: Float,
    height: Float,
    showVolume: Boolean = true,
    showMa: Boolean = true,
    showAxis: Boolean = true,
    selectedIndex: Int = -1,
    onSelectIndex: ((Int) -> Unit)? = null,
) {
    Canvas({
        attr { size(width, height) }
        event {
            onSelectIndex?.let { handler -> bindChartHit(bars.size, width, 46f, handler) }
        }
    }) { ctx, w, h ->
        ChartPainter.candles(ctx, bars, w, h, showVolume, showMa, showAxis, selectedIndex)
    }
}

/**
 * 柱状图：支持正负值，涨跌着色。
 */
fun ViewContainer<*, *>.BarChart(
    entries: List<com.kuikly.stockchat.domain.chat.BarEntry>,
    width: Float,
    height: Float,
    unit: String = "",
    selectedIndex: Int = -1,
    onSelectIndex: ((Int) -> Unit)? = null,
) {
    Canvas({
        attr { size(width, height) }
        event {
            onSelectIndex?.let { handler -> bindChartHit(entries.size, width, 44f, handler) }
        }
    }) { ctx, w, h ->
        ChartPainter.bar(ctx, entries, w, h, unit, selectedIndex)
    }
}

fun ViewContainer<*, *>.SeriesChart(
    kind: SeriesChartKind,
    categories: List<String>,
    series: List<ChartSeries>,
    width: Float,
    height: Float,
    unit: String = "",
    selectedIndex: Int = -1,
    onSelectIndex: ((Int) -> Unit)? = null,
) {
    Canvas({
        attr { size(width, height) }
        event {
            onSelectIndex?.let { handler -> bindChartHit(categories.size, width, 46f, handler) }
        }
    }) { ctx, w, h ->
        when (kind) {
            SeriesChartKind.LINE -> ChartPainter.multiLine(ctx, categories, series, w, h, unit, selectedIndex)
            SeriesChartKind.GROUPED_BAR -> ChartPainter.groupedBar(ctx, categories, series, w, h, unit, selectedIndex)
        }
    }
}

private fun Event.bindChartHit(count: Int, width: Float, rightPad: Float, onSelect: (Int) -> Unit) {
    fun hit(x: Float) {
        val index = ChartHitIndex.at(x, count, width, rightPad)
        if (index >= 0) onSelect(index)
    }
    click { hit(it.x) }
    pan { hit(it.x) }
}

/**
 * 仪表盘：半圆弧 + 中心数值 + 标签。
 */
fun ViewContainer<*, *>.GaugeChart(
    value: Float,
    max: Float,
    width: Float,
    height: Float,
    label: String,
) {
    Canvas({
        attr { size(width, height) }
    }) { ctx, w, h ->
        ChartPainter.gauge(ctx, value, max, w, h, label)
    }
}

object ChartPainter {

    private val axisText = Color(0xFF9CA3AFL)
    private val gridLine = Color(0xFFEEF1F5L)
    private val ma5Color = Color(0xFFF59E0BL)
    private val ma10Color = Color(0xFF334155L)
    private val ma20Color = Color(0xFF7C3AEDL)

    fun bar(
        ctx: CanvasContext,
        entries: List<com.kuikly.stockchat.domain.chat.BarEntry>,
        w: Float,
        h: Float,
        unit: String = "",
        selectedIndex: Int = -1,
    ) {
        if (entries.isEmpty()) return
        val rightPad = 44f
        val topPad = 4f
        val bottomPad = 24f
        val plotW = w - rightPad
        val plotH = h - topPad - bottomPad

        val maxAbs = entries.maxOf { kotlin.math.abs(it.value) }.let { if (it <= 0) 1.0 else it } * 1.1
        val zeroY = topPad + (plotH * 0.5f).toFloat()
        val slot = plotW / entries.size
        val barW = max(2f, slot * 0.56f)

        // 网格 + 刻度
        ctx.lineWidth(0.8f)
        ctx.strokeStyle(gridLine)
        ctx.font(10f, "")
        ctx.textAlign(TextAlign.LEFT)
        for (i in 0..4) {
            val y = topPad + plotH * i / 4
            ctx.beginPath(); ctx.moveTo(0f, y); ctx.lineTo(plotW, y); ctx.stroke()
            val valAtY = maxAbs * (1 - 2.0 * i / 4)
            ctx.fillStyle(axisText)
            ctx.fillText(NumberFormat.compact(valAtY), plotW + 4f, y + (if (i == 0) 10f else if (i == 4) -2f else 4f))
        }

        // 零轴加粗
        ctx.strokeStyle(Color(0xFFCCD2DEL))
        ctx.lineWidth(1f)
        ctx.beginPath(); ctx.moveTo(0f, zeroY); ctx.lineTo(plotW, zeroY); ctx.stroke()

        // 柱体
        entries.forEachIndexed { i, entry ->
            val color = when (entry.color) {
                com.kuikly.stockchat.domain.chat.BarColor.UP -> AppTheme.up
                com.kuikly.stockchat.domain.chat.BarColor.DOWN -> AppTheme.down
                com.kuikly.stockchat.domain.chat.BarColor.NEUTRAL -> AppTheme.accent
            }
            val x = slot * i + slot / 2 - barW / 2
            val barH = (kotlin.math.abs(entry.value) / maxAbs * plotH / 2).toFloat()
            ctx.fillStyle(color)
            if (entry.value >= 0) {
                fillRect(ctx, x, zeroY - barH, barW, barH)
            } else {
                fillRect(ctx, x, zeroY, barW, barH)
            }
        }

        if (selectedIndex in entries.indices) {
            val x = slot * selectedIndex + slot / 2
            ctx.strokeStyle(Color(0x66191919L))
            ctx.lineWidth(1f)
            ctx.setLineDash(listOf(3f, 3f))
            ctx.beginPath(); ctx.moveTo(x, topPad); ctx.lineTo(x, topPad + plotH); ctx.stroke()
            ctx.setLineDash(emptyList())
        }

        // X 轴标签
        ctx.fillStyle(axisText)
        ctx.textAlign(TextAlign.CENTER)
        entries.forEachIndexed { i, entry ->
            val x = slot * i + slot / 2
            ctx.fillText(entry.label, x, h - 8f)
        }
    }

    fun gauge(
        ctx: CanvasContext,
        value: Float,
        max: Float,
        w: Float,
        h: Float,
        label: String,
    ) {
        val pct = (value / max).coerceIn(0f, 1f)
        val cx = w / 2
        val cy = h * 0.72f
        val radius = min(w, h) * 0.36f
        val startAngle = (PI).toFloat()
        val endAngle = (2 * PI).toFloat()

        // 背景弧
        ctx.strokeStyle(AppTheme.surfaceMuted)
        ctx.lineWidth(radius * 0.28f)
        ctx.lineCapRound()
        ctx.beginPath()
        ctx.arc(cx, cy, radius, startAngle, endAngle, false)
        ctx.stroke()

        // 值弧
        val valEnd = startAngle + pct * (endAngle - startAngle)
        val arcColor = when {
            pct >= 0.65f -> AppTheme.up
            pct <= 0.35f -> AppTheme.down
            else -> AppTheme.warning
        }
        ctx.strokeStyle(arcColor)
        ctx.beginPath()
        ctx.arc(cx, cy, radius, startAngle, valEnd, false)
        ctx.stroke()

        // 中心数值
        ctx.fillStyle(AppTheme.textPrimary)
        ctx.font(28f, "bold")
        ctx.textAlign(TextAlign.CENTER)
        ctx.fillText(NumberFormat.fixed(value.toDouble(), 0), cx, cy - 2f)

        // 标签
        ctx.fillStyle(axisText)
        ctx.font(11f, "")
        ctx.fillText(label, cx, cy + 16f)
    }

    fun sparkline(ctx: CanvasContext, values: List<Double>, w: Float, h: Float, color: Color, fill: Color) {
        if (values.size < 2) return
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val padY = 2f
        val stepX = w / (values.size - 1)
        fun px(i: Int) = i * stepX
        fun py(v: Double) = (h - padY) - ((v - minV) / range * (h - padY * 2)).toFloat()

        // 填充
        ctx.beginPath()
        ctx.moveTo(0f, h)
        values.forEachIndexed { i, v -> ctx.lineTo(px(i), py(v)) }
        ctx.lineTo(w, h)
        ctx.closePath()
        val gradient = ctx.createLinearGradient(0f, 0f, 0f, h)
        gradient.addColorStop(0f, fill)
        gradient.addColorStop(1f, Color(0x00FFFFFFL))
        ctx.fillStyle(gradient)
        ctx.fill()

        // 折线
        ctx.beginPath()
        ctx.strokeStyle(color)
        ctx.lineWidth(1.6f)
        ctx.lineCapRound()
        values.forEachIndexed { i, v -> if (i == 0) ctx.moveTo(px(i), py(v)) else ctx.lineTo(px(i), py(v)) }
        ctx.stroke()
    }

    fun intraday(ctx: CanvasContext, series: IntradaySeries, w: Float, h: Float) {
        val ticks = series.ticks
        if (ticks.size < 2) return
        val volumeH = h * 0.22f
        val priceH = h - volumeH - 18f
        val leftPad = 0f
        val rightPad = 44f
        val plotW = w - leftPad - rightPad

        val prices = ticks.map { it.price }
        val prev = series.prevClose.takeIf { it > 0 } ?: prices.first()
        val maxDev = max(prices.max() - prev, prev - prices.min()).let { if (it <= 0) prev * 0.005 else it } * 1.08
        val top = prev + maxDev
        val bottom = prev - maxDev
        fun py(v: Double) = 4f + ((top - v) / (top - bottom) * (priceH - 8f)).toFloat()
        val totalSlots = max(ticks.size, expectedSlots(ticks.size))
        fun px(i: Int) = leftPad + plotW * i / (totalSlots - 1)

        // 网格
        ctx.strokeStyle(gridLine)
        ctx.lineWidth(0.8f)
        for (i in 0..4) {
            val y = 4f + (priceH - 8f) * i / 4
            ctx.beginPath(); ctx.moveTo(leftPad, y); ctx.lineTo(leftPad + plotW, y); ctx.stroke()
        }
        // 昨收虚线
        ctx.setLineDash(listOf(4f, 4f))
        ctx.strokeStyle(Color(0xFFB0B7C3L))
        ctx.beginPath(); ctx.moveTo(leftPad, py(prev)); ctx.lineTo(leftPad + plotW, py(prev)); ctx.stroke()
        ctx.setLineDash(emptyList())

        val last = prices.last()
        val lineColor = AppTheme.changeColor(last - prev)
        val fillColor = if (last >= prev) Color(0x3316A34AL) else Color(0x33E5484DL)

        // 填充
        ctx.beginPath()
        ctx.moveTo(px(0), py(prev))
        ticks.forEachIndexed { i, t -> ctx.lineTo(px(i), py(t.price)) }
        ctx.lineTo(px(ticks.size - 1), py(prev))
        ctx.closePath()
        val g = ctx.createLinearGradient(0f, 0f, 0f, priceH)
        g.addColorStop(0f, fillColor)
        g.addColorStop(1f, Color(0x00FFFFFFL))
        ctx.fillStyle(g)
        ctx.fill()

        // 折线
        ctx.beginPath()
        ctx.strokeStyle(lineColor)
        ctx.lineWidth(1.6f)
        ticks.forEachIndexed { i, t -> if (i == 0) ctx.moveTo(px(i), py(t.price)) else ctx.lineTo(px(i), py(t.price)) }
        ctx.stroke()

        // 右侧价格刻度
        ctx.font(10f, "")
        ctx.textAlign(TextAlign.LEFT)
        ctx.fillStyle(axisText)
        ctx.fillText(NumberFormat.price(top), leftPad + plotW + 4f, 12f)
        ctx.fillText(NumberFormat.price(bottom), leftPad + plotW + 4f, priceH - 2f)
        ctx.fillStyle(lineColor)
        ctx.fillText(NumberFormat.signedPct((maxDev) / prev * 100), leftPad + plotW + 4f, py(prev) - 12f)
        ctx.fillStyle(axisText)
        ctx.fillText(NumberFormat.price(prev), leftPad + plotW + 4f, py(prev) + 4f)

        // 时间轴
        ctx.textAlign(TextAlign.LEFT)
        ctx.fillText(ticks.first().time, leftPad, priceH + 13f)
        ctx.textAlign(TextAlign.RIGHT)
        ctx.fillText(ticks.last().time, leftPad + plotW, priceH + 13f)

        // 成交量（增量）
        val volumes = ticks.mapIndexed { i, t -> if (i == 0) t.volume else max(0.0, t.volume - ticks[i - 1].volume) }
        val maxVol = volumes.max().takeIf { it > 0 } ?: 1.0
        val barW = max(1f, plotW / totalSlots * 0.7f)
        val volTop = priceH + 18f
        ticks.forEachIndexed { i, t ->
            val up = if (i == 0) t.price >= prev else t.price >= ticks[i - 1].price
            ctx.fillStyle(if (up) Color(0x9916A34AL) else Color(0x99E5484DL))
            val bh = (volumes[i] / maxVol * (volumeH - 2f)).toFloat()
            fillRect(ctx, px(i) - barW / 2, volTop + volumeH - bh, barW, bh)
        }
    }

    /** 港股 331 / A 股 242 / 美股 391 个分时点 */
    private fun expectedSlots(size: Int): Int = when {
        size <= 242 -> 242
        size <= 331 -> 331
        else -> 391
    }

    fun candles(
        ctx: CanvasContext,
        bars: List<KLineBar>,
        w: Float,
        h: Float,
        showVolume: Boolean,
        showMa: Boolean,
        showAxis: Boolean,
        selectedIndex: Int = -1,
    ) {
        if (bars.isEmpty()) return
        val rightPad = if (showAxis) 46f else 0f
        val topPad = if (showMa) 16f else 4f
        val volumeH = if (showVolume) h * 0.2f else 0f
        val axisH = if (showAxis) 16f else 0f
        val priceH = h - volumeH - axisH - topPad
        val plotW = w - rightPad
        val count = bars.size
        val slot = plotW / count
        val bodyW = max(1.5f, slot * 0.62f)

        val closes = bars.map { it.close }
        val ma5 = if (showMa) AnalysisEngine.smaSeries(closes, 5) else emptyList()
        val ma10 = if (showMa) AnalysisEngine.smaSeries(closes, 10) else emptyList()
        val ma20 = if (showMa) AnalysisEngine.smaSeries(closes, 20) else emptyList()

        var maxP = bars.maxOf { it.high }
        var minP = bars.minOf { it.low }
        listOf(ma5, ma10, ma20).forEach { series -> series.filterNotNull().forEach { maxP = max(maxP, it); minP = min(minP, it) } }
        val range = (maxP - minP).takeIf { it > 0 } ?: maxP * 0.02
        fun py(v: Double) = topPad + ((maxP - v) / range * priceH).toFloat()
        fun cx(i: Int) = slot * i + slot / 2

        // 网格 + 刻度
        ctx.lineWidth(0.8f)
        ctx.strokeStyle(gridLine)
        ctx.font(10f, "")
        ctx.textAlign(TextAlign.LEFT)
        for (i in 0..4) {
            val y = topPad + priceH * i / 4
            ctx.beginPath(); ctx.moveTo(0f, y); ctx.lineTo(plotW, y); ctx.stroke()
            if (showAxis) {
                ctx.fillStyle(axisText)
                val value = maxP - range * i / 4
                ctx.fillText(NumberFormat.price(value), plotW + 4f, y + (if (i == 0) 10f else if (i == 4) -2f else 4f))
            }
        }

        // 蜡烛
        bars.forEachIndexed { i, bar ->
            val color = if (bar.isUp) AppTheme.up else AppTheme.down
            ctx.strokeStyle(color)
            ctx.fillStyle(color)
            ctx.lineWidth(1f)
            val x = cx(i)
            ctx.beginPath(); ctx.moveTo(x, py(bar.high)); ctx.lineTo(x, py(bar.low)); ctx.stroke()
            val top = py(max(bar.open, bar.close))
            val bottom = py(min(bar.open, bar.close))
            val bodyH = max(1f, bottom - top)
            fillRect(ctx, x - bodyW / 2, top, bodyW, bodyH)
        }

        if (selectedIndex in bars.indices) {
            val x = cx(selectedIndex)
            ctx.strokeStyle(Color(0x66191919L))
            ctx.lineWidth(1f)
            ctx.setLineDash(listOf(3f, 3f))
            ctx.beginPath()
            ctx.moveTo(x, topPad)
            ctx.lineTo(x, topPad + priceH)
            ctx.stroke()
            ctx.setLineDash(emptyList())
        }

        // 均线
        if (showMa) {
            drawSeries(ctx, ma5, ma5Color, ::cx, ::py)
            drawSeries(ctx, ma10, ma10Color, ::cx, ::py)
            drawSeries(ctx, ma20, ma20Color, ::cx, ::py)
            ctx.font(10f, "")
            ctx.textAlign(TextAlign.LEFT)
            var x = 2f
            listOf(
                Triple("MA5", ma5.lastOrNull(), ma5Color),
                Triple("MA10", ma10.lastOrNull(), ma10Color),
                Triple("MA20", ma20.lastOrNull(), ma20Color),
            ).forEach { (label, value, color) ->
                ctx.fillStyle(color)
                val text = "$label ${NumberFormat.price(value)}"
                ctx.fillText(text, x, 11f)
                x += ctx.measureText(text).width + 10f
            }
        }

        // 成交量
        if (showVolume) {
            val maxVol = bars.maxOf { it.volume }.takeIf { it > 0 } ?: 1.0
            val volTop = topPad + priceH + 6f
            bars.forEachIndexed { i, bar ->
                ctx.fillStyle(if (bar.isUp) Color(0x9916A34AL) else Color(0x99E5484DL))
                val bh = (bar.volume / maxVol * (volumeH - 6f)).toFloat()
                fillRect(ctx, cx(i) - bodyW / 2, volTop + (volumeH - 6f) - bh, bodyW, bh)
            }
        }

        // 时间轴
        if (showAxis) {
            ctx.fillStyle(axisText)
            ctx.font(10f, "")
            val y = h - 3f
            ctx.textAlign(TextAlign.LEFT)
            ctx.fillText(shortDate(bars.first().date), 0f, y)
            if (count > 2) {
                ctx.textAlign(TextAlign.CENTER)
                ctx.fillText(shortDate(bars[count / 2].date), plotW / 2, y)
            }
            ctx.textAlign(TextAlign.RIGHT)
            ctx.fillText(shortDate(bars.last().date), plotW, y)
        }
    }

    fun multiLine(
        ctx: CanvasContext,
        categories: List<String>,
        series: List<ChartSeries>,
        w: Float,
        h: Float,
        unit: String,
        selectedIndex: Int,
    ) {
        if (categories.isEmpty() || series.isEmpty()) return
        val rightPad = 46f
        val topPad = 16f
        val bottomPad = 22f
        val plotW = w - rightPad
        val plotH = h - topPad - bottomPad
        val values = series.flatMap { it.values }.filterNotNull()
        if (values.isEmpty()) return
        var maxV = values.max()
        var minV = values.min()
        if (maxV == minV) {
            maxV += 1
            minV -= 1
        }
        val range = maxV - minV
        val slot = plotW / categories.size
        fun cx(i: Int) = slot * i + slot / 2
        fun py(v: Double) = topPad + ((maxV - v) / range * plotH).toFloat()

        ctx.lineWidth(0.8f)
        ctx.strokeStyle(gridLine)
        ctx.font(10f, "")
        ctx.textAlign(TextAlign.LEFT)
        for (i in 0..4) {
            val y = topPad + plotH * i / 4
            ctx.beginPath(); ctx.moveTo(0f, y); ctx.lineTo(plotW, y); ctx.stroke()
            ctx.fillStyle(axisText)
            val valAtY = maxV - range * i / 4
            ctx.fillText(NumberFormat.fixed(valAtY, 1) + unit, plotW + 4f, y + (if (i == 0) 10f else if (i == 4) -2f else 4f))
        }

        series.forEach { line ->
            drawSeries(ctx, line.values, Color(line.colorArgb), ::cx, ::py)
        }

        if (selectedIndex in categories.indices) {
            val x = cx(selectedIndex)
            ctx.strokeStyle(Color(0x66191919L))
            ctx.lineWidth(1f)
            ctx.setLineDash(listOf(3f, 3f))
            ctx.beginPath(); ctx.moveTo(x, topPad); ctx.lineTo(x, topPad + plotH); ctx.stroke()
            ctx.setLineDash(emptyList())
            series.forEach { line ->
                val v = line.values.getOrNull(selectedIndex) ?: return@forEach
                ctx.fillStyle(Color(line.colorArgb))
                val px = cx(selectedIndex)
                val y = py(v)
                ctx.beginPath()
                ctx.arc(px, y, 3.5f, 0f, (2 * PI).toFloat(), false)
                ctx.fill()
            }
        }

        ctx.fillStyle(axisText)
        ctx.font(10f, "")
        ctx.textAlign(TextAlign.LEFT)
        ctx.fillText(categories.first(), 0f, h - 6f)
        if (categories.size > 2) {
            ctx.textAlign(TextAlign.CENTER)
            ctx.fillText(categories[categories.size / 2], plotW / 2, h - 6f)
        }
        ctx.textAlign(TextAlign.RIGHT)
        ctx.fillText(categories.last(), plotW, h - 6f)
    }

    fun groupedBar(
        ctx: CanvasContext,
        categories: List<String>,
        series: List<ChartSeries>,
        w: Float,
        h: Float,
        unit: String,
        selectedIndex: Int,
    ) {
        if (categories.isEmpty() || series.isEmpty()) return
        val rightPad = 46f
        val topPad = 8f
        val bottomPad = 22f
        val plotW = w - rightPad
        val plotH = h - topPad - bottomPad
        val values = series.flatMap { it.values }.filterNotNull()
        if (values.isEmpty()) return
        val maxAbs = values.maxOf { kotlin.math.abs(it) }.let { if (it <= 0) 1.0 else it } * 1.1
        val zeroY = topPad + (plotH * 0.5f)
        val slot = plotW / categories.size
        val groupW = slot * 0.78f
        val barW = max(2f, groupW / series.size)

        ctx.lineWidth(0.8f)
        ctx.strokeStyle(gridLine)
        ctx.font(10f, "")
        ctx.textAlign(TextAlign.LEFT)
        for (i in 0..4) {
            val y = topPad + plotH * i / 4
            ctx.beginPath(); ctx.moveTo(0f, y); ctx.lineTo(plotW, y); ctx.stroke()
            ctx.fillStyle(axisText)
            val valAtY = maxAbs * (1 - 2.0 * i / 4)
            ctx.fillText(NumberFormat.fixed(valAtY, 1) + unit, plotW + 4f, y + (if (i == 0) 10f else if (i == 4) -2f else 4f))
        }
        ctx.strokeStyle(Color(0xFFCCD2DEL))
        ctx.lineWidth(1f)
        ctx.beginPath(); ctx.moveTo(0f, zeroY); ctx.lineTo(plotW, zeroY); ctx.stroke()

        categories.forEachIndexed { i, _ ->
            series.forEachIndexed { j, line ->
                val v = line.values.getOrNull(i) ?: return@forEachIndexed
                val x = slot * i + (slot - groupW) / 2 + j * barW
                val barH = (kotlin.math.abs(v) / maxAbs * plotH / 2).toFloat()
                ctx.fillStyle(Color(line.colorArgb))
                if (v >= 0) fillRect(ctx, x, zeroY - barH, barW - 1f, barH)
                else fillRect(ctx, x, zeroY, barW - 1f, barH)
            }
        }

        if (selectedIndex in categories.indices) {
            val x = slot * selectedIndex + slot / 2
            ctx.strokeStyle(Color(0x66191919L))
            ctx.lineWidth(1f)
            ctx.setLineDash(listOf(3f, 3f))
            ctx.beginPath(); ctx.moveTo(x, topPad); ctx.lineTo(x, topPad + plotH); ctx.stroke()
            ctx.setLineDash(emptyList())
        }

        ctx.fillStyle(axisText)
        ctx.textAlign(TextAlign.CENTER)
        categories.forEachIndexed { i, label ->
            ctx.fillText(label, slot * i + slot / 2, h - 6f)
        }
    }

    private fun drawSeries(ctx: CanvasContext, series: List<Double?>, color: Color, cx: (Int) -> Float, py: (Double) -> Float) {
        ctx.beginPath()
        ctx.strokeStyle(color)
        ctx.lineWidth(1.2f)
        var started = false
        series.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            if (!started) { ctx.moveTo(cx(i), py(v)); started = true } else ctx.lineTo(cx(i), py(v))
        }
        if (started) ctx.stroke()
    }

    private fun fillRect(ctx: CanvasContext, x: Float, y: Float, w: Float, h: Float) {
        ctx.beginPath()
        ctx.moveTo(x, y); ctx.lineTo(x + w, y); ctx.lineTo(x + w, y + h); ctx.lineTo(x, y + h)
        ctx.closePath()
        ctx.fill()
    }

    /** 2024-06-21 → 06-21；2024-06 → 24-06 */
    private fun shortDate(date: String): String {
        val cleaned = date.replace('/', '-')
        return when {
            cleaned.length >= 10 -> cleaned.substring(5, 10)
            cleaned.length >= 7 -> cleaned.substring(2, 7)
            else -> cleaned
        }
    }
}
