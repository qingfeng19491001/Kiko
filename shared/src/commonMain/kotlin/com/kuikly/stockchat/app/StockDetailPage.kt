package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.ai.StockPredictionService
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.domain.chat.ChartFollowUpPrompt
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.InstrumentCodec
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.PrimaryButton
import com.kuikly.stockchat.ui.components.Spacer
import com.kuikly.stockchat.ui.detail.AiInsightCard
import com.kuikly.stockchat.ui.detail.ChartFollowUpBar
import com.kuikly.stockchat.ui.detail.MetricsCard
import com.kuikly.stockchat.ui.detail.PeriodTabs
import com.kuikly.stockchat.ui.detail.PredictionCard
import com.kuikly.stockchat.ui.detail.DemoQuoteOverview
import com.kuikly.stockchat.ui.detail.RiskCard
import com.kuikly.stockchat.ui.detail.StockDetailViewModel
import com.kuikly.stockchat.ui.detail.TechnicalCard
import com.kuikly.stockchat.ui.detail.ValuationCard
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 主图指标（官方完整 Demo 同款可选项） */
private enum class MainIndicator(val label: String, val template: String?, val params: List<Int> = emptyList()) {
    BARE("裸K", null),
    MA("MA", "MA", listOf(5, 10, 20, 30)),
    BOLL("BOLL", "BOLL"),
    EXPMA("EXPMA", "EXPMA"),
    BBI("BBI", "BBI"),
    ENE("ENE", "ENE"),
}

/** 第一副图指标 */
private enum class FirstIndicator(val label: String, val template: String) {
    VOLUME("成交量", "VOL"),
    MACD("MACD", "MACD"),
    AMOUNT("成交额", "AMOUNT"),
}

/** 第二副图指标 */
private enum class SecondIndicator(val label: String, val template: String) {
    MACD("MACD", "MACD"),
    KDJ("KDJ", "KDJ"),
    RSI("RSI", "RSI"),
    WR("WR", "WR"),
    BBD("BBD", "BBD"),
}

/** 当前打开的浮层菜单：周期 / 主图指标 / 第一副图 / 第二副图 */
private enum class ChartMenu { PERIOD, MAIN, FIRST, SECOND }

private data class PopupPlacement(val top: Float, val opensUpward: Boolean)

/** 与官方 Demo 一致：菜单优先向下展开，放不下则向上 */
private fun popupPlacement(anchorTop: Float, anchorHeight: Float, optionCount: Int, containerHeight: Float): PopupPlacement {
    val menuHeight = optionCount * 27f + 10f
    val belowTop = anchorTop + anchorHeight
    return if (belowTop + menuHeight <= containerHeight) {
        PopupPlacement(belowTop, opensUpward = false)
    } else {
        PopupPlacement((anchorTop - menuHeight).coerceAtLeast(0f), opensUpward = true)
    }
}

/**
 * 个股 / 指数详情页：行情头部 + 周期栏 + 完整 K 线工作区（一主图二副图），
 * 上半部分完整还原 KuiklyKLineChart 官方 FullChartDemo，下半部分为本项目的 AI 解读卡片。
 */
@Page("StockDetail")
internal class StockDetailPage : Pager() {

    internal lateinit var vm: StockDetailViewModel
    internal lateinit var instrument: Instrument
    internal var instrumentMissing by observable(false)

    // 指标与菜单状态（对齐官方 FullChartDemo）
    private var mainIndicator by observable(MainIndicator.MA)
    private var firstIndicator by observable(FirstIndicator.VOLUME)
    private var secondIndicator by observable(SecondIndicator.MACD)
    private var openMenu by observable<ChartMenu?>(null)
    private var priceHeaderTop by observable(0f)
    private var firstHeaderTop by observable(0f)
    private var secondHeaderTop by observable(0f)

