package com.kuikly.stockchat.domain.prediction

import com.kuikly.stockchat.domain.model.KLineBar
import kotlin.math.abs

/**
 * 把已校验的情景预测编成可追加到日 K 的线点（OHLC 相等）。
 * 日期不连续或相对最新收盘跳得过大时返回空，避免把幻想曲线画进图。
 */
object PredictionChartMapper {
    const val MAX_STEP_PCT = 0.35
    const val MAX_TOTAL_PCT = 0.50

    fun toForecastBars(
        history: List<KLineBar>,
        prediction: StockPrediction?,
        maxStepPct: Double = MAX_STEP_PCT,
        maxTotalPct: Double = MAX_TOTAL_PCT,
    ): List<KLineBar> {
        if (prediction == null || prediction.points.isEmpty() || history.isEmpty()) return emptyList()
        val last = history.last()
        val lastDate = last.date.take(10)
        val lastClose = last.close
        if (lastClose <= 0) return emptyList()
        var prevClose = lastClose
        val bars = ArrayList<KLineBar>(prediction.points.size)
        for (point in prediction.points) {
            val date = point.date.take(10)
            if (date <= lastDate) return emptyList()
            if (bars.isNotEmpty() && date <= bars.last().date.take(10)) return emptyList()
            if (!point.price.isFinite() || point.price <= 0) return emptyList()
            val step = abs(point.price - prevClose) / prevClose
            if (step > maxStepPct) return emptyList()
            val total = abs(point.price - lastClose) / lastClose
            if (total > maxTotalPct) return emptyList()
            val low = point.lower?.takeIf { it > 0 && it <= point.price } ?: point.price
            val high = point.upper?.takeIf { it >= point.price } ?: point.price
            bars += KLineBar(
                date = date,
                open = point.price,
                close = point.price,
                high = high,
                low = low,
                volume = 0.0,
            )
            prevClose = point.price
        }
        return bars
    }
}
