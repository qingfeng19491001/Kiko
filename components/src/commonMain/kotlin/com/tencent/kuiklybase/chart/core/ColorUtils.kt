package com.tencent.kuiklybase.chart.core

import com.tencent.kuikly.core.base.Color

internal fun Long.toChartColor(): Color = Color(this)

/** 替换 ARGB 的 alpha 通道（0–255）。 */
internal fun Long.withAlpha(alphaByte: Int): Long {
    val a = alphaByte.coerceIn(0, 255).toLong()
    return (this and 0x00FFFFFFL) or (a shl 24)
}
