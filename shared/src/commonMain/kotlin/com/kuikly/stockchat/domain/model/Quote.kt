package com.kuikly.stockchat.domain.model

/**
 * 实时行情快照。
 * 金额类字段单位统一为“元”（对应市场货币），市值单位为“亿”。
 */
data class Quote(
    val instrument: Instrument,
    val price: Double,
    val prevClose: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val change: Double,
    val changePct: Double,
    /** 成交量（股） */
    val volume: Double,
    /** 成交额（元） */
    val turnover: Double,
    val pe: Double? = null,
    val pb: Double? = null,
    /** 流通市值（亿） */
    val marketCap: Double? = null,
    /** 总市值（亿） */
    val totalMarketCap: Double? = null,
    /** 振幅 % */
    val amplitude: Double? = null,
    /** 换手率 % */
    val turnoverRate: Double? = null,
    val high52w: Double? = null,
    val low52w: Double? = null,
    /** 行情时间，如 "06-23 16:00" */
    val updateTime: String,
    /** 交易状态描述，如 "港股已收盘" */
    val tradingStatus: String,
    /** 是否为离线演示数据 */
    val isMock: Boolean = false,
) {
    val isUp: Boolean get() = change > 0
    val isDown: Boolean get() = change < 0
    val isFlat: Boolean get() = change == 0.0
}
