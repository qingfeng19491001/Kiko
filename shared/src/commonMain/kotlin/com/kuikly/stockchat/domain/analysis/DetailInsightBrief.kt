package com.kuikly.stockchat.domain.analysis

import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.NumberFormat

/** 诊股解读里的一节：标题 + 正文，数字均可回溯到行情与日 K。 */
data class InsightSection(
    val title: String,
    val body: String,
    val tone: TagTone = TagTone.NEUTRAL,
)

/**
 * 详情页 AI 解读骨架。结论与点位由本地计算，模型只负责聊天页长文；
 * 本页用可核对的摘要、趋势、点位与信号把诊股讲清楚。
 */
data class DetailInsightBrief(
    val headline: String,
    val headlineTone: TagTone,
    val snapshotItems: List<SignalItem>,
    val sections: List<InsightSection>,
) {
    fun asParagraph(): String = buildString {
        append(headline.trimEnd('。', '.'))
        append("。")
        sections.forEach { section ->
            append(section.body.trim())
            if (!section.body.trim().endsWith("。")) append("。")
        }
    }

    companion object {
        fun from(
            quote: Quote,
            analysis: TechnicalAnalysis,
            flow: CapitalFlowData? = null,
        ): DetailInsightBrief {
            val plan = DetailActionPlan.from(quote, analysis)
            val strength = when {
                analysis.score >= 65 -> "偏强"
                analysis.score <= 38 -> "偏弱"
                else -> "中性"
            }
            val headlineTone = when {
                analysis.score >= 65 -> TagTone.POSITIVE
                analysis.score <= 38 -> TagTone.NEGATIVE
                else -> TagTone.WARNING
            }
            return DetailInsightBrief(
                headline = "${quote.instrument.name}技术面$strength（${analysis.score}分）· ${plan.stance}",
                headlineTone = headlineTone,
                snapshotItems = snapshotItems(quote, analysis),
                sections = listOf(
                    InsightSection("行情总结", marketSummary(quote, analysis, flow)),
                    InsightSection("趋势判断", trendJudgement(quote, analysis), toneOf(analysis.shortTermTrend)),
                    InsightSection("操作提示", plan.operationTip, plan.stanceTone),
                ),
            )
        }

        fun flowSignal(flow: CapitalFlowData?): SignalItem? {
            val data = flow ?: return null
            val item = data.flows.firstOrNull { it.label.contains("主力") }
                ?: data.flows.firstOrNull()
                ?: return null
            val tone = when {
                item.netInflow > 0 -> TagTone.POSITIVE
                item.netInflow < 0 -> TagTone.NEGATIVE
                else -> TagTone.NEUTRAL
            }
            val verb = when {
                item.netInflow > 0 -> "净流入"
                item.netInflow < 0 -> "净流出"
                else -> "持平"
            }
            return SignalItem(
                title = "资金",
                value = (if (item.netInflow > 0) "+" else "") + NumberFormat.compact(item.netInflow),
                hint = "${item.label}$verb",
                tone = tone,
                reading = "${data.date} ${item.label}$verb ${NumberFormat.compact(item.netInflow)}，占成交 ${NumberFormat.fixed(item.pct, 2)}%。资金只反映当日博弈，需和趋势一起看。",
            )
        }

        private fun snapshotItems(quote: Quote, analysis: TechnicalAnalysis): List<SignalItem> = listOf(
            SignalItem(
                title = "今日",
                value = NumberFormat.signedPct(quote.changePct),
                hint = quote.tradingStatus.ifBlank { "最新 ${NumberFormat.price(quote.price)}" },
                tone = toneOfChange(quote.changePct),
            ),
            SignalItem(
                title = "近5日",
                value = analysis.change5dPct?.let { NumberFormat.signedPct(it) } ?: "--",
                hint = "短线涨跌",
                tone = toneOfChange(analysis.change5dPct),
            ),
            SignalItem(
                title = "近20日",
                value = analysis.change20dPct?.let { NumberFormat.signedPct(it) } ?: "--",
                hint = "波段涨跌",
                tone = toneOfChange(analysis.change20dPct),
            ),
            SignalItem(
                title = "量比",
                value = analysis.volumeRatio?.let { NumberFormat.fixed(it, 2) } ?: "--",
                hint = volumeHint(quote, analysis),
                tone = volumeTone(quote, analysis),
            ),
        )

        private fun marketSummary(quote: Quote, analysis: TechnicalAnalysis, flow: CapitalFlowData?): String {
            val ins = quote.instrument
            val move = when {
                quote.isUp -> "上涨"
                quote.isDown -> "下跌"
                else -> "平盘"
            }
            val driver = when {
                ins.isIndex -> "权重股与市场流动性预期是主要扰动"
                ins.sector.contains("互联网") -> "核心业务与新业务增长预期仍在定价里"
                ins.sector.contains("新能源") || ins.sector.contains("电动车") -> "销量与价格竞争预期影响定价"
                ins.sector.contains("消费") || ins.sector.contains("白酒") -> "消费复苏与渠道动销是常见变量"
                ins.sector.contains("金融") -> "利率环境与资产质量预期影响定价"
                else -> "行业景气与资金偏好变化仍在影响定价"
            }
            val vol = quote.turnover.takeIf { it > 0 }?.let { "成交额 ${NumberFormat.compact(it)}。" }.orEmpty()
            val flowNote = flowSignal(flow)?.let { "${it.hint} ${it.value}。" }.orEmpty()
            return "${ins.name}今日$move ${NumberFormat.signedPct(quote.changePct)}，$driver。$vol${rangeComment(quote.price, analysis)}$flowNote".trim()
        }

        private fun trendJudgement(quote: Quote, analysis: TechnicalAnalysis): String {
            val ma = maComment(quote.price, analysis)
            val rsi = analysis.rsi14?.let { "RSI ${NumberFormat.fixed(it, 0)}，${rsiComment(it)}" }.orEmpty()
            val five = analysis.change5dPct?.let { "近 5 日 ${NumberFormat.signedPct(it)}，" }.orEmpty()
            val twenty = analysis.change20dPct?.let { "近 20 日 ${NumberFormat.signedPct(it)}。" }.orEmpty()
            return "短期${analysis.shortTermTrend.label}、中期${analysis.midTermTrend.label}。$ma $rsi $five$twenty".replace("  ", " ").trim()
        }

        private fun rangeComment(price: Double, analysis: TechnicalAnalysis): String {
            val support = analysis.support ?: return ""
            val resistance = analysis.resistance ?: return ""
            if (resistance <= support) return ""
            val pos = (price - support) / (resistance - support)
            return when {
                pos >= 0.9 -> "现价贴近近 20 日区间上沿，突破需要量能配合。"
                pos <= 0.1 -> "现价贴近近 20 日区间下沿，先看能否站稳。"
                else -> "现价位于近 20 日高低点之间，更适合按区间观察。"
            }
        }

        private fun maComment(price: Double, analysis: TechnicalAnalysis): String {
            val ma5 = analysis.ma5 ?: return "均线样本不足。"
            val ma20 = analysis.ma20 ?: return "均线样本不足。"
            return when {
                price > ma5 && ma5 > ma20 -> "股价站上短中期均线，多头排列仍在。"
                price < ma5 && ma5 < ma20 -> "股价在短中期均线之下，空头排列，反弹需先收复 MA5。"
                price > ma20 -> "股价在 MA20 上方，短线均线纠结，偏强势整理。"
                else -> "股价跌破 MA20，中期转弱，需观察能否快速收回。"
            }
        }

        private fun rsiComment(rsi: Double): String = when {
            rsi >= 70 -> "已进入超买区，短线追高风险上升。"
            rsi <= 30 -> "处于超卖区，先等止跌确认。"
            rsi >= 55 -> "多方力量占优，动能偏强。"
            rsi <= 45 -> "空方略占上风，动能偏弱。"
            else -> "多空相对均衡。"
        }

        private fun volumeHint(quote: Quote, analysis: TechnicalAnalysis): String {
            val ratio = analysis.volumeRatio ?: return "暂无量比"
            return when {
                ratio >= 1.3 && quote.isUp -> "放量上行"
                ratio >= 1.3 && quote.isDown -> "放量下跌"
                ratio <= 0.7 -> "成交偏弱"
                else -> "量能一般"
            }
        }

        private fun volumeTone(quote: Quote, analysis: TechnicalAnalysis): TagTone {
            val ratio = analysis.volumeRatio ?: return TagTone.NEUTRAL
            return when {
                ratio >= 1.3 && quote.isUp -> TagTone.POSITIVE
                ratio >= 1.3 && quote.isDown -> TagTone.NEGATIVE
                else -> TagTone.NEUTRAL
            }
        }

        private fun toneOf(bias: TrendBias): TagTone = when (bias) {
            TrendBias.BULLISH -> TagTone.POSITIVE
            TrendBias.BEARISH -> TagTone.NEGATIVE
            TrendBias.NEUTRAL -> TagTone.NEUTRAL
        }

        private fun toneOfChange(value: Double?): TagTone = when {
            value == null -> TagTone.NEUTRAL
            value > 0 -> TagTone.POSITIVE
            value < 0 -> TagTone.NEGATIVE
            else -> TagTone.NEUTRAL
        }
    }
}
