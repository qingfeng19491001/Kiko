package com.kuikly.stockchat.domain.analysis

import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 技术面分析引擎：纯函数，输入行情快照，输出可解释的技术指标与标签。
 * 这里的“AI 解读”建立在真实数据之上，保证卡片中的数字可追溯。
 */
object AnalysisEngine {

    fun analyze(snapshot: MarketSnapshot): TechnicalAnalysis {
        val bars = snapshot.dailyBars
        val closes = bars.map { it.close }
        val latest = snapshot.quote.price.takeIf { it > 0 } ?: closes.lastOrNull() ?: 0.0

        val ma5 = sma(closes, 5)
        val ma10 = sma(closes, 10)
        val ma20 = sma(closes, 20)
        val ma60 = sma(closes, 60)
        val rsi = rsi(closes, 14)

        val recent20 = bars.takeLast(20)
        val support = recent20.minOfOrNull { it.low }
        val resistance = recent20.maxOfOrNull { it.high }

        val change5d = pctChange(closes, 5)
        val change20d = pctChange(closes, 20)
        val bias20 = ma20?.let { (latest - it) / it * 100 }

        val shortTrend = when {
            ma5 == null || ma20 == null -> TrendBias.NEUTRAL
            ma5 > ma20 * 1.005 && latest > ma5 -> TrendBias.BULLISH
            ma5 < ma20 * 0.995 && latest < ma5 -> TrendBias.BEARISH
            else -> TrendBias.NEUTRAL
        }
        val midTrend = when {
            change20d == null -> TrendBias.NEUTRAL
            ma60 != null && ma20 != null && ma20 > ma60 && change20d > 3 -> TrendBias.BULLISH
            ma60 != null && ma20 != null && ma20 < ma60 && change20d < -3 -> TrendBias.BEARISH
            abs(change20d) <= 5 -> TrendBias.NEUTRAL
            change20d > 5 -> TrendBias.BULLISH
            else -> TrendBias.BEARISH
        }

        val volatility = annualizedVolatility(closes)
        val volumeRatio = volumeRatio(bars)

        var score = 50
        if (shortTrend == TrendBias.BULLISH) score += 12
        if (shortTrend == TrendBias.BEARISH) score -= 12
        if (midTrend == TrendBias.BULLISH) score += 10
        if (midTrend == TrendBias.BEARISH) score -= 10
        rsi?.let {
            when {
                it > 70 -> score -= 8
                it < 30 -> score += 8
                it > 55 -> score += 4
                it < 45 -> score -= 4
            }
        }
        volumeRatio?.let { if (it > 1.3 && snapshot.quote.isUp) score += 5 }
        bias20?.let { if (abs(it) > 8) score -= 5 }
        score = score.coerceIn(5, 95)

        val tags = buildTags(shortTrend, midTrend, rsi, resistance, support, latest, volumeRatio)

        return TechnicalAnalysis(
            ma5 = ma5, ma10 = ma10, ma20 = ma20, ma60 = ma60, rsi14 = rsi,
            support = support, resistance = resistance,
            shortTermTrend = shortTrend, midTermTrend = midTrend,
            volatilityPct = volatility, volumeRatio = volumeRatio,
            change5dPct = change5d, change20dPct = change20d, biasToMa20Pct = bias20,
            score = score, tags = tags,
        )
    }

    private fun buildTags(
        shortTrend: TrendBias,
        midTrend: TrendBias,
        rsi: Double?,
        resistance: Double?,
        support: Double?,
        latest: Double,
        volumeRatio: Double?,
    ): List<InsightTag> {
        val tags = mutableListOf<InsightTag>()
        tags += when (shortTrend) {
            TrendBias.BULLISH -> InsightTag("趋势偏多", TagTone.POSITIVE)
            TrendBias.BEARISH -> InsightTag("趋势偏空", TagTone.NEGATIVE)
            TrendBias.NEUTRAL -> InsightTag("短期震荡", TagTone.NEUTRAL)
        }
        tags += when (midTrend) {
            TrendBias.BULLISH -> InsightTag("中期向好", TagTone.POSITIVE)
            TrendBias.BEARISH -> InsightTag("中期承压", TagTone.NEGATIVE)
            TrendBias.NEUTRAL -> InsightTag("中期震荡", TagTone.WARNING)
        }
        if (resistance != null && resistance > latest) {
            tags += InsightTag("压力位 ${NumberFormat.price(resistance)}", TagTone.NEGATIVE)
        } else if (support != null) {
            tags += InsightTag("支撑位 ${NumberFormat.price(support)}", TagTone.POSITIVE)
        }
        rsi?.let {
            if (it > 70) tags += InsightTag("RSI 超买", TagTone.WARNING)
            if (it < 30) tags += InsightTag("RSI 超卖", TagTone.POSITIVE)
        }
        volumeRatio?.let { if (it > 1.5) tags += InsightTag("量能放大", TagTone.WARNING) }
        return tags.take(4)
    }

    fun sma(values: List<Double>, period: Int): Double? {
        if (values.size < period || period <= 0) return null
        return values.takeLast(period).sum() / period
    }

    /** 逐点均线序列（长度与输入一致，前 period-1 个为 null） */
    fun smaSeries(values: List<Double>, period: Int): List<Double?> {
        if (period <= 0) return values.map { null }
        val result = ArrayList<Double?>(values.size)
        var window = 0.0
        values.forEachIndexed { index, value ->
            window += value
            if (index >= period) window -= values[index - period]
            result += if (index >= period - 1) window / period else null
        }
        return result
    }

    fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        var gain = 0.0
        var loss = 0.0
        val slice = values.takeLast(period + 1)
        for (i in 1 until slice.size) {
            val diff = slice[i] - slice[i - 1]
            if (diff >= 0) gain += diff else loss -= diff
        }
        if (gain + loss == 0.0) return 50.0
        if (loss == 0.0) return 100.0
        val rs = (gain / period) / (loss / period)
        return 100 - 100 / (1 + rs)
    }

    private fun pctChange(values: List<Double>, days: Int): Double? {
        if (values.size <= days) return null
        val base = values[values.size - 1 - days]
        if (base == 0.0) return null
        return (values.last() - base) / base * 100
    }

    private fun annualizedVolatility(values: List<Double>): Double? {
        if (values.size < 11) return null
        val returns = values.zipWithNext { a, b -> if (a == 0.0) 0.0 else (b - a) / a }.takeLast(20)
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        return sqrt(variance) * sqrt(250.0) * 100
    }

    private fun volumeRatio(bars: List<KLineBar>): Double? {
        if (bars.size < 6) return null
        val latest = bars.last().volume
        val avg = bars.dropLast(1).takeLast(5).map { it.volume }.average()
        if (avg <= 0) return null
        return ((latest / avg) * 100).roundToInt() / 100.0
    }
}
