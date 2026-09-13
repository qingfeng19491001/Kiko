package com.kuikly.stockchat.domain.chat

/** 把点击 / 拖动手势的 x 映射到图表序列下标。 */
object ChartHitIndex {
    fun at(x: Float, count: Int, width: Float, rightPad: Float = 46f): Int {
        if (count <= 0) return -1
        val plotW = (width - rightPad).coerceAtLeast(1f)
        val clampedX = x.coerceIn(0f, plotW)
        return (clampedX / plotW * count).toInt().coerceIn(0, count - 1)
    }
}
