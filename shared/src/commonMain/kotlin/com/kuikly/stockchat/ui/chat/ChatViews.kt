package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.Intent
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
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

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
    pageWidth: Float,
    compact: () -> Boolean,
    promptPage: () -> Int,
    onShuffle: () -> Unit,
    onPrompt: (String) -> Unit,
    onOpenInstrument: (String) -> Unit,
) {
    View {
        attr { flex(1f); paddingLeft(16f); paddingRight(16f) }
        View {
            attr { flex(1f); allCenter() }
            Image {
                attr {
                    val isCompact = compact()
                    size(58f, 58f)
                    borderRadius(29f)
                    src(ImageUri.commonAssets("robot.png"))
                    transform(Translate(0f, 0f, 0f, if (isCompact) 30f else 0f))
                    animation(Animation.easeOut(0.28f), isCompact)
                }
            }
            View {
                attr {
                    val isCompact = compact()
                    height(if (isCompact) 0f else 86f)
                    alignItemsCenter()
                    overflow(true)
                    animation(Animation.easeOut(0.28f), isCompact)
                }
                View {
                    attr {
                        val isCompact = compact()
                        alignItemsCenter()
                        opacity(if (isCompact) 0f else 1f)
                        transform(Translate(0f, 0f, 0f, if (isCompact) 16f else 0f))
                        animation(Animation.easeOut(0.2f), isCompact)
                    }
                    Text {
                        attr {
                            text("你好，今天想了解\n哪只股票的行情？")
                            fontSize(20f); lineHeight(30f); textAlignCenter()
                            color(AppTheme.textPrimary); marginTop(26f)
                        }
                    }
                }
            }
        }
        View {
            attr {
                val isCompact = compact()
                height(if (isCompact) 0f else 184f)
                opacity(if (isCompact) 0f else 1f)
                transform(Translate(0f, 0f, 0f, if (isCompact) 12f else 0f))
                overflow(true)
                animation(Animation.easeOut(0.22f), isCompact)
            }
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter(); justifyContentSpaceBetween()
                    height(32f); marginBottom(8f)
                }
                Text {
                    attr {
                        text("猜你想问")
                        fontSize(13f); fontWeight600(); color(AppTheme.textSecondary)
                    }
                }
                ShuffleButton(
                    promptPage = promptPage,
                    pageCount = PromptBank.pageCount(),
                    onClick = onShuffle,
                )
            }
            (0 until PromptBank.WELCOME_PAGE_SIZE).forEach { index ->
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        height(40f); marginBottom(8f)
                        backgroundColor(AppTheme.surfaceMuted); borderRadius(20f)
                        paddingLeft(14f); paddingRight(12f)
                    }
                    event {
                        click {
                            PromptBank.welcomePage(promptPage()).getOrNull(index)?.prompt?.let(onPrompt)
                        }
                    }
                    Icon(IconKind.SPARKLE, 16f, AppTheme.textTertiary)
                    Text {
                        attr {
                            val item = PromptBank.welcomePage(promptPage()).getOrNull(index)
                            text(item?.prompt ?: "")
                            fontSize(14f); color(AppTheme.textPrimary); marginLeft(8f)
                            flex(1f); lines(1)
                        }
                    }
                    Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.textTertiary)
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
    Intent.MARKET_OVERVIEW -> "正在汇总主要指数与市场表现…"
    Intent.KNOWLEDGE -> "正在整理相关投资知识…"
    Intent.GREETING -> "正在准备可提问的股票场景…"
    Intent.UNKNOWN -> "正在理解问题并匹配分析场景…"
}

// endregion

// region 输入框

