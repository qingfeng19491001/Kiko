package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.Card
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.BrandMark
import com.kuikly.stockchat.ui.components.PillButton
import com.kuikly.stockchat.ui.components.TagChip
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// region 头部行情


/** KuiklyKLineChart 完整 Demo 同款行情头部：左侧 120f 大价格，右侧 3×3 横向指标。 */
fun ViewContainer<*, *>.DemoQuoteOverview(quote: Quote) {
    val changeColor = AppTheme.changeColor(quote.change)
    View {
        attr {
            height(104f)
            paddingLeft(12f); paddingRight(12f); paddingTop(10f); paddingBottom(8f)
            backgroundColor(AppTheme.surface)
            flexDirectionRow(); alignItemsCenter()
        }
        View {
            attr { width(120f); height(86f); justifyContentCenter() }
            Text {
                val priceText = NumberFormat.price(quote.price)
                attr {
                    text(priceText)
                    // 茅台这类千元标的价格 7 个字符，34f 会超出 120f 宽度，缩到 28f 防换行
                    fontSize(if (priceText.length > 6) 28f else 34f)
                    fontWeight600()
                    color(changeColor)
                }
            }
            View {
                attr { height(22f); flexDirectionRow(); alignItemsCenter(); marginTop(2f) }
                Text { attr { text(NumberFormat.signed(quote.change)); fontSize(13f); fontWeight600(); color(changeColor) } }
                Text { attr { text(NumberFormat.signedPct(quote.changePct)); fontSize(13f); fontWeight600(); marginLeft(8f); color(changeColor) } }
            }
        }
        View {
            attr { flex(1f); height(86f); marginLeft(4f); justifyContentCenter() }
            demoQuoteMetrics(quote).chunked(3).forEach { row ->
                View {
                    attr { height(27f); flexDirectionRow(); alignItemsCenter() }
                    row.forEach { (label, value) -> DemoQuoteMetric(label, value) }
                }
            }
        }
    }
}

private fun demoQuoteMetrics(quote: Quote): List<Pair<String, String>> = listOf(
    "高" to NumberFormat.price(quote.high),
    "总值" to NumberFormat.capFromYi(quote.totalMarketCap ?: quote.marketCap),
    "量比" to "--",
    "低" to NumberFormat.price(quote.low),
    "流通" to NumberFormat.capFromYi(quote.marketCap),
    "换手" to NumberFormat.pct(quote.turnoverRate),
    "开" to NumberFormat.price(quote.open),
    "量" to NumberFormat.compact(quote.volume),
    "额" to NumberFormat.compact(quote.turnover),
)

/** 官方 Demo 的指标 cell：label(9f，固定 23f 宽) + value(10f 半粗) 横向排列 */
private fun ViewContainer<*, *>.DemoQuoteMetric(label: String, value: String) {
    View {
        attr { flex(1f); height(27f); flexDirectionRow(); alignItemsCenter() }
        Text { attr { text(label); fontSize(9f); color(Color(0xFF8A94A6L)); width(23f) } }
        Text { attr { text(value); fontSize(10f); fontWeight600(); color(Color(0xFF374151L)) } }
    }
}

// endregion

// region 周期 Tab

/**
 * KuiklyKLineChart 完整 Demo 同款周期栏：
 * 34f 高通栏 + 底部 1px 细线，选中项蓝色加粗；“更多 ▾”弹出分钟级 / 季K / 年K 菜单。
 */
