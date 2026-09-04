package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.Card
import com.kuikly.stockchat.ui.components.ChangeBadge
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.InstrumentAvatar
import com.kuikly.stockchat.ui.components.BrandMark
import com.kuikly.stockchat.ui.components.PillButton
import com.kuikly.stockchat.ui.components.TagChip
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// region 头部行情

fun ViewContainer<*, *>.QuoteHeaderView(instrument: Instrument, quote: Quote) {
    val color = AppTheme.changeColor(quote.change)
    val trading = quote.tradingStatus
    val inSession = trading.contains("交易中")
    Card(padding = 12f) {
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            InstrumentAvatar(instrument, 44f)
            View {
                attr { flex(1f); marginLeft(12f) }
                Text {
                    attr {
                        text(instrument.name)
                        fontSize(18f)
                        fontWeight700()
                        color(AppTheme.textPrimary)
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(3f) }
                    Text {
                        attr {
                            text(instrument.displayCode)
                            fontSize(12f)
                            color(AppTheme.textTertiary)
                        }
                    }
                    View {
                        attr {
                            marginLeft(6f)
                            paddingLeft(6f); paddingRight(6f); paddingTop(1f); paddingBottom(1f)
                            borderRadius(4f)
                            backgroundColor(AppTheme.surfaceMuted)
                        }
                        Text { attr { text(instrument.market.label); fontSize(10f); color(AppTheme.textSecondary) } }
                    }
                    if (instrument.sector.isNotEmpty()) {
                        Text {
                            attr {
                                text(instrument.sector)
                                fontSize(11f)
                                color(AppTheme.textTertiary)
                                marginLeft(6f)
                            }
                        }
                    }
                }
            }
            View {
                attr {
                    paddingLeft(8f); paddingRight(8f); paddingTop(3f); paddingBottom(3f)
                    borderRadius(6f)
                    backgroundColor(if (inSession) AppTheme.upSoft else AppTheme.surfaceMuted)
                }
                Text {
                    attr {
                        text(trading)
                        fontSize(11f)
                        color(if (inSession) AppTheme.up else AppTheme.textSecondary)
                    }
                }
            }
        }
        View {
            attr { flexDirectionRow(); alignItemsFlexEnd(); marginTop(10f) }
            Text {
                attr {
                    text(NumberFormat.price(quote.price))
                    fontSize(32f)
                    fontWeight700()
                    color(color)
                }
            }
            View {
                attr { marginLeft(12f); marginBottom(6f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(NumberFormat.signed(quote.change))
                        fontSize(15f)
                        fontWeight500()
                        color(color)
                    }
                }
                View { attr { marginLeft(8f) } ; ChangeBadge(quote.change, NumberFormat.signedPct(quote.changePct), 13f, filled = true) }
            }
        }
        Text {
            attr {
                text("${instrument.market.currency} · 更新于 ${quote.updateTime}")
                fontSize(11f)
                color(AppTheme.textTertiary)
                marginTop(6f)
            }
        }
    }
}

// endregion

// region 周期 Tab

fun ViewContainer<*, *>.PeriodTabs(selected: () -> KLinePeriod, onSelect: (KLinePeriod) -> Unit) {
    View {
        attr {
            flexDirectionRow()
            backgroundColor(AppTheme.surfaceMuted)
            borderRadius(8f)
            padding(3f)
        }
        KLinePeriod.values().forEach { p ->
            View {
                attr {
                    flex(1f)
                    height(30f)
                    allCenter()
                    borderRadius(6f)
                    backgroundColor(if (p == selected()) AppTheme.surface else Color.TRANSPARENT)
                }
                event { click { onSelect(p) } }
                Text {
                    attr {
                        val active = p == selected()
                        text(p.label)
                        fontSize(13f)
                        if (active) fontWeight600() else fontWeight400()
                        color(if (active) AppTheme.textPrimary else AppTheme.textSecondary)
                    }
                }
            }
        }
    }
}

// endregion

// region 关键指标

private data class Metric(val label: String, val value: String, val color: Color? = null)

