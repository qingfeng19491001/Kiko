package com.kuikly.stockchat.app

import com.kuikly.stockchat.app.di.createChatViewModel
import com.kuikly.stockchat.data.ai.VoiceModule
import com.kuikly.stockchat.data.ai.VoiceBridge
import com.kuikly.stockchat.data.ai.VoiceNativeModule
import com.kuikly.stockchat.data.attachment.AttachmentModule
import com.kuikly.stockchat.data.share.ContentActionModule
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.data.codec.InstrumentCodec
import com.kuikly.stockchat.ui.chat.ChatViewModel
import com.kuikly.stockchat.ui.chat.ChatUiMessage
import com.kuikly.stockchat.ui.chat.DrawerState
import com.kuikly.stockchat.ui.chat.DrawerSkill
import com.kuikly.stockchat.ui.chat.StockChatActions
import com.kuikly.stockchat.ui.chat.StockChatRuntime
import com.kuikly.stockchat.ui.chat.StockChatScreen
import com.kuikly.stockchat.ui.chat.StockChatScreenHost
import com.kuikly.stockchat.ui.chat.VoiceRecordZone
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.ListView

/**
 * AI 股票问答主页：生命周期、路由、组装。抽屉 / 语音 / 附件在 [StockChatActions]。
 */
@Page("StockChat")
internal class StockChatPage : Pager(), StockChatScreenHost, StockChatRuntime {

    private lateinit var vm: ChatViewModel
    override lateinit var voiceModule: VoiceModule
    private val drawer = DrawerState()
    private val actions by lazy { StockChatActions(this, this) }

    override val chatViewModel get() = vm
    override val drawerState get() = drawer

    override var keyboardHeight by observable(0f)
    override var composerFocused by observable(false)
    override var attachmentPanelVisible by observable(false)
    override var voiceMode by observable(false)
    override var voiceRecording by observable(false)
    override var voiceZone by observable(VoiceRecordZone.SEND)
    override var voiceWavePhase by observable(0)
    override var voiceFingerX by observable(0f)
    override var voiceFingerY by observable(0f)
    override var promptPage by observable(0)
    override var listRef: ViewRef<ListView<*, *>>? = null
    override var inputRef: ViewRef<InputView>? = null
    override var listContentHeight = 0f
    override var followBottom = true
    override var panSource = 0
    override var suppressDrawerClick = false

    override fun createExternalModules(): Map<String, Module>? = mapOf(
        AttachmentModule.MODULE_NAME to AttachmentModule(),
        ContentActionModule.MODULE_NAME to ContentActionModule(),
        VoiceNativeModule.MODULE_NAME to VoiceNativeModule(),
    )

    override fun created() {
        super.created()
        voiceModule = VoiceModule.createDefault()
        VoiceBridge.native = acquireModule(VoiceNativeModule.MODULE_NAME)
        vm = createChatViewModel(this)
        vm.onReplyCompleted = { text ->
            if (vm.ttsEnabled) {
                voiceModule.speak(text) { result ->
                    if (!result.success) setTimeout(0) { vm.banner = result.message }
                }
            }
        }
        vm.loadHistory()
        vm.loadSettings()
        vm.onPruneFiles = { paths ->
            val module = acquireModule<AttachmentModule>(AttachmentModule.MODULE_NAME)
            paths.forEach { path -> module.deleteFile(path) }
        }
        vm.verifyAttachments = { attachments, done -> actions.verifyAttachmentFiles(attachments, done) }
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
            if (followBottom) actions.scheduleScrollToBottom()
        }
        bindValueChange({ vm.showHistory }) {
            if (!drawer.dragging) actions.settleDrawer(if (vm.showHistory) 1f else 0f)
            actions.syncDrawerBackHandler()
        }
    }

    override fun body(): ViewBuilder = StockChatScreen(
        host = this,
        pageWidth = pagerData.pageViewWidth,
        pageHeight = pagerData.pageViewHeight,
        statusBarHeight = pagerData.statusBarHeight,
        safeAreaTop = pagerData.safeAreaInsets.top,
        bottomInset = pagerData.safeAreaInsets.bottom,
    )

    override fun sendPrompt(text: String) = actions.sendPrompt(text)
    override fun openDrawer() = actions.openDrawer()
    override fun closeDrawer() = actions.closeDrawer()
    override fun openDrawerSkill(skill: DrawerSkill) = actions.openDrawerSkill(skill)
    override fun toggleTts() = actions.toggleTts()
    override fun speakMessage(message: ChatUiMessage) = actions.speakMessage(message)
    override fun feedback(message: ChatUiMessage, value: Int) = actions.feedback(message, value)
    override fun shareMessage(message: ChatUiMessage) = actions.shareMessage(message)
    override fun copyText(text: String) = actions.copyText(text)
    override fun toggleAttachmentPanel() = actions.toggleAttachmentPanel()
    override fun selectAttachment(type: String) = actions.selectAttachment(type)
    override fun toggleVoiceMode() = actions.toggleVoiceMode()
    override fun handleVoiceLongPress(params: LongPressParams) = actions.handleVoiceLongPress(params)
    override fun handleHomeSwipe(params: PanGestureParams) = actions.handleHomeSwipe(params)
    override fun handleCloseSwipe(params: PanGestureParams) = actions.handleCloseSwipe(params)
    override fun scrollToBottom(animated: Boolean) = actions.scrollToBottom(animated)

    override fun pageDidAppear() {
        super.pageDidAppear()
        if (::vm.isInitialized) {
            vm.loadSettings()
            vm.reloadConversationsFromStore()
        }
    }

    override fun openMarket() {
        val fromMarket = pageData.params.optString("from") == "market"
        val router = acquireModule<RouterModule>(RouterModule.MODULE_NAME)
        if (fromMarket) router.closePage() else router.openPage("MarketList")
    }

    override fun openSettings() {
        actions.closeDrawer()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("StockChatSettings")
    }

    override fun openDetail(instrumentKey: String) {
        actions.dismissKeyboard()
        val instrument = InstrumentCache.get(instrumentKey)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockDetail",
            JSONObject().apply {
                put("instrumentKey", instrumentKey)
                instrument?.let { put("instrument", InstrumentCodec.encode(it)) }
            },
        )
    }

    override fun pageWillDestroy() {
        actions.cancelMotions()
        if (::voiceModule.isInitialized) voiceModule.release()
        VoiceBridge.native = null
        actions.detachBackHandlers()
        vm.stopGenerating()
        super.pageWillDestroy()
    }

    override fun after(ms: Int, block: () -> Unit) {
        setTimeout(ms) { block() }
    }

    override fun now(): Long = DateTime.currentTimestamp()
    override fun pageWidth(): Float = pagerData.pageViewWidth
    override fun pageHeight(): Float = pagerData.pageViewHeight
    override fun bottomInset(): Float = pagerData.safeAreaInsets.bottom
    override fun attachmentModule(): AttachmentModule =
        acquireModule(AttachmentModule.MODULE_NAME)
    override fun shareModule(): ContentActionModule =
        acquireModule(ContentActionModule.MODULE_NAME)
    override fun addBackCallback(callback: BackPressCallback) {
        getBackPressHandler().addCallback(callback)
    }
    override fun removeBackCallback(callback: BackPressCallback) {
        getBackPressHandler().removeCallback(callback)
    }
    override fun containsBackCallback(callback: BackPressCallback): Boolean =
        getBackPressHandler().containsCallback(callback)
}
