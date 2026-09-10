package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.ai.AiService
import com.kuikly.stockchat.data.ai.RemoteAiEngine
import com.kuikly.stockchat.data.ai.VoiceModule
import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.ui.chat.AttachmentPanel
import com.kuikly.stockchat.ui.chat.AssistantMessageView
import com.kuikly.stockchat.ui.chat.ChatNavBar
import com.kuikly.stockchat.ui.chat.ChatViewModel
import com.kuikly.stockchat.ui.chat.ComposerCapsulesView
import com.kuikly.stockchat.ui.chat.ComposerView
import com.kuikly.stockchat.ui.chat.DrawerPhysics
import com.kuikly.stockchat.ui.chat.DrawerMotion
import com.kuikly.stockchat.ui.chat.DrawerState
import com.kuikly.stockchat.ui.chat.HistoryDrawerContent
import com.kuikly.stockchat.ui.chat.UserMessageView
import com.kuikly.stockchat.ui.chat.VoiceRecordingOverlay
import com.kuikly.stockchat.ui.chat.WelcomeView
import com.kuikly.stockchat.ui.chat.chatDrawerWidth
import com.kuikly.stockchat.ui.chat.followPan
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs

/**
 * AI 股票问答主页。
 * 抽屉、主页形变与遮罩共用可中断的显示进度。
 */
@Page("StockChat")
internal class StockChatPage : Pager() {

    private lateinit var vm: ChatViewModel
    private lateinit var voiceModule: VoiceModule
    private val drawer = DrawerState()

    private var keyboardHeight by observable(0f)
    private var composerFocused by observable(false)
    private var attachmentPanelVisible by observable(false)
    private var voiceMode by observable(false)
    private var voiceRecording by observable(false)
    private var voiceCancelArmed by observable(false)
    private var voiceWavePhase by observable(0)
    private var promptPage by observable(0)
    private var listRef: ViewRef<ListView<*, *>>? = null
    private var inputRef: ViewRef<InputView>? = null
    private var listContentHeight = 0f
    private var followBottom = true

