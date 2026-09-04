package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.ai.AiService
import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.ui.chat.AssistantMessageView
import com.kuikly.stockchat.ui.chat.ChatNavBar
import com.kuikly.stockchat.ui.chat.ChatViewModel
import com.kuikly.stockchat.ui.chat.ComposerView
import com.kuikly.stockchat.ui.chat.HistoryDrawerView
import com.kuikly.stockchat.ui.chat.UserMessageView
import com.kuikly.stockchat.ui.chat.WelcomeView
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
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

/**
 * AI 股票问答主页：欢迎态 + 对话态 + 历史抽屉。
 */
@Page("StockChat")
internal class StockChatPage : Pager() {

    private lateinit var vm: ChatViewModel

    private var keyboardHeight by observable(0f)
    private var listRef: ViewRef<ListView<*, *>>? = null
    private var inputRef: ViewRef<InputView>? = null
    private var listContentHeight = 0f
    private var followBottom = true

    override fun created() {
        super.created()
        val http = KuiklyHttpClient(this)
        val store = KuiklyKeyValueStore(this)
        val marketRepository = MarketRepository(http)
        vm = ChatViewModel(
            aiService = AiService(pagerId, marketRepository),
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
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val pageWidth = pagerData.pageViewWidth
        val bottomInset = pagerData.safeAreaInsets.bottom
        // AI 回复内容宽度 = 页宽 - 两侧边距
        val contentWidth = pageWidth - AppTheme.pageHorizontalPadding * 2
        return {
            attr { backgroundColor(AppTheme.background) }

            View {
                attr {
                    flex(1f)
                    paddingBottom(if (ctx.keyboardHeight > 0) ctx.keyboardHeight else bottomInset)
                    animation(Animation.easeOut(0.2f), ctx.keyboardHeight)
                }
                ChatNavBar(
                    statusBarHeight = ctx.pagerData.statusBarHeight,
                    onMenu = { ctx.dismissKeyboard(); ctx.vm.showHistory = true },
                    onNewChat = { ctx.vm.newConversation() },
                )
                // 离线提示条
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
                // 内容区
                vif({ !ctx.vm.hasConversation }) {
                    List {
                        attr { flex(1f) }
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
                        attr { flex(1f) }
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
                )
            }

            HistoryDrawerView(
                vm = ctx.vm,
                pageWidth = pageWidth,
                statusBarHeight = ctx.pagerData.statusBarHeight,
                bottomInset = bottomInset,
                onOpen = { ctx.vm.openConversation(it) },
                onDelete = { ctx.vm.deleteConversation(it) },
                onNewChat = { ctx.vm.newConversation() },
                onClearAll = { ctx.vm.clearAllConversations() },
            )
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
        vm.stopGenerating()
        super.pageWillDestroy()
    }
}
