package com.kuikly.stockchat.ui.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.InstrumentAvatar
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal interface MarketListScreenHost {
    val viewModel: MarketListViewModel
    fun onOpenSearch()
    fun onOpenAi()
    fun onOpenDetail(row: MarketQuoteRow)
    fun onOpenInstrument(instrument: Instrument)
    fun onSelectBoard(board: MarketBoard)
}

internal fun MarketListScreen(
    host: MarketListScreenHost,
    statusBarHeight: Float,
    bottomInset: Float,
): ViewBuilder {
    return {
        attr {
            flex(1f)
            backgroundColor(Color(0xFFF5F6F8L))
        }
        MarketListHeader(host, statusBarHeight)
        vif({ host.viewModel.searchOpen }) {
            MarketSearchBar(host)
        }
        vif({ host.viewModel.usedMock }) {
            View {
                attr {
                    paddingLeft(16f); paddingRight(16f); paddingTop(6f); paddingBottom(6f)
                    backgroundColor(AppTheme.warningSoft)
                }
                Text {
                    attr {
                        text("部分报价未从腾讯拉到，已留空显示，稍后下拉或重进页面重试。")
                        fontSize(11f)
                        color(AppTheme.warning)
                    }
                }
            }
        }
        List {
            attr { flex(1f); showScrollerIndicator(false) }
            vif({ host.viewModel.board != MarketBoard.WATCH }) {
                vbind({
                    "${host.viewModel.board.name}-${host.viewModel.overviewSnaps.size}-${host.viewModel.overviewLoading}-${host.viewModel.overviewStats?.turnoverYuan}-${host.viewModel.overviewStats?.sizeHeadline}"
                }) {
                    View {
                        MarketIndexOverview(host)
                    }
                }
            }
            vbind({ host.viewModel.board.name }) {
                View {
                    attr {
                        paddingTop(if (host.viewModel.board == MarketBoard.WATCH) 0f else 14f)
                        backgroundColor(Color(0xFFF5F6F8L))
                    }
                    if (host.viewModel.board != MarketBoard.WATCH) {
                        MarketSectionTitle(MarketBoardOverview.listTitle(host.viewModel.board))
                    }
                    MarketColumnHeader()
                }
            }
            vif({ host.viewModel.loading && host.viewModel.visibleRows.isEmpty() }) {
                View {
                    attr { height(120f); allCenter(); backgroundColor(Color.WHITE) }
                    ActivityIndicator { attr { isGrayStyle(true) } }
                    Text {
                        attr {
                            text("正在获取行情…")
                            fontSize(12f)
                            color(AppTheme.textTertiary)
                            marginTop(8f)
                        }
                    }
                }
            }
            velse {
                vif({ host.viewModel.visibleRows.isEmpty() }) {
                    View {
                        attr { height(160f); allCenter(); padding(24f); backgroundColor(Color.WHITE) }
                        Text {
                            attr {
                                text(
                                    if (host.viewModel.board == MarketBoard.WATCH) "还没有自选。打开个股详情后点星标即可出现在这里。"
                                    else "这个市场还没有可展示的标的。",
                                )
                                fontSize(13f)
                                color(AppTheme.textSecondary)
                            }
                        }
                    }
                }
                velse {
                    vfor({ host.viewModel.visibleRows }) { row ->
                        MarketQuoteRowView(row) { host.onOpenDetail(row) }
                    }
                }
            }
            View { attr { height(bottomInset + 12f); backgroundColor(Color.WHITE) } }
        }
    }
}

