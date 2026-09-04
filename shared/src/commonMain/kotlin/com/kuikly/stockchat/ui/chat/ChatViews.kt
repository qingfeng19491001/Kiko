package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.AnswerBlockView
import com.kuikly.stockchat.ui.components.BrandMark
import com.kuikly.stockchat.ui.components.ChangeBadge
import com.kuikly.stockchat.ui.components.HSpacer
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.Spacer
import com.kuikly.stockchat.ui.components.StreamingMarkdownView
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 首页推荐问题 */
data class QuickPrompt(val category: String, val prompt: String)

val quickPrompts: List<QuickPrompt> = listOf(
    QuickPrompt("行情", "腾讯控股后市如何？"),
    QuickPrompt("板块", "港股科技板块趋势分析"),
    QuickPrompt("对比", "比亚迪 vs 特斯拉 对比"),
    QuickPrompt("风险", "阿里巴巴有哪些风险"),
    QuickPrompt("宏观", "美联储利率影响解读"),
    QuickPrompt("知识", "什么是市盈率"),
)

// region 导航栏

fun ViewContainer<*, *>.ChatNavBar(
    statusBarHeight: Float,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    View {
        attr {
            paddingTop(statusBarHeight)
            backgroundColor(AppTheme.surface)
        }
        View {
            attr {
                height(AppTheme.navBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(8f)
                paddingRight(8f)
            }
            IconButton(IconKind.MENU, iconSize = 20f, onClick = onMenu)
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter() }
                BrandMark(22f)
                Text {
                    attr {
                        text("StockChat")
                        fontSize(16f)
                        fontWeight700()
                        color(AppTheme.textPrimary)
                        marginLeft(7f)
                    }
                }
            }
            IconButton(IconKind.PLUS, iconSize = 20f, onClick = onNewChat)
        }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
    }
}

// endregion

// region 首页（欢迎态）

fun ViewContainer<*, *>.WelcomeView(
    vm: ChatViewModel,
    pageWidth: Float,
    onPrompt: (String) -> Unit,
    onOpenInstrument: (String) -> Unit,
) {
    val tileWidth = (pageWidth - AppTheme.pageHorizontalPadding * 2 - 16f) / 3
    View {
        attr { paddingLeft(AppTheme.pageHorizontalPadding); paddingRight(AppTheme.pageHorizontalPadding) }

        // 标题区
        Spacer(20f)
        Text {
            attr {
                text("今天想了解什么？")
                fontSize(24f)
                fontWeight700()
                color(AppTheme.textPrimary)
            }
        }
        Text {
            attr {
                text("覆盖港股 · A 股 · 美股实时行情，支持走势判断、标的对比、风险提示与金融知识问答")
                fontSize(13f)
                lineHeight(20f)
                color(AppTheme.textSecondary)
                marginTop(6f)
            }
        }

        // 市场概览
        SectionHeader("市场概览", "腾讯证券实时行情")
        vif({ vm.indexSnapshots.isEmpty() }) {
            View {
                attr { flexDirectionRow(); justifyContentSpaceBetween() }
                repeat(3) { IndexTileSkeleton(tileWidth) }
            }
        }
        velse {
            View {
                attr { flexDirectionRow(); justifyContentSpaceBetween() }
                vfor({ vm.indexSnapshots }) { snap ->
                    IndexTile(snap, tileWidth) { onOpenInstrument(snap.quote.instrument.key) }
                }
            }
        }

        // 推荐问题
        SectionHeader("试着问我", "")
        View {
            attr {
                backgroundColor(AppTheme.surface)
                border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                borderRadius(AppTheme.cardRadius)
            }
            quickPrompts.forEachIndexed { index, item ->
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        height(46f)
                        paddingLeft(14f); paddingRight(12f)
                    }
                    event { click { onPrompt(item.prompt) } }
                    Text {
                        attr {
                            text(item.category)
                            fontSize(11f)
                            fontWeight500()
                            color(AppTheme.textTertiary)
                            width(30f)
                        }
                    }
                    Text {
                        attr {
                            text(item.prompt)
                            fontSize(14f)
                            color(AppTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.textTertiary)
                }
                if (index != quickPrompts.lastIndex) {
                    View { attr { height(0.5f); backgroundColor(AppTheme.divider); marginLeft(14f) } }
                }
            }
        }

        // 热门标的
        SectionHeader("热门标的", "点击查看详情")
        View {
            attr {
                backgroundColor(AppTheme.surface)
                border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                borderRadius(AppTheme.cardRadius)
            }
            vif({ vm.hotSnapshots.isEmpty() }) {
                View {
                    attr { height(120f); allCenter() }
                    ActivityIndicator { attr { isGrayStyle(true) } }
                }
            }
            velse {
                vfor({ vm.hotSnapshots }) { snap ->
                    HotStockRow(snap, pageWidth) { onOpenInstrument(snap.quote.instrument.key) }
                }
            }
        }
        Spacer(24f)
    }
}