fun ViewContainer<*, *>.PeriodTabs(
    selected: () -> KLinePeriod,
    periodMenuOpen: () -> Boolean,
    onTogglePeriodMenu: () -> Unit,
    onSelect: (KLinePeriod) -> Unit,
) {
    val activeBlue = Color(0xFF1677FFL)
    val inactiveGray = Color(0xFF8C8C8CL)
    View {
        attr { height(34f); backgroundColor(AppTheme.surface) }
        View {
            attr {
                absolutePositionAllZero()
                flexDirectionRow(); alignItemsCenter()
                borderBottom(Border(1f, BorderStyle.SOLID, Color(0xFFE5E7EBL)))
            }
            KLinePeriod.mainTabs.forEach { period ->
                vbind({ selected() == period }) {
                    View {
                        attr { flex(1f); height(34f); allCenter() }
                        event { click { onSelect(period) } }
                        Text {
                            attr {
                                val active = selected() == period
                                text(period.label)
                                fontSize(12f)
                                color(if (active) activeBlue else inactiveGray)
                                if (active) fontWeight600()
                            }
                        }
                    }
                }
            }
            // 更多 ▾
            vbind({ selected().isExtended }) {
                View {
                    attr { flex(1f); height(34f); allCenter(); flexDirectionRow() }
                    event { click { onTogglePeriodMenu() } }
                    val extended = selected().isExtended
                    Text {
                        attr {
                            text(if (extended) selected().label else "更多")
                            fontSize(12f)
                            color(if (extended) activeBlue else inactiveGray)
                            if (extended) fontWeight600()
                        }
                    }
                    Text { attr { text("▾"); fontSize(9f); marginLeft(2f); color(if (extended) activeBlue else inactiveGray) } }
                }
            }
        }
        vif({ periodMenuOpen() }) {
            View {
                attr {
                    positionAbsolute(); top(34f); right(0f); zIndex(50)
                    width(82f); padding(8f); flexDirectionColumn()
                    borderRadius(8f); backgroundColor(AppTheme.surface)
                    boxShadow(BoxShadow(0f, 2f, 8f, Color(0x1F000000L)))
                }
                KLinePeriod.extendedTabs.forEach { period ->
                    vbind({ selected() == period }) {
                        View {
                            attr {
                                height(30f); allCenter(); borderRadius(5f)
                                backgroundColor(if (selected() == period) Color(0x1A1677FFL) else Color.TRANSPARENT)
                            }
                            event { click { onSelect(period) } }
                            Text {
                                attr {
                                    text(period.label)
                                    fontSize(12f)
                                    color(if (selected() == period) activeBlue else Color(0xFF595959L))
                                }
                            }
                        }
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

/** 估值区分为“已知数据”和“暂无数据”，比空白字段更适合演示与真实 API 切换。 */
fun ViewContainer<*, *>.ValuationCard(quote: Quote) {
    Card {
        SectionTitle("估值与规模", IconKind.BOOK)
        View {
            attr { flexDirectionRow(); marginTop(12f) }
            ValuationCell("市盈率", NumberFormat.ratio(quote.pe), quote.pe?.let { if (it <= 20) AppTheme.up else AppTheme.warning })
            ValuationCell("市净率", NumberFormat.ratio(quote.pb), null)
            ValuationCell("总市值", NumberFormat.capFromYi(quote.totalMarketCap ?: quote.marketCap), null)
        }
        Text {
            attr {
                text(if (quote.pe != null || quote.pb != null) "估值仅作横向参考，需结合行业景气与盈利质量判断。" else "该标的暂未返回估值数据，可在接入真实行情接口后补充。")
                fontSize(11f); lineHeight(17f); color(AppTheme.textTertiary); marginTop(12f)
            }
        }
    }
}

private fun ViewContainer<*, *>.ValuationCell(label: String, value: String, color: Color?) {
    View {
        attr { flex(1f) }
        Text { attr { text(label); fontSize(11f); color(AppTheme.textTertiary) } }
        Text { attr { text(value); fontSize(14f); fontWeight600(); color(color ?: AppTheme.textPrimary); marginTop(3f) } }
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

fun ViewContainer<*, *>.PredictionCard(vm: StockDetailViewModel) {
    Card {
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.SPARKLE, 16f, AppTheme.accent)
            Text {
                attr {
                    text("AI 情景预测")
                    fontSize(15f)
                    fontWeight700()
                    color(AppTheme.textPrimary)
                    marginLeft(8f)
                    flex(1f)
                }
            }
        }
        Text {
            attr {
                text("仅在模型请求成功并通过校验后展示，不绘制本地伪造曲线。演示信息，不构成投资建议。")
                fontSize(11f)
                color(AppTheme.textTertiary)
                marginTop(8f)
            }
        }
        when (vm.predictionState) {
            StockDetailViewModel.PredictionState.LOADING, StockDetailViewModel.PredictionState.IDLE -> {
                Text {
                    attr {
                        text("正在向百炼请求未来 5 个交易日情景…")
                        fontSize(13f)
                        color(AppTheme.textSecondary)
                        marginTop(12f)
                    }
                }
            }
            StockDetailViewModel.PredictionState.UNAVAILABLE, StockDetailViewModel.PredictionState.FAILED -> {
                Text {
                    attr {
                        text(vm.predictionMessage.ifBlank { "预测暂不可用" })
                        fontSize(13f)
                        color(AppTheme.warning)
                        marginTop(12f)
                    }
                }
            }
            StockDetailViewModel.PredictionState.READY -> {
                val prediction = vm.prediction
                if (prediction == null) {
                    Text {
                        attr {
                            text("预测结果为空")
                            fontSize(13f)
                            color(AppTheme.warning)
                            marginTop(12f)
                        }
                    }
                } else {
                    Text {
                        attr {
                            text("${prediction.direction}  ·  置信度 ${(prediction.confidence * 100).toInt()}%")
                            fontSize(14f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                            marginTop(12f)
                        }
                    }
                    Text {
                        attr {
                            text(prediction.rationale)
                            fontSize(13f)
                            lineHeight(21f)
                            color(AppTheme.textSecondary)
                            marginTop(8f)
                        }
                    }
                    View {
                        attr { flexDirectionRow(); flexWrapWrap(); marginTop(10f) }
                        prediction.points.forEach { point ->
                            val selected = vm.selectedBar?.date == point.date && vm.selectedBar?.isForecast == true
                            View {
                                attr {
                                    paddingLeft(8f); paddingRight(8f); paddingTop(6f); paddingBottom(6f)
                                    backgroundColor(if (selected) AppTheme.accentSoft else AppTheme.surfaceMuted)
                                    borderRadius(8f)
                                    marginRight(6f)
                                    marginTop(6f)
                                    if (selected) border(Border(1f, BorderStyle.SOLID, AppTheme.accent))
                                }
                                event {
                                    click {
                                        val bars = vm.forecastBars
                                        val index = bars.indexOfFirst { it.date.take(10) == point.date }
                                        val bar = bars.getOrNull(index)
                                        val prev = if (index <= 0) vm.dailyBars.lastOrNull()?.close else bars.getOrNull(index - 1)?.close
                                        if (bar != null) vm.selectPoint(SelectedChartPoint.fromBar(bar, prev, isForecast = true))
                                    }
                                }
                                Text {
                                    attr {
                                        text("${point.date.takeLast(5)}  ${NumberFormat.price(point.price)}")
                                        fontSize(11f)
                                        color(if (selected) AppTheme.accent else AppTheme.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

fun ViewContainer<*, *>.ChartFollowUpBar(vm: StockDetailViewModel, onAsk: () -> Unit) {
    val points = vm.stripPoints()
    if (points.isEmpty()) return
    Card {
        Text {
            attr {
                text("点位条")
                fontSize(13f)
                fontWeight700()
                color(AppTheme.textPrimary)
            }
        }
        Text {
            attr {
                text("点选历史 K 或情景预测日，再带回对话追问。KuiklyKLineChart 无选中回调时用此条代替。")
                fontSize(11f)
                color(AppTheme.textTertiary)
                marginTop(6f)
            }
        }
        View {
            attr { flexDirectionRow(); flexWrapWrap(); marginTop(8f) }
            points.forEach { point ->
                val selected = vm.selectedBar?.date == point.date && vm.selectedBar?.isForecast == point.isForecast
                View {
                    attr {
                        paddingLeft(8f); paddingRight(8f); paddingTop(6f); paddingBottom(6f)
                        backgroundColor(if (selected) AppTheme.accentSoft else AppTheme.surfaceMuted)
                        borderRadius(8f)
                        marginRight(6f)
                        marginTop(6f)
                        if (selected) border(Border(1f, BorderStyle.SOLID, AppTheme.accent))
                    }
                    event { click { vm.selectPoint(point) } }
                    Text {
                        attr {
                            text("${point.date.takeLast(5)}${if (point.isForecast) " 预" else ""}")
                            fontSize(11f)
                            color(if (selected) AppTheme.accent else AppTheme.textPrimary)
                        }
                    }
                }
            }
        }
        vm.selectedBar?.let { selected ->
            val change = selected.changePct?.let { NumberFormat.signedPct(it) }.orEmpty()
            Text {
                attr {
                    text(
                        buildString {
                            append(if (selected.isForecast) "情景预测  " else "历史K  ")
                            append(selected.date)
                            append("  收 ")
                            append(NumberFormat.price(selected.close))
                            if (change.isNotEmpty()) append("  ").append(change)
                        },
                    )
                    fontSize(13f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginTop(10f)
                }
            }
            View { attr { marginTop(10f) } }
            PillButton(
                "把这一点带回对话",
                background = AppTheme.primaryLight,
                textColor = AppTheme.primary,
                borderColor = AppTheme.primarySoft,
            ) { onAsk() }
        }
    }
}

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
