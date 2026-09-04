package com.kuikly.stockchat.domain.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 跨平台数字格式化工具（KMP common 下没有 String.format）。
 */
object NumberFormat {

    fun fixed(value: Double, digits: Int): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val factor = 10.0.pow(digits)
        val rounded = (abs(value) * factor).roundToLong()
        val intPart = rounded / factor.toLong()
        val fracPart = rounded % factor.toLong()
        val sign = if (value < 0 && rounded != 0L) "-" else ""
        if (digits == 0) return sign + intPart.toString()
        return sign + intPart.toString() + "." + fracPart.toString().padStart(digits, '0')
    }

    /** 价格：>=1000 保留 2 位，>=100 保留 2 位，小于 100 保留 2~3 位 */
    fun price(value: Double?): String {
        if (value == null) return "--"
        return when {
            abs(value) >= 1000 -> fixed(value, 2)
            abs(value) >= 1 -> fixed(value, 2)
            else -> fixed(value, 3)
        }
    }

    fun signed(value: Double?, digits: Int = 2): String {
        if (value == null) return "--"
        val text = fixed(value, digits)
        return if (value > 0) "+$text" else text
    }

    fun signedPct(value: Double?, digits: Int = 2): String {
        if (value == null) return "--"
        return signed(value, digits) + "%"
    }

    fun pct(value: Double?, digits: Int = 2): String {
        if (value == null) return "--"
        return fixed(value, digits) + "%"
    }

    /** 大数字缩写：亿 / 万 */
    fun compact(value: Double?, digits: Int = 2): String {
        if (value == null || value.isNaN()) return "--"
        val absValue = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            absValue >= 1_0000_0000_0000.0 -> sign + fixed(absValue / 1_0000_0000_0000.0, digits) + "万亿"
            absValue >= 1_0000_0000.0 -> sign + fixed(absValue / 1_0000_0000.0, digits) + "亿"
            absValue >= 1_0000.0 -> sign + fixed(absValue / 1_0000.0, digits) + "万"
            else -> sign + fixed(absValue, digits)
        }
    }

    /** 以“亿”为单位的市值转成缩写 */
    fun capFromYi(valueYi: Double?, digits: Int = 2): String {
        if (valueYi == null) return "--"
        return compact(valueYi * 1_0000_0000.0, digits)
    }

    fun ratio(value: Double?, digits: Int = 2): String {
        if (value == null) return "--"
        return fixed(value, digits)
    }
}
