package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal interface StockChatScreenHost {
    val chatViewModel: ChatViewModel
    val drawerState: DrawerState
    var keyboardHeight: Float
    var composerFocused: Boolean
    var attachmentPanelVisible: Boolean
    var voiceMode: Boolean
    var voiceRecording: Boolean
    var voiceZone: VoiceRecordZone
    var voiceWavePhase: Int
    var voiceFingerX: Float
    var voiceFingerY: Float
    var promptPage: Int
    var listRef: ViewRef<ListView<*, *>>?
    var inputRef: ViewRef<InputView>?
    var listContentHeight: Float
    var followBottom: Boolean
    var panSource: Int
    var suppressDrawerClick: Boolean
    fun sendPrompt(text: String)
    fun openDrawer()
    fun closeDrawer()
    fun toggleTts()
    fun speakMessage(message: ChatUiMessage)
    fun feedback(message: ChatUiMessage, value: Int)
    fun shareMessage(message: ChatUiMessage)
    fun copyText(text: String)
    fun toggleAttachmentPanel()
    fun toggleVoiceMode()
    fun handleVoiceLongPress(params: LongPressParams)
    fun selectAttachment(type: String)
    fun openDetail(instrumentKey: String)
    fun openSettings()
    fun openDrawerSkill(skill: DrawerSkill)
    fun handleHomeSwipe(params: PanGestureParams)
    fun handleCloseSwipe(params: PanGestureParams)
    fun scrollToBottom(animated: Boolean)
    fun openMarket()
}

