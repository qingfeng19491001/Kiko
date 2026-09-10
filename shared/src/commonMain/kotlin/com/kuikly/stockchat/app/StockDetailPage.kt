package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.ui.components.Card
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.charts.IntradayChart
import com.kuikly.stockchat.ui.components.PrimaryButton
import com.kuikly.stockchat.ui.components.Spacer
import com.kuikly.stockchat.ui.detail.AiInsightCard
import com.kuikly.stockchat.ui.detail.MetricsCard
import com.kuikly.stockchat.ui.detail.PeriodTabs
import com.kuikly.stockchat.ui.detail.QuoteHeaderView
import com.kuikly.stockchat.ui.detail.RiskCard
import com.kuikly.stockchat.ui.detail.StockDetailViewModel
import com.kuikly.stockchat.ui.detail.TechnicalCard
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 个股 / 指数详情页：上半屏固定报价与走势，下半屏滚动解读与指标。
 */
@Page("StockDetail")
internal class StockDetailPage : Pager() {

    internal lateinit var vm: StockDetailViewModel
    internal lateinit var instrument: Instrument

    override fun created() {
        super.created()
        val key = pageData.params.optString("instrumentKey")
        instrument = StockCatalog.findByKey(key) ?: StockCatalog.tencent
        val store = KuiklyKeyValueStore(this)
        vm = StockDetailViewModel(
            instrument = instrument,
            marketRepository = MarketRepository(KuiklyHttpClient(this)),
            watchlistRepository = WatchlistRepository(store),
        )
        vm.load()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val pageWidth = pagerData.pageViewWidth
        val bottomInset = pagerData.safeAreaInsets.bottom
        val chartWidth = pageWidth - AppTheme.pageHorizontalPadding * 2 - 24f
        val chartHeight = 200f
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
                            text("行情加载失败，请检查网络后重试")
                            fontSize(14f)
                            color(AppTheme.textSecondary)
                            marginTop(12f)
                            marginBottom(16f)
                        }
                    }
                    PrimaryButton("重新加载", IconKind.REFRESH, flex = false) { ctx.vm.load() }
                }
            }
            velse {
                View {
                    attr {
                        flex(1f)
                        paddingLeft(AppTheme.pageHorizontalPadding)
                        paddingRight(AppTheme.pageHorizontalPadding)
                        paddingTop(12f)
                    }
                    vif({ ctx.vm.isMock }) {
                        View {
                            attr {
                                flexDirectionRow(); alignItemsCenter()
                                backgroundColor(AppTheme.warningSoft)
                                borderRadius(10f)
                                paddingLeft(12f); paddingRight(12f); paddingTop(8f); paddingBottom(8f)
                                marginBottom(10f)
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
                    QuoteSection(ctx)
                    Spacer(10f)
                    Card(padding = 12f) {
                        PeriodTabs({ ctx.vm.period }) { ctx.vm.selectPeriod(it) }
                        View {
                            attr { height(chartHeight); marginTop(10f) }
                            vif({ ctx.vm.chartLoading }) {
                                ChartPlaceholder("走势加载中…")
                            }
                            velse {
                                vif({ ctx.vm.period == KLinePeriod.MINUTE }) {
                                    vif({ ctx.vm.intraday != null }) {
                                        ctx.vm.intraday?.let { series -> IntradayChart(series, chartWidth, chartHeight) }
                                    }
                                    velse {
                                        ChartPlaceholder("暂无分时数据", loading = false)
                                    }
                                }
                                velse {
                                    vbind({ ctx.vm.barsJson }) {
                                        View {
                                            attr { flex(1f) }
                                            if (ctx.vm.barsJson.isNotEmpty()) {
                                                PlatformKLineChart(
                                                    flexValue = 1f,
                                                    symbolCode = ctx.instrument.displayCode,
                                                    symbolName = ctx.instrument.name,
                                                    periodValue = 1,
                                                    periodUnit = ctx.vm.period.apiKey,
                                                    modeName = "compact",
                                                    themeName = "light",
                                                    barsJson = ctx.vm.barsJson,
                                                    configJson = KLINE_CONFIG,
                                                ) { code, message ->
                                                    println("KLineChart error: $code $message")
                                                }
                                            } else {
                                                ChartPlaceholder("暂无 K 线数据", loading = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text(
                                    if (ctx.vm.period == KLinePeriod.MINUTE) "分时 · 分钟 K，虚线昨收，下方成交量"
                                    else "${ctx.vm.period.label} · 双指缩放，已叠加 MA5 / MA10 / MA20",
                                )
                                fontSize(11f)
                                color(AppTheme.textTertiary)
                                marginTop(6f)
                            }
                        }
                    }
                    List {
                        attr {
                            flex(1f)
                            marginTop(10f)
                            showScrollerIndicator(false)
                        }
                        View {
                            attr { paddingBottom(bottomInset + 20f) }
                            BodySection(ctx)
                        }
                    }
                }
            }
        }
    }

    internal fun askAi(prompt: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockChat",
            JSONObject().apply { put("prompt", prompt) },
        )
    }

    private fun close() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    companion object {
        /** K 线面板配置：价格 + 成交量，叠加 MA5/10/20 */
        private const val KLINE_CONFIG =
            """{"panes":[{"id":"price","kind":"price","order":0,"weight":3.0,"minHeight":150},{"id":"volume","kind":"indicator","order":1,"weight":1.0,"minHeight":56}],"indicators":[{"id":"ma5","template":"MA","paneId":"price","params":[5]},{"id":"ma10","template":"MA","paneId":"price","params":[10]},{"id":"ma20","template":"MA","paneId":"price","params":[20]},{"id":"vol","template":"VOL","paneId":"volume"}]}"""
    }
}

/** 头部行情随 quote 变化重建 */
internal fun ViewContainer<*, *>.QuoteSection(ctx: StockDetailPage) {
    vbind({ ctx.vm.quote }) {
        ctx.vm.quote?.let { q -> QuoteHeaderView(ctx.instrument, q) }
    }
}

internal fun ViewContainer<*, *>.BodySection(ctx: StockDetailPage) {
    vbind({ ctx.vm.analysis }) {
        val q = ctx.vm.quote ?: return@vbind
        val a = ctx.vm.analysis ?: return@vbind
        AiInsightCard(ctx.vm.insight, a, ctx.instrument) { ctx.askAi(it) }
        Spacer(12f)
        MetricsCard(q, a)
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
