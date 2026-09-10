package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.data.ai.AiService
import com.kuikly.stockchat.data.ai.AnswerListener
import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.SettingsRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.domain.chat.AiAnswer
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.Conversation
import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.ParsedIntent
import com.kuikly.stockchat.domain.chat.Role
import com.kuikly.stockchat.domain.chat.instrumentKeys
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentRules
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

/**
 * 聊天页视图模型：持有响应式状态，编排 AI 服务与会话持久化。
 * 不直接依赖任何 View，页面只读状态 / 调用动作。
 */
class ChatViewModel(
    private val aiService: AiService,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    val marketRepository: MarketRepository,
) {
    var messages by observableList<ChatUiMessage>()
    var conversations by observableList<Conversation>()

    var isGenerating by observable(false)
    var showHistory by observable(false)
    var inputText by observable("")
    var pendingAttachments by observableList<Attachment>()
    var ttsEnabled by observable(false)

    /** 由页面注入的原生播报回调，避免 ViewModel 依赖具体平台实现。 */
    var onReplyCompleted: ((String) -> Unit)? = null

    /** 是否有消息（欢迎页 / 对话页切换） */
    var hasConversation by observable(false)

    /** 首页推荐问题轮换偏移（「换一换」） */
    var welcomeOffset by observable(0)

    /** 抽屉内搜索关键词（空串 = 不过滤） */
    var drawerQuery by observable("")

    /** 抽屉顶栏点开搜索后展开输入行 */
    var drawerSearchOpen by observable(false)

    /** 抽屉展示的会话列表（drawerQuery 过滤后的派生列表，由 syncDrawerList 维护） */
    var drawerConversations by observableList<Conversation>()

    /** 更新搜索关键词并刷新派生列表 */
    fun onDrawerQueryChange(query: String) {
        drawerQuery = query
        syncDrawerList()
    }

    /** conversations 变更后同步抽屉派生列表（vfor 需要真实 ObservableList 才能增量刷新） */
    fun syncDrawerList() {
        val q = drawerQuery
        drawerConversations.clear()
        drawerConversations.addAll(
            if (q.isBlank()) conversations.toList()
            else conversations.filter { it.title.contains(q, ignoreCase = true) },
        )
    }

    /** 顶部提示（如离线模式） */
    var banner by observable("")

    /** 通知 UI 滚动到底部的信号（每次自增） */
    var scrollSignal by observable(0)

    var currentConversationId: String = ""
        private set
    private var currentCreatedAt: Long = 0L
    private var idSeed = 0

    val canSend: Boolean get() = inputText.isNotBlank() && !isGenerating

    /** 首页市场概览：主要指数 + 热门标的（真实行情） */
    var indexSnapshots by observableList<MarketSnapshot>()
    var hotSnapshots by observableList<MarketSnapshot>()
    var overviewLoading by observable(false)

    fun loadHistory() {
        conversations.clear()
        conversations.addAll(conversationRepository.loadAll())
        syncDrawerList()
    }

    fun loadSettings() {
        ttsEnabled = settingsRepository.isTtsEnabled()
    }

    fun persistTtsEnabled(enabled: Boolean) {
        ttsEnabled = enabled
        settingsRepository.setTtsEnabled(enabled)
    }

    /** 从其他页面改过存储后同步会话列表（例如设置页清空记录）。 */
    fun reloadConversationsFromStore() {
        val list = conversationRepository.reloadFromStore()
        conversations.clear()
        conversations.addAll(list)
        syncDrawerList()
        if (currentConversationId.isNotEmpty() && list.none { it.id == currentConversationId }) {
            messages.clear()
            currentConversationId = ""
            currentCreatedAt = 0L
            hasConversation = false
        }
    }

    fun loadOverview() {
        if (overviewLoading) return
        overviewLoading = true
        var pending = 2
        fun done() {
            pending -= 1
            if (pending == 0) overviewLoading = false
        }
        marketRepository.loadSnapshots(StockCatalog.overviewIndices) { list ->
            indexSnapshots.clear()
            indexSnapshots.addAll(list)
            done()
        }
        marketRepository.loadSnapshots(StockCatalog.hot) { list ->
            hotSnapshots.clear()
            hotSnapshots.addAll(list)
            if (list.any { it.quote.isMock } && banner.isEmpty()) {
                banner = "部分行情接口不可用，已使用离线演示数据"
            }
            done()
        }
    }

    // region 会话操作

    fun newConversation() {
        if (isGenerating) stopGenerating()
        persistCurrent()
        messages.clear()
        currentConversationId = ""
        currentCreatedAt = 0L
        hasConversation = false
        showHistory = false
    }

    fun openConversation(id: String) {
        if (id == currentConversationId) {
            showHistory = false
            return
        }
        if (isGenerating) stopGenerating()
        persistCurrent()
        val conversation = conversationRepository.find(id) ?: return
        messages.clear()
        conversation.messages.forEach { messages.add(ChatUiMessage.fromDomain(it)) }
        currentConversationId = conversation.id
        currentCreatedAt = conversation.createdAt
        hasConversation = messages.isNotEmpty()
        showHistory = false
        requestScrollToBottom()
    }

    fun deleteConversation(id: String) {
        conversationRepository.delete(id)
        conversations.removeAll { it.id == id }
        syncDrawerList()
        if (id == currentConversationId) {
            messages.clear()
            currentConversationId = ""
            currentCreatedAt = 0L
            hasConversation = false
        }
    }

    fun clearAllConversations() {
        conversationRepository.clear()
        conversations.clear()
        syncDrawerList()
        messages.clear()
        currentConversationId = ""
        currentCreatedAt = 0L
        hasConversation = false
        showHistory = false
    }

    // endregion

    // region 发送 / 生成

    fun send(
        rawText: String,
        attachments: List<Attachment> = emptyList(),
        hintInstruments: List<Instrument> = emptyList(),
    ) {
        val text = rawText.trim()
        val sendAttachments = if (attachments.isEmpty()) pendingAttachments.toList() else attachments
        if (!AttachmentRules.canSend(text, sendAttachments) || isGenerating) return
        InstrumentCache.rememberAll(hintInstruments)
        val effectiveText = text.ifEmpty { AttachmentRules.defaultPrompt(sendAttachments) }
        inputText = ""
        pendingAttachments.clear()
        val now = DateTime.currentTimestamp()
        if (currentConversationId.isEmpty()) {
            currentConversationId = "c$now"
            currentCreatedAt = now
        }
        messages.add(ChatUiMessage(nextId(), Role.USER, effectiveText, now, MessageStatus.DONE, initialAttachments = sendAttachments))
        hasConversation = true

        val aiMessage = ChatUiMessage(nextId(), Role.ASSISTANT, "", now + 1, MessageStatus.THINKING)
        messages.add(aiMessage)
        isGenerating = true
        requestScrollToBottom()

        aiService.ask(
            text = effectiveText,
            contextInstruments = contextInstruments(hintInstruments),
            attachments = sendAttachments,
            listener = object : AnswerListener {
                override fun onThinking(parsed: ParsedIntent) {
                    aiMessage.intent = parsed.intent
                }

                override fun onBlock(index: Int, block: AnswerBlock) {
                    aiMessage.status = MessageStatus.STREAMING
                    aiMessage.appendCard(index, block)
                    requestScrollToBottom()
                }

                override fun onMarkdownStart(index: Int) {
                    aiMessage.status = MessageStatus.STREAMING
                    aiMessage.appendMarkdown(index)
                }

                override fun onMarkdownDelta(index: Int, text: String, finished: Boolean) {
                    aiMessage.markdownModel(index)?.update(text, finished)
                    if (finished) aiMessage.recordMarkdown(index, text)
                    requestScrollToBottom()
                }

                override fun onComplete(answer: AiAnswer) {
                    aiMessage.status = MessageStatus.DONE
                    isGenerating = false
                    if (answer.relatedSnapshots.any { it.quote.isMock }) {
                        banner = "部分行情接口不可用，已使用离线演示数据"
                    } else if (answer.relatedSnapshots.isNotEmpty()) {
                        banner = ""
                    }
                    persistCurrent()
                    requestScrollToBottom()
                    answer.relatedSnapshots.forEach { InstrumentCache.put(it.quote.instrument) }
                    answer.blocks.filterIsInstance<AnswerBlock.Markdown>()
                        .joinToString("\n") { it.text }
                        .takeIf { it.isNotBlank() }
                        ?.let { onReplyCompleted?.invoke(it) }
                }

                override fun onError(message: String) {
                    aiMessage.status = MessageStatus.ERROR
                    aiMessage.errorMessage = message
                    isGenerating = false
                    persistCurrent()
                }
            },
        )
    }

    fun addPendingAttachments(items: List<Attachment>) {
        items.forEach { item ->
            if (pendingAttachments.none { it.id == item.id }) pendingAttachments.add(item)
        }
    }

    fun removePendingAttachment(id: String) {
        pendingAttachments.removeAll { it.id == id }
    }

    fun stopGenerating() {
        aiService.cancel()
        messages.lastOrNull()?.takeIf { !it.isUser && (it.isStreaming || it.isThinking) }?.let { msg ->
            if (msg.blocks.isEmpty()) {
                msg.status = MessageStatus.ERROR
                msg.errorMessage = "已停止生成"
            } else {
                msg.blocks.forEach { block ->
                    if (block is UiBlock.Markdown) {
                        block.model.setFinal(block.model.text)
                        msg.recordMarkdown(block.index, block.model.text)
                    }
                }
                msg.status = MessageStatus.DONE
            }
        }
        isGenerating = false
        persistCurrent()
    }

    /** 重新生成最后一条 AI 回复 */
    fun retryLast() {
        if (isGenerating) return
        val lastUser = messages.lastOrNull { it.isUser } ?: return
        val lastIndex = messages.indexOfLast { it.isUser }
        while (messages.size > lastIndex) messages.removeAt(messages.size - 1)
        send(lastUser.text, lastUser.attachments)
    }

    // endregion

    private fun contextInstruments(hint: List<Instrument> = emptyList()): List<Instrument> {
        val fromBlocks = messages.asReversed()
            .flatMap { msg -> msg.toDomain().blocks.flatMap { it.instrumentKeys() } }
            .mapNotNull { InstrumentCache.get(it) ?: StockCatalog.findByKey(it) }
        return (hint + fromBlocks).distinctBy { it.key }
    }

    private fun requestScrollToBottom() {
        scrollSignal += 1
    }

    private fun persistCurrent() {
        if (currentConversationId.isEmpty() || messages.isEmpty()) return
        val domainMessages = messages.map { it.toDomain() }
        val title = domainMessages.firstOrNull { it.role == Role.USER }?.text?.take(30) ?: "新对话"
        val conversation = Conversation(
            id = currentConversationId,
            title = title,
            createdAt = currentCreatedAt,
            updatedAt = DateTime.currentTimestamp(),
            messages = domainMessages,
        )
        conversationRepository.save(conversation)
        conversations.removeAll { it.id == conversation.id }
        conversations.add(0, conversation)
        syncDrawerList()
    }

    private fun nextId(): String = "m${DateTime.currentTimestamp()}_${idSeed++}"
}
