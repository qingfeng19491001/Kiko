package com.kuikly.stockchat.ui.detail

import com.kuikly.components.kline.PlatformKLineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal enum class MainIndicator(val label: String, val template: String?, val params: List<Int> = emptyList()) {
    BARE("裸K", null),
    MA("MA", "MA", listOf(5, 10, 20, 30)),
    BOLL("BOLL", "BOLL"),
    EXPMA("EXPMA", "EXPMA"),
    BBI("BBI", "BBI"),
    ENE("ENE", "ENE"),
}

internal enum class FirstIndicator(val label: String, val template: String) {
    VOLUME("成交量", "VOL"),
    MACD("MACD", "MACD"),
    AMOUNT("成交额", "AMOUNT"),
}

internal enum class SecondIndicator(val label: String, val template: String) {
    MACD("MACD", "MACD"),
    KDJ("KDJ", "KDJ"),
    RSI("RSI", "RSI"),
    WR("WR", "WR"),
    BBD("BBD", "BBD"),
}

internal enum class ChartMenu { PERIOD, MAIN, FIRST, SECOND }

private data class PopupPlacement(val top: Float, val opensUpward: Boolean)

private fun popupPlacement(anchorTop: Float, anchorHeight: Float, optionCount: Int, containerHeight: Float): PopupPlacement {
    val menuHeight = optionCount * 27f + 10f
    val belowTop = anchorTop + anchorHeight
    return if (belowTop + menuHeight <= containerHeight) {
        PopupPlacement(belowTop, opensUpward = false)
    } else {
        PopupPlacement((anchorTop - menuHeight).coerceAtLeast(0f), opensUpward = true)
    }
}

internal fun klineFullConfig(
    mainIndicator: MainIndicator,
    firstIndicator: FirstIndicator,
    secondIndicator: SecondIndicator,
): String {
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

/** FullChartDemo 同款工作区：主图 + 两副图 + pane 菜单。底部 AI 卡不迁。 */
internal fun ViewContainer<*, *>.KLineWorkspace(host: KLineWorkspaceHost, chartHeight: Float) {
    View {
        attr { height(chartHeight); backgroundColor(AppTheme.surface) }
        vif({ host.chartLoading }) {
            ChartPlaceholder("走势加载中…")
        }
        velse {
            vif({ host.barsJson.isEmpty() }) {
                ChartPlaceholder("暂无 K 线数据", loading = false)
            }
            velse {
                vbind({ host.chartBindKey() }) {
                    PlatformKLineChart(
                        height = chartHeight,
                        symbolCode = host.symbolCode,
                        symbolName = host.symbolName,
                        periodValue = host.periodValue,
                        periodUnit = host.periodUnit,
                        modeName = "full",
                        themeName = "light",
                        barsJson = host.barsJson,
                        configJson = klineFullConfig(host.mainIndicator, host.firstIndicator, host.secondIndicator),
                        priceStyleName = if (host.priceAsLine) "line" else "candle",
                        touchEnabled = host.openMenu == null,
                        onPaneLayoutChange = { price, first, second ->
                            host.priceHeaderTop = price
                            host.firstHeaderTop = first
                            host.secondHeaderTop = second
                        },
                        onPaneHeaderClick = { paneId ->
                            val menu = when (paneId) {
                                "price" -> ChartMenu.MAIN
                                "first" -> ChartMenu.FIRST
                                "second" -> ChartMenu.SECOND
                                else -> null
                            }
                            if (menu != null) {
                                host.openMenu = if (host.openMenu == menu) null else menu
                            }
                        },
                        onBarClick = { _, index -> host.onBarClick(index) },
                        onCrosshairChange = { timestamp, price ->
                            host.onCrosshairChange(timestamp, price)
                        },
                    )
                }
                vbind({ "${host.priceHeaderTop}:${host.mainIndicator.name}" }) {
                    paneMenuButton(host, host.mainIndicator.label, ChartMenu.MAIN, host.priceHeaderTop).invoke(this)
                }
                vbind({ "${host.firstHeaderTop}:${host.firstIndicator.name}" }) {
                    paneMenuButton(host, host.firstIndicator.label, ChartMenu.FIRST, host.firstHeaderTop).invoke(this)
                }
                vbind({ "${host.secondHeaderTop}:${host.secondIndicator.name}" }) {
                    paneMenuButton(host, host.secondIndicator.label, ChartMenu.SECOND, host.secondHeaderTop).invoke(this)
                }
                vif({ host.openMenu != null }) {
                    View {
                        attr { positionAbsolute(); absolutePositionAllZero(); zIndex(45) }
                        event { click { host.openMenu = null } }
                    }
                }
                vif({ host.openMenu == ChartMenu.MAIN }) {
                    optionColumn(host, MainIndicator.entries, host.priceHeaderTop, chartHeight, { it.label }, { it == host.mainIndicator }) {
                        host.mainIndicator = it
                    }.invoke(this)
                }
                vif({ host.openMenu == ChartMenu.FIRST }) {
                    optionColumn(host, FirstIndicator.entries, host.firstHeaderTop, chartHeight, { it.label }, { it == host.firstIndicator }) {
                        host.firstIndicator = it
                    }.invoke(this)
                }
                vif({ host.openMenu == ChartMenu.SECOND }) {
                    optionColumn(host, SecondIndicator.entries, host.secondHeaderTop, chartHeight, { it.label }, { it == host.secondIndicator }) {
                        host.secondIndicator = it
                    }.invoke(this)
                }
            }
        }
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

internal interface KLineWorkspaceHost {
    var mainIndicator: MainIndicator
    var firstIndicator: FirstIndicator
    var secondIndicator: SecondIndicator
    var openMenu: ChartMenu?
    var priceHeaderTop: Float
    var firstHeaderTop: Float
    var secondHeaderTop: Float
    val chartLoading: Boolean
    val barsJson: String
    val symbolCode: String
    val symbolName: String
    val periodValue: Int
    val periodUnit: String
    val priceAsLine: Boolean
    fun chartBindKey(): String
    fun onBarClick(index: Int)
    fun onCrosshairChange(timestamp: Long?, price: Double?)
}

private fun paneMenuButton(host: KLineWorkspaceHost, title: String, menu: ChartMenu, top: Float): ViewBuilder = {
    View {
        attr {
            positionAbsolute(); left(0f); top(top); zIndex(40); size(76f, 18f)
            flexDirectionRow(); alignItemsCenter(); paddingLeft(4f); borderRadius(4f)
            backgroundColor(Color(0xFFF5F5F5L))
        }
        event { click { host.openMenu = if (host.openMenu == menu) null else menu } }
        Text { attr { text("$title ▾"); fontSize(10f); color(Color(0xFF595959L)) } }
    }
}

private fun <T> optionColumn(
    host: KLineWorkspaceHost,
    options: List<T>,
    anchorTop: Float,
    containerHeight: Float,
    label: (T) -> String,
    selected: (T) -> Boolean,
    select: (T) -> Unit,
): ViewBuilder {
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
                    event { click { select(option); host.openMenu = null } }
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
