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
    MENU, PLUS, SEND, ARROW_UP, ARROW_UP_RIGHT, BACK, CLOSE, CHEVRON_RIGHT, CHEVRON_DOWN,
    STAR, STAR_FILLED, SHARE, CLOCK, TRASH, SPARKLE, REFRESH, TREND_UP, ALERT, CHART, COMPARE, BOOK,
    STRATEGY, EYE, RADAR,
    STOP, WIFI_OFF, MESSAGE, CANDLE, SEARCH, VOICE, KEYBOARD, EDIT, SPEAKER, SPEAKER_OFF,
    CAMERA, IMAGE, FOLDER, SETTINGS, LAYERS, MESSAGE_PLUS,
    THUMBS_UP, THUMBS_DOWN,
    CALENDAR, MEMORY, HEX_CUBE, GRID,
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
            IconKind.ARROW_UP_RIGHT -> {
                line(ctx, 7 * u, 17 * u, 17 * u, 7 * u)
                path(ctx) { moveTo(10.2f * u, 7 * u); lineTo(17 * u, 7 * u); lineTo(17 * u, 13.8f * u) }
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
            IconKind.THUMBS_UP -> {
                path(ctx) {
                    moveTo(9 * u, 20 * u); lineTo(5 * u, 20 * u); lineTo(5 * u, 10 * u); lineTo(9 * u, 10 * u); closePath()
                }
                path(ctx) {
                    moveTo(9 * u, 10 * u); lineTo(12.5f * u, 4 * u); lineTo(14.5f * u, 4 * u)
                    lineTo(14 * u, 9 * u); lineTo(19 * u, 9 * u); lineTo(20 * u, 10.5f * u)
                    lineTo(18.5f * u, 19 * u); lineTo(9 * u, 19 * u); closePath()
                }
            }
            IconKind.THUMBS_DOWN -> {
                path(ctx) {
                    moveTo(9 * u, 4 * u); lineTo(5 * u, 4 * u); lineTo(5 * u, 14 * u); lineTo(9 * u, 14 * u); closePath()
                }
                path(ctx) {
                    moveTo(9 * u, 14 * u); lineTo(12.5f * u, 20 * u); lineTo(14.5f * u, 20 * u)
                    lineTo(14 * u, 15 * u); lineTo(19 * u, 15 * u); lineTo(20 * u, 13.5f * u)
                    lineTo(18.5f * u, 5 * u); lineTo(9 * u, 5 * u); closePath()
                }
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
            IconKind.STRATEGY -> {
                // 分叉路径：一个起点，两条去向
                circle(ctx, 12 * u, 4.2f * u, 1.5f * u, fill = true)
                line(ctx, 12 * u, 5.8f * u, 12 * u, 11.5f * u)
                line(ctx, 12 * u, 11.5f * u, 5.6f * u, 18.2f * u)
                line(ctx, 12 * u, 11.5f * u, 18.4f * u, 18.2f * u)
                circle(ctx, 5.6f * u, 19.5f * u, 1.5f * u, fill = true)
                circle(ctx, 18.4f * u, 19.5f * u, 1.5f * u, fill = true)
            }
            IconKind.EYE -> {
                ctx.beginPath()
                ctx.moveTo(3f * u, 12 * u)
                ctx.quadraticCurveTo(12 * u, 5f * u, 21f * u, 12 * u)
                ctx.quadraticCurveTo(12 * u, 19f * u, 3f * u, 12 * u)
                ctx.closePath()
                ctx.stroke()
                circle(ctx, 12 * u, 12 * u, 3.1f * u, fill = false)
                circle(ctx, 12 * u, 12 * u, 1.35f * u, fill = true)
            }
            IconKind.RADAR -> {
                circle(ctx, 12 * u, 12 * u, 1.3f * u, fill = true)
                ctx.beginPath()
                ctx.arc(12 * u, 12 * u, 5f * u, 0f, (2 * PI).toFloat(), false)
                ctx.stroke()
                ctx.beginPath()
                ctx.arc(12 * u, 12 * u, 8.4f * u, 0f, (2 * PI).toFloat(), false)
                ctx.stroke()
                line(ctx, 12 * u, 12 * u, 17.6f * u, 6.6f * u)
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
            IconKind.MESSAGE, IconKind.MESSAGE_PLUS -> {
                path(ctx) {
                    moveTo(4.5f * u, 5.5f * u); lineTo(19.5f * u, 5.5f * u); lineTo(19.5f * u, 15.5f * u); lineTo(10 * u, 15.5f * u)
                    lineTo(6 * u, 19 * u); lineTo(6 * u, 15.5f * u); lineTo(4.5f * u, 15.5f * u); closePath()
                }
                if (kind == IconKind.MESSAGE_PLUS) {
                    line(ctx, 12 * u, 8.2f * u, 12 * u, 12.8f * u)
                    line(ctx, 9.7f * u, 10.5f * u, 14.3f * u, 10.5f * u)
                }
            }
            IconKind.SETTINGS -> {
                circle(ctx, 12 * u, 12 * u, 3.2f * u, fill = false)
                circle(ctx, 12 * u, 12 * u, 7.2f * u, fill = false)
                for (i in 0 until 6) {
                    val angle = i * PI / 3
                    val inner = 7.2f * u
                    val outer = 10.2f * u
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    line(ctx, 12 * u + inner * dx, 12 * u + inner * dy, 12 * u + outer * dx, 12 * u + outer * dy)
                }
            }
            IconKind.LAYERS -> {
                fun diamond(cy: Float, half: Float) {
                    path(ctx) {
                        moveTo(12 * u, cy - half)
                        lineTo(18.5f * u, cy)
                        lineTo(12 * u, cy + half)
                        lineTo(5.5f * u, cy)
                        closePath()
                    }
                }
                diamond(8.2f * u, 3.4f * u)
                diamond(12f * u, 3.4f * u)
                diamond(15.8f * u, 3.4f * u)
            }
            IconKind.VOICE -> {
                // 声波：5 根竖线，中间最高、两侧渐低（Kimi 语音输入图标）
                line(ctx, 5 * u, 10 * u, 5 * u, 14 * u)
                line(ctx, 8 * u, 7.5f * u, 8 * u, 16.5f * u)
                line(ctx, 11 * u, 5 * u, 11 * u, 19 * u)
                line(ctx, 14 * u, 7.5f * u, 14 * u, 16.5f * u)
                line(ctx, 17 * u, 10 * u, 17 * u, 14 * u)
            }
            IconKind.KEYBOARD -> {
                rect(ctx, 3.5f * u, 5.5f * u, 17f * u, 13f * u)
                for (row in 0 until 2) {
                    for (column in 0 until 5) {
                        circle(ctx, (6f + column * 3f) * u, (9f + row * 3f) * u, 0.55f * u, fill = true)
                    }
                }
                line(ctx, 7f * u, 15.5f * u, 17f * u, 15.5f * u)
            }
            IconKind.EDIT -> {
                path(ctx) {
                    moveTo(5f * u, 18.5f * u); lineTo(7f * u, 13f * u); lineTo(16.5f * u, 3.5f * u)
                    lineTo(20.5f * u, 7.5f * u); lineTo(11f * u, 17f * u); closePath()
                }
                line(ctx, 5f * u, 18.5f * u, 10.5f * u, 17f * u)
            }
            IconKind.SPEAKER, IconKind.SPEAKER_OFF -> {
                // Kimi 语音播报：实心喇叭 + 右侧声波 / 关闭叉号
                ctx.beginPath()
                ctx.moveTo(2.2f * u, 8.6f * u)
                ctx.lineTo(6.6f * u, 8.6f * u)
                ctx.lineTo(12f * u, 3.8f * u)
                ctx.lineTo(12f * u, 20.2f * u)
                ctx.lineTo(6.6f * u, 15.4f * u)
                ctx.lineTo(2.2f * u, 15.4f * u)
                ctx.closePath()
                ctx.fill()
                if (kind == IconKind.SPEAKER) {
                    ctx.beginPath()
                    ctx.arc(13.1f * u, 12 * u, 3.4f * u, -0.78f, 0.78f, false)
                    ctx.stroke()
                    ctx.beginPath()
                    ctx.arc(13.1f * u, 12 * u, 6.3f * u, -0.88f, 0.88f, false)
                    ctx.stroke()
                } else {
                    line(ctx, 15.2f * u, 8.1f * u, 21.5f * u, 16.4f * u)
                    line(ctx, 21.5f * u, 8.1f * u, 15.2f * u, 16.4f * u)
                }
            }
            IconKind.CAMERA -> {
                // 相机：圆角矩形机身 + 顶部三角 + 圆形镜头 + 闪光灯小圆
                path(ctx) {
                    moveTo(4 * u, 8 * u); lineTo(7 * u, 8 * u); lineTo(8.5f * u, 5.5f * u)
                    lineTo(15.5f * u, 5.5f * u); lineTo(17 * u, 8 * u); lineTo(20 * u, 8 * u)
                    lineTo(20 * u, 18.5f * u); lineTo(4 * u, 18.5f * u); closePath()
                }
                circle(ctx, 12 * u, 12.5f * u, 3.8f * u, fill = false)
                circle(ctx, 12 * u, 12.5f * u, 1.6f * u, fill = true)
                circle(ctx, 16.8f * u, 7 * u, 0.9f * u, fill = true)
            }
            IconKind.IMAGE -> {
                // 图片：矩形 + 山形 + 太阳
                path(ctx) {
                    moveTo(4 * u, 6 * u); lineTo(20 * u, 6 * u); lineTo(20 * u, 18.5f * u)
                    lineTo(4 * u, 18.5f * u); closePath()
                }
                // 山脉折线
                ctx.beginPath()
                ctx.moveTo(4 * u, 15.5f * u)
                ctx.lineTo(9 * u, 10.5f * u)
                ctx.lineTo(13 * u, 14 * u)
                ctx.lineTo(16.5f * u, 11f * u)
                ctx.lineTo(20 * u, 15.5f * u)
                ctx.stroke()
                circle(ctx, 15.5f * u, 8.5f * u, 1.6f * u, fill = true)
            }
            IconKind.FOLDER -> {
                // 文件夹上传：文件 + 向上箭头
                path(ctx) {
                    moveTo(3.5f * u, 7.5f * u); lineTo(3.5f * u, 18.5f * u)
                    lineTo(20.5f * u, 18.5f * u); lineTo(20.5f * u, 8.5f * u)
                    lineTo(11.5f * u, 8.5f * u); lineTo(10f * u, 7.5f * u); closePath()
                }
                // 向上箭头
                line(ctx, 12 * u, 17 * u, 12 * u, 12.5f * u)
                path(ctx) { moveTo(9 * u, 14.5f * u); lineTo(12 * u, 11.5f * u); lineTo(15 * u, 14.5f * u) }
            }
            IconKind.CALENDAR -> {
                rect(ctx, 4.2f * u, 6.2f * u, 15.6f * u, 13.8f * u)
                line(ctx, 4.2f * u, 10.4f * u, 19.8f * u, 10.4f * u)
                line(ctx, 8.2f * u, 3.6f * u, 8.2f * u, 7.4f * u)
                line(ctx, 15.8f * u, 3.6f * u, 15.8f * u, 7.4f * u)
                circle(ctx, 15.4f * u, 16.2f * u, 4.1f * u, fill = false)
                line(ctx, 15.4f * u, 16.2f * u, 15.4f * u, 13.8f * u)
                line(ctx, 15.4f * u, 16.2f * u, 17.8f * u, 16.2f * u)
            }
            IconKind.MEMORY -> {
                circle(ctx, 10.4f * u, 8.2f * u, 3.3f * u, fill = false)
                ctx.beginPath()
                ctx.arc(10.4f * u, 20.6f * u, 7.4f * u, (PI * 1.12).toFloat(), (PI * 1.88).toFloat(), false)
                ctx.stroke()
                ctx.beginPath()
                star4(ctx, 18.2f * u, 7.2f * u, 3.1f * u, 0.9f * u)
                ctx.stroke()
            }
            IconKind.HEX_CUBE -> {
                path(ctx) {
                    moveTo(12 * u, 3.4f * u)
                    lineTo(20 * u, 8f * u)
                    lineTo(20 * u, 16f * u)
                    lineTo(12 * u, 20.6f * u)
                    lineTo(4 * u, 16f * u)
                    lineTo(4 * u, 8f * u)
                    closePath()
                }
                line(ctx, 12 * u, 3.4f * u, 12 * u, 12f * u)
                line(ctx, 12 * u, 12f * u, 4 * u, 16f * u)
                line(ctx, 12 * u, 12f * u, 20 * u, 16f * u)
            }
            IconKind.GRID -> {
                val cell = 6.2f * u
                val gap = 1.8f * u
                val x0 = 4.8f * u
                val y0 = 4.8f * u
                rect(ctx, x0, y0, cell, cell)
                rect(ctx, x0 + cell + gap, y0, cell, cell)
                rect(ctx, x0, y0 + cell + gap, cell, cell)
                rect(ctx, x0 + cell + gap, y0 + cell + gap, cell, cell)
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
