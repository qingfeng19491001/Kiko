package com.kuikly.stockchat.domain.analysis

import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.NumberFormat

/** 详情诊股用的参考点位（演示信息，不构成买卖指令）。 */
data class ActionLevel(
    val label: String,
    val price: String,
    val note: String,
    val tone: TagTone,
    val gap: String = "",
)

data class SignalItem(
    val title: String,
    val value: String,
    val hint: String,
    val tone: TagTone,
    val reading: String = hint,
)

data class DetailActionPlan(
    val stance: String,
    val stanceTone: TagTone,
    val levels: List<ActionLevel>,
    val signals: List<SignalItem>,
    val operationTip: String,
) {
    companion object {
        fun from(quote: Quote, analysis: TechnicalAnalysis): DetailActionPlan {
            val stance = when {
                analysis.score >= 65 -> "逢低关注"
                analysis.score <= 38 -> "控制仓位"
                else -> "区间操作"
            }
            val stanceTone = when {
                analysis.score >= 65 -> TagTone.POSITIVE
                analysis.score <= 38 -> TagTone.NEGATIVE
                else -> TagTone.WARNING
            }
            val support = analysis.support
            val resistance = analysis.resistance
            val levels = buildList {
                if (support != null && support > 0) {
                    add(
                        ActionLevel(
                            label = "买入观察",
                            price = NumberFormat.price(support),
                            note = "回踩近 20 日低点附近，可作分批观察",
                            tone = TagTone.POSITIVE,
                            gap = gapLabel(support, quote.price),
                        ),
                    )
                }
                add(
                    ActionLevel(
                        label = "现价",
                        price = NumberFormat.price(quote.price),
                        note = "相对昨收 ${NumberFormat.signedPct(quote.changePct)}",
                        tone = TagTone.NEUTRAL,
                    ),
                )
                if (resistance != null && resistance > 0) {
                    add(
                        ActionLevel(
                            label = "卖出观察",
                            price = NumberFormat.price(resistance),
                            note = "接近近 20 日高点，适合评估兑现",
                            tone = TagTone.NEGATIVE,
                            gap = gapLabel(resistance, quote.price),
                        ),
                    )
                }
            }
            val rsi = analysis.rsi14
            val rsiTone = when {
                rsi == null -> TagTone.NEUTRAL
                rsi >= 70 -> TagTone.NEGATIVE
                rsi <= 30 -> TagTone.POSITIVE
                else -> TagTone.NEUTRAL
            }
            val rsiHint = when {
                rsi == null -> "样本不足"
                rsi >= 70 -> "短线超买，注意回撤"
                rsi <= 30 -> "短线超卖，等止跌确认"
                else -> "动能中性"
            }
            val signals = listOf(
                SignalItem(
                    title = "短期",
                    value = analysis.shortTermTrend.label,
                    hint = "5 日相对 20 日均线",
                    tone = toneOf(analysis.shortTermTrend),
                    reading = "短期看 MA5 与 MA20 的位置。当前${analysis.shortTermTrend.label}，用来判断近几日多空谁占优，不单独作为买卖依据。",
                ),
                SignalItem(
                    title = "中期",
                    value = analysis.midTermTrend.label,
                    hint = "20 日相对 60 日 / 涨跌",
                    tone = toneOf(analysis.midTermTrend),
                    reading = "中期综合 MA20 / MA60 与近 20 日涨跌。当前${analysis.midTermTrend.label}，用来看波段方向是否还在。",
                ),
                SignalItem(
                    title = "RSI",
                    value = rsi?.let { NumberFormat.fixed(it, 0) } ?: "--",
                    hint = rsiHint,
                    tone = rsiTone,
                    reading = rsi?.let { "RSI(14) 为 ${NumberFormat.fixed(it, 1)}，$rsiHint。超买超卖只说明短线拥挤，需要价格结构确认。" }
                        ?: "日 K 样本不足，RSI 暂缺。",
                ),
                SignalItem(
                    title = "量能",
                    value = analysis.volumeRatio?.let { NumberFormat.fixed(it, 2) + " 倍" } ?: "--",
                    hint = when {
                        analysis.volumeRatio == null -> "暂无量比"
                        analysis.volumeRatio >= 1.3 && quote.isUp -> "放量上行"
                        analysis.volumeRatio >= 1.3 && quote.isDown -> "放量下跌"
                        else -> "量能一般"
                    },
                    tone = when {
                        analysis.volumeRatio == null -> TagTone.NEUTRAL
                        analysis.volumeRatio >= 1.3 && quote.isUp -> TagTone.POSITIVE
                        analysis.volumeRatio >= 1.3 && quote.isDown -> TagTone.NEGATIVE
                        else -> TagTone.NEUTRAL
                    },
                    reading = analysis.volumeRatio?.let { ratio ->
                        val hint = when {
                            ratio >= 1.3 && quote.isUp -> "高于近 5 日均量且价格上涨，属于放量上行。"
                            ratio >= 1.3 && quote.isDown -> "高于近 5 日均量且价格下跌，属于放量下跌，短线波动会加大。"
                            else -> "量能接近近期均值，方向仍看价格结构。"
                        }
                        "最新成交量相对近 5 日均量为 ${NumberFormat.fixed(ratio, 2)} 倍。$hint"
                    } ?: "成交量样本不足，量比暂缺。",
                ),
            )
            val operationTip = when {
                analysis.score >= 65 ->
                    "趋势偏强：优先看回踩买入观察区是否站稳，不追高打满。"
                analysis.score <= 38 ->
                    "趋势偏弱：先看卖出观察区能否受阻、买入观察区是否失守，控制仓位。"
                else ->
                    "震荡市：买入观察区附近观察、卖出观察区附近评估兑现，做区间而不是单边。"
            }
            return DetailActionPlan(stance, stanceTone, levels, signals, operationTip)
        }

        private fun gapLabel(level: Double, price: Double): String {
            if (price <= 0.0) return ""
            return "距现价 ${NumberFormat.signedPct((level - price) / price * 100)}"
        }

        private fun toneOf(bias: TrendBias): TagTone = when (bias) {
            TrendBias.BULLISH -> TagTone.POSITIVE
            TrendBias.BEARISH -> TagTone.NEGATIVE
            TrendBias.NEUTRAL -> TagTone.NEUTRAL
        }
    }
}
