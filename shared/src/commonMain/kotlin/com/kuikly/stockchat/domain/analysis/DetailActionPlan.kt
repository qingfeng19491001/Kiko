package com.kuikly.stockchat.domain.analysis

import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.NumberFormat

/** 详情诊股用的参考点位（演示信息，不构成买卖指令）。 */
data class ActionLevel(
    val label: String,
    val price: String,
    val note: String,
    val tone: TagTone,
)

data class SignalItem(
    val title: String,
    val value: String,
    val hint: String,
    val tone: TagTone,
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
                            label = "关注区",
                            price = NumberFormat.price(support),
                            note = "回踩近 20 日低点附近，可作分批观察",
                            tone = TagTone.POSITIVE,
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
                            label = "压力区",
                            price = NumberFormat.price(resistance),
                            note = "接近近 20 日高点，适合评估兑现",
                            tone = TagTone.NEGATIVE,
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
                SignalItem("短期", analysis.shortTermTrend.label, "5 日相对 20 日均线", toneOf(analysis.shortTermTrend)),
                SignalItem("中期", analysis.midTermTrend.label, "20 日相对 60 日 / 涨跌", toneOf(analysis.midTermTrend)),
                SignalItem(
                    "RSI",
                    rsi?.let { NumberFormat.fixed(it, 0) } ?: "--",
                    rsiHint,
                    rsiTone,
                ),
                SignalItem(
                    "量能",
                    analysis.volumeRatio?.let { NumberFormat.fixed(it, 2) + " 倍" } ?: "--",
                    when {
                        analysis.volumeRatio == null -> "暂无量比"
                        analysis.volumeRatio >= 1.3 && quote.isUp -> "放量上行"
                        analysis.volumeRatio >= 1.3 && quote.isDown -> "放量下跌"
                        else -> "量能一般"
                    },
                    when {
                        analysis.volumeRatio == null -> TagTone.NEUTRAL
                        analysis.volumeRatio >= 1.3 && quote.isUp -> TagTone.POSITIVE
                        analysis.volumeRatio >= 1.3 && quote.isDown -> TagTone.NEGATIVE
                        else -> TagTone.NEUTRAL
                    },
                ),
            )
            val operationTip = when {
                analysis.score >= 65 ->
                    "趋势偏强：优先看回踩关注区是否站稳，不追高打满。"
                analysis.score <= 38 ->
                    "趋势偏弱：先看压力区能否受阻、关注区是否失守，控制仓位。"
                else ->
                    "震荡市：关注区附近观察、压力区附近评估兑现，做区间而不是单边。"
            }
            return DetailActionPlan(stance, stanceTone, levels, signals, operationTip)
        }

        private fun toneOf(bias: TrendBias): TagTone = when (bias) {
            TrendBias.BULLISH -> TagTone.POSITIVE
            TrendBias.BEARISH -> TagTone.NEGATIVE
            TrendBias.NEUTRAL -> TagTone.NEUTRAL
        }
    }
}
