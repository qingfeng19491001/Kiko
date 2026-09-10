package com.kuikly.stockchat.domain.model

/** 详情页当前点选的 K 线或情景预测点。 */
data class SelectedChartPoint(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val isForecast: Boolean,
    val changePct: Double? = null,
) {
    companion object {
        fun fromBar(bar: KLineBar, previousClose: Double?, isForecast: Boolean): SelectedChartPoint {
            val changePct = previousClose?.takeIf { it > 0 }?.let { (bar.close - it) / it * 100 }
            return SelectedChartPoint(
                date = bar.date.take(10),
                open = bar.open,
                high = bar.high,
                low = bar.low,
                close = bar.close,
                isForecast = isForecast,
                changePct = changePct,
            )
        }
    }
}