private fun ViewContainer<*, *>.MarketListHeader(host: MarketListScreenHost, statusBarHeight: Float) {
    View {
        attr {
            paddingTop(statusBarHeight)
            paddingBottom(14f)
            backgroundLinearGradient(
                Direction.TO_BOTTOM,
                ColorStop(Color(0xFFE8F2F8L), 0f),
                ColorStop(Color(0xFFF0F5F8L), 0.55f),
                ColorStop(Color(0xFFF3F6F8L), 1f),
            )
        }
        View {
            attr {
                height(56f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(8f)
                paddingRight(16f)
            }
            vbind({ host.viewModel.board.name }) {
                View {
                    attr { flex(1f); flexDirectionRow(); alignItemsCenter(); height(56f) }
                    MarketTopTab("自选", selected = host.viewModel.watchTabSelected) {
                        host.viewModel.selectWatchTab()
                    }
                    MarketTopTab("行情", selected = !host.viewModel.watchTabSelected) {
                        host.viewModel.selectQuoteTab()
                    }
                }
            }
            HeaderActionChip(radius = 18f, onClick = { host.onOpenSearch() }) {
                View {
                    attr { size(36f, 36f); allCenter() }
                    Icon(IconKind.SEARCH, 18f, Color(0xFF111827L), 2f)
                }
            }
            View { attr { width(8f) } }
            HeaderActionChip(radius = 18f, onClick = { host.onOpenAi() }) {
                View {
                    attr {
                        height(36f)
                        paddingLeft(4f)
                        paddingRight(12f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Image {
                        attr {
                            size(28f, 28f)
                            resizeContain()
                            src(ImageUri.commonAssets("robot.png"))
                        }
                    }
                    Text {
                        attr {
                            text("AI")
                            fontSize(14f)
                            fontWeight600()
                            color(Color(0xFF111827L))
                            marginLeft(4f)
                        }
                    }
                }
            }
        }
        vif({ host.viewModel.board != MarketBoard.WATCH }) {
            MarketSegmentBar(host)
        }
    }
}

private fun ViewContainer<*, *>.MarketTopTab(title: String, selected: Boolean, onClick: () -> Unit) {
    View {
        attr {
            paddingLeft(12f)
            paddingRight(12f)
            alignItemsCenter()
        }
        event { click { onClick() } }
        Text {
            attr {
                text(title)
                fontSize(if (selected) 20f else 17f)
                fontWeight700()
                color(if (selected) Color(0xFF111827L) else Color(0xFF9AA3AFL))
            }
        }
        View {
            attr {
                width(if (selected) 18f else 0f)
                height(3f)
                marginTop(6f)
                borderRadius(1.5f)
                backgroundColor(if (selected) AppTheme.accent else Color.TRANSPARENT)
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketSearchBar(host: MarketListScreenHost) {
    View {
        attr {
            height(48f)
            paddingLeft(16f)
            paddingRight(16f)
            paddingBottom(8f)
            backgroundColor(Color(0xFFF4F5F7L))
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                flex(1f)
                height(36f)
                borderRadius(18f)
                backgroundColor(Color.WHITE)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(12f)
                paddingRight(12f)
            }
            Icon(IconKind.SEARCH, 14f, AppTheme.textTertiary)
            Input {
                attr {
                    flex(1f)
                    height(24f)
                    marginLeft(8f)
                    fontSize(14f)
                    color(AppTheme.textPrimary)
                    placeholder("搜索名称 / 代码")
                    placeholderColor(AppTheme.textTertiary)
                    autofocus(true)
                }
                event { textDidChange { host.viewModel.onQueryChange(it.text) } }
            }
        }
    }
}

private val quoteSegments = listOf(
    MarketBoard.ALL,
    MarketBoard.CN,
    MarketBoard.HK,
    MarketBoard.US,
    MarketBoard.HK_CONNECT,
)

private fun ViewContainer<*, *>.MarketSegmentBar(host: MarketListScreenHost) {
    View {
        attr {
            height(44f)
            marginLeft(16f)
            marginRight(16f)
            marginTop(4f)
        }
        View {
            attr {
                height(44f)
                padding(3f)
                borderRadius(22f)
                backgroundColor(Color.WHITE)
                flexDirectionRow()
                alignItemsCenter()
            }
            quoteSegments.forEach { item ->
                vbind({ host.viewModel.board.name }) {
                    val selected = host.viewModel.board == item
                    View {
                        attr {
                            flex(1f)
                            height(38f)
                            borderRadius(19f)
                            allCenter()
                            backgroundColor(if (selected) Color(0xFF191919L) else Color.TRANSPARENT)
                        }
                        event { click { host.onSelectBoard(item) } }
                        Text {
                            attr {
                                text(item.label)
                                fontSize(12f)
                                fontWeight600()
                                color(if (selected) Color.WHITE else Color(0xFF8B8F97L))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketColumnHeader() {
    View {
        attr {
            height(28f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(16f)
            paddingRight(16f)
            backgroundColor(Color.WHITE)
        }
        Text { attr { flex(1f); text("名称 / 代码"); fontSize(11f); color(AppTheme.textTertiary) } }
        Text { attr { width(72f); text("最新价"); fontSize(11f); color(AppTheme.textTertiary); textAlignRight() } }
        Text { attr { width(68f); text("涨跌额"); fontSize(11f); color(AppTheme.textTertiary); textAlignRight() } }
        Text { attr { width(72f); text("涨跌幅"); fontSize(11f); color(AppTheme.textTertiary); textAlignRight() } }
    }
}

private fun ViewContainer<*, *>.MarketQuoteRowView(row: MarketQuoteRow, onClick: () -> Unit) {
    View {
        attr {
            height(64f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(16f)
            paddingRight(16f)
            backgroundColor(Color.WHITE)
        }
        event { click { onClick() } }
        InstrumentAvatar(row.instrument, 36f)
        View {
            attr { flex(1f); marginLeft(10f) }
            Text {
                attr {
                    text(row.instrument.name)
                    fontSize(15f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(row.instrument.displayCode)
                    fontSize(11f)
                    color(AppTheme.textTertiary)
                    marginTop(2f)
                }
            }
        }
        vbind({ row.quote?.price ?: -1.0 }) {
            val quote = row.quote
            val color = quote?.let { AppTheme.changeColor(it.change) } ?: AppTheme.textTertiary
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        width(72f)
                        text(quote?.let { NumberFormat.price(it.price) } ?: "--")
                        fontSize(15f)
                        fontWeight600()
                        color(color)
                        textAlignRight()
                    }
                }
                Text {
                    attr {
                        width(68f)
                        text(quote?.let { formatChangeAmount(it) } ?: "--")
                        fontSize(13f)
                        fontWeight600()
                        color(color)
                        textAlignRight()
                    }
                }
                Text {
                    attr {
                        width(72f)
                        text(quote?.let { NumberFormat.signedPct(it.changePct) } ?: "--")
                        fontSize(13f)
                        fontWeight600()
                        color(color)
                        textAlignRight()
                    }
                }
            }
        }
        View {
            attr {
                positionAbsolute()
                left(16f)
                right(16f)
                bottom(0f)
                height(0.5f)
                backgroundColor(AppTheme.divider)
            }
        }
    }
}

private fun formatChangeAmount(quote: Quote): String = NumberFormat.signed(quote.change)
