package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.ai.AiService
import com.kuikly.stockchat.data.ai.RemoteAiEngine
import com.kuikly.stockchat.data.ai.RemoteIntentRecognizer
import com.kuikly.stockchat.data.ai.VoiceModule
import com.kuikly.stockchat.data.attachment.AttachmentModule
import com.kuikly.stockchat.data.attachment.KuiklyFileContentReader
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.domain.attachment.AttachmentSource
import com.kuikly.stockchat.domain.attachment.AttachmentStatus
import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.chat.SettingsRepository
import com.kuikly.stockchat.data.market.DerivedMarketRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.market.TencentSymbolSearch
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.InstrumentCodec
import com.kuikly.stockchat.domain.model.InstrumentResolver
import com.kuikly.stockchat.data.network.KuiklyHttpClient
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
import com.kuikly.stockchat.ui.chat.VoiceRecordZone
import com.kuikly.stockchat.ui.chat.VoiceRecordingOverlay
import com.kuikly.stockchat.ui.chat.WelcomeView
import com.kuikly.stockchat.ui.chat.voiceRecordZone
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
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
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
    private var voiceZone by observable(VoiceRecordZone.SEND)
    private var voiceWavePhase by observable(0)
    private var voiceFingerX by observable(0f)
    private var voiceFingerY by observable(0f)
    private var promptPage by observable(0)
    private var listRef: ViewRef<ListView<*, *>>? = null
    private var inputRef: ViewRef<InputView>? = null
    private var listContentHeight = 0f
    private var followBottom = true

    override fun createExternalModules(): Map<String, Module>? = mapOf(
        AttachmentModule.MODULE_NAME to AttachmentModule(),
    )

    private val drawerBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            closeDrawer()
        }
    }
    private val accessoryBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            attachmentPanelVisible = false
            if (voiceRecording) voiceModule.cancelListening()
            voiceMode = false
            resetVoiceRecording()
            syncAccessoryBackHandler()
        }
    }

    override fun created() {
        super.created()
        val http = KuiklyHttpClient(this)
        val store = KuiklyKeyValueStore(this)
        val marketRepository = MarketRepository(http)
        val derivedRepository = DerivedMarketRepository(http)
        val aiEngine = RemoteAiEngine(http)
        voiceModule = VoiceModule.createDefault()
        vm = ChatViewModel(
            aiService = AiService(
                pagerId,
                marketRepository,
                aiEngine,
                derivedRepository,
                RemoteIntentRecognizer(http),
                KuiklyFileContentReader(this),
                InstrumentResolver(TencentSymbolSearch(http)::search),
            ),
            conversationRepository = ConversationRepository(store),
            settingsRepository = SettingsRepository(store),
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
        vm.loadSettings()
        vm.loadOverview()
        val hint = InstrumentCodec.decode(pageData.params.optJSONObject("instrument"))
        hint?.let(InstrumentCache::put)
        pageData.params.optString("prompt").takeIf { it.isNotEmpty() }?.let { prompt ->
            setTimeout(400) { vm.send(prompt, hintInstruments = listOfNotNull(hint)) }
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
                        // 附件面板打开时始终使用系统安全区，不继承上一次键盘高度。
                        val bottomPadding = if (ctx.attachmentPanelVisible) bottomInset else if (ctx.keyboardHeight > 0) ctx.keyboardHeight else bottomInset
                        paddingBottom(bottomPadding)
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
                        // 附件面板使用固定的收起态输入框布局，避免从键盘态切入时高度漂移。
                        expanded = { (ctx.composerFocused || ctx.keyboardHeight > 0f) && !ctx.attachmentPanelVisible },
                        keyboardVisible = { ctx.keyboardHeight > 0f },
                        attachmentPanelVisible = { ctx.attachmentPanelVisible },
                        voiceMode = { ctx.voiceMode },
                        onSend = { ctx.send(it) },
                        onStop = { ctx.vm.stopGenerating() },
                        onKeyboardHeight = {
                            // 面板态与键盘态互斥。收起键盘的异步回调可能晚于面板状态切换到达，
                            // 此时必须丢弃它，不能让旧键盘高度再次把面板向上/向下推。
                            if (ctx.attachmentPanelVisible) {
                                ctx.keyboardHeight = 0f
                                ctx.composerFocused = false
                            } else {
                                ctx.keyboardHeight = it
                                ctx.composerFocused = it > 0f
                            }
                        },
                        onFocusChange = { ctx.composerFocused = it },
                        onInputRef = { ctx.inputRef = it },
                        onAttachClick = { ctx.toggleAttachmentPanel() },
                        onVoiceClick = { ctx.toggleVoiceMode() },
                        onVoiceLongPress = { ctx.handleVoiceLongPress(it) },
                        onPickCamera = { ctx.selectAttachment("拍照") },
                        onPickPhoto = { ctx.selectAttachment("照片") },
                        onPickFile = { ctx.selectAttachment("本地文件") },
                        onRemoveAttachment = { ctx.vm.removePendingAttachment(it) },
                    )
                }
                VoiceRecordingOverlay(
                    visible = { ctx.voiceRecording },
                    phase = { ctx.voiceWavePhase },
                    zone = { ctx.voiceZone },
                    fingerX = { ctx.voiceFingerX },
                    fingerY = { ctx.voiceFingerY },
                    pageHeight = ctx.pagerData.pageViewHeight,
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
                    onClose = { if (!ctx.suppressDrawerClick) ctx.closeDrawer() },
                    onOpen = { if (!ctx.suppressDrawerClick) ctx.vm.openConversation(it) },
                    onDelete = { if (!ctx.suppressDrawerClick) ctx.vm.deleteConversation(it) },
                    onNewChat = { if (!ctx.suppressDrawerClick) ctx.vm.newConversation() },
                    onSettings = { if (!ctx.suppressDrawerClick) ctx.openSettings() },
                    onPan = { params -> ctx.handleCloseSwipe(params) },
                )
            }
        }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        attachmentPanelVisible = false
        resetVoiceRecording()
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
        resetVoiceRecording()
        syncAccessoryBackHandler()
        dismissKeyboard()
        settleDrawer(1f)
    }

    private fun closeDrawer() {
        if (vm.drawerSearchOpen) {
            vm.drawerSearchOpen = false
            vm.onDrawerQueryChange("")
        }
        settleDrawer(0f)
    }

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
        vm.persistTtsEnabled(!vm.ttsEnabled)
        if (!vm.ttsEnabled) voiceModule.stopSpeaking()
        vm.banner = if (vm.ttsEnabled) "语音播报已开启，下一条回复将自动朗读" else "语音播报已关闭"
    }

    private fun toggleAttachmentPanel() {
        if (voiceRecording) voiceModule.cancelListening()
        voiceMode = false
        resetVoiceRecording()
        attachmentPanelVisible = !attachmentPanelVisible
        if (attachmentPanelVisible) {
            // 附件面板与系统键盘互斥：先结束输入焦点，再展示面板。
            keyboardHeight = 0f
            composerFocused = false
            dismissKeyboard()
        }
        syncAccessoryBackHandler()
    }

    private fun closeAttachmentPanel() {
        if (!attachmentPanelVisible) return
        attachmentPanelVisible = false
        syncAccessoryBackHandler()
    }

    private fun selectAttachment(type: String) {
        val module = acquireModule<AttachmentModule>(AttachmentModule.MODULE_NAME)
        val callback: (JSONObject?) -> Unit = { result: JSONObject? ->
            setTimeout(0) {
                if (result?.optBoolean("cancelled", false) == true) return@setTimeout
                val attachments = result?.optJSONArray("attachments")?.let(::decodeAttachments).orEmpty()
                if (attachments.isNotEmpty()) {
                    vm.addPendingAttachments(attachments)
                    vm.banner = "已添加${attachments.size}个附件，可继续编辑后发送"
                }
                attachmentPanelVisible = false
                voiceMode = false
                syncAccessoryBackHandler()
            }
            Unit
        }
        when (type) {
            "拍照" -> module.openCamera(callback)
            "照片" -> module.openPhotoLibrary(callback)
            else -> module.openFilePicker(callback)
        }
    }

    private fun decodeAttachments(array: JSONArray): List<Attachment> =
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { json ->
                val kind = if (json.optString("kind") == "IMAGE") AttachmentKind.IMAGE else AttachmentKind.DOCUMENT
                val source = when (json.optString("source")) {
                    "CAMERA" -> AttachmentSource.CAMERA
                    "PHOTO_LIBRARY" -> AttachmentSource.PHOTO_LIBRARY
                    else -> AttachmentSource.FILE
                }
                Attachment(
                    id = json.optString("id"), displayName = json.optString("displayName"),
                    mimeType = json.optString("mimeType"), byteSize = json.optLong("byteSize"),
                    localPath = json.optString("localPath"), thumbnailPath = json.optString("thumbnailPath").ifEmpty { null },
                    source = source, kind = kind,
                    status = AttachmentStatus.READY,
                )
            }
        }

    private fun toggleVoiceMode() {
        attachmentPanelVisible = false
        resetVoiceRecording()
        voiceMode = !voiceMode
        if (voiceMode) {
            dismissKeyboard()
            vm.banner = ""
        }
        syncAccessoryBackHandler()
    }

    private var voiceWaveGeneration = 0

    private fun resetVoiceRecording() {
        voiceRecording = false
        voiceZone = VoiceRecordZone.SEND
        ++voiceWaveGeneration
    }

    private fun handleVoiceLongPress(params: LongPressParams) {
        when (params.state) {
            "start" -> {
                if (!voiceMode || voiceRecording) return
                voiceFingerX = params.pageX
                voiceFingerY = params.pageY
                voiceZone = VoiceRecordZone.SEND
                voiceModule.startListening { result ->
                    setTimeout(0) {
                        if (!result.success) {
                            vm.banner = result.message
                            return@setTimeout
                        }
                        voiceRecording = true
                        voiceZone = VoiceRecordZone.SEND
                        voiceFingerX = params.pageX
                        voiceFingerY = params.pageY
                        vm.banner = ""
                        startVoiceWave()
                        syncAccessoryBackHandler()
                    }
                }
            }
            "move" -> if (voiceRecording) {
                voiceFingerX = params.pageX
                voiceFingerY = params.pageY
                voiceZone = voiceRecordZone(
                    params.pageX,
                    params.pageY,
                    pagerData.pageViewWidth,
                    pagerData.pageViewHeight,
                    pagerData.safeAreaInsets.bottom,
                )
            }
            "end" -> {
                if (!voiceRecording) return
                val zone = if (params.isCancel) VoiceRecordZone.CANCEL else voiceZone
                resetVoiceRecording()
                syncAccessoryBackHandler()
                when (zone) {
                    VoiceRecordZone.CANCEL -> {
                        voiceModule.cancelListening()
                        vm.banner = "已取消语音输入"
                    }
                    VoiceRecordZone.EDIT -> finishVoiceToComposer()
                    VoiceRecordZone.SEND -> finishVoiceToSend()
                }
            }
        }
    }

    private fun finishVoiceToSend() {
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

    private fun finishVoiceToComposer() {
        vm.banner = "正在识别语音…"
        voiceModule.finishListening { result ->
            setTimeout(0) {
                val text = result.text
                if (!text.isNullOrBlank()) {
                    vm.banner = ""
                    voiceMode = false
                    vm.inputText = text
                    inputRef?.view?.setText(text)
                    inputRef?.view?.focus()
                    syncAccessoryBackHandler()
                } else {
                    vm.banner = result.error ?: "未识别到有效语音"
                }
            }
        }
    }

    private fun startVoiceWave() {
        val generation = ++voiceWaveGeneration
        fun tick() {
            if (!voiceRecording || generation != voiceWaveGeneration) return
            voiceWavePhase = (voiceWavePhase + 1) % 360
            setTimeout(48) { tick() }
        }
        setTimeout(48) { tick() }
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

    override fun pageDidAppear() {
        super.pageDidAppear()
        if (::vm.isInitialized) {
            vm.loadSettings()
            vm.reloadConversationsFromStore()
        }
    }

    private fun openSettings() {
        closeDrawer()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("StockChatSettings")
    }

    private fun openDetail(instrumentKey: String) {
        dismissKeyboard()
        val instrument = InstrumentCache.get(instrumentKey)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockDetail",
            JSONObject().apply {
                put("instrumentKey", instrumentKey)
                instrument?.let { put("instrument", InstrumentCodec.encode(it)) }
            },
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
