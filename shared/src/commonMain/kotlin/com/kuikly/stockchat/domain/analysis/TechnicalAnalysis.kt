package com.kuikly.stockchat.domain.analysis

/** 趋势倾向 */
enum class TrendBias(val label: String) {
    BULLISH("偏多"),
    BEARISH("偏空"),
    NEUTRAL("震荡"),
}

/** 标签语义色 */
enum class TagTone {
    POSITIVE,
    NEGATIVE,
    WARNING,
    NEUTRAL,
}

data class InsightTag(val text: String, val tone: TagTone)

/**
 * 基于日 K 计算出的技术面分析结果。所有价格字段与标的报价同单位。
 */
data class TechnicalAnalysis(
    val ma5: Double?,
    val ma10: Double?,
    val ma20: Double?,
    val ma60: Double?,
    val rsi14: Double?,
    /** 近 20 日最低（支撑位） */
    val support: Double?,
    /** 近 20 日最高（压力位） */
    val resistance: Double?,
    /** 短期趋势（5 日 vs 20 日） */
    val shortTermTrend: TrendBias,
    /** 中期趋势（20 日 vs 60 日 / 20 日涨跌幅） */
    val midTermTrend: TrendBias,
    /** 年化波动率 % */
    val volatilityPct: Double?,
    /** 量比：最新成交量 / 近 5 日均量 */
    val volumeRatio: Double?,
    /** 近 5 / 20 日涨跌幅 % */
    val change5dPct: Double?,
    val change20dPct: Double?,
    /** 现价相对 MA20 的偏离 % */
    val biasToMa20Pct: Double?,
    /** 综合评分 0~100（>60 偏强，<40 偏弱） */
    val score: Int,
    val tags: List<InsightTag>,
) {
    val hasEnoughData: Boolean get() = ma5 != null
}