fun ViewContainer<*, *>.MetricsCard(quote: Quote, analysis: TechnicalAnalysis?) {
    val metrics = buildList {
        add(Metric("今开", NumberFormat.price(quote.open), AppTheme.changeColor(quote.open - quote.prevClose)))
        add(Metric("昨收", NumberFormat.price(quote.prevClose)))
        add(Metric("最高", NumberFormat.price(quote.high)))
        add(Metric("最低", NumberFormat.price(quote.low)))
        add(Metric("成交量", NumberFormat.compact(quote.volume)))
        add(Metric("成交额", NumberFormat.compact(quote.turnover)))
        quote.amplitude?.let { add(Metric("振幅", NumberFormat.pct(it))) }
        quote.turnoverRate?.let { add(Metric("换手率", NumberFormat.pct(it))) }
        quote.pe?.let { add(Metric("市盈率", NumberFormat.ratio(it))) }
        quote.pb?.let { add(Metric("市净率", NumberFormat.ratio(it))) }
        quote.marketCap?.let { add(Metric("总市值", NumberFormat.capFromYi(it))) }
        quote.high52w?.let { add(Metric("52周高", NumberFormat.price(it))) }
        quote.low52w?.let { add(Metric("52周低", NumberFormat.price(it))) }
        analysis?.volatilityPct?.let { add(Metric("20日波动", NumberFormat.pct(it))) }
    }
    Card {
        SectionTitle("关键指标", IconKind.CHART)
        metrics.chunked(3).forEach { row ->
            View {
                attr { flexDirectionRow(); marginTop(10f) }
                row.forEach { m ->
                    View {
                        attr { flex(1f) }
                        Text { attr { text(m.label); fontSize(11f); color(AppTheme.textTertiary) } }
                        Text {
                            attr {
                                text(m.value)
                                fontSize(14f)
                                fontWeight600()
                                color(m.color ?: AppTheme.textPrimary)
                                marginTop(2f)
                            }
                        }
                    }
                }
                repeat(3 - row.size) { View { attr { flex(1f) } } }
            }
        }
    }
}

// endregion

// region AI 解读

fun ViewContainer<*, *>.AiInsightCard(
    insight: String,
    analysis: TechnicalAnalysis,
    instrument: Instrument,
    onAsk: (String) -> Unit,
) {
    Card {
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            BrandMark(22f)
            Text {
                attr {
                    text("技术解读")
                    fontSize(15f)
                    fontWeight700()
                    color(AppTheme.textPrimary)
                    marginLeft(8f)
                    flex(1f)
                }
            }
            ScoreBadge(analysis.score)
        }
        Text {
            attr {
                text(insight)
                fontSize(14f)
                lineHeight(23f)
                color(AppTheme.textSecondary)
                marginTop(12f)
            }
        }
        if (analysis.tags.isNotEmpty()) {
            View {
                attr { flexDirectionRow(); flexWrapWrap(); marginTop(4f) }
                analysis.tags.forEach { TagChip(it) }
            }
        }
        Text {
            attr {
                text("继续问")
                fontSize(12f)
                fontWeight600()
                color(AppTheme.textTertiary)
                marginTop(12f)
            }
        }
        QuickAskPills(instrument, onAsk)
    }
}

private fun ViewContainer<*, *>.ScoreBadge(score: Int) {
    val color = when {
        score >= 65 -> AppTheme.up
        score <= 38 -> AppTheme.down
        else -> AppTheme.warning
    }
    val label = when {
        score >= 65 -> "偏强"
        score <= 38 -> "偏弱"
        else -> "中性"
    }
    View {
        attr {
            flexDirectionRow(); alignItemsCenter()
            paddingLeft(8f); paddingRight(8f); paddingTop(3f); paddingBottom(3f)
            borderRadius(10f)
            backgroundColor(AppTheme.changeSoftColor(if (score >= 65) 1.0 else if (score <= 38) -1.0 else 0.0))
        }
        Text { attr { text("技术评分 $score"); fontSize(11f); fontWeight600(); color(color) } }
        Text { attr { text(" · $label"); fontSize(11f); color(color) } }
    }
}

// endregion

// region 技术面

fun ViewContainer<*, *>.TechnicalCard(quote: Quote, analysis: TechnicalAnalysis) {
    val rsi = analysis.rsi14
    val rsiLabel = when {
        rsi == null -> "--"
        rsi >= 70 -> "${NumberFormat.fixed(rsi, 1)} 超买"
        rsi <= 30 -> "${NumberFormat.fixed(rsi, 1)} 超卖"
        else -> NumberFormat.fixed(rsi, 1)
    }
    val rsiColor = when {
        rsi == null -> AppTheme.textPrimary
        rsi >= 70 -> AppTheme.down
        rsi <= 30 -> AppTheme.up
        else -> AppTheme.textPrimary
    }
    Card {
        SectionTitle("技术面", IconKind.TREND_UP)
        View {
            attr { flexDirectionRow(); marginTop(12f) }
            listOf("MA5" to analysis.ma5, "MA10" to analysis.ma10, "MA20" to analysis.ma20, "MA60" to analysis.ma60).forEach { (label, value) ->
                View {
                    attr { flex(1f) }
                    Text { attr { text(label); fontSize(11f); color(AppTheme.textTertiary) } }
                    Text {
                        attr {
                            text(NumberFormat.price(value))
                            fontSize(13f)
                            fontWeight600()
                            color(value?.let { AppTheme.changeColor(quote.price - it) } ?: AppTheme.textPrimary)
                            marginTop(2f)
                        }
                    }
                }
            }
        }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider); marginTop(12f); marginBottom(12f) } }
        View {
            attr { flexDirectionRow() }
            TrendCell("短期趋势", analysis.shortTermTrend)
            TrendCell("中期趋势", analysis.midTermTrend)
            View {
                attr { flex(1.2f) }
                Text { attr { text("RSI(14)"); fontSize(11f); color(AppTheme.textTertiary) } }
                Text {
                    attr {
                        text(rsiLabel)
                        fontSize(13f)
                        fontWeight600()
                        color(rsiColor)
                        marginTop(2f)
                    }
                }
            }
        }
        // 支撑 / 压力
        View {
            attr { marginTop(14f) }
            SupportResistanceBar(quote.price, analysis.support, analysis.resistance)
        }
    }
}

