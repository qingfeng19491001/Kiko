package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.PrimaryButton
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal interface StockDetailScreenHost : KLineWorkspaceHost {
    val viewModel: StockDetailViewModel
    val detailInstrument: Instrument
    val detailInstrumentMissing: Boolean
    var selectedDetailTab: DetailTab
    var selectedDetailSubTab: String
    fun onBack()
    fun onRetry()
    fun onToggleWatch()
    fun onSelectPeriod(period: com.kuikly.stockchat.domain.model.KLinePeriod)
    fun onSelectDetailTab(tab: DetailTab)
    fun onAskAi(prompt: String)
    fun onAskSelection()
    fun onOpenInstrument(instrument: Instrument)
    val fromChat: Boolean
    val chatSummary: String
}

internal fun StockDetailScreen(
    host: StockDetailScreenHost,
    pageHeight: Float,
    statusBarHeight: Float,
    bottomInset: Float,
): ViewBuilder {
    val chartHeight = detailWorkspaceHeight(pageHeight, statusBarHeight, bottomInset)
    return {
        attr {
            flex(1f)
            backgroundColor(AppTheme.background)
        }
        DetailNavBar(host, statusBarHeight)
        vif({ host.viewModel.loadState == StockDetailViewModel.LoadState.LOADING }) {
            DetailLoading(host.detailInstrument)
        }
        velseif({ host.viewModel.loadState == StockDetailViewModel.LoadState.ERROR }) {
            DetailError(host)
        }
        velse {
            List {
                attr { flex(1f); showScrollerIndicator(false) }
                View {
                    attr { backgroundColor(AppTheme.surface) }
                    vif({ host.viewModel.isMock }) { OfflineMarketBanner() }
                    vif({ host.fromChat }) { ChatLandingBanner(host) }
                    vbind({ host.viewModel.quote }) {
                        host.viewModel.quote?.let { DemoQuoteOverview(it) }
                    }
                    View {
                        attr { overflow(false) }
                        PeriodTabs(
                            selected = { host.viewModel.period },
                            periodMenuOpen = { host.openMenu == ChartMenu.PERIOD },
                            onTogglePeriodMenu = {
                                host.openMenu = if (host.openMenu == ChartMenu.PERIOD) null else ChartMenu.PERIOD
                            },
                        ) { period ->
                            host.openMenu = null
                            host.onSelectPeriod(period)
                        }
                        KLineWorkspace(host, chartHeight)
                        vif({ host.openMenu == ChartMenu.PERIOD }) {
                            PeriodMoreMenu(
                                selected = { host.viewModel.period },
                            ) { period ->
                                host.openMenu = null
                                host.onSelectPeriod(period)
                            }
                        }
                    }
                    DetailTabBar(
                        selectedTab = { host.selectedDetailTab },
                        selectedSubTab = { host.selectedDetailSubTab },
                        onSelectTab = host::onSelectDetailTab,
                        onSelectSubTab = { host.selectedDetailSubTab = it },
                    )
                    View {
                        attr {
                            paddingLeft(AppTheme.pageHorizontalPadding)
                            paddingRight(AppTheme.pageHorizontalPadding)
                            paddingBottom(bottomInset + 24f)
                            backgroundColor(AppTheme.surface)
                        }
                        DetailBodySection(host)
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DetailNavBar(host: StockDetailScreenHost, statusBarHeight: Float) {
    View {
        attr { paddingTop(statusBarHeight); backgroundColor(AppTheme.surface) }
        View {
            attr {
                height(AppTheme.navBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(8f)
                paddingRight(8f)
            }
            IconButton(IconKind.BACK, onClick = host::onBack)
            View {
                attr { flex(1f); alignItemsCenter() }
                Text {
                    attr {
                        text(host.detailInstrument.name)
                        fontSize(16f)
                        fontWeight700()
                        color(AppTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(host.detailInstrument.displayCode)
                        fontSize(10f)
                        color(AppTheme.textTertiary)
                    }
                }
            }
            IconButton(
                IconKind.SPARKLE,
                color = AppTheme.accent,
                onClick = {
                    host.onAskAi("帮我解读一下${host.detailInstrument.name}（${host.detailInstrument.displayCode}）")
                },
            )
            vif({ host.viewModel.isWatching }) {
                IconButton(IconKind.STAR_FILLED, color = AppTheme.warning, onClick = host::onToggleWatch)
            }
            velse {
                IconButton(IconKind.STAR, onClick = host::onToggleWatch)
            }
        }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
    }
}

private fun ViewContainer<*, *>.DetailLoading(instrument: Instrument) {
    View {
        attr { flex(1f); allCenter() }
        ActivityIndicator { attr { isGrayStyle(true) } }
        Text {
            attr {
                text("正在获取 ${instrument.name} 行情…")
                fontSize(13f)
                color(AppTheme.textSecondary)
                marginTop(12f)
            }
        }
    }
}

private fun ViewContainer<*, *>.DetailError(host: StockDetailScreenHost) {
    View {
        attr { flex(1f); allCenter(); padding(32f) }
        Icon(IconKind.WIFI_OFF, 44f, AppTheme.textTertiary, 1.4f)
        Text {
            attr {
                text(
                    if (host.detailInstrumentMissing) "无法识别该标的，未打开默认股票"
                    else "行情加载失败，请检查网络后重试",
                )
                fontSize(14f)
                color(AppTheme.textSecondary)
                marginTop(12f)
                marginBottom(16f)
            }
        }
        if (!host.detailInstrumentMissing) {
            PrimaryButton("重新加载", IconKind.REFRESH, flex = false, onClick = host::onRetry)
        }
    }
}

private fun ViewContainer<*, *>.ChatLandingBanner(host: StockDetailScreenHost) {
    View {
        attr {
            backgroundColor(AppTheme.accentSoft)
            borderRadius(10f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingTop(10f)
            paddingBottom(10f)
            marginLeft(AppTheme.pageHorizontalPadding)
            marginRight(AppTheme.pageHorizontalPadding)
            marginTop(10f)
            marginBottom(2f)
        }
        Text {
            attr {
                text("来自 AI 问答 · ${host.detailInstrument.name}")
                fontSize(12f)
                fontWeight600()
                color(AppTheme.accent)
            }
        }
        Text {
            attr {
                text(
                    host.chatSummary.ifBlank {
                        "上方为实时行情与走势，下滑诊股可看解读、点位、信号与风险。"
                    },
                )
                fontSize(12f)
                lineHeight(18f)
                color(AppTheme.textSecondary)
                marginTop(4f)
            }
        }
    }
}

private fun ViewContainer<*, *>.OfflineMarketBanner() {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(AppTheme.warningSoft)
            borderRadius(10f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingTop(8f)
            paddingBottom(8f)
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

/**
 * K 线吃掉 List 视口里报价 / 周期 / 诊股 Tab / 底栏安全区之后的剩余高度。
 * 不设 420 上限：高机图更高，解读仍按内容排在 Tab 下方，靠整页 List 滚动。
 */
internal fun detailWorkspaceHeight(
    pageHeight: Float,
    statusBarHeight: Float,
    bottomInset: Float,
): Float {
    val listViewport = pageHeight - statusBarHeight - AppTheme.navBarHeight
    val chromeInList = DETAIL_QUOTE_HEIGHT + DETAIL_PERIOD_HEIGHT + DETAIL_TAB_BAR_HEIGHT + bottomInset
    val leftover = listViewport - chromeInList
    if (leftover <= 0f) return DETAIL_CHART_MIN
    return leftover.coerceAtLeast(DETAIL_CHART_MIN)
}

private const val DETAIL_QUOTE_HEIGHT = 104f
private const val DETAIL_PERIOD_HEIGHT = 34f
private const val DETAIL_TAB_BAR_HEIGHT = 80.5f
private const val DETAIL_CHART_MIN = 200f

private fun ViewContainer<*, *>.DetailBodySection(host: StockDetailScreenHost) {
    vbind({
        listOf(
            host.selectedDetailTab.name,
            host.selectedDetailSubTab,
            host.viewModel.analysis?.score,
            host.viewModel.predictionState.name,
            host.viewModel.predictionMessage,
            host.viewModel.prediction?.confidence,
            host.viewModel.selectedBar?.date,
            host.viewModel.period.name,
            host.viewModel.dailyBars.size,
            host.viewModel.capitalFlow?.date,
            host.viewModel.capitalFlowLoading,
            host.viewModel.quote?.updateTime,
            host.viewModel.insight,
            host.fromChat,
            host.chatSummary,
        ).joinToString("|")
    }) {
        val quote = host.viewModel.quote
        val analysis = host.viewModel.analysis
        when (host.selectedDetailTab) {
            DetailTab.DIAGNOSIS -> DiagnosisPane(
                vm = host.viewModel,
                instrument = host.detailInstrument,
                subTab = host.selectedDetailSubTab,
                onAsk = host::onAskAi,
                onAskSelection = host::onAskSelection,
                fromChat = host.fromChat,
                chatSummary = host.chatSummary,
            )
            DetailTab.PROFILE -> if (quote != null) ProfilePane(quote, analysis, host.selectedDetailSubTab)
            DetailTab.TECH -> if (quote != null && analysis != null) TechPane(quote, analysis, host.viewModel.period)
            DetailTab.FLOW -> if (quote != null) {
                FlowPane(
                    quote = quote,
                    flow = host.viewModel.capitalFlow,
                    loading = host.viewModel.capitalFlowLoading,
                    onAsk = { host.onAskAi("${host.detailInstrument.name}资金流向怎么样") },
                    volumeRatio = analysis?.volumeRatio,
                )
            }
            DetailTab.SECTOR -> SectorPane(
                instrument = host.detailInstrument,
                onOpen = host::onOpenInstrument,
                onAsk = host::onAskAi,
            )
        }
    }
}
