package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
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
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.sin

/** 首页推荐问题池，「换一换」按 3 条轮换 */
val welcomePrompts: List<String> = listOf(
    "腾讯控股后市如何？",
    "今天大盘怎么样？",
    "连板梯队",
    "贵州茅台资金流向",
    "比亚迪 vs 特斯拉 对比",
    "阿里巴巴有哪些风险",
    "恒生指数短期走势判断",
    "什么是市盈率",
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
                attr { flex(1f); flexDirectionRow(); alignItemsCenter(); paddingLeft(12f) }
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
    compact: () -> Boolean,
    onPrompt: (String) -> Unit,
    onOpenInstrument: (String) -> Unit,
) {
    View {
        attr { flex(1f); paddingLeft(20f); paddingRight(20f) }
        // 上半部分：头像 + 大标题 + 建议卡片（小布式顶部布局）
        View {
            attr {
                val isCompact = compact()
                flex(1f)
                opacity(if (isCompact) 0f else 1f)
                transform(Translate(0f, 0f, 0f, if (isCompact) 36f else 0f))
                animation(Animation.easeOut(0.2f), isCompact)
                overflow(true)
            }
            Image {
                attr {
                    size(72f, 72f)
                    borderRadius(36f)
                    marginTop(18f)
                    src(ImageUri.commonAssets("robot.png"))
                }
            }
            Text {
                attr {
                    text("你好呀，我是小财")
                    fontSize(24f)
                    fontWeight700()
                    color(AppTheme.textPrimary)
                    marginTop(16f)
                }
            }
            Text {
                attr {
                    text("行情、连板、资金动向，随时问我")
                    fontSize(14f)
                    color(AppTheme.textTertiary)
                    marginTop(6f)
                }
            }
            // 建议卡片列表（3 条，可换一批）
            View {
                attr { marginTop(22f) }
                val offset = vm.welcomeOffset
                (0 until 3).forEach { i ->
                    val prompt = welcomePrompts[(offset + i) % welcomePrompts.size]
                    View {
                        attr {
                            backgroundColor(Color.WHITE)
                            borderRadius(14f)
                            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                            paddingLeft(16f); paddingRight(16f); paddingTop(14f); paddingBottom(14f)
                            marginTop(if (i == 0) 0f else 10f)
                        }
                        event { click { onPrompt(prompt) } }
                        Text {
                            attr {
                                text(prompt)
                                fontSize(15f)
                                color(AppTheme.textPrimary)
                            }
                        }
                    }
                }
            }
            // 换一换
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    marginTop(14f)
                }
                event { click { vm.welcomeOffset = (vm.welcomeOffset + 3) % welcomePrompts.size } }
                Icon(IconKind.REFRESH, 14f, AppTheme.textTertiary)
                Text {
                    attr {
                        text("换一换")
                        fontSize(13f)
                        color(AppTheme.textTertiary)
                        marginLeft(6f)
                    }
                }
            }
        }
        // 底部提问胶囊：按内容宽度排布，超出时横向滑动，避免四等分挤压文字。
        // 负边距抵消欢迎区 20 水平内边距，滚动时两端可露出下一颗胶囊。
        View {
            attr {
                val isCompact = compact()
                height(if (isCompact) 0f else 44f)
                opacity(if (isCompact) 0f else 1f)
                transform(Translate(0f, 0f, 0f, if (isCompact) 12f else 0f))
                overflow(true)
                marginLeft(-20f)
                marginRight(-20f)
                animation(Animation.easeOut(0.22f), isCompact)
            }
            Scroller {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    height(44f)
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                val prompts = listOf(
                    Triple(IconKind.CHART, "行情分析", "腾讯控股后市如何？"),
                    Triple(IconKind.CANDLE, "帮我盯盘", "今天大盘怎么样？"),
                    Triple(IconKind.TREND_UP, "连板梯队", "连板梯队"),
                    Triple(IconKind.SPARKLE, "资金流向", "贵州茅台资金流向"),
                )
                prompts.forEachIndexed { index, (icon, title, prompt) ->
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            height(36f)
                            paddingLeft(12f)
                            paddingRight(14f)
                            borderRadius(18f)
                            backgroundColor(AppTheme.surfaceMuted)
                            marginLeft(if (index == 0) 20f else 8f)
                            marginRight(if (index == prompts.lastIndex) 20f else 0f)
                        }
                        event { click { onPrompt(prompt) } }
                        Icon(icon, 15f, AppTheme.textSecondary, 1.8f)
                        Text {
                            attr {
                                text(title)
                                fontSize(13f)
                                fontWeight500()
                                color(AppTheme.textPrimary)
                                marginLeft(6f)
                                lines(1)
                            }
                        }
                    }
                }
            }
        }
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
                Text { attr { text("· 正在分析"); fontSize(12f); color(AppTheme.textTertiary); marginLeft(4f) } }
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
                        text(thinkingText(message.intent))
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