private fun ViewContainer<*, *>.TrendCell(label: String, bias: TrendBias) {
    val color = when (bias) {
        TrendBias.BULLISH -> AppTheme.up
        TrendBias.BEARISH -> AppTheme.down
        TrendBias.NEUTRAL -> AppTheme.flat
    }
    View {
        attr { flex(1.3f) }
        Text { attr { text(label); fontSize(11f); color(AppTheme.textTertiary) } }
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginTop(3f) }
            View { attr { size(8f, 8f); borderRadius(4f); backgroundColor(color) } }
            Text { attr { text(bias.label); fontSize(13f); fontWeight600(); color(color); marginLeft(5f) } }
        }
    }
}

private fun ViewContainer<*, *>.SupportResistanceBar(price: Double, support: Double?, resistance: Double?) {
    if (support == null || resistance == null || resistance <= support) return
    val ratio = ((price - support) / (resistance - support)).coerceIn(0.0, 1.0).toFloat()
    val leftWeight = ratio.coerceAtLeast(0.02f)
    val rightWeight = (1f - ratio).coerceAtLeast(0.02f)
    View {
        View {
            attr { flexDirectionRow(); justifyContentSpaceBetween() }
            Text { attr { text("支撑 ${NumberFormat.price(support)}"); fontSize(11f); color(AppTheme.up) } }
            Text { attr { text("现价 ${NumberFormat.price(price)}"); fontSize(11f); fontWeight600(); color(AppTheme.textPrimary) } }
            Text { attr { text("压力 ${NumberFormat.price(resistance)}"); fontSize(11f); color(AppTheme.down) } }
        }
        View {
            attr {
                height(12f)
                marginTop(6f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    flex(leftWeight)
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(AppTheme.ink)
                }
            }
            View {
                attr {
                    width(3f)
                    height(12f)
                    borderRadius(1.5f)
                    backgroundColor(AppTheme.ink)
                    marginLeft(1f)
                    marginRight(1f)
                }
            }
            View {
                attr {
                    flex(rightWeight)
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(AppTheme.surfaceMuted)
                }
            }
        }
    }
}

// endregion

// region 风险

fun ViewContainer<*, *>.RiskCard(risks: List<String>) {
    if (risks.isEmpty()) return
    Card {
        SectionTitle("风险提示", IconKind.ALERT, AppTheme.warning)
        risks.forEach { risk ->
            View {
                attr { flexDirectionRow(); alignItemsFlexStart(); marginTop(10f) }
                View { attr { size(6f, 6f); borderRadius(3f); backgroundColor(AppTheme.warning); marginTop(7f) } }
                Text {
                    attr {
                        text(risk)
                        fontSize(13f)
                        lineHeight(20f)
                        color(AppTheme.textSecondary)
                        marginLeft(8f)
                        flex(1f)
                    }
                }
            }
        }
        Text {
            attr {
                text("以上内容由 AI 基于公开行情自动生成，仅供参考，不构成投资建议。")
                fontSize(11f)
                color(AppTheme.textTertiary)
                marginTop(12f)
            }
        }
    }
}

// endregion

// region 快捷提问

private fun ViewContainer<*, *>.QuickAskPills(instrument: Instrument, onAsk: (String) -> Unit) {
    val items = listOf(
        "后市怎么看" to "${instrument.name}后市如何？",
        "主要风险" to "${instrument.name}有哪些风险？",
        "估值水平" to "${instrument.name}的估值水平怎么看？",
        (if (instrument.isIndex) "成分股" else "同行对比") to
            if (instrument.isIndex) "${instrument.name}成分股表现如何？" else "${instrument.name}和同行业对比如何？",
    )
    View {
        attr { flexDirectionRow(); flexWrapWrap(); marginTop(8f) }
        items.forEach { (label, prompt) ->
            View {
                attr { marginRight(8f); marginBottom(8f) }
                PillButton(label) { onAsk(prompt) }
            }
        }
    }
}

// endregion

internal fun ViewContainer<*, *>.SectionTitle(title: String, icon: IconKind, color: Color = AppTheme.textPrimary) {
    View {
        attr { flexDirectionRow(); alignItemsCenter() }
        View {
            attr { size(24f, 24f); borderRadius(6f); backgroundColor(AppTheme.surfaceMuted); allCenter() }
            Icon(icon, 14f, color)
        }
        Text {
            attr {
                text(title)
                fontSize(15f)
                fontWeight700()
                color(AppTheme.textPrimary)
                marginLeft(8f)
            }
        }
    }
}