private fun ViewContainer<*, *>.SectionHeader(title: String, trailing: String) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); marginTop(24f); marginBottom(10f) }
        Text {
            attr {
                text(title)
                fontSize(13f)
                fontWeight600()
                color(AppTheme.textSecondary)
                flex(1f)
            }
        }
        if (trailing.isNotEmpty()) {
            Text { attr { text(trailing); fontSize(11f); color(AppTheme.textTertiary) } }
        }
    }
}

private fun ViewContainer<*, *>.IndexTile(snap: MarketSnapshot, width: Float, onClick: () -> Unit) {
    val q = snap.quote
    val color = AppTheme.changeColor(q.change)
    View {
        attr {
            width(width)
            backgroundColor(AppTheme.surface)
            borderRadius(AppTheme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
            padding(12f)
        }
        event { click { onClick() } }
        Text {
            attr {
                text(q.instrument.name)
                fontSize(12f)
                color(AppTheme.textSecondary)
                lines(1)
            }
        }
        Text {
            attr {
                text(NumberFormat.price(q.price))
                fontSize(17f)
                fontWeight700()
                color(color)
                marginTop(6f)
            }
        }
        Text {
            attr {
                text("${NumberFormat.signed(q.change)}  ${NumberFormat.signedPct(q.changePct)}")
                fontSize(11f)
                fontWeight500()
                color(color)
                marginTop(2f)
                lines(1)
            }
        }
    }
}

private fun ViewContainer<*, *>.IndexTileSkeleton(width: Float) {
    View {
        attr {
            width(width)
            height(84f)
            backgroundColor(AppTheme.surface)
            borderRadius(AppTheme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
            padding(12f)
        }
        View { attr { width(48f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted) } }
        View { attr { width(72f); height(16f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(10f) } }
        View { attr { width(56f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(8f) } }
    }
}

private fun ViewContainer<*, *>.HotStockRow(snap: MarketSnapshot, pageWidth: Float, onClick: () -> Unit) {
    val q = snap.quote
    val ins = q.instrument
    val color = AppTheme.changeColor(q.change)
    View {
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                height(56f)
                paddingLeft(14f); paddingRight(14f)
            }
            event { click { onClick() } }
            View {
                attr { width(pageWidth * 0.32f) }
                Text { attr { text(ins.name); fontSize(14f); fontWeight600(); color(AppTheme.textPrimary); lines(1) } }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(3f) }
                    MarketTag(ins.market.suffix)
                    Text { attr { text(ins.displayCode); fontSize(11f); color(AppTheme.textTertiary); marginLeft(4f) } }
                }
            }
            View {
                attr { flex(1f); alignItemsCenter() }
                if (snap.sparkline.size >= 2) {
                    SparklineChart(snap.sparkline, 72f, 28f, color, Color.TRANSPARENT)
                }
            }
            View {
                attr { alignItemsFlexEnd(); width(96f) }
                Text { attr { text(NumberFormat.price(q.price)); fontSize(15f); fontWeight600(); color(color) } }
                View { attr { marginTop(3f) }; ChangeBadge(q.change, NumberFormat.signedPct(q.changePct), 11f, filled = true) }
            }
        }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider); marginLeft(14f) } }
    }
}

/** 市场小标签（HK / SH / US） */
fun ViewContainer<*, *>.MarketTag(label: String) {
    View {
        attr {
            paddingLeft(4f); paddingRight(4f); paddingTop(1f); paddingBottom(1f)
            borderRadius(3f)
            backgroundColor(AppTheme.inkSoft)
        }
        Text { attr { text(label); fontSize(9f); fontWeight600(); color(AppTheme.textSecondary) } }
    }
}

// endregion

// region 消息项

fun ViewContainer<*, *>.UserMessageView(message: ChatUiMessage) {
    View {
        attr {
            flexDirectionRow()
            justifyContentFlexEnd()
            paddingLeft(64f)
            paddingRight(AppTheme.pageHorizontalPadding)
            marginTop(20f)
        }
        View {
            attr {
                backgroundColor(AppTheme.userBubble)
                borderRadius(AppTheme.bubbleRadius)
                paddingLeft(14f); paddingRight(14f); paddingTop(10f); paddingBottom(10f)
            }
            Text {
                attr {
                    text(message.text)
                    fontSize(15f)
                    lineHeight(22f)
                    color(AppTheme.textPrimary)
                }
            }
        }
    }
}

