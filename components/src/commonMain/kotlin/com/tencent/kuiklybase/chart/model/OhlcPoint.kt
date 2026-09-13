package com.tencent.kuiklybase.chart.model

/** 股票 / K 线数据点（开高低收）。 */
data class OhlcPoint(
    val label: String,
    val x: Float,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Float? = null,
) {
    /** 保留旧版本的 6 参数构造签名，避免已编译调用方链接失败。 */
    constructor(
        label: String,
        x: Float,
        open: Float,
        high: Float,
        low: Float,
        close: Float,
    ) : this(label, x, open, high, low, close, null)

    @Deprecated("Binary compatibility bridge", level = DeprecationLevel.HIDDEN)
    fun copy(
        label: String = this.label,
        x: Float = this.x,
        open: Float = this.open,
        high: Float = this.high,
        low: Float = this.low,
        close: Float = this.close,
    ): OhlcPoint = OhlcPoint(label, x, open, high, low, close, volume)
}

internal fun OhlcPoint.hasValidOhlcSemantics(): Boolean =
    x.isFinite() && open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite() &&
        low <= high && open in low..high && close in low..high
