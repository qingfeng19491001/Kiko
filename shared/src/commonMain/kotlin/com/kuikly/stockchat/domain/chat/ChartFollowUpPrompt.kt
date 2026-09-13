package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.prediction.StockPrediction
import com.kuikly.stockchat.domain.util.NumberFormat

object ChartFollowUpPrompt {
    fun build(
        instrument: Instrument,
        point: SelectedChartPoint,
        prediction: StockPrediction?,
    ): String = build(instrument.name, point, prediction)

    fun build(
        name: String,
        point: SelectedChartPoint,
        prediction: StockPrediction?,
    ): String {
        val price = NumberFormat.price(point.close)
        val change = point.changePct?.let { "，涨跌幅 ${NumberFormat.signedPct(it)}" }.orEmpty()
        return if (point.isForecast) {
            val conf = prediction?.let { "，置信度 ${(it.confidence * 100).toInt()}%" }.orEmpty()
            val band = if (point.low != point.high) {
                "，区间 ${NumberFormat.price(point.low)}–${NumberFormat.price(point.high)}"
            } else ""
            "$name ${point.date} 收盘 $price$change（情景预测$conf$band）：这一点怎么看？"
        } else {
            "$name ${point.date} 收盘 $price$change：这一点怎么看？"
        }
    }

    fun fromBars(name: String, bars: List<KLineBar>, selectedIndex: Int): String? {
        val bar = bars.getOrNull(selectedIndex) ?: return null
        val prev = bars.getOrNull(selectedIndex - 1)?.close
        return build(name, SelectedChartPoint.fromBar(bar, prev, isForecast = false), prediction = null)
    }
}