fun ViewContainer<*, *>.AssistantMessageView(
    message: ChatUiMessage,
    contentWidth: Float,
    onOpenInstrument: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    onRetry: () -> Unit,
) {
    View {
        attr {
            paddingLeft(AppTheme.pageHorizontalPadding)
            paddingRight(AppTheme.pageHorizontalPadding)
            marginTop(18f)
        }
        // 身份行
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
            BrandMark(20f)
            Text {
                attr {
                    text("StockChat")
                    fontSize(12f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                }
            }
            vif({ message.status == MessageStatus.THINKING }) {
                Text { attr { text("· 正在检索行情"); fontSize(12f); color(AppTheme.textTertiary); marginLeft(4f) } }
            }
            vif({ message.status == MessageStatus.STREAMING }) {
                Text { attr { text("· 生成中"); fontSize(12f); color(AppTheme.textTertiary); marginLeft(4f) } }
            }
        }
        // 思考中
        vif({ message.status == MessageStatus.THINKING }) {
            View {
                attr { flexDirectionRow(); alignItemsCenter(); height(22f) }
                ActivityIndicator { attr { isGrayStyle(true) } }
                Text {
                    attr {
                        text("正在拉取实时行情并计算技术指标…")
                        fontSize(13f)
                        color(AppTheme.textSecondary)
                        marginLeft(8f)
                    }
                }
            }
        }
        // 内容块
        vfor({ message.blocks }) { block ->
            View {
                when (block) {
                    is UiBlock.Markdown -> StreamingMarkdownView(block.model)
                    is UiBlock.Card -> AnswerBlockView(block.block, contentWidth, onOpenInstrument, onFollowUp)
                }
            }
        }
        // 错误
        vif({ message.status == MessageStatus.ERROR }) {
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    marginTop(4f)
                    padding(10f)
                    borderRadius(8f)
                    border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                }
                Icon(IconKind.WIFI_OFF, 16f, AppTheme.down)
                Text {
                    attr {
                        text(message.errorMessage.ifEmpty { "回答生成失败" })
                        fontSize(13f)
                        color(AppTheme.textSecondary)
                        marginLeft(8f)
                        flex(1f)
                    }
                }
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        paddingLeft(10f); paddingRight(10f); paddingTop(5f); paddingBottom(5f)
                        borderRadius(6f)
                        backgroundColor(AppTheme.ink)
                    }
                    event { click { onRetry() } }
                    Icon(IconKind.REFRESH, 12f, Color.WHITE)
                    Text { attr { text("重试"); fontSize(12f); color(Color.WHITE); marginLeft(4f) } }
                }
            }
        }
        // 流式光标
        vif({ message.status == MessageStatus.STREAMING }) {
            View { attr { width(8f); height(14f); borderRadius(1f); backgroundColor(AppTheme.ink); marginTop(4f) } }
        }
    }
}

// endregion

// region 输入框

fun ViewContainer<*, *>.ComposerView(
    vm: ChatViewModel,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onKeyboardHeight: (Float) -> Unit,
    onInputRef: (ViewRef<InputView>) -> Unit,
) {
    View {
        attr { backgroundColor(AppTheme.surface) }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(12f); paddingRight(12f); paddingTop(10f)
            }
            View {
                attr {
                    flex(1f)
                    height(AppTheme.composerHeight - 6f)
                    borderRadius(10f)
                    backgroundColor(AppTheme.surfaceMuted)
                    border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(14f)
                    paddingRight(12f)
                }
                Input {
                    ref { onInputRef(it) }
                    attr {
                        flex(1f)
                        height(AppTheme.composerHeight - 8f)
                        fontSize(15f)
                        color(AppTheme.textPrimary)
                        placeholder("输入股票名称、代码或问题")
                        placeholderColor(AppTheme.textTertiary)
                        returnKeyTypeSend()
                        maxTextLength(200)
                    }
                    event {
                        textDidChange { vm.inputText = it.text }
                        inputReturn { onSend(it.text) }
                        keyboardHeightChange { onKeyboardHeight(it.height) }
                    }
                }
            }
            HSpacer(8f)
            View {
                attr {
                    size(AppTheme.composerHeight - 6f, AppTheme.composerHeight - 6f)
                    borderRadius(10f)
                    allCenter()
                    backgroundColor(
                        when {
                            vm.isGenerating -> AppTheme.down
                            vm.inputText.isNotBlank() -> AppTheme.ink
                            else -> AppTheme.primarySoft
                        },
                    )
                }
                event {
                    click {
                        if (vm.isGenerating) onStop() else onSend(vm.inputText)
                    }
                }
                vif({ vm.isGenerating }) { Icon(IconKind.STOP, 16f, Color.WHITE) }
                velse { Icon(IconKind.ARROW_UP, 20f, Color.WHITE, 2.2f) }
            }
        }
        View {
            attr { alignItemsCenter(); paddingTop(6f); paddingBottom(8f) }
            Text {
                attr {
                    text("行情数据来自腾讯证券，AI 内容仅供参考，不构成投资建议")
                    fontSize(10f)
                    color(AppTheme.textTertiary)
                }
            }
        }
    }
}

// endregion
