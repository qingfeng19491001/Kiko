package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.DetailActionPlan
import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.PeerUniverse
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.InstrumentAvatar
import com.kuikly.stockchat.ui.components.TagChip
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View


/**
 * 详情页基础行情下方的页内 Tab。
 * 一级：诊股 / 简况 / 技术 / 资金 / 板块；二级随一级切换，避免长页堆叠。
 */
internal enum class DetailTab(val label: String, val subTabs: List<String>) {
    DIAGNOSIS("诊股", listOf("全部", "解读", "点位", "预测", "追问", "风险")),
    PROFILE("简况", listOf("全部", "指标", "估值")),
    TECH("技术", emptyList()),
    FLOW("资金", emptyList()),
    SECTOR("板块", emptyList()),
}

internal fun ViewContainer<*, *>.DetailTabBar(
    selectedTab: () -> DetailTab,
    selectedSubTab: () -> String,
    onSelectTab: (DetailTab) -> Unit,
    onSelectSubTab: (String) -> Unit,
) {
    val muted = AppTheme.textTertiary
    View {
        attr { backgroundColor(AppTheme.surface) }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
        View {
            attr {
                height(44f)
                flexDirectionRow()
                borderBottom(Border(0.5f, BorderStyle.SOLID, AppTheme.divider))
            }
            DetailTab.entries.forEach { tab ->
                vbind({ selectedTab() == tab }) {
                    val active = selectedTab() == tab
                    View {
                        attr { flex(1f); height(44f); allCenter() }
                        event { click { onSelectTab(tab) } }
                        Text {
                            attr {
                                text(tab.label)
                                fontSize(if (active) 15f else 14f)
                                color(if (active) AppTheme.textPrimary else muted)
                                if (active) fontWeight700()
                            }
                        }
                        View {
                            attr {
                                absolutePosition(left = 0f, right = 0f, bottom = 0f)
                                height(2.5f)
                                alignItemsCenter()
                            }
                            View {
                                attr {
                                    width(18f)
                                    height(2.5f)
                                    borderRadius(1.5f)
                                    backgroundColor(if (active) AppTheme.ink else Color.TRANSPARENT)
                                }
                            }
                        }
                    }
                }
            }
        }
        vbind({ selectedTab().name }) {
            val subs = selectedTab().subTabs
            if (subs.isEmpty()) {
                View { attr { height(0f) } }
            } else {
                View {
                    attr {
                        height(36f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(6f)
                        paddingRight(8f)
                        borderBottom(Border(0.5f, BorderStyle.SOLID, AppTheme.divider))
                    }
                    subs.forEach { label ->
                        vbind({ selectedSubTab() == label }) {
                            val active = selectedSubTab() == label
                            View {
                                attr {
                                    height(36f)
                                    paddingLeft(10f)
                                    paddingRight(10f)
                                    justifyContentCenter()
                                }
                                event { click { onSelectSubTab(label) } }
                                Text {
                                    attr {
                                        text(label)
                                        fontSize(12f)
                                        color(if (active) AppTheme.textPrimary else muted)
                                        if (active) fontWeight600()
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

internal fun ViewContainer<*, *>.renderDiagnosisPane(
    vm: StockDetailViewModel,
    instrument: Instrument,
    subTab: String,
    onAsk: (String) -> Unit,
    onAskSelection: () -> Unit,
    fromChat: Boolean = false,
    chatSummary: String = "",
) {
    val showAll = subTab == "全部"
    val analysis = vm.analysis
    val quote = vm.quote
    val plan = if (quote != null && analysis != null) DetailActionPlan.from(quote, analysis) else null
    if (fromChat && (showAll || subTab == "解读")) {
        ChatHandoffBanner(instrument, chatSummary, onAsk)
    }
    if (showAll || subTab == "解读") {
        if (analysis != null) {
            AiInsightCard(vm.insight, analysis, instrument, onAsk, plan)
        }
    }
    if ((showAll || subTab == "点位") && plan != null && quote != null) {
        ActionLevelsCard(quote, analysis!!, plan)
    } else if (subTab == "点位") {
        DetailEmptyHint("行情加载后将给出关注区与压力区。")
    }
    if (showAll || subTab == "预测") {
        PredictionCard(vm)
    }
    if (showAll || subTab == "追问") {
        if (vm.stripPoints().isNotEmpty()) {
            ChartFollowUpBar(vm, onAskSelection)
        } else if (subTab == "追问") {
            DetailEmptyHint("暂无可用点位。切换到日 K 后可点选再带回对话。")
        }
    }
    if (showAll || subTab == "风险") {
        if (vm.risks.isEmpty() && subTab == "风险") {
            DetailEmptyHint("暂无风险提示。")
        } else {
            RiskCard(vm.risks)
        }
    }
}

internal fun ViewContainer<*, *>.renderProfilePane(quote: Quote, analysis: TechnicalAnalysis?, subTab: String) {
    val showAll = subTab == "全部"
    if (showAll || subTab == "指标") {
        MetricsCard(quote, analysis)
    }
    if (showAll || subTab == "估值") {
        ValuationCard(quote)
    }
}

internal fun ViewContainer<*, *>.renderTechPane(quote: Quote, analysis: TechnicalAnalysis, period: KLinePeriod) {
    TechnicalCard(quote, analysis, period)
}

internal fun ViewContainer<*, *>.renderFlowPane(
    quote: Quote,
    flow: CapitalFlowData?,
    loading: Boolean,
    onAsk: () -> Unit,
) {
    TabBlock {
        TabHeading("资金与活跃度")
        View {
            attr { flexDirectionRow(); marginTop(12f) }
            ValuationCell("成交额", NumberFormat.compact(quote.turnover), null)
            ValuationCell("成交量", NumberFormat.compact(quote.volume), null)
            ValuationCell("换手率", NumberFormat.pct(quote.turnoverRate), null)
        }
        View {
            attr { flexDirectionRow(); marginTop(12f) }
            ValuationCell("振幅", NumberFormat.pct(quote.amplitude), null)
            ValuationCell("量比", "--", null)
            View { attr { flex(1f) } }
        }
        when {
            loading -> Text {
                attr {
                    text("正在获取主力资金流向…")
                    fontSize(12f)
                    color(AppTheme.textTertiary)
                    marginTop(12f)
                }
            }
            flow != null && flow.flows.isNotEmpty() -> {
                Text {
                    attr {
                        text("主力资金（${flow.date}）")
                        fontSize(12f)
                        fontWeight600()
                        color(AppTheme.textPrimary)
                        marginTop(14f)
                    }
                }
                flow.flows.forEach { item ->
                    val color = AppTheme.changeColor(item.netInflow)
                    val amount = (if (item.netInflow > 0) "+" else "") + NumberFormat.compact(item.netInflow)
                    val pct = (if (item.pct > 0) "+" else "") + NumberFormat.fixed(item.pct, 2) + "%"
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(10f)
                        }
                        Text { attr { text(item.label); fontSize(13f); color(AppTheme.textSecondary); width(52f) } }
                        Text {
                            attr {
                                text(amount)
                                fontSize(13f)
                                fontWeight600()
                                color(color)
                                flex(1f)
                            }
                        }
                        Text { attr { text(pct); fontSize(12f); fontWeight600(); color(color) } }
                    }
                }
            }
            else -> Text {
                attr {
                    text("资金流向当前未接入。指数与日 K 不受影响，也可向对话询问该股后市。")
                    fontSize(12f)
                    lineHeight(18f)
                    color(AppTheme.textTertiary)
                    marginTop(12f)
                }
            }
        }
        TabAskLink("问 AI 资金流向", onAsk)
    }
}

internal fun ViewContainer<*, *>.renderSectorPane(
    instrument: Instrument,
    onOpen: (Instrument) -> Unit,
    onAsk: (String) -> Unit,
) {
    TabBlock {
        TabHeading("所属板块")
        Text {
            attr {
                text(instrument.sector.ifBlank { "暂无板块分类" })
                fontSize(16f)
                fontWeight600()
                color(AppTheme.textPrimary)
                marginTop(10f)
            }
        }
        Text {
            attr {
                text("同业来自内置目录，点击可打开对方详情；报价以详情页实时行情为准。")
                fontSize(12f)
                lineHeight(18f)
                color(AppTheme.textTertiary)
                marginTop(6f)
            }
        }
    }
    val peers = PeerUniverse.peersOf(instrument, 6)
    if (peers.isEmpty()) {
        DetailEmptyHint(if (instrument.isIndex) "指数没有同业个股列表。" else "目录中暂无同市场同业标的。")
    } else {
        peers.forEachIndexed { index, peer ->
            View {
                attr {
                    height(56f)
                    flexDirectionRow()
                    alignItemsCenter()
                    if (index < peers.lastIndex) {
                        borderBottom(Border(0.5f, BorderStyle.SOLID, AppTheme.divider))
                    }
                }
                event { click { onOpen(peer) } }
                InstrumentAvatar(peer, 36f)
                View {
                    attr { flex(1f); marginLeft(10f) }
                    Text {
                        attr {
                            text(peer.name)
                            fontSize(15f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("${peer.displayCode}  ·  ${peer.sector.ifBlank { "—" }}")
                            fontSize(11f)
                            color(AppTheme.textTertiary)
                            marginTop(2f)
                        }
                    }
                }
                Icon(IconKind.CHEVRON_RIGHT, 16f, AppTheme.textTertiary)
            }
        }
    }
    TabAskLink(
        "问 AI 板块对比",
        {
            onAsk(
                if (instrument.isIndex) "${instrument.name}成分股表现如何？"
                else "${instrument.name}和同行业对比如何？",
            )
        },
    )
}

private fun ViewContainer<*, *>.TabBlock(
    showDivider: Boolean = true,
    minHeight: Float = 0f,
    content: DivView.() -> Unit,
) {
    View {
        attr {
            paddingTop(14f)
            paddingBottom(14f)
            if (minHeight > 0f) minHeight(minHeight)
        }
        content()
    }
    if (showDivider) {
        View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
    }
}

private fun ViewContainer<*, *>.TabHeading(title: String, color: Color = AppTheme.textPrimary) {
    Text {
        attr {
            text(title)
            fontSize(16f)
            fontWeight600()
            color(color)
        }
    }
}

private fun ViewContainer<*, *>.TabAskLink(text: String, onClick: () -> Unit) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            height(36f)
            marginTop(12f)
        }
        event { click { onClick() } }
        Text {
            attr {
                text(text)
                fontSize(14f)
                fontWeight600()
                color(AppTheme.textPrimary)
            }
        }
        Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.textTertiary)
    }
}

private fun ViewContainer<*, *>.DetailEmptyHint(text: String) {
    View {
        attr {
            paddingTop(20f)
            paddingBottom(12f)
            alignItemsCenter()
        }
        Text {
            attr {
                text(text)
                fontSize(13f)
                color(AppTheme.textTertiary)
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
    TabBlock {
        TabHeading("关键指标")
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
    TabBlock {
        TabHeading("估值与规模")
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
    plan: DetailActionPlan? = null,
    minHeight: Float = 0f,
) {
    TabBlock(minHeight = minHeight) {
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            TabHeading("AI 分析与解读")
            View { attr { flex(1f) } }
            ScoreBadge(analysis.score)
        }
        plan?.let { ActionStanceRow(it) }
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
        plan?.let { SignalBoard(it) }
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

private fun ViewContainer<*, *>.ChatHandoffBanner(
    instrument: Instrument,
    summary: String,
    onAsk: (String) -> Unit,
) {
    TabBlock {
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            TabHeading("对话承接")
            View { attr { flex(1f) } }
            Text {
                attr {
                    text("来自问答")
                    fontSize(11f)
                    fontWeight600()
                    color(AppTheme.accent)
                }
            }
        }
        Text {
            attr {
                text(
                    summary.ifBlank {
                        "从聊天点进 ${instrument.name}。上方是实时行情与走势，下面是本页根据日 K 生成的解读。"
                    },
                )
                fontSize(13f)
                lineHeight(21f)
                color(AppTheme.textSecondary)
                marginTop(10f)
            }
        }
        TabAskLink("继续在对话里问 ${instrument.name}") {
            onAsk("结合刚才的结论，再解读一下${instrument.name}")
        }
    }
}

private fun ViewContainer<*, *>.ActionStanceRow(plan: DetailActionPlan) {
    val toneColor = AppTheme.toneColor(plan.stanceTone)
    View {
        attr {
            marginTop(10f)
            backgroundColor(AppTheme.toneSoftColor(plan.stanceTone))
            borderRadius(8f)
            paddingLeft(10f)
            paddingRight(10f)
            paddingTop(8f)
            paddingBottom(8f)
        }
        Text {
            attr {
                text("操作提示 · ${plan.stance}")
                fontSize(12f)
                fontWeight600()
                color(toneColor)
            }
        }
        Text {
            attr {
                text(plan.operationTip)
                fontSize(13f)
                lineHeight(20f)
                color(AppTheme.textSecondary)
                marginTop(4f)
            }
        }
    }
}

private fun ViewContainer<*, *>.SignalBoard(plan: DetailActionPlan) {
    plan.signals.chunked(2).forEach { row ->
        View {
            attr { flexDirectionRow(); marginTop(10f) }
            row.forEach { signal ->
                View {
                    attr { flex(1f); marginRight(10f) }
                    Text { attr { text(signal.title); fontSize(10f); color(AppTheme.textTertiary) } }
                    Text {
                        attr {
                            text(signal.value)
                            fontSize(14f)
                            fontWeight600()
                            color(AppTheme.toneColor(signal.tone))
                            marginTop(2f)
                        }
                    }
                    Text {
                        attr {
                            text(signal.hint)
                            fontSize(11f)
                            color(AppTheme.textTertiary)
                            marginTop(2f)
                        }
                    }
                }
            }
            repeat(2 - row.size) { View { attr { flex(1f) } } }
        }
    }
}

private fun ViewContainer<*, *>.ActionLevelsCard(
    quote: Quote,
    analysis: TechnicalAnalysis,
    plan: DetailActionPlan,
) {
    TabBlock {
        TabHeading("关注 / 压力点位")
        Text {
            attr {
                text("由近 20 日高低点推算，仅作观察参考，不是下单指令。")
                fontSize(11f)
                color(AppTheme.textTertiary)
                marginTop(6f)
            }
        }
        View {
            attr { flexDirectionRow(); marginTop(12f) }
            plan.levels.forEach { level ->
                View {
                    attr { flex(1f); marginRight(8f) }
                    Text { attr { text(level.label); fontSize(11f); color(AppTheme.toneColor(level.tone)) } }
                    Text {
                        attr {
                            text(level.price)
                            fontSize(16f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                            marginTop(3f)
                        }
                    }
                    Text {
                        attr {
                            text(level.note)
                            fontSize(10f)
                            lineHeight(15f)
                            color(AppTheme.textTertiary)
                            marginTop(4f)
                        }
                    }
                }
            }
        }
        View { attr { marginTop(14f) } }
        SupportResistanceBar(quote.price, analysis.support, analysis.resistance)
    }
}

fun ViewContainer<*, *>.PredictionCard(vm: StockDetailViewModel) {
    TabBlock {
        TabHeading("AI 情景预测")
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
                                    marginRight(12f)
                                    marginTop(8f)
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
                                        fontSize(13f)
                                        fontWeight600()
                                        color(if (selected) AppTheme.textPrimary else AppTheme.textTertiary)
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
        attr { flexDirectionRow(); alignItemsCenter() }
        Text { attr { text("技术评分 $score"); fontSize(12f); fontWeight600(); color(color) } }
        Text { attr { text(" · $label"); fontSize(12f); color(color) } }
    }
}

// endregion

// region 技术面

fun ViewContainer<*, *>.TechnicalCard(quote: Quote, analysis: TechnicalAnalysis, period: KLinePeriod? = null) {
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
    TabBlock {
        TabHeading("技术面")
        period?.let { current ->
            Text {
                attr {
                    text(
                        if (current == KLinePeriod.MINUTE) "分时 · 分钟 K，有 iTick 走分钟线，否则回退腾讯"
                        else "${current.label} · 双指缩放，已叠加 MA5 / MA10 / MA20",
                    )
                    fontSize(11f)
                    color(AppTheme.textTertiary)
                    marginTop(6f)
                }
            }
        }
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
    TabBlock {
        TabHeading("风险提示", AppTheme.warning)
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
    TabBlock {
        TabHeading("点位条")
        Text {
            attr {
                text("点选历史 K 或情景预测日，或点图上 K 线，再带回对话追问。")
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
                        marginRight(14f)
                        marginTop(8f)
                    }
                    event { click { vm.selectPoint(point) } }
                    Text {
                        attr {
                            text("${point.date.takeLast(5)}${if (point.isForecast) " 预" else ""}")
                            fontSize(13f)
                            fontWeight600()
                            color(if (selected) AppTheme.textPrimary else AppTheme.textTertiary)
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
            TabAskLink("把这一点带回对话", onAsk)
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
                attr {
                    height(32f)
                    justifyContentCenter()
                    marginRight(16f)
                    marginBottom(4f)
                }
                event { click { onAsk(prompt) } }
                Text {
                    attr {
                        text(label)
                        fontSize(13f)
                        fontWeight600()
                        color(AppTheme.textPrimary)
                    }
                }
            }
        }
    }
}

// endregion

