package com.kuikly.stockchat.ui.components

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 应用内所有图标均由 Canvas 矢量绘制，避免引入字体图标 / 多倍图资源，三端表现一致。
 */
enum class IconKind {
    MENU, PLUS, SEND, ARROW_UP, BACK, CLOSE, CHEVRON_RIGHT, CHEVRON_DOWN,
    STAR, STAR_FILLED, SHARE, CLOCK, TRASH, SPARKLE, REFRESH, TREND_UP, ALERT, CHART, COMPARE, BOOK,
    STOP, WIFI_OFF, MESSAGE, CANDLE, SEARCH,
}

fun ViewContainer<*, *>.Icon(
    kind: IconKind,
    size: Float = 22f,
    color: Color = Color(0xFF111827L),
    strokeWidth: Float = 1.8f,
) {
    Canvas({
        attr { size(size, size) }
    }) { ctx, w, h ->
        IconPainter.draw(ctx, kind, w, h, color, strokeWidth)
    }
}

object IconPainter {

    fun draw(ctx: CanvasContext, kind: IconKind, w: Float, h: Float, color: Color, stroke: Float) {
        ctx.strokeStyle(color)
        ctx.fillStyle(color)
        ctx.lineWidth(stroke)
        ctx.lineCapRound()
        val s = minOf(w, h)
        val u = s / 24f // 以 24 网格为单位
        when (kind) {
            IconKind.CANDLE -> {
                // 两根蜡烛：左阴线（空心）右阳线（实心）
                line(ctx, 8 * u, 3 * u, 8 * u, 7 * u)
                line(ctx, 8 * u, 15 * u, 8 * u, 20 * u)
                rect(ctx, 5.5f * u, 7 * u, 5 * u, 8 * u)
                line(ctx, 16 * u, 4 * u, 16 * u, 9 * u)
                line(ctx, 16 * u, 17 * u, 16 * u, 21 * u)
                ctx.beginPath()
                ctx.moveTo(13.5f * u, 9 * u); ctx.lineTo(18.5f * u, 9 * u); ctx.lineTo(18.5f * u, 17 * u); ctx.lineTo(13.5f * u, 17 * u)
                ctx.closePath()
                ctx.fill()
            }
            IconKind.SEARCH -> {
                ctx.beginPath()
                ctx.arc(11 * u, 11 * u, 6 * u, 0f, 6.2832f, false)
                ctx.stroke()
                line(ctx, 15.5f * u, 15.5f * u, 20 * u, 20 * u)
            }
            IconKind.MENU -> {
                line(ctx, 4 * u, 7 * u, 20 * u, 7 * u)
                line(ctx, 4 * u, 12 * u, 20 * u, 12 * u)
                line(ctx, 4 * u, 17 * u, 20 * u, 17 * u)
            }
            IconKind.PLUS -> {
                line(ctx, 12 * u, 5 * u, 12 * u, 19 * u)
                line(ctx, 5 * u, 12 * u, 19 * u, 12 * u)
            }
            IconKind.ARROW_UP -> {
                line(ctx, 12 * u, 19 * u, 12 * u, 5.5f * u)
                path(ctx) {
                    moveTo(6 * u, 11.5f * u); lineTo(12 * u, 5.5f * u); lineTo(18 * u, 11.5f * u)
                }
            }
            IconKind.SEND -> {
                ctx.beginPath()
                ctx.moveTo(3.5f * u, 11 * u)
                ctx.lineTo(20.5f * u, 3.5f * u)
                ctx.lineTo(13 * u, 20.5f * u)
                ctx.lineTo(10.5f * u, 13.5f * u)
                ctx.closePath()
                ctx.fill()
            }
            IconKind.BACK -> path(ctx) {
                moveTo(15 * u, 5 * u); lineTo(8 * u, 12 * u); lineTo(15 * u, 19 * u)
            }
            IconKind.CHEVRON_RIGHT -> path(ctx) {
                moveTo(9.5f * u, 6 * u); lineTo(15.5f * u, 12 * u); lineTo(9.5f * u, 18 * u)
            }
            IconKind.CHEVRON_DOWN -> path(ctx) {
                moveTo(6 * u, 9.5f * u); lineTo(12 * u, 15.5f * u); lineTo(18 * u, 9.5f * u)
            }
            IconKind.CLOSE -> {
                line(ctx, 6 * u, 6 * u, 18 * u, 18 * u)
                line(ctx, 18 * u, 6 * u, 6 * u, 18 * u)
            }
            IconKind.STAR, IconKind.STAR_FILLED -> {
                ctx.beginPath()
                val cx = 12 * u
                val cy = 12.6f * u
                val outer = 9.2f * u
                val inner = 4.2f * u
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val angle = -PI / 2 + i * PI / 5
                    val x = cx + (r * cos(angle)).toFloat()
                    val y = cy + (r * sin(angle)).toFloat()
                    if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
                }
                ctx.closePath()
                if (kind == IconKind.STAR_FILLED) ctx.fill() else ctx.stroke()
            }
            IconKind.SHARE -> {
                circle(ctx, 18 * u, 5.5f * u, 2.4f * u, fill = false)
                circle(ctx, 6 * u, 12 * u, 2.4f * u, fill = false)
                circle(ctx, 18 * u, 18.5f * u, 2.4f * u, fill = false)
                line(ctx, 8.2f * u, 10.9f * u, 15.8f * u, 6.6f * u)
                line(ctx, 8.2f * u, 13.1f * u, 15.8f * u, 17.4f * u)
            }
            IconKind.CLOCK -> {
                circle(ctx, 12 * u, 12 * u, 8.5f * u, fill = false)
                path(ctx) { moveTo(12 * u, 7.5f * u); lineTo(12 * u, 12.3f * u); lineTo(15.5f * u, 14.3f * u) }
            }
            IconKind.TRASH -> {
                line(ctx, 4.5f * u, 7 * u, 19.5f * u, 7 * u)
                path(ctx) { moveTo(9.5f * u, 7 * u); lineTo(9.5f * u, 4.5f * u); lineTo(14.5f * u, 4.5f * u); lineTo(14.5f * u, 7 * u) }
                path(ctx) {
                    moveTo(6.5f * u, 7 * u); lineTo(7.3f * u, 19.5f * u); lineTo(16.7f * u, 19.5f * u); lineTo(17.5f * u, 7 * u)
                }
                line(ctx, 10.2f * u, 10.5f * u, 10.5f * u, 16.5f * u)
                line(ctx, 13.8f * u, 10.5f * u, 13.5f * u, 16.5f * u)
            }
            IconKind.SPARKLE -> {
                ctx.beginPath()
                star4(ctx, 11 * u, 13 * u, 8 * u, 2.2f * u)
                ctx.fill()
                ctx.beginPath()
                star4(ctx, 18.5f * u, 6 * u, 3.4f * u, 1f * u)
                ctx.fill()
            }
            IconKind.REFRESH -> {
                ctx.beginPath()
                ctx.arc(12 * u, 12 * u, 7.5f * u, (-PI / 2 + 0.55).toFloat(), (PI * 1.25).toFloat(), false)
                ctx.stroke()
                path(ctx) { moveTo(12.5f * u, 2.5f * u); lineTo(16 * u, 5.5f * u); lineTo(12.5f * u, 8.5f * u) }
            }
            IconKind.TREND_UP -> {
                path(ctx) {
                    moveTo(3.5f * u, 17 * u); lineTo(9 * u, 11.5f * u); lineTo(13 * u, 14.5f * u); lineTo(20.5f * u, 7 * u)
                }
                path(ctx) { moveTo(15.5f * u, 7 * u); lineTo(20.5f * u, 7 * u); lineTo(20.5f * u, 12 * u) }
            }
            IconKind.ALERT -> {
                path(ctx) { moveTo(12 * u, 3.5f * u); lineTo(21 * u, 19.5f * u); lineTo(3 * u, 19.5f * u); closePath() }
                line(ctx, 12 * u, 9.5f * u, 12 * u, 14 * u)
                circle(ctx, 12 * u, 16.8f * u, 0.9f * u, fill = true)
            }
            IconKind.CHART -> {
                line(ctx, 4 * u, 20 * u, 20 * u, 20 * u)
                rect(ctx, 6 * u, 12 * u, 3 * u, 8 * u)
                rect(ctx, 10.5f * u, 7 * u, 3 * u, 13 * u)
                rect(ctx, 15 * u, 10 * u, 3 * u, 10 * u)
            }
            IconKind.COMPARE -> {
                rect(ctx, 4 * u, 5 * u, 6.5f * u, 14 * u)
                rect(ctx, 13.5f * u, 5 * u, 6.5f * u, 14 * u)
                line(ctx, 7.2f * u, 9 * u, 7.2f * u, 15 * u)
                line(ctx, 16.8f * u, 9 * u, 16.8f * u, 15 * u)
            }
            IconKind.BOOK -> {
                path(ctx) {
                    moveTo(4.5f * u, 5 * u); lineTo(4.5f * u, 19.5f * u); lineTo(12 * u, 17.5f * u); lineTo(19.5f * u, 19.5f * u); lineTo(19.5f * u, 5 * u)
                    lineTo(12 * u, 7 * u); closePath()
                }
                line(ctx, 12 * u, 7 * u, 12 * u, 17.5f * u)
            }
            IconKind.STOP -> {
                ctx.beginPath()
                ctx.moveTo(7 * u, 7 * u); ctx.lineTo(17 * u, 7 * u); ctx.lineTo(17 * u, 17 * u); ctx.lineTo(7 * u, 17 * u)
                ctx.closePath()
                ctx.fill()
            }
            IconKind.WIFI_OFF -> {
                ctx.beginPath()
                ctx.arc(12 * u, 18 * u, 9.5f * u, (PI * 1.2).toFloat(), (PI * 1.8).toFloat(), false)
                ctx.stroke()
                ctx.beginPath()
                ctx.arc(12 * u, 18 * u, 5.5f * u, (PI * 1.2).toFloat(), (PI * 1.8).toFloat(), false)
                ctx.stroke()
                circle(ctx, 12 * u, 18 * u, 1.3f * u, fill = true)
                line(ctx, 4 * u, 4 * u, 20 * u, 20 * u)
            }
            IconKind.MESSAGE -> {
                path(ctx) {
                    moveTo(4.5f * u, 5.5f * u); lineTo(19.5f * u, 5.5f * u); lineTo(19.5f * u, 15.5f * u); lineTo(10 * u, 15.5f * u)
                    lineTo(6 * u, 19 * u); lineTo(6 * u, 15.5f * u); lineTo(4.5f * u, 15.5f * u); closePath()
                }
            }
        }
    }

    private inline fun path(ctx: CanvasContext, block: CanvasContext.() -> Unit) {
        ctx.beginPath()
        ctx.block()
        ctx.stroke()
    }

    private fun line(ctx: CanvasContext, x1: Float, y1: Float, x2: Float, y2: Float) {
        ctx.beginPath()
        ctx.moveTo(x1, y1)
        ctx.lineTo(x2, y2)
        ctx.stroke()
    }

    private fun rect(ctx: CanvasContext, x: Float, y: Float, w: Float, h: Float) {
        ctx.beginPath()
        ctx.moveTo(x, y); ctx.lineTo(x + w, y); ctx.lineTo(x + w, y + h); ctx.lineTo(x, y + h)
        ctx.closePath()
        ctx.stroke()
    }

    private fun circle(ctx: CanvasContext, cx: Float, cy: Float, r: Float, fill: Boolean) {
        ctx.beginPath()
        ctx.arc(cx, cy, r, 0f, (2 * PI).toFloat(), false)
        if (fill) ctx.fill() else ctx.stroke()
    }

    private fun star4(ctx: CanvasContext, cx: Float, cy: Float, outer: Float, inner: Float) {
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 4
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) ctx.moveTo(x, y) else ctx.lineTo(x, y)
        }
        ctx.closePath()
    }
}