internal fun StockChatScreen(
    host: StockChatScreenHost,
    pageWidth: Float,
    pageHeight: Float,
    statusBarHeight: Float,
    safeAreaTop: Float,
    bottomInset: Float,
): ViewBuilder {
    val contentWidth = pageWidth - AppTheme.pageHorizontalPadding * 2
    val drawerWidth = chatDrawerWidth(pageWidth)
    return {
        attr { backgroundColor(AppTheme.drawerBackground); overflow(true) }
        View {
            attr {
                val progress = host.drawerState.progress.coerceIn(0f, 1f)
                val homeShift = (drawerWidth - pageWidth * (1f - DrawerMotion.HOME_MIN_SCALE) / 2f) * progress
                val homeScale = 1f - (1f - DrawerMotion.HOME_MIN_SCALE) * progress
                absolutePosition(0f, 0f, 0f, 0f)
                backgroundColor(Color.WHITE)
                overflow(true)
                zIndex(2, useOutline = false)
                borderRadius(DrawerMotion.HOME_MAX_RADIUS * progress)
                transform(scale = Scale(homeScale, homeScale), translate = Translate(0f, 0f, homeShift, 0f))
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                touchEnable(progress == 0f || host.panSource == PAN_SOURCE_HOME)
            }
            event { followPan(host::handleHomeSwipe) }
            View {
                attr {
                    flex(1f)
                    backgroundColor(Color.WHITE)
                    opacity(1f - DrawerMotion.SCRIM_ALPHA * host.drawerState.progress)
                    val bottomPadding = when {
                        host.attachmentPanelVisible -> bottomInset
                        host.keyboardHeight > 0f -> host.keyboardHeight
                        else -> 4f
                    }
                    paddingBottom(bottomPadding)
                }
                ChatNavBar(
                    statusBarHeight = statusBarHeight,
                    ttsEnabled = host.chatViewModel.ttsEnabled,
                    onMenu = host::openDrawer,
                    onTts = host::toggleTts,
                    onMarket = host::openMarket,
                )
                vif({ host.chatViewModel.banner.isNotEmpty() }) {
                    ChatBanner(host)
                }
                vif({ !host.chatViewModel.hasConversation }) {
                    View {
                        attr { flex(1f); backgroundColor(Color.WHITE) }
                        WelcomeView(
                            pageWidth = pageWidth,
                            compact = { host.keyboardHeight > 0f },
                            promptPage = { host.promptPage },
                            onShuffle = { host.promptPage += 1 },
                            onPrompt = host::sendPrompt,
                            onOpenInstrument = host::openDetail,
                        )
                    }
                }
                velse {
                    List {
                        ref { host.listRef = it }
                        attr { flex(1f); backgroundColor(Color.WHITE) }
                        event {
                            contentSizeChanged { _, height ->
                                host.listContentHeight = height
                                if (host.followBottom) host.scrollToBottom(animated = false)
                            }
                            dragBegin { host.followBottom = false }
                            scrollEnd { params ->
                                val listHeight = host.listRef?.view?.flexNode?.layoutFrame?.height ?: 0f
                                host.followBottom = params.offsetY + listHeight >= host.listContentHeight - 40f
                            }
                        }
                        vfor({ host.chatViewModel.messages }) { message ->
                            View {
                                if (message.isUser) {
                                    UserMessageView(message)
                                } else {
                                    AssistantMessageView(
                                        message = message,
                                        contentWidth = contentWidth,
                                        onOpenInstrument = host::openDetail,
                                        onFollowUp = host::sendPrompt,
                                        onRetry = host.chatViewModel::retryLast,
                                        onSpeak = host::speakMessage,
                                        onFeedback = host::feedback,
                                        onShare = host::shareMessage,
                                        onCopy = host::copyText,
                                    )
                                }
                            }
                        }
                        View { attr { height(20f) } }
                    }
                }
                ComposerCapsulesView(
                    pageWidth = pageWidth,
                    promptPage = { host.promptPage },
                    visible = {
                        host.keyboardHeight == 0f && !host.voiceMode && !host.attachmentPanelVisible
                    },
                    onShuffle = { host.promptPage += 1 },
                    onPrompt = host::sendPrompt,
                )
                ComposerView(
                    vm = host.chatViewModel,
                    expanded = {
                        (host.composerFocused || host.keyboardHeight > 0f) && !host.attachmentPanelVisible
                    },
                    keyboardVisible = { host.keyboardHeight > 0f },
                    attachmentPanelVisible = { host.attachmentPanelVisible },
                    voiceMode = { host.voiceMode },
                    onSend = host::sendPrompt,
                    onStop = host.chatViewModel::stopGenerating,
                    onKeyboardHeight = {
                        if (host.attachmentPanelVisible) {
                            host.keyboardHeight = 0f
                            host.composerFocused = false
                        } else {
                            host.keyboardHeight = it
                            host.composerFocused = it > 0f
                        }
                    },
                    onFocusChange = { host.composerFocused = it },
                    onInputRef = { host.inputRef = it },
                    onAttachClick = host::toggleAttachmentPanel,
                    onVoiceClick = host::toggleVoiceMode,
                    onVoiceLongPress = host::handleVoiceLongPress,
                    onPickCamera = { host.selectAttachment("拍照") },
                    onPickPhoto = { host.selectAttachment("照片") },
                    onPickFile = { host.selectAttachment("本地文件") },
                    onRemoveAttachment = host.chatViewModel::removePendingAttachment,
                )
            }
            VoiceRecordingOverlay(
                visible = { host.voiceRecording },
                phase = { host.voiceWavePhase },
                zone = { host.voiceZone },
                fingerX = { host.voiceFingerX },
                fingerY = { host.voiceFingerY },
                pageHeight = pageHeight,
                bottomInset = bottomInset,
            )
        }
        DrawerScrim(host, pageWidth, drawerWidth)
        View {
            attr {
                val progress = host.drawerState.progress.coerceIn(0f, 1f)
                absolutePosition(left = 0f, top = 0f, bottom = 0f)
                transform(Translate(0f, 0f, DrawerMotion.drawerShift(progress, drawerWidth), 0f))
                width(drawerWidth)
                backgroundColor(DrawerMotion.DRAWER_BG)
                overflow(true)
                touchEnable(progress > 0f && host.panSource != PAN_SOURCE_HOME)
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                zIndex(1, useOutline = false)
            }
            event { followPan(host::handleCloseSwipe) }
            HistoryDrawerContent(
                vm = host.chatViewModel,
                statusBarHeight = maxOf(statusBarHeight, safeAreaTop),
                bottomInset = bottomInset,
                onClose = { if (!host.suppressDrawerClick) host.closeDrawer() },
                onOpen = { if (!host.suppressDrawerClick) host.chatViewModel.openConversation(it) },
                onDelete = { if (!host.suppressDrawerClick) host.chatViewModel.deleteConversation(it) },
                onNewChat = { if (!host.suppressDrawerClick) host.chatViewModel.newConversation() },
                onSettings = { if (!host.suppressDrawerClick) host.openSettings() },
                onSkill = { if (!host.suppressDrawerClick) host.openDrawerSkill(it) },
                onPan = host::handleCloseSwipe,
            )
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.ChatBanner(host: StockChatScreenHost) {
    View {
        attr {
            flexDirectionRow(); alignItemsCenter()
            backgroundColor(AppTheme.warningSoft)
            paddingLeft(16f); paddingRight(16f); paddingTop(6f); paddingBottom(6f)
        }
        Icon(IconKind.WIFI_OFF, 14f, AppTheme.warning)
        Text {
            attr {
                text(host.chatViewModel.banner)
                fontSize(12f)
                color(AppTheme.warning)
                marginLeft(6f)
                flex(1f)
            }
        }
        View {
            attr { padding(4f) }
            event { click { host.chatViewModel.banner = "" } }
            Icon(IconKind.CLOSE, 14f, AppTheme.warning)
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.DrawerScrim(
    host: StockChatScreenHost,
    pageWidth: Float,
    drawerWidth: Float,
) {
    View {
        attr {
            val progress = host.drawerState.progress.coerceIn(0f, 1f)
            absolutePosition(0f, 0f, 0f, 0f)
            val homeScale = 1f - (1f - DrawerMotion.HOME_MIN_SCALE) * progress
            val homeShift = (drawerWidth - pageWidth * (1f - DrawerMotion.HOME_MIN_SCALE) / 2f) * progress
            transform(scale = Scale(homeScale, homeScale), translate = Translate(0f, 0f, homeShift, 0f))
            backgroundColor(Color.TRANSPARENT)
            touchEnable((progress > 0f || host.drawerState.settling) && host.panSource != PAN_SOURCE_HOME)
            capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            zIndex(3, useOutline = false)
        }
        event {
            click { if (!host.suppressDrawerClick) host.closeDrawer() }
            followPan(host::handleCloseSwipe)
        }
    }
}