private fun thinkingText(intent: Intent): String = when (intent) {
    Intent.STOCK_ANALYSIS -> "正在读取行情并计算技术指标…"
    Intent.TREND -> "正在计算均线、动能与支撑压力…"
    Intent.RISK -> "正在检查波动、估值与行业风险…"
    Intent.COMPARE -> "正在对齐两只标的的行情指标…"
    Intent.MARKET_OVERVIEW -> "正在汇总主要指数与市场广度…"
    Intent.LIMIT_UP_LADDER -> "正在统计涨停池与连板梯队…"
    Intent.CAPITAL_FLOW -> "正在读取主力资金流向…"
    Intent.KNOWLEDGE -> "正在整理相关投资知识…"
    Intent.GREETING -> "正在准备可提问的股票场景…"
    Intent.UNKNOWN -> "正在理解问题并匹配分析场景…"
}

// endregion

// region 输入框

fun ViewContainer<*, *>.ComposerView(
    vm: ChatViewModel,
    expanded: () -> Boolean,
    keyboardVisible: () -> Boolean,
    attachmentPanelVisible: () -> Boolean,
    voiceMode: () -> Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onKeyboardHeight: (Float) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onInputRef: (ViewRef<InputView>) -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onVoiceLongPress: (LongPressParams) -> Unit,
    onPickCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
        View {
            attr {
                backgroundColor(Color(0xFFFFFFFFL))
                flexDirectionColumn()
                overflow(false)
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                val isExpanded = expanded() && !attachmentPanelVisible()
                paddingLeft(8f); paddingRight(8f); paddingTop(8f)
                paddingBottom(if (isExpanded) 8f else 0f)
                animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
            }
            // 聚焦时由单行胶囊展开为上下两层，输入节点本身保持不变，避免丢失焦点。
            View {
                attr {
                    val isExpanded = expanded() && !attachmentPanelVisible()
                    flex(1f)
                    val hasAttachments = vm.pendingAttachments.isNotEmpty()
                    // 展开或有附件时进入“编辑器”两层布局；收起态为单行胶囊。
                    height(
                        if (isExpanded) 82f + if (hasAttachments) 56f else 0f else 56f,
                    )
                    borderRadius(if (isExpanded) 22f else 28f)
                    backgroundColor(Color.WHITE)
                    boxShadow(BoxShadow(0f, 4f, 18f, Color(0x10000000L)))
                    animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
                }
                vif({ vm.pendingAttachments.isNotEmpty() }) {
                    View {
                        attr {
                            absolutePosition(left = 10f, top = 8f, right = 10f)
                            height(42f)
                            flexDirectionRow()
                            overflow(true)
                        }
                        vm.pendingAttachments.forEach { attachment ->
                            PendingAttachmentCard(attachment) { onRemoveAttachment(attachment.id) }
                        }
                    }
                }
                View {
                    attr {
                        val isExpanded = expanded() && !attachmentPanelVisible()
                        absolutePosition(left = 8f, top = if (isExpanded) 40f + if (vm.pendingAttachments.isNotEmpty()) 56f else 0f else 10f)
                        size(36f, 36f)
                        borderRadius(18f)
                        allCenter()
                        backgroundColor(AppTheme.surfaceMuted)
                        animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
                    }
                    event { click { onAttachClick() } }
                    Icon(if (attachmentPanelVisible()) IconKind.CLOSE else IconKind.PLUS, 18f, AppTheme.textSecondary, 1.8f)
                }
                Input {
                    ref { onInputRef(it) }
                    attr {
                        val isExpanded = expanded() && !attachmentPanelVisible()
                        absolutePosition(
                            left = if (isExpanded) 12f else 52f,
                            top = if (isExpanded) 10f + if (vm.pendingAttachments.isNotEmpty()) 56f else 0f else 16f,
                            right = if (isExpanded) 12f else 52f,
                        )
                        height(28f)
                        opacity(if (voiceMode()) 0f else 1f)
                        touchEnable(!voiceMode())
                        fontSize(16f)
                        color(AppTheme.textPrimary)
                        placeholder("输入股票名称、代码或问题")
                        placeholderColor(AppTheme.textTertiary)
                        returnKeyTypeSend()
                        maxTextLength(200)
                        animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
                    }
                    event {
                        textDidChange { vm.inputText = it.text }
                        inputReturn { onSend(it.text) }
                        inputFocus {
                            if (attachmentPanelVisible()) onAttachClick()
                            onFocusChange(true)
                        }
                        inputBlur { onFocusChange(false) }
                        keyboardHeightChange { onKeyboardHeight(it.height) }
                    }
                }
                View {
                    attr {
                        absolutePosition(left = 52f, top = if (vm.pendingAttachments.isNotEmpty()) 56f else 0f, right = 52f, bottom = 0f)
                        allCenter()
                        opacity(if (voiceMode()) 1f else 0f)
                        touchEnable(voiceMode())
                    }
                    event {
                        click { }
                        longPress { onVoiceLongPress(it) }
                    }
                    Text {
                        attr {
                            text("按住说话")
                            fontSize(15f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                        }
                    }
                }
                View {
                    attr {
                        val isExpanded = expanded() && !attachmentPanelVisible()
                        absolutePosition(right = 8f, top = if (isExpanded) 40f + if (vm.pendingAttachments.isNotEmpty()) 56f else 0f else 10f)
                        size(36f, 36f)
                        borderRadius(18f)
                        allCenter()
                        backgroundColor(
                            when {
                                voiceMode() -> AppTheme.surfaceMuted
                                vm.isGenerating -> AppTheme.ink
                                vm.inputText.isNotBlank() || vm.pendingAttachments.isNotEmpty() -> AppTheme.ink
                                else -> Color.TRANSPARENT
                            },
                        )
                        animation(Animation.easeOut(0.24f), isExpanded)
                    }
                    event {
                        click {
                            when {
                                attachmentPanelVisible() -> onAttachClick()
                                voiceMode() -> onVoiceClick()
                                vm.isGenerating -> onStop()
                                vm.inputText.isNotBlank() -> onSend(vm.inputText)
                                else -> onVoiceClick()
                            }
                        }
                    }
                    vif({ attachmentPanelVisible() }) { Icon(IconKind.CLOSE, 18f, AppTheme.textSecondary) }
                    velse {
                        vif({ voiceMode() }) { Icon(IconKind.KEYBOARD, 18f, AppTheme.textSecondary) }
                        velse {
                            vif({ vm.isGenerating }) { Icon(IconKind.STOP, 14f, Color.WHITE) }
                            velse {
                                vif({ vm.inputText.isNotBlank() || vm.pendingAttachments.isNotEmpty() }) {
                                    Icon(IconKind.ARROW_UP, 18f, Color.WHITE, 2.4f)
                                }
                                velse { Icon(IconKind.VOICE, 20f, AppTheme.textSecondary, 1.8f) }
                            }
                        }
                    }
                }
            }
        }
        // 附件面板：嵌入输入框下方，由 ComposerView 统一管理布局，与输入框紧密贴合。
        View {
            attr {
                val isVisible = attachmentPanelVisible()
                // 面板高度包含底部留白，避免入口卡片被 Android 导航栏裁切。
                height(if (isVisible) 144f else 0f)
                opacity(if (isVisible) 1f else 0f)
                overflow(true)
                marginTop(0f)
                zIndex(5, useOutline = false)
                paddingLeft(8f); paddingRight(8f); paddingTop(0f); paddingBottom(20f)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsStretch()
                    justifyContentSpaceBetween()
                    paddingLeft(10f); paddingRight(10f); paddingTop(10f); paddingBottom(10f)
                }
                AttachmentActionCard(IconKind.CAMERA, "拍照", onPickCamera)
                AttachmentActionCard(IconKind.IMAGE, "相册", onPickPhoto)
                AttachmentActionCard(IconKind.FOLDER, "文件", onPickFile)
            }
        }
        // 键盘或附件面板状态下直接不创建免责声明节点，避免零高度动画节点仍叠加到输入框。
        vif({ !expanded() && !keyboardVisible() && !attachmentPanelVisible() }) {
            View {
                attr { height(24f); allCenter() }
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
}

private fun ViewContainer<*, *>.PendingAttachmentCard(attachment: Attachment, onRemove: () -> Unit) {
    View {
        attr {
            width(if (attachment.kind == AttachmentKind.IMAGE) 42f else 128f)
            height(40f)
            marginRight(6f)
            borderRadius(9f)
            backgroundColor(AppTheme.surfaceMuted)
            paddingLeft(8f); paddingRight(6f)
            flexDirectionRow(); alignItemsCenter()
        }
        Text {
            attr {
                text(if (attachment.kind == AttachmentKind.IMAGE) "图片" else attachment.displayName)
                fontSize(10f); color(AppTheme.textSecondary); lines(1); flex(1f)
            }
        }
        View {
            attr { size(18f, 18f); allCenter() }
            event { click { onRemove() } }
            Text { attr { text("×"); fontSize(13f); color(AppTheme.textTertiary) } }
        }
    }
}

/** 长按录音时手指所在区域：底部发送、左侧取消、右侧编辑。 */
enum class VoiceRecordZone { SEND, EDIT, CANCEL }

internal const val VOICE_OVERLAY_BODY = 360f

private val voiceSendBlue = Color(0xFF4D8EFFL)
private val voiceEditGray = Color(0xFFC4C4C4L)
private val voiceFingerFill = Color(0, 0, 0, 0.12f)

fun voiceRecordZone(
    pageX: Float,
    pageY: Float,
    pageWidth: Float,
    pageHeight: Float,
    bottomInset: Float,
): VoiceRecordZone {
    val fromBottom = pageHeight - pageY
    if (fromBottom <= 88f + bottomInset) return VoiceRecordZone.SEND
    if (fromBottom <= 300f + bottomInset) {
        val edge = pageWidth * 0.42f
        if (pageX < edge) return VoiceRecordZone.CANCEL
        if (pageX > pageWidth - edge) return VoiceRecordZone.EDIT
    }
    return VoiceRecordZone.SEND
}

/** Kimi 式长按录音浮层：面板升起，三态拖动，波形随区域变色。 */
fun ViewContainer<*, *>.VoiceRecordingOverlay(
    visible: () -> Boolean,
    phase: () -> Int,
    zone: () -> VoiceRecordZone,
    fingerX: () -> Float,
    fingerY: () -> Float,
    pageHeight: Float,
    bottomInset: Float,
) {
    val overlayHeight = VOICE_OVERLAY_BODY + bottomInset
    View {
        attr {
            val isVisible = visible()
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(overlayHeight)
            paddingBottom(bottomInset)
            backgroundColor(Color.WHITE)
            borderRadius(28f, 28f, 0f, 0f)
            boxShadow(BoxShadow(0f, -6f, 28f, Color(0x14000000L)))
            opacity(if (isVisible) 1f else 0f)
            transform(Translate(0f, 0f, 0f, if (isVisible) 0f else overlayHeight + 24f))
            animation(Animation.easeOut(0.26f), isVisible)
            touchEnable(false)
            zIndex(20, useOutline = false)
        }
        View {
            attr { alignItemsCenter(); paddingTop(56f) }
            VoiceWave(phase, zone)
            Text {
                attr {
                    val current = zone()
                    text(
                        when (current) {
                            VoiceRecordZone.SEND -> "松开发送"
                            VoiceRecordZone.EDIT -> "编辑文字"
                            VoiceRecordZone.CANCEL -> "取消输入"
                        },
                    )
                    fontSize(12f)
                    color(AppTheme.textTertiary)
                    marginTop(12f)
                    animation(Animation.easeOut(0.16f), current)
                }
            }
        }
        View { attr { flex(1f) } }
        View {
            attr {
                flexDirectionRow(); justifyContentSpaceBetween(); alignItemsCenter()
                paddingLeft(36f); paddingRight(36f); marginBottom(22f); height(48f)
            }
            VoiceTargetChip(
                armed = { zone() == VoiceRecordZone.CANCEL },
                danger = true,
                icon = IconKind.CLOSE,
                label = "取消",
            )
            VoiceTargetChip(
                armed = { zone() == VoiceRecordZone.EDIT },
                danger = false,
                icon = IconKind.EDIT,
                label = "编辑",
            )
        }
        View {
            attr {
                val sending = zone() == VoiceRecordZone.SEND
                height(56f)
                marginLeft(8f); marginRight(8f); marginBottom(8f)
                borderRadius(28f)
                allCenter()
                backgroundColor(if (sending) Color.WHITE else AppTheme.surfaceMuted)
                boxShadow(
                    if (sending) BoxShadow(0f, 4f, 18f, Color(0x18000000L))
                    else BoxShadow(0f, 0f, 0f, Color.TRANSPARENT),
                )
                animation(Animation.easeOut(0.18f), sending)
            }
            Text {
                attr {
                    val sending = zone() == VoiceRecordZone.SEND
                    text(if (sending) "松开发送" else "语音输入")
                    fontSize(15f); fontWeight600()
                    color(if (sending) voiceSendBlue else AppTheme.textTertiary)
                    animation(Animation.easeOut(0.18f), sending)
                }
            }
        }
        View {
            attr {
                val isVisible = visible()
                absolutePosition(
                    left = fingerX() - 19f,
                    top = fingerY() - (pageHeight - overlayHeight) - 19f,
                )
                size(38f, 38f)
                borderRadius(19f)
                backgroundColor(voiceFingerFill)
                opacity(if (isVisible) 1f else 0f)
                touchEnable(false)
                animation(Animation.linear(0.01f), fingerX() + fingerY())
            }
        }
    }
}

private fun ViewContainer<*, *>.VoiceTargetChip(
    armed: () -> Boolean,
    danger: Boolean,
    icon: IconKind,
    label: String,
) {
    View {
        attr {
            val isArmed = armed()
            height(48f)
            width(if (isArmed) 112f else 44f)
            borderRadius(24f)
            paddingLeft(13f)
            flexDirectionRow()
            alignItemsCenter()
            overflow(true)
            backgroundColor(if (isArmed) Color.WHITE else Color(0x00FFFFFFL))
            animation(Animation.easeOut(0.18f), isArmed)
        }
        vif({ armed() }) {
            Icon(icon, 18f, if (danger) AppTheme.down else AppTheme.textPrimary)
        }
        velse {
            Icon(icon, 18f, AppTheme.textTertiary)
        }
        Text {
            attr {
                val isArmed = armed()
                text(label)
                fontSize(14f)
                fontWeight600()
                color(if (danger) AppTheme.down else AppTheme.textPrimary)
                marginLeft(6f)
                opacity(if (isArmed) 1f else 0f)
                animation(Animation.easeOut(0.18f), isArmed)
            }
        }
    }
}

private fun ViewContainer<*, *>.VoiceWave(phase: () -> Int, zone: () -> VoiceRecordZone) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); height(18f) }
        repeat(16) { index ->
            View {
                attr {
                    val p = phase()
                    val current = zone()
                    width(4f)
                    height(waveBarHeight(index, p, current))
                    borderRadius(2f)
                    marginRight(if (index == 15) 0f else 3.5f)
                    backgroundColor(waveColor(current))
                    animation(Animation.easeInOut(0.12f), p)
                }
            }
        }
    }
}

