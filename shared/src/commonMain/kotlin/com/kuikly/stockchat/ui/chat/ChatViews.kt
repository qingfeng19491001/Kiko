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
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Image
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
    ttsEnabled: Boolean,
    onMenu: () -> Unit,
    onTts: () -> Unit,
) {
    View {
        attr {
            backgroundColor(Color(0xFFFFFFFFL))
            paddingTop(statusBarHeight)
        }
        View {
            attr {
                height(AppTheme.navBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f)
                paddingRight(16f)
            }
            IconButton(
                kind = IconKind.MENU,
                size = 40f,
                iconSize = 20f,
                color = AppTheme.textPrimary,
                background = Color.WHITE,
                shadow = true,
                onClick = onMenu,
            )
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter() }
                Text {
                    attr {
                        text("StockChat")
                        fontSize(17f)
                        fontWeight700()
                        color(AppTheme.textPrimary)
                    }
                }
            }
            IconButton(
                kind = if (ttsEnabled) IconKind.SPEAKER else IconKind.SPEAKER_OFF,
                size = 40f,
                iconSize = 20f,
                color = AppTheme.textPrimary,
                background = Color.WHITE,
                shadow = true,
                onClick = onTts,
            )
        }
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
    View {
        attr {
            paddingLeft(AppTheme.pageHorizontalPadding)
            paddingRight(AppTheme.pageHorizontalPadding)
            flex(1f)
        }

        // 机器人头像
        View {
            attr { allCenter(); marginTop(18f) }
            View {
                attr {
                    size(112f, 112f)
                    borderRadius(56f)
                    allCenter()
                    backgroundColor(AppTheme.accentSoft)
                }
                Image {
                    attr {
                        size(112f, 112f)
                        borderRadius(56f)
                        src(ImageUri.commonAssets("robot.png"))
                    }
                }
            }
        }

        // 标题
        View {
            attr { alignItemsCenter(); marginTop(20f) }
            Text {
                attr {
                    text("AI 股票助手")
                    fontSize(26f)
                    fontWeight700()
                    color(AppTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("帮你看懂行情、做分析、给建议")
                    fontSize(14f)
                    color(AppTheme.textSecondary)
                    marginTop(8f)
                }
            }
        }

        // 功能卡片 2x2 网格
        Spacer(32f)
        val cardGap = 12f
        val cardWidth = (pageWidth - AppTheme.pageHorizontalPadding * 2 - cardGap) / 2
        View {
            attr { flexDirectionRow(); flexWrapWrap() }
            FunctionCard(
                width = cardWidth,
                icon = IconKind.CHART,
                iconBg = Color(0xFFEDF1FEL),
                iconColor = AppTheme.accent,
                title = "行情分析",
                subtitle = "洞察市场走势",
            ) { onPrompt("腾讯控股后市如何？") }
            View { attr { width(cardGap) } }
            FunctionCard(
                width = cardWidth,
                icon = IconKind.COMPARE,
                iconBg = Color(0xFFE6F4EBL),
                iconColor = Color(0xFF12924AL),
                title = "对比行情",
                subtitle = "多股对比分析",
            ) { onPrompt("比亚迪 vs 特斯拉 对比") }
        }
        View { attr { height(cardGap) } }
        View {
            attr { flexDirectionRow(); flexWrapWrap() }
            FunctionCard(
                width = cardWidth,
                icon = IconKind.TREND_UP,
                iconBg = Color(0xFFEEF6FFL),
                iconColor = Color(0xFF2563EBL),
                title = "趋势判断",
                subtitle = "把握趋势机会",
            ) { onPrompt("恒生指数短期走势判断") }
            View { attr { width(cardGap) } }
            FunctionCard(
                width = cardWidth,
                icon = IconKind.ALERT,
                iconBg = Color(0xFFFFF1EBL),
                iconColor = Color(0xFFEA580CL),
                title = "风险提醒",
                subtitle = "识别风险信号",
            ) { onPrompt("阿里巴巴有哪些风险") }
        }

        Spacer(24f)
    }
}