    override fun created() {
        super.created()
        val key = pageData.params.optString("instrumentKey")
        val resolved = InstrumentCodec.decode(pageData.params.optJSONObject("instrument"))
            ?: InstrumentCache.get(key)
            ?: StockCatalog.findByKey(key)
        instrumentMissing = resolved == null
        instrument = resolved ?: Instrument(code = "----", name = "未知标的", market = com.kuikly.stockchat.domain.model.Market.SH)
        InstrumentCache.put(instrument)
        val store = KuiklyKeyValueStore(this)
        val http = KuiklyHttpClient(this)
        vm = StockDetailViewModel(
            instrument = instrument,
            marketRepository = MarketRepository(http),
            watchlistRepository = WatchlistRepository(store),
            predictionService = StockPredictionService(http),
        )
        if (instrumentMissing) {
            vm.loadState = StockDetailViewModel.LoadState.ERROR
        } else {
            vm.load()
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val bottomInset = pagerData.safeAreaInsets.bottom
        // 一主图二副图（weight 3.6/1.15/1.15，minHeight 180/74/74）+ 坐标轴的舒适高度
        val chartHeight = 420f
        return {
            attr { backgroundColor(AppTheme.background) }

            // 导航栏
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(AppTheme.surface)
                }
                View {
                    attr {
                        height(AppTheme.navBarHeight)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(8f); paddingRight(8f)
                    }
                    IconButton(IconKind.BACK) { ctx.close() }
                    View {
                        attr { flex(1f); alignItemsCenter() }
                        Text {
                            attr {
                                text(ctx.instrument.name)
                                fontSize(16f)
                                fontWeight700()
                                color(AppTheme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.instrument.displayCode)
                                fontSize(10f)
                                color(AppTheme.textTertiary)
                            }
                        }
                    }
                    vif({ ctx.vm.isWatching }) {
                        IconButton(IconKind.STAR_FILLED, color = AppTheme.warning) { ctx.vm.toggleWatch() }
                    }
                    velse {
                        IconButton(IconKind.STAR) { ctx.vm.toggleWatch() }
                    }
                }
                View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
            }

            vif({ ctx.vm.loadState == StockDetailViewModel.LoadState.LOADING }) {
                View {
                    attr { flex(1f); allCenter() }
                    ActivityIndicator { attr { isGrayStyle(true) } }
                    Text {
                        attr {
                            text("正在获取 ${ctx.instrument.name} 行情…")
                            fontSize(13f)
                            color(AppTheme.textSecondary)
                            marginTop(12f)
                        }
                    }
                }
            }
            velseif({ ctx.vm.loadState == StockDetailViewModel.LoadState.ERROR }) {
                View {
                    attr { flex(1f); allCenter(); padding(32f) }
                    Icon(IconKind.WIFI_OFF, 44f, AppTheme.textTertiary, 1.4f)
                    Text {
                        attr {
                            text(if (ctx.instrumentMissing) "无法识别该标的，未打开默认股票" else "行情加载失败，请检查网络后重试")
                            fontSize(14f)
                            color(AppTheme.textSecondary)
                            marginTop(12f)
                            marginBottom(16f)
                        }
                    }
                    if (!ctx.instrumentMissing) {
                        PrimaryButton("重新加载", IconKind.REFRESH, flex = false) { ctx.vm.load() }
                    }
                }
            }
            velse {
                List {
                    attr {
                        flex(1f)
                        showScrollerIndicator(false)
                    }
                    View {
                        attr { paddingBottom(bottomInset + 24f) }

                        vif({ ctx.vm.isMock }) {
                            View {
                                attr {
                                    flexDirectionRow(); alignItemsCenter()
                                    backgroundColor(AppTheme.warningSoft)
                                    borderRadius(10f)
                                    paddingLeft(12f); paddingRight(12f); paddingTop(8f); paddingBottom(8f)
                                    marginLeft(AppTheme.pageHorizontalPadding)
                                    marginRight(AppTheme.pageHorizontalPadding)
                                    marginTop(10f)
                                    marginBottom(2f)
                                }
                                Icon(IconKind.WIFI_OFF, 14f, AppTheme.warning)
                                Text {
                                    attr {
                                        text("行情接口不可用，当前展示离线演示数据")
                                        fontSize(12f)
                                        color(AppTheme.warning)
                                        marginLeft(6f)
                                    }
                                }
                            }
                        }

                        // ===== 官方 FullChartDemo 结构：行情头部 → 周期栏 → K 线工作区（通栏白底） =====
                        vbind({ ctx.vm.quote }) { ctx.vm.quote?.let { DemoQuoteOverview(it) } }

                        PeriodTabs(
                            selected = { ctx.vm.period },
                            periodMenuOpen = { ctx.openMenu == ChartMenu.PERIOD },
                            onTogglePeriodMenu = {
                                ctx.openMenu = if (ctx.openMenu == ChartMenu.PERIOD) null else ChartMenu.PERIOD
                            },
                        ) { period ->
                            ctx.openMenu = null
                            ctx.vm.selectPeriod(period)
                        }

                        // K 线工作区：图表 + pane 头部指标切换按钮 + 浮层菜单
                        View {
                            attr { height(chartHeight); backgroundColor(AppTheme.surface) }
                            vif({ ctx.vm.chartLoading }) {
                                ChartPlaceholder("走势加载中…")
                            }
                            velse {
                                vif({ ctx.vm.barsJson.isEmpty() }) {
                                    ChartPlaceholder("暂无 K 线数据", loading = false)
                                }
                                velse {
                                    vbind({
                                        listOf(
                                            ctx.vm.period.name,
                                            ctx.mainIndicator.name,
                                            ctx.firstIndicator.name,
                                            ctx.secondIndicator.name,
                                            ctx.openMenu?.name ?: "none",
                                            ctx.vm.barsJson.hashCode(),
                                            ctx.vm.predictionState.name,
                                            ctx.vm.prediction?.points?.joinToString { it.date }.orEmpty(),
                                        ).joinToString("|")
                                    }) {
                                        View {
                                            attr { flex(1f) }
                                            PlatformKLineChart(
                                                flexValue = 1f,
                                                symbolCode = ctx.instrument.displayCode,
                                                symbolName = ctx.instrument.name,
                                                periodValue = ctx.vm.period.span,
                                                periodUnit = ctx.vm.period.unit,
                                                // full 模式才会开启坐标轴、副图、十字光标和缩放交互
                                                modeName = "full",
                                                themeName = "light",
                                                barsJson = ctx.vm.barsJson,
                                                configJson = ctx.fullConfig(),
                                                priceStyleName = if (ctx.vm.period.line) "line" else "candle",
                                                touchEnabled = ctx.openMenu == null,
                                                onError = { code, message ->
                                                    println("KLineChart error: $code $message")
                                                },
                                                onPaneLayoutChange = { price, first, second ->
                                                    ctx.priceHeaderTop = price
                                                    ctx.firstHeaderTop = first
                                                    ctx.secondHeaderTop = second
                                                },
                                                onPaneHeaderClick = { paneId ->
                                                    val menu = when (paneId) {
                                                        "price" -> ChartMenu.MAIN
                                                        "first" -> ChartMenu.FIRST
                                                        "second" -> ChartMenu.SECOND
                                                        else -> null
                                                    }
                                                    if (menu != null) {
                                                        ctx.openMenu = if (ctx.openMenu == menu) null else menu
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    // pane 头部指标切换按钮（MA ▾ / 成交量 ▾ / MACD ▾）
                                    vbind({ "${ctx.priceHeaderTop}:${ctx.mainIndicator.name}" }) {
                                        ctx.paneMenuButton(ctx.mainIndicator.label, ChartMenu.MAIN, ctx.priceHeaderTop).invoke(this)
                                    }
                                    vbind({ "${ctx.firstHeaderTop}:${ctx.firstIndicator.name}" }) {
                                        ctx.paneMenuButton(ctx.firstIndicator.label, ChartMenu.FIRST, ctx.firstHeaderTop).invoke(this)
                                    }
                                    vbind({ "${ctx.secondHeaderTop}:${ctx.secondIndicator.name}" }) {
                                        ctx.paneMenuButton(ctx.secondIndicator.label, ChartMenu.SECOND, ctx.secondHeaderTop).invoke(this)
                                    }
                                    // 点击空白处关闭菜单
                                    vif({ ctx.openMenu != null }) {
                                        View {
                                            attr { positionAbsolute(); absolutePositionAllZero(); zIndex(45) }
                                            event { click { ctx.openMenu = null } }
                                        }
                                    }
                                    // 指标切换菜单
                                    vif({ ctx.openMenu == ChartMenu.MAIN }) {
                                        ctx.optionColumn(MainIndicator.entries, ctx.priceHeaderTop, chartHeight, { it.label }, { it == ctx.mainIndicator }) { ctx.mainIndicator = it }.invoke(this)
                                    }
                                    vif({ ctx.openMenu == ChartMenu.FIRST }) {
                                        ctx.optionColumn(FirstIndicator.entries, ctx.firstHeaderTop, chartHeight, { it.label }, { it == ctx.firstIndicator }) { ctx.firstIndicator = it }.invoke(this)
                                    }
                                    vif({ ctx.openMenu == ChartMenu.SECOND }) {
                                        ctx.optionColumn(SecondIndicator.entries, ctx.secondHeaderTop, chartHeight, { it.label }, { it == ctx.secondIndicator }) { ctx.secondIndicator = it }.invoke(this)
                                    }
                                }
                            }
                        }

                        // ===== 本项目的 AI 解读与指标卡片 =====
                        View {
                            attr {
                                paddingLeft(AppTheme.pageHorizontalPadding)
                                paddingRight(AppTheme.pageHorizontalPadding)
                            }
                            Spacer(12f)
                            vbind({
                                listOf(
                                    ctx.vm.period.name,
                                    ctx.vm.selectedBar?.date,
                                    ctx.vm.selectedBar?.isForecast,
                                    ctx.vm.predictionState.name,
                                    ctx.vm.dailyBars.size,
                                ).joinToString("|")
                            }) {
                                ChartFollowUpBar(ctx.vm) { ctx.askAboutSelection() }
                                Spacer(12f)
                            }
                            BodySection(ctx)
                        }
                    }
                }
            }
        }
    }

    /** 官方 fullConfig 同款：一主图（price）+ 二副图（first/second），主图 MA(5,10,20,30) */
    private fun fullConfig(): String {
        val main = mainIndicator.template?.let { template ->
            val params = if (mainIndicator.params.isEmpty()) "" else ",\"params\":[${mainIndicator.params.joinToString()}]"
            "{\"id\":\"main\",\"template\":\"$template\",\"paneId\":\"price\"$params},"
        }.orEmpty()
        return """{"panes":[""" +
            """{"id":"price","kind":"price","order":0,"weight":3.6,"minHeight":180},""" +
            """{"id":"first","kind":"indicator","order":1,"weight":1.15,"minHeight":74},""" +
            """{"id":"second","kind":"indicator","order":2,"weight":1.15,"minHeight":74}],""" +
            """"indicators":[$main""" +
            """{"id":"first-indicator","template":"${firstIndicator.template}","paneId":"first"},""" +
            """{"id":"second-indicator","template":"${secondIndicator.template}","paneId":"second"}]}"""
    }

    /** pane 头部浮动按钮（官方 paneMenuButton 同款） */
    private fun paneMenuButton(title: String, menu: ChartMenu, top: Float): ViewBuilder {
        val page = this
        return {
            View {
                attr {
                    positionAbsolute(); left(0f); top(top); zIndex(40); size(76f, 18f)
                    flexDirectionRow(); alignItemsCenter(); paddingLeft(4f); borderRadius(4f)
                    backgroundColor(Color(0xFFF5F5F5L))
                }
                event { click { page.openMenu = if (page.openMenu == menu) null else menu } }
                Text { attr { text("$title ▾"); fontSize(10f); color(Color(0xFF595959L)) } }
            }
        }
    }

    /** 指标切换浮层菜单（官方 optionColumn 同款） */
    private fun <T> optionColumn(
        options: List<T>,
        anchorTop: Float,
        containerHeight: Float,
        label: (T) -> String,
        selected: (T) -> Boolean,
        select: (T) -> Unit,
    ): ViewBuilder {
        val page = this
        val placement = popupPlacement(anchorTop, 18f, options.size, containerHeight)
        return {
            View {
                attr {
                    positionAbsolute(); left(0f); top(placement.top); zIndex(50); width(82f); padding(5f)
                    backgroundColor(AppTheme.surface); borderRadius(7f)
                    boxShadow(BoxShadow(0f, if (placement.opensUpward) -2f else 2f, 8f, Color(0x1F000000L)))
                }
                options.forEach { option ->
                    View {
                        attr {
                            height(27f); allCenter(); borderRadius(5f)
                            backgroundColor(if (selected(option)) Color(0x1A1677FFL) else Color.TRANSPARENT)
                        }
                        event { click { select(option); page.openMenu = null } }
                        Text {
                            attr {
                                text(label(option))
                                fontSize(11f)
                                color(if (selected(option)) Color(0xFF1677FFL) else Color(0xFF595959L))
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun askAi(prompt: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockChat",
            JSONObject().apply {
                put("prompt", prompt)
                put("instrumentKey", ctxInstrumentKey())
                put("instrument", InstrumentCodec.encode(instrument))
            },
        )
    }

    internal fun askAboutSelection() {
        val point = vm.selectedBar ?: return
        askAi(ChartFollowUpPrompt.build(instrument, point, vm.prediction))
    }

    private fun ctxInstrumentKey(): String = instrument.key

    private fun close() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }
}

internal fun ViewContainer<*, *>.BodySection(ctx: StockDetailPage) {
    vbind({
        listOf(
            ctx.vm.analysis?.score,
            ctx.vm.predictionState.name,
            ctx.vm.predictionMessage,
            ctx.vm.prediction?.confidence,
            ctx.vm.selectedBar?.date,
        ).joinToString("|")
    }) {
        val q = ctx.vm.quote ?: return@vbind
        val a = ctx.vm.analysis ?: return@vbind
        AiInsightCard(ctx.vm.insight, a, ctx.instrument) { ctx.askAi(it) }
        Spacer(12f)
        PredictionCard(ctx.vm)
        Spacer(12f)
        MetricsCard(q, a)
        Spacer(12f)
        ValuationCard(q)
        Spacer(12f)
        TechnicalCard(q, a)
        Spacer(12f)
        RiskCard(ctx.vm.risks)
    }
}

internal fun ViewContainer<*, *>.ChartPlaceholder(text: String, loading: Boolean = true) {
    View {
        attr { flex(1f); allCenter() }
        if (loading) {
            ActivityIndicator { attr { isGrayStyle(true) } }
        }
        Text { attr { text(text); fontSize(12f); color(AppTheme.textTertiary); marginTop(if (loading) 8f else 0f) } }
    }
}