private fun waveBarHeight(index: Int, phase: Int, zone: VoiceRecordZone): Float {
    val wave = ((sin((phase * 0.28f + index * 0.62f).toDouble()) + 1.0) * 0.5).toFloat()
    val amplitude = if (zone == VoiceRecordZone.SEND) 6f else 3.2f
    return 7f + wave * amplitude
}

private fun waveColor(zone: VoiceRecordZone): Color = when (zone) {
    VoiceRecordZone.SEND -> voiceSendBlue
    VoiceRecordZone.EDIT -> voiceEditGray
    VoiceRecordZone.CANCEL -> AppTheme.down
}

private fun ViewContainer<*, *>.AttachmentActionCard(icon: IconKind, title: String, onClick: () -> Unit) {
    View {
        attr {
            flex(1f)
            height(96f)
            alignItemsCenter()
            justifyContentCenter()
            marginLeft(4f); marginRight(4f)
        }
        event { click { onClick() } }
        View {
            attr {
                width(96f)
                height(96f)
                borderRadius(14f)
                flexDirectionColumn()
                allCenter()
                backgroundColor(AppTheme.surfaceMuted)
            }
            View {
                attr {
                    width(28f)
                    height(28f)
                    allCenter()
                }
                Icon(icon, 24f, AppTheme.textSecondary, 1.6f)
            }
            Text { attr { text(title); fontSize(13f); color(AppTheme.textSecondary); marginTop(8f) } }
        }
    }
}

// endregion