/** 输入框上方的提问胶囊：固定 6 个坑位，文案在 attr 里读页码，保证换一换会刷新。 */
fun ViewContainer<*, *>.ComposerCapsulesView(
    pageWidth: Float,
    promptPage: () -> Int,
    visible: () -> Boolean,
    onShuffle: () -> Unit,
    onPrompt: (String) -> Unit,
) {
    View {
        attr {
            val isVisible = visible()
            height(if (isVisible) 100f else 0f)
            opacity(if (isVisible) 1f else 0f)
            overflow(true)
            animation(Animation.easeOut(0.2f), isVisible)
        }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter(); justifyContentSpaceBetween()
                height(28f); paddingLeft(16f); paddingRight(12f)
            }
            Text {
                attr {
                    text("大家都在问")
                    fontSize(11f); fontWeight600(); color(AppTheme.textTertiary)
                }
            }
            ShuffleButton(
                promptPage = promptPage,
                pageCount = PromptBank.pageCount(PromptBank.CAPSULE_PAGE_SIZE, PromptBank.composerCapsules),
                onClick = onShuffle,
            )
        }
        View {
            attr {
                flexDirectionRow(); flexWrapWrap(); alignItemsCenter()
                paddingLeft(16f); paddingRight(8f)
            }
            (0 until PromptBank.CAPSULE_PAGE_SIZE).forEach { index ->
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        height(32f); marginRight(8f); marginBottom(8f)
                        backgroundColor(AppTheme.surfaceMuted); borderRadius(16f)
                        paddingLeft(12f); paddingRight(12f)
                    }
                    event {
                        click {
                            PromptBank.capsulePage(promptPage()).getOrNull(index)?.prompt?.let(onPrompt)
                        }
                    }
                    Text {
                        attr {
                            val item = PromptBank.capsulePage(promptPage()).getOrNull(index)
                            text(item?.capsule ?: "")
                            fontSize(13f)
                            color(AppTheme.textPrimary)
                            lines(1)
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ShuffleButton(
    promptPage: () -> Int,
    pageCount: Int,
    onClick: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow(); alignItemsCenter()
            height(28f)
            paddingLeft(10f); paddingRight(10f)
            backgroundColor(Color(0xFFEAF4FAL))
            borderRadius(14f)
        }
        event { click { onClick() } }
        Icon(IconKind.REFRESH, 13f, Color(0xFF398BB5L), 1.6f)
        Text {
            attr {
                val page = promptPage()
                val current = if (pageCount == 0) 1 else page.mod(pageCount) + 1
                text("换一换 $current/$pageCount")
                fontSize(12f); color(Color(0xFF398BB5L)); marginLeft(4f)
            }
        }
    }
}

fun ViewContainer<*, *>.ComposerView(
    vm: ChatViewModel,
    expanded: () -> Boolean,
    voiceMode: () -> Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onKeyboardHeight: (Float) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onInputRef: (ViewRef<InputView>) -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onVoiceLongPress: (LongPressParams) -> Unit,
) {
    View {
        attr { backgroundColor(Color(0xFFFFFFFFL)) }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                val isExpanded = expanded()
                paddingLeft(8f); paddingRight(8f); paddingTop(8f)
                paddingBottom(if (isExpanded) 8f else 0f)
                animation(Animation.easeOut(0.24f), isExpanded)
            }
            // 聚焦时由单行胶囊展开为上下两层，输入节点本身保持不变，避免丢失焦点。
            View {
                attr {
                    val isExpanded = expanded()
                    flex(1f)
                    height(if (isExpanded) 82f else 56f)
                    borderRadius(if (isExpanded) 22f else 28f)
                    backgroundColor(Color.WHITE)
                    boxShadow(BoxShadow(0f, 4f, 18f, Color(0x10000000L)))
                    animation(Animation.easeOut(0.24f), isExpanded)
                }
                View {
                    attr {
                        val isExpanded = expanded()
                        absolutePosition(left = 8f, top = if (isExpanded) 40f else 10f)
                        size(36f, 36f)
                        borderRadius(18f)
                        allCenter()
                        backgroundColor(AppTheme.surfaceMuted)
                        animation(Animation.easeOut(0.24f), isExpanded)
                    }
                    event { click { onAttachClick() } }
                    Icon(IconKind.PLUS, 18f, AppTheme.textSecondary, 1.8f)
                }
                Input {
                    ref { onInputRef(it) }
                    attr {
                        val isExpanded = expanded()
                        absolutePosition(
                            left = if (isExpanded) 12f else 52f,
                            top = if (isExpanded) 10f else 16f,
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
                        animation(Animation.easeOut(0.24f), isExpanded)
                    }
                    event {
                        textDidChange { vm.inputText = it.text }
                        inputReturn { onSend(it.text) }
                        inputFocus { onFocusChange(true) }
                        inputBlur { onFocusChange(false) }
                        keyboardHeightChange { onKeyboardHeight(it.height) }
                    }
                }
                View {
                    attr {
                        absolutePosition(left = 52f, top = 0f, right = 52f, bottom = 0f)
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
                        val isExpanded = expanded()
                        absolutePosition(right = 8f, top = if (isExpanded) 40f else 10f)
                        size(36f, 36f)
                        borderRadius(18f)
                        allCenter()
                        backgroundColor(
                            when {
                                voiceMode() -> AppTheme.surfaceMuted
                                vm.isGenerating -> AppTheme.ink
                                vm.inputText.isNotBlank() -> AppTheme.ink
                                else -> Color.TRANSPARENT
                            },
                        )
                        animation(Animation.easeOut(0.24f), isExpanded)
                    }
                    event {
                        click {
                            when {
                                voiceMode() -> onVoiceClick()
                                vm.isGenerating -> onStop()
                                vm.inputText.isNotBlank() -> onSend(vm.inputText)
                                else -> onVoiceClick()
                            }
                        }
                    }
                    vif({ voiceMode() }) { Icon(IconKind.KEYBOARD, 18f, AppTheme.textSecondary) }
                    velse {
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
        }
        View {
            attr {
                val isExpanded = expanded()
                height(if (isExpanded) 0f else 24f)
                opacity(if (isExpanded) 0f else 1f)
                transform(Translate(0f, 0f, 0f, if (isExpanded) 8f else 0f))
                allCenter(); overflow(true)
                animation(Animation.easeOut(0.2f), isExpanded)
            }
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

/** Kimi 式长按录音浮层：底部面板升起，波形持续变化，松手后发送。 */
fun ViewContainer<*, *>.VoiceRecordingOverlay(
    visible: () -> Boolean,
    phase: () -> Int,
    cancelArmed: () -> Boolean,
    bottomInset: Float,
) {
    View {
        attr {
            val isVisible = visible()
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(500f + bottomInset)
            paddingBottom(bottomInset)
            backgroundColor(Color.WHITE)
            borderRadius(26f)
            boxShadow(BoxShadow(0f, -8f, 30f, Color(0x14000000L)))
            opacity(if (isVisible) 1f else 0f)
            transform(Translate(0f, 0f, 0f, if (isVisible) 0f else 540f))
            animation(Animation.easeOut(0.24f), isVisible)
            touchEnable(false)
            zIndex(20, useOutline = false)
        }
        View {
            attr { alignItemsCenter(); paddingTop(72f) }
            VoiceWave(phase, cancelArmed)
            Text {
                attr {
                    text(if (cancelArmed()) "松开取消" else "松开发送")
                    fontSize(12f)
                    color(if (cancelArmed()) AppTheme.down else AppTheme.textTertiary)
                    marginTop(12f)
                }
            }
        }
        View { attr { flex(1f) } }
        View {
            attr {
                flexDirectionRow(); justifyContentSpaceBetween(); alignItemsCenter()
                paddingLeft(42f); paddingRight(42f); marginBottom(24f)
            }
            Icon(IconKind.CLOSE, 18f, AppTheme.textTertiary)
            Icon(IconKind.EDIT, 18f, AppTheme.textTertiary)
        }
        View {
            attr {
                height(58f)
                marginLeft(8f); marginRight(8f); marginBottom(8f)
                borderRadius(29f)
                allCenter()
                backgroundColor(Color.WHITE)
                boxShadow(BoxShadow(0f, 3f, 18f, Color(0x16000000L)))
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(if (cancelArmed()) "松开取消" else "松开发送")
                        fontSize(14f); fontWeight600()
                        color(if (cancelArmed()) AppTheme.down else Color(0xFF4D9FEFL))
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginLeft(5f); height(16f) }
                    repeat(4) { index ->
                        View {
                            attr {
                                width(2f); height(if ((phase() + index) % 3 == 0) 12f else 7f)
                                borderRadius(1f); marginRight(2f)
                                backgroundColor(if (cancelArmed()) AppTheme.down else Color(0xFF4D9FEFL))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.VoiceWave(phase: () -> Int, cancelArmed: () -> Boolean) {
    val levels = listOf(3f, 6f, 10f, 14f, 8f, 5f, 12f, 16f, 9f, 5f, 13f, 8f)
    View {
        attr { flexDirectionRow(); alignItemsCenter(); height(22f) }
        repeat(20) { index ->
            View {
                attr {
                    val p = phase()
                    width(2f)
                    height(levels[(index + p) % levels.size])
                    borderRadius(1f)
                    marginRight(2f)
                    backgroundColor(if (cancelArmed()) AppTheme.down else Color(0xFF4D9FEFL))
                    animation(Animation.easeInOut(0.1f), p)
                }
            }
        }
    }
}

/** 输入框上方的附件操作面板，保持轻量但具备完整的打开、选择、关闭闭环。 */
fun ViewContainer<*, *>.AttachmentPanel(
    visible: () -> Boolean,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPickStock: () -> Unit,
) {
    View {
        attr {
            val isVisible = visible()
            height(if (isVisible) 82f else 0f)
            opacity(if (isVisible) 1f else 0f)
            overflow(true)
            animation(Animation.easeOut(0.2f), isVisible)
            paddingLeft(16f); paddingRight(16f); paddingBottom(8f)
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                justifyContentSpaceBetween()
                backgroundColor(Color.WHITE)
                borderRadius(16f)
                paddingLeft(12f); paddingRight(12f); paddingTop(10f); paddingBottom(10f)
                boxShadow(BoxShadow(0f, 2f, 12f, Color(0x12000000L)))
            }
            AttachmentAction(IconKind.CHART, "股票", onPickStock)
            AttachmentAction(IconKind.BOOK, "文件", onPickFile)
            AttachmentAction(IconKind.CANDLE, "图片", onPickImage)
        }
    }
}

private fun ViewContainer<*, *>.AttachmentAction(icon: IconKind, title: String, onClick: () -> Unit) {
    View {
        attr {
            flex(1f)
            alignItemsCenter()
            paddingTop(2f); paddingBottom(2f)
        }
        event { click { onClick() } }
        View {
            attr {
                size(34f, 34f)
                borderRadius(17f)
                allCenter()
                backgroundColor(AppTheme.surfaceMuted)
            }
            Icon(icon, 17f, AppTheme.textSecondary)
        }
        Text { attr { text(title); fontSize(11f); color(AppTheme.textSecondary); marginTop(4f) } }
    }
}

// endregion
