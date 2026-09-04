package com.kuikly.stockchat.domain.chat

enum class Role { USER, ASSISTANT }

enum class MessageStatus {
    /** AI 正在思考（尚未产出内容） */
    THINKING,
    /** 正在流式输出 */
    STREAMING,
    DONE,
    ERROR,
}

/**
 * 一条聊天消息。用户消息只有 [text]；AI 消息由 [blocks] 组成。
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String = "",
    val blocks: List<AnswerBlock> = emptyList(),
    val status: MessageStatus = MessageStatus.DONE,
    val intent: Intent = Intent.UNKNOWN,
    val createdAt: Long,
    val errorMessage: String = "",
) {
    val isUser: Boolean get() = role == Role.USER
}

/**
 * 一次会话（对应历史记录中的一条）。
 */
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
) {
    /** 会话涉及的标的 key（按最近出现排序），用于追问时的上下文补全 */
    val contextInstrumentKeys: List<String>
        get() = messages.asReversed()
            .flatMap { msg -> msg.blocks.flatMap { it.instrumentKeys() } }
            .distinct()
}

fun AnswerBlock.instrumentKeys(): List<String> = when (this) {
    is AnswerBlock.StockCard -> listOf(quote.instrument.key)
    is AnswerBlock.CompareCard -> listOf(leftKey, rightKey)
    is AnswerBlock.ChartCard -> listOf(instrumentKey)
    else -> emptyList()
}