    private val drawerBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            closeDrawer()
        }
    }
    private val accessoryBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            attachmentPanelVisible = false
            voiceMode = false
            voiceRecording = false
            voiceCancelArmed = false
            ++voiceWaveGeneration
            syncAccessoryBackHandler()
        }
    }

    override fun created() {
        super.created()
        val http = KuiklyHttpClient(this)
        val store = KuiklyKeyValueStore(this)
        val marketRepository = MarketRepository(http)
        val aiEngine = RemoteAiEngine(http)
        voiceModule = VoiceModule.createDefault()
        vm = ChatViewModel(
            aiService = AiService(pagerId, marketRepository, aiEngine),
            conversationRepository = ConversationRepository(store),
            marketRepository = marketRepository,
        )
        vm.onReplyCompleted = { text ->
            if (vm.ttsEnabled) {
                voiceModule.speak(text) { result ->
                    if (!result.success) setTimeout(0) { vm.banner = result.message }
                }
            }
        }
        vm.loadHistory()
        vm.loadOverview()
        pageData.params.optString("prompt").takeIf { it.isNotEmpty() }?.let { prompt ->
            setTimeout(400) { vm.send(prompt) }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        bindValueChange({ vm.scrollSignal }) {
            if (followBottom) scheduleScrollToBottom()
        }
        bindValueChange({ vm.showHistory }) {
            if (!drawer.dragging) settleDrawer(if (vm.showHistory) 1f else 0f)
            syncDrawerBackHandler()
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val pageWidth = pagerData.pageViewWidth
        val bottomInset = pagerData.safeAreaInsets.bottom
        val contentWidth = pageWidth - AppTheme.pageHorizontalPadding * 2
        val drawerWidth = chatDrawerWidth(pageWidth)
        return {
            attr {
                backgroundColor(AppTheme.drawerBackground)
                overflow(true)
            }

            // 主内容区：抽屉打开时同步平移、缩放并产生圆角形变
            View {
                attr {
                    val p = ctx.drawer.progress.coerceIn(0f, 1f)
                    val homeShift = (drawerWidth - pageWidth * (1f - DrawerMotion.HOME_MIN_SCALE) / 2f) * p
                    val homeScale = 1f - (1f - DrawerMotion.HOME_MIN_SCALE) * p
                    absolutePosition(0f, 0f, 0f, 0f)
                    backgroundColor(Color(0xFFFFFFFFL))
                    overflow(true)
                    // Android 的默认 outline 会在左边缘生成一条类似分割线的投影。
                    zIndex(2, useOutline = false)
                    borderRadius(DrawerMotion.HOME_MAX_RADIUS * p)
                    transform(
                        scale = Scale(homeScale, homeScale),
                        translate = Translate(0f, 0f, homeShift, 0f),
                    )
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                    // 关闭时整个主页都可右滑打开；拖拽开始后保持事件链不断开。
                    touchEnable(p == 0f || ctx.panSource == PAN_SOURCE_HOME)
                }
                event { followPan { params -> ctx.handleHomeSwipe(params) } }
                View {
                    attr {
                        flex(1f)
                        backgroundColor(Color(0xFFFFFFFFL))
                        opacity(1f - DrawerMotion.SCRIM_ALPHA * ctx.drawer.progress)
                        paddingBottom(if (ctx.keyboardHeight > 0) ctx.keyboardHeight else bottomInset)
                        animation(Animation.easeOut(0.2f), ctx.keyboardHeight)
                    }
                    ChatNavBar(
                        statusBarHeight = ctx.pagerData.statusBarHeight,
                        ttsEnabled = ctx.vm.ttsEnabled,
                        onMenu = { ctx.openDrawer() },
                        onTts = { ctx.toggleTts() },
                    )
                    vif({ ctx.vm.banner.isNotEmpty() }) {
                        View {
                            attr {
                                flexDirectionRow(); alignItemsCenter()
                                backgroundColor(AppTheme.warningSoft)
                                paddingLeft(16f); paddingRight(16f); paddingTop(6f); paddingBottom(6f)
                            }
                            Icon(IconKind.WIFI_OFF, 14f, AppTheme.warning)
                            Text {
                                attr {
                                    text(ctx.vm.banner)
                                    fontSize(12f)
                                    color(AppTheme.warning)
                                    marginLeft(6f)
                                    flex(1f)
                                }
                            }
                            View {
                                attr { padding(4f) }
                                event { click { ctx.vm.banner = "" } }
                                Icon(IconKind.CLOSE, 14f, AppTheme.warning)
                            }
                        }
                    }
                    vif({ !ctx.vm.hasConversation }) {
                        View {
                            attr {
                                flex(1f)
                                backgroundColor(Color(0xFFFFFFFFL))
                            }
                            WelcomeView(
                                pageWidth = pageWidth,
                                compact = { ctx.keyboardHeight > 0f },
                                promptPage = { ctx.promptPage },
                                onShuffle = { ctx.promptPage += 1 },
                                onPrompt = { ctx.send(it) },
                                onOpenInstrument = { ctx.openDetail(it) },
                            )
                        }
                    }
                    velse {
                        List {
                            ref { ctx.listRef = it }
                            attr {
                                flex(1f)
                                backgroundColor(Color(0xFFFFFFFFL))
                            }
                            event {
                                contentSizeChanged { _, height ->
                                    ctx.listContentHeight = height
                                    if (ctx.followBottom) ctx.scrollToBottom(animated = false)
                                }
                                dragBegin { ctx.followBottom = false }
                                scrollEnd { params ->
                                    val listHeight = ctx.listRef?.view?.flexNode?.layoutFrame?.height ?: 0f
                                    ctx.followBottom = params.offsetY + listHeight >= ctx.listContentHeight - 40f
                                }
                            }
                            vfor({ ctx.vm.messages }) { message ->
                                View {
                                    attr { capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL)) }
                                    event { followPan { params -> ctx.handleHomeSwipe(params) } }
                                    if (message.isUser) {
                                        UserMessageView(message)
                                    } else {
                                        AssistantMessageView(
                                            message = message,
                                            contentWidth = contentWidth,
                                            onOpenInstrument = { ctx.openDetail(it) },
                                            onFollowUp = { ctx.send(it) },
                                            onRetry = { ctx.vm.retryLast() },
                                        )
                                    }
                                }
                            }
                            View { attr { height(20f) } }
                        }
                    }
                    ComposerCapsulesView(
                        pageWidth = pageWidth,
                        promptPage = { ctx.promptPage },
                        visible = {
                            ctx.keyboardHeight == 0f &&
                                !ctx.voiceMode &&
                                !ctx.attachmentPanelVisible
                        },
                        onShuffle = { ctx.promptPage += 1 },
                        onPrompt = { ctx.send(it) },
                    )
                    ComposerView(
                        vm = ctx.vm,
                        expanded = { ctx.composerFocused },
                        voiceMode = { ctx.voiceMode },
                        onSend = { ctx.send(it) },
                        onStop = { ctx.vm.stopGenerating() },
                        onKeyboardHeight = {
                            ctx.keyboardHeight = it
                            ctx.composerFocused = it > 0f
                        },
                        onFocusChange = { ctx.composerFocused = it },
                        onInputRef = { ctx.inputRef = it },
                        onAttachClick = { ctx.toggleAttachmentPanel() },
                        onVoiceClick = { ctx.toggleVoiceMode() },
                        onVoiceLongPress = { ctx.handleVoiceLongPress(it) },
                    )
                    AttachmentPanel(
                        visible = { ctx.attachmentPanelVisible },
                        onPickImage = { ctx.selectAttachment("图片") },
                        onPickFile = { ctx.selectAttachment("文件") },
                        onPickStock = { ctx.selectAttachment("股票") },
                    )
                }
                VoiceRecordingOverlay(
                    visible = { ctx.voiceRecording },
                    phase = { ctx.voiceWavePhase },
                    cancelArmed = { ctx.voiceCancelArmed },
                    bottomInset = bottomInset,
                )
            }

            // 遮罩消费点击以关闭，不能透传到主页；水平拖拽可中断收尾。
            View {
                attr {
                    val p = ctx.drawer.progress.coerceIn(0f, 1f)
                    absolutePosition(0f, 0f, 0f, 0f)
                    val homeScale = 1f - (1f - DrawerMotion.HOME_MIN_SCALE) * p
                    val homeShift = (drawerWidth - pageWidth * (1f - DrawerMotion.HOME_MIN_SCALE) / 2f) * p
                    transform(scale = Scale(homeScale, homeScale), translate = Translate(0f, 0f, homeShift, 0f))
                    // 仅接管触摸。淡出内容由主页子层完成，避免 Android 半透明圆角叠层的灰边。
                    backgroundColor(Color.TRANSPARENT)

                    touchEnable((p > 0f || ctx.drawer.settling) && ctx.panSource != PAN_SOURCE_HOME)
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                    zIndex(3, useOutline = false)
                }
                event {
                    click { if (!ctx.suppressDrawerClick) ctx.closeDrawer() }
                    followPan { params -> ctx.handleCloseSwipe(params) }
                }
            }

            // 抽屉：从左侧滑入，位于浮起的主页下方，左滑关闭。
            View {
                attr {
                    val p = ctx.drawer.progress.coerceIn(0f, 1f)
                    val shift = DrawerMotion.drawerShift(p, drawerWidth)
                    absolutePosition(left = 0f, top = 0f, bottom = 0f)
                    transform(Translate(0f, 0f, shift, 0f))
                    width(drawerWidth)
                    backgroundColor(DrawerMotion.DRAWER_BG)
                    overflow(true)

                    touchEnable(p > 0f && ctx.panSource != PAN_SOURCE_HOME)
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                    zIndex(1, useOutline = false)
                }
                event { followPan { params -> ctx.handleCloseSwipe(params) } }
                HistoryDrawerContent(
                    vm = ctx.vm,
                    statusBarHeight = maxOf(ctx.pagerData.statusBarHeight, ctx.pagerData.safeAreaInsets.top),
                    bottomInset = bottomInset,
                    onOpen = { if (!ctx.suppressDrawerClick) ctx.vm.openConversation(it) },
                    onDelete = { if (!ctx.suppressDrawerClick) ctx.vm.deleteConversation(it) },
                    onNewChat = { if (!ctx.suppressDrawerClick) ctx.vm.newConversation() },
                    onClearAll = { if (!ctx.suppressDrawerClick) ctx.vm.clearAllConversations() },
                    onPan = { params -> ctx.handleCloseSwipe(params) },
                )
            }
        }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        attachmentPanelVisible = false
        voiceRecording = false
        voiceCancelArmed = false
        ++voiceWaveGeneration
        syncAccessoryBackHandler()
        followBottom = true
        inputRef?.view?.setText("")
        vm.send(text)
    }

    private fun dismissKeyboard() {
        composerFocused = false
        inputRef?.view?.blur()
    }

    private var motionGeneration = 0
    private var targetProgress = 0f

    private fun openDrawer() {
        attachmentPanelVisible = false
        voiceMode = false
        voiceRecording = false
        voiceCancelArmed = false
        ++voiceWaveGeneration
        syncAccessoryBackHandler()
        dismissKeyboard()
        settleDrawer(1f)
    }

    private fun closeDrawer() = settleDrawer(0f)

    /** Advance the displayed progress itself, including radius, rather than animating a target flag. */
    private fun settleDrawer(target: Float) {
        if (drawer.settling && targetProgress == target && !drawer.dragging) return
        val generation = ++motionGeneration
        targetProgress = target
        drawer.dragging = false
        panSource = 0
        val from = drawer.progress
        drawer.settling = abs(from - target) > 0.0001f
        if (vm.showHistory != (target == 1f)) vm.showHistory = target == 1f
        syncDrawerBackHandler()
        if (!drawer.settling) {
            drawer.progress = target
            syncDrawerBackHandler()
            return
        }
        val started = DateTime.currentTimestamp()
        fun frame() {
            if (generation != motionGeneration) return
            val time = ((DateTime.currentTimestamp() - started) / 360f).coerceIn(0f, 1f)
            drawer.progress = from + (target - from) * DrawerPhysics.ease(time)
            if (time < 1f) {
                setTimeout(16) { frame() }
            } else {
                drawer.progress = target
                drawer.settling = false
                syncDrawerBackHandler()
            }
        }
        setTimeout(16) { frame() }
    }

    private fun syncDrawerBackHandler() {
        val handler = getBackPressHandler()
        if (vm.showHistory || drawer.progress > 0f || drawer.settling || drawer.dragging) {
            if (!handler.containsCallback(drawerBackCallback)) {
                handler.addCallback(drawerBackCallback)
            }
        } else {
            handler.removeCallback(drawerBackCallback)
        }
    }

    private fun toggleTts() {
        vm.ttsEnabled = !vm.ttsEnabled
        if (!vm.ttsEnabled) voiceModule.stopSpeaking()
        vm.banner = if (vm.ttsEnabled) "语音播报已开启，下一条回复将自动朗读" else "语音播报已关闭"
    }

    private fun toggleAttachmentPanel() {
        voiceMode = false
        voiceRecording = false
        attachmentPanelVisible = !attachmentPanelVisible
        if (attachmentPanelVisible) dismissKeyboard()
        syncAccessoryBackHandler()
    }

    private fun selectAttachment(type: String) {
        attachmentPanelVisible = false
        voiceMode = false
        syncAccessoryBackHandler()
        val prompt = when (type) {
            "股票" -> "结合腾讯控股最新行情分析后市"
            "图片" -> "根据腾讯控股走势图分析近期趋势"
            else -> "解读腾讯控股财报中的主要风险"
        }
        inputRef?.view?.setText(prompt)
        vm.inputText = prompt
        vm.banner = "已添加${type}上下文，可编辑后发送"
    }

    private fun toggleVoiceMode() {
        attachmentPanelVisible = false
        voiceRecording = false
        voiceCancelArmed = false
        ++voiceWaveGeneration
        voiceMode = !voiceMode
        if (voiceMode) {
            dismissKeyboard()
            vm.banner = ""
        }
        syncAccessoryBackHandler()
    }

    private var voiceWaveGeneration = 0

    private fun handleVoiceLongPress(params: LongPressParams) {
        when (params.state) {
            "start" -> {
                if (!voiceMode || voiceRecording) return
                voiceModule.startListening { result ->
                    setTimeout(0) {
                        if (!result.success) {
                            vm.banner = result.message
                            return@setTimeout
                        }
                        voiceRecording = true
                        voiceCancelArmed = false
                        vm.banner = ""
                        startVoiceWave()
                        syncAccessoryBackHandler()
                    }
                }
            }
            "move" -> if (voiceRecording) {
                voiceCancelArmed = params.pageY < pagerData.pageViewHeight - 260f
            }
            "end" -> {
                if (!voiceRecording) return
                val canceled = params.isCancel || voiceCancelArmed
                voiceRecording = false
                voiceCancelArmed = false
                ++voiceWaveGeneration
                syncAccessoryBackHandler()
                if (canceled) {
                    voiceModule.cancelListening()
                    vm.banner = "已取消语音输入"
                } else {
                    vm.banner = "正在识别语音…"
                    voiceModule.finishListening { result ->
                        setTimeout(0) {
                            val text = result.text
                            if (!text.isNullOrBlank()) {
                                vm.banner = ""
                                send(text)
                            } else {
                                vm.banner = result.error ?: "未识别到有效语音"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startVoiceWave() {
        val generation = ++voiceWaveGeneration
        fun tick() {
            if (!voiceRecording || generation != voiceWaveGeneration) return
            voiceWavePhase = (voiceWavePhase + 1) % 120
            setTimeout(90) { tick() }
        }
        setTimeout(90) { tick() }
    }

    private fun syncAccessoryBackHandler() {
        val handler = getBackPressHandler()
        if (attachmentPanelVisible || voiceMode || voiceRecording) {
            if (!handler.containsCallback(accessoryBackCallback)) handler.addCallback(accessoryBackCallback)
        } else {
            handler.removeCallback(accessoryBackCallback)
        }
    }

    private var panStartX = 0f
    private var panStartY = 0f
    private var panStartProgress = 0f
    private var panResumeTarget = 0f
    private var panLastX = 0f
    private var panLastTime = 0L
    private var panVelocityX = 0f
    private var panLocked = false
    private var panSource = 0
    private var suppressDrawerClick = false
    private var clickGeneration = 0

    private fun handleHomeSwipe(params: PanGestureParams) = handleDrawerPan(params, PAN_SOURCE_HOME)
    private fun handleCloseSwipe(params: PanGestureParams) = handleDrawerPan(params, PAN_SOURCE_CLOSE)

    private fun handleDrawerPan(params: PanGestureParams, source: Int) {
        when (params.state) {
            "start" -> {
                if (panSource != 0) return
                if (source == PAN_SOURCE_HOME && drawer.progress > 0f) return
                panResumeTarget = targetProgress
                ++motionGeneration
                drawer.settling = false
                panSource = source
                panStartX = params.pageX
                panStartY = params.pageY
                panStartProgress = drawer.progress
                panLastX = params.pageX
                panLastTime = DateTime.currentTimestamp()
                panVelocityX = 0f
                panLocked = false
                suppressDrawerClick = false
                ++clickGeneration
            }
            "move" -> {
                if (panSource != source) return
                val dx = params.pageX - panStartX
                val dy = params.pageY - panStartY
                if (!panLocked) {
                    if (maxOf(abs(dx), abs(dy)) < 8f) return
                    if (abs(dy) >= abs(dx)) {
                        settleDrawer(panResumeTarget)
                        return
                    }
                    if (source == PAN_SOURCE_HOME && dx <= 0f) return
                    panLocked = true
                    drawer.dragging = true
                    suppressDrawerClick = true
                    dismissKeyboard()
                    syncDrawerBackHandler()
                }
                val now = DateTime.currentTimestamp()
                val dt = now - panLastTime
                if (dt > 0) {
                    panVelocityX = (params.pageX - panLastX) / dt * 1000f
                    panLastX = params.pageX
                    panLastTime = now
                }
                drawer.progress = DrawerPhysics.progress(
                    panStartProgress, dx, chatDrawerWidth(pagerData.pageViewWidth),
                )
            }
            "end", "cancel" -> {
                if (panSource != source) return
                val locked = panLocked
                val velocity = if (DateTime.currentTimestamp() - panLastTime > 100L) 0f else panVelocityX
                val target = if (params.state == "cancel" || !locked) panResumeTarget
                    else DrawerPhysics.target(drawer.progress, velocity)
                panLocked = false
                settleDrawer(target)
                if (locked) {
                    val token = ++clickGeneration
                    setTimeout(100) { if (token == clickGeneration) suppressDrawerClick = false }
                }
            }
        }
    }

    private fun openDetail(instrumentKey: String) {
        dismissKeyboard()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockDetail",
            JSONObject().apply { put("instrumentKey", instrumentKey) },
        )
    }

    private fun scheduleScrollToBottom() {
        setTimeout(60) { scrollToBottom(animated = true) }
    }

    private fun scrollToBottom(animated: Boolean) {
        val list = listRef?.view ?: return
        val listHeight = list.flexNode.layoutFrame.height
        val target = listContentHeight - listHeight
        if (target > 0) list.setContentOffset(0f, target, animated)
    }

    override fun pageWillDestroy() {
        ++motionGeneration
        ++clickGeneration
        if (::voiceModule.isInitialized) {
            voiceModule.release()
        }
        getBackPressHandler().removeCallback(drawerBackCallback)
        getBackPressHandler().removeCallback(accessoryBackCallback)
        vm.stopGenerating()
        super.pageWillDestroy()
    }

    private companion object {
        const val PAN_SOURCE_HOME = 1
        const val PAN_SOURCE_CLOSE = 2
    }
}
