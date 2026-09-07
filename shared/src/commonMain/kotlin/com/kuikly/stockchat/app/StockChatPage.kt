package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.ai.AiService
import com.kuikly.stockchat.data.ai.RemoteAiEngine
import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.ui.chat.AssistantMessageView
import com.kuikly.stockchat.ui.chat.ChatNavBar
import com.kuikly.stockchat.ui.chat.ChatViewModel
import com.kuikly.stockchat.ui.chat.ComposerView
import com.kuikly.stockchat.ui.chat.DrawerMotion
import com.kuikly.stockchat.ui.chat.DrawerState
import com.kuikly.stockchat.ui.chat.HistoryDrawerContent
import com.kuikly.stockchat.ui.chat.UserMessageView
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
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
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
 * Overlay Drawer：抽屉从左侧滑入覆盖主内容，主内容静止不动。
 */
@Page("StockChat")
internal class StockChatPage : Pager() {

    private lateinit var vm: ChatViewModel
    private val drawer = DrawerState()

    private var keyboardHeight by observable(0f)
    private var listRef: ViewRef<ListView<*, *>>? = null
    private var inputRef: ViewRef<InputView>? = null
    private var listContentHeight = 0f
    private var followBottom = true

    private val drawerBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            closeDrawer()
        }
    }

    override fun created() {
        super.created()
        val http = KuiklyHttpClient(this)
        val store = KuiklyKeyValueStore(this)
        val marketRepository = MarketRepository(http)
        val aiEngine = RemoteAiEngine(http)
        vm = ChatViewModel(
            aiService = AiService(pagerId, marketRepository, aiEngine),
            conversationRepository = ConversationRepository(store),
            marketRepository = marketRepository,
        )
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
            if (!drawer.dragging) {
                val target = if (vm.showHistory) 1f else 0f
                if (abs(drawer.progress - target) > 0.001f) {
                    drawer.progress = target
                }
            }
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
                backgroundColor(Color.WHITE)
                overflow(false)
            }

            // 主内容区：静止不动，铺满全屏
            View {
                attr {
                    absolutePosition(0f, 0f, 0f, 0f)
                    backgroundColor(Color(0xFFFFFFFFL))
                    overflow(true)
                    zIndex(0)
                }
                View {
                    attr {
                        flex(1f)
                        backgroundColor(Color(0xFFFFFFFFL))
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
                        List {
                            attr {
                                flex(1f)
                                backgroundColor(Color(0xFFFFFFFFL))
                            }
                            WelcomeView(
                                vm = ctx.vm,
                                pageWidth = pageWidth,
                                onPrompt = { ctx.send(it) },
                                onOpenInstrument = { ctx.openDetail(it) },
                            )
                            View { attr { height(16f) } }
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
                    ComposerView(
                        vm = ctx.vm,
                        onSend = { ctx.send(it) },
                        onStop = { ctx.vm.stopGenerating() },
                        onKeyboardHeight = { ctx.keyboardHeight = it },
                        onInputRef = { ctx.inputRef = it },
                        onAttachClick = { ctx.showAttachPlaceholder() },
                        onVoiceClick = { ctx.showVoicePlaceholder() },
                    )
                }
            }

            // 左侧边缘手势区：关闭时右滑唤起抽屉
            // 层级在遮罩下方，打开后会被遮罩覆盖不拦截事件
            View {
                attr {
                    val p = ctx.drawer.progress
                    absolutePosition(
                        left = 0f,
                        top = ctx.pagerData.statusBarHeight + AppTheme.navBarHeight,
                        bottom = (if (ctx.keyboardHeight > 0f) ctx.keyboardHeight else bottomInset) + DrawerMotion.COMPOSER_BLOCK_HEIGHT,
                    )
                    width(DrawerMotion.EDGE_ZONE_WIDTH)
                    backgroundColor(Color.TRANSPARENT)
                    touchEnable(p < 0.5f || ctx.drawer.dragging)
                    zIndex(0)
                }
                event { followPan { params -> ctx.handleEdgeSwipe(params) } }
            }

            // 遮罩层：打开时变暗，只处理 click 关闭（pan 由抽屉处理）
            View {
                attr {
                    val p = ctx.drawer.progress.coerceIn(0f, 1f)
                    absolutePosition(0f, 0f, 0f, 0f)
                    backgroundColor(Color(0, 0, 0, p * DrawerMotion.SCRIM_ALPHA))
                    if (!ctx.drawer.dragging) {
                        animation(DrawerMotion.settleAnim(p), ctx.drawer.progress)
                    }
                    touchEnable(p > 0.02f)
                    zIndex(1)
                }
                event {
                    click { ctx.closeDrawer() }
                }
            }

            // 抽屉：从左侧滑入覆盖主内容，左滑关闭
            View {
                attr {
                    val p = ctx.drawer.progress.coerceIn(0f, 1f)
                    val shift = DrawerMotion.drawerShift(p, drawerWidth)
                    absolutePosition(left = shift, top = 0f, bottom = 0f)
                    width(drawerWidth)
                    backgroundColor(DrawerMotion.DRAWER_BG)
                    overflow(false)
                    if (!ctx.drawer.dragging) {
                        animation(DrawerMotion.settleAnim(p), ctx.drawer.progress)
                    }
                    touchEnable(p > 0.02f)
                    zIndex(2)
                }
                event {
                    followPan { params -> ctx.handleCloseSwipe(params) }
                }
                HistoryDrawerContent(
                    vm = ctx.vm,
                    statusBarHeight = maxOf(ctx.pagerData.statusBarHeight, ctx.pagerData.safeAreaInsets.top),
                    bottomInset = bottomInset,
                    onOpen = { ctx.vm.openConversation(it) },
                    onDelete = { ctx.vm.deleteConversation(it) },
                    onNewChat = { ctx.vm.newConversation() },
                    onClearAll = { ctx.vm.clearAllConversations() },
                )
            }
        }
    }

    private fun send(text: String) {
        if (text.isBlank()) return
        followBottom = true
        inputRef?.view?.setText("")
        vm.send(text)
    }

    private fun dismissKeyboard() {
        inputRef?.view?.blur()
    }

    private fun openDrawer() {
        dismissKeyboard()
        drawer.dragging = false
        drawer.progress = 1f
        vm.showHistory = true
    }

    private fun closeDrawer() {
        drawer.dragging = false
        drawer.progress = 0f
        vm.showHistory = false
    }

    private fun syncDrawerBackHandler() {
        val handler = getBackPressHandler()
        if (vm.showHistory) {
            if (!handler.containsCallback(drawerBackCallback)) {
                handler.addCallback(drawerBackCallback)
            }
        } else {
            handler.removeCallback(drawerBackCallback)
        }
    }

    private fun toggleTts() {
        vm.ttsEnabled = !vm.ttsEnabled
        vm.banner = if (vm.ttsEnabled) "语音播报已开启，朗读回复即将支持" else "语音播报已关闭"
    }

    private fun showAttachPlaceholder() {
        dismissKeyboard()
        vm.banner = "附件面板开发中，支持图片 / 文档后开放"
    }

    private fun showVoicePlaceholder() {
        dismissKeyboard()
        vm.banner = "语音输入开发中，敬请期待"
    }

    private var panStartX = 0f
    private var panStartY = 0f
    private var panLastX = 0f
    private var panLastTime = 0L
    private var panVelocityX = 0f
    private var panLocked = false
    private var panActive = false

    /** 关闭状态：从左边缘右滑唤起抽屉。 */
    private fun handleEdgeSwipe(params: PanGestureParams) {
        if (drawer.dragging) return
        if (drawer.progress >= 0.5f) return
        val travel = DrawerMotion.travelDistance(chatDrawerWidth(pagerData.pageViewWidth))
        when (params.state) {
            "start" -> {
                drawer.dragging = true
                beginPan(params)
            }
            "move" -> if (panActive) {
                val dx = params.pageX - panStartX
                val dy = params.pageY - panStartY
                if (!panLocked) {
                    if (abs(dx) < 4f && abs(dy) < 4f) return
                    if (abs(dx) > abs(dy) && dx > 0f) {
                        panLocked = true
                        dismissKeyboard()
                    } else {
                        abandonPan()
                        return
                    }
                }
                trackVelocity(params.pageX)
                drawer.progress = (dx / travel).coerceIn(0f, 1f)
            }
            "end", "cancel" -> {
                if (panActive && panLocked) {
                    finishPan(opening = true)
                } else {
                    abandonPan()
                }
            }
        }
    }

    /** 打开状态：在抽屉上左滑关闭。 */
    private fun handleCloseSwipe(params: PanGestureParams) {
        if (drawer.dragging) return
        if (drawer.progress <= 0.02f) return
        val travel = DrawerMotion.travelDistance(chatDrawerWidth(pagerData.pageViewWidth))
        when (params.state) {
            "start" -> {
                drawer.dragging = true
                beginPan(params)
            }
            "move" -> if (panActive) {
                val dx = params.pageX - panStartX
                val dy = params.pageY - panStartY
                if (!panLocked) {
                    if (abs(dx) < 4f && abs(dy) < 4f) return
                    if (abs(dx) > abs(dy) && dx < 0f) {
                        panLocked = true
                    } else {
                        abandonPan()
                        return
                    }
                }
                trackVelocity(params.pageX)
                drawer.progress = (1f + dx / travel).coerceIn(0f, 1f)
            }
            "end", "cancel" -> {
                if (panActive && panLocked) {
                    finishPan(opening = false)
                } else {
                    abandonPan()
                }
            }
        }
    }

    private fun beginPan(params: PanGestureParams) {
        panStartX = params.pageX
        panStartY = params.pageY
        panLastX = params.pageX
        panLastTime = DateTime.currentTimestamp()
        panVelocityX = 0f
        panLocked = false
        panActive = true
    }

    /** 方向不对或未 lock：放弃 pan，恢复非拖拽状态。 */
    private fun abandonPan() {
        panActive = false
        panLocked = false
        drawer.dragging = false
    }

    private fun trackVelocity(pageX: Float) {
        val now = DateTime.currentTimestamp()
        val dt = (now - panLastTime).coerceAtLeast(1L)
        val instant = (pageX - panLastX) / dt * 1000f
        panVelocityX = if (abs(panVelocityX) < 0.1f) instant else panVelocityX * 0.6f + instant * 0.4f
        panLastX = pageX
        panLastTime = now
    }

    private fun finishPan(opening: Boolean) {
        val currentProgress = drawer.progress
        panActive = false
        panLocked = false
        val shouldOpen = if (opening) {
            // 右滑打开：快速右滑 或 进度过半
            panVelocityX > DrawerMotion.FLING_VELOCITY || currentProgress >= 0.5f
        } else {
            // 左滑关闭：快速左滑 或 进度不到一半
            panVelocityX < -DrawerMotion.FLING_VELOCITY || currentProgress < 0.5f
        }
        drawer.dragging = false
        drawer.progress = if (shouldOpen) 1f else 0f
        vm.showHistory = shouldOpen
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
        getBackPressHandler().removeCallback(drawerBackCallback)
        vm.stopGenerating()
        super.pageWillDestroy()
    }
}