/** 首页功能入口卡片 */
private fun ViewContainer<*, *>.FunctionCard(
    width: Float,
    icon: IconKind,
    iconBg: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    View {
        attr {
            width(width)
            height(104f)
            backgroundColor(AppTheme.surface)
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
            borderRadius(AppTheme.cardRadius)
            padding(16f)
            justifyContentCenter()
        }
        event { click { onClick() } }
        View {
            attr {
                size(40f, 40f)
                borderRadius(10f)
                backgroundColor(iconBg)
                allCenter()
            }
            Icon(icon, 22f, iconColor, 1.8f)
        }
        Text {
            attr {
                text(title)
                fontSize(15f)
                fontWeight600()
                color(AppTheme.textPrimary)
                marginTop(10f)
            }
        }
        Text {
            attr {
                text(subtitle)
                fontSize(12f)
                color(AppTheme.textTertiary)
                marginTop(2f)
            }
        }
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
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    View {
        attr { backgroundColor(Color(0xFFFFFFFFL)) }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f); paddingRight(16f); paddingTop(10f)
            }
            // Kimi 式大胶囊：+ / 输入 / 语音·发送 收进同一颗 pill
            View {
                attr {
                    flex(1f)
                    minHeight(56f)
                    borderRadius(28f)
                    backgroundColor(Color.WHITE)
                    boxShadow(BoxShadow(0f, 2f, 8f, Color(0x0F000000L)))
                    border(Border(0.5f, BorderStyle.SOLID, Color(0x0F000000L)))
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(8f)
                    paddingRight(8f)
                    paddingTop(6f)
                    paddingBottom(6f)
                }
                View {
                    attr {
                        size(36f, 36f)
                        borderRadius(18f)
                        allCenter()
                        backgroundColor(AppTheme.surfaceMuted)
                    }
                    event { click { onAttachClick() } }
                    Icon(IconKind.PLUS, 18f, AppTheme.textSecondary, 1.8f)
                }
                Input {
                    ref { onInputRef(it) }
                    attr {
                        flex(1f)
                        minHeight(24f)
                        maxHeight(96f)
                        fontSize(16f)
                        color(AppTheme.textPrimary)
                        placeholder("输入股票名称、代码或问题")
                        placeholderColor(AppTheme.textTertiary)
                        returnKeyTypeSend()
                        maxTextLength(200)
                        marginLeft(8f)
                        marginRight(8f)
                    }
                    event {
                        textDidChange { vm.inputText = it.text }
                        inputReturn { onSend(it.text) }
                        keyboardHeightChange { onKeyboardHeight(it.height) }
                    }
                }
                View {
                    attr {
                        size(36f, 36f)
                        borderRadius(18f)
                        allCenter()
                        backgroundColor(
                            when {
                                vm.isGenerating -> AppTheme.ink
                                vm.inputText.isNotBlank() -> AppTheme.ink
                                else -> Color.TRANSPARENT
                            },
                        )
                    }
                    event {
                        click {
                            when {
                                vm.isGenerating -> onStop()
                                vm.inputText.isNotBlank() -> onSend(vm.inputText)
                                else -> onVoiceClick()
                            }
                        }
                    }
                    vif({ vm.isGenerating }) { Icon(IconKind.STOP, 14f, Color.WHITE) }
                    velse {
                        vif({ vm.inputText.isNotBlank() }) {
                            Icon(IconKind.ARROW_UP, 18f, Color.WHITE, 2.4f)
                        }
                        velse {
                            Icon(IconKind.VOICE, 20f, AppTheme.textSecondary, 1.8f)
                        }
                    }
                }
            }
        }
        View {
            attr { alignItemsCenter(); paddingTop(4f); paddingBottom(8f) }
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
