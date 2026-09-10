package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.ChatMessage
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.Role
import com.kuikly.stockchat.ui.components.StreamingMarkdownModel
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

/**
 * 消息中的一个可渲染块。
 * Markdown 块持有流式模型；其他块一次性渲染。
 */
sealed class UiBlock(val index: Int) {
    class Markdown(index: Int, val model: StreamingMarkdownModel) : UiBlock(index)
    class Card(index: Int, val block: AnswerBlock) : UiBlock(index)
}

/**
 * 聊天消息的响应式 UI 模型。
 */
class ChatUiMessage(
    val id: String,
    val role: Role,
    val text: String,
    val createdAt: Long,
    initialStatus: MessageStatus,
    initialIntent: Intent = Intent.UNKNOWN,
    initialAttachments: List<Attachment> = emptyList(),
) {
    var attachments: List<Attachment> = initialAttachments
    var status by observable(initialStatus)
    var intent by observable(initialIntent)
    var errorMessage by observable("")
    var blocks by observableList<UiBlock>()

    /** 最终的 Markdown 文本（用于持久化） */
    private val markdownTexts = mutableMapOf<Int, String>()
    private val cardBlocks = mutableMapOf<Int, AnswerBlock>()

    val isUser: Boolean get() = role == Role.USER
    val isThinking: Boolean get() = status == MessageStatus.THINKING
    val isStreaming: Boolean get() = status == MessageStatus.STREAMING

    fun appendMarkdown(index: Int): StreamingMarkdownModel {
        val model = StreamingMarkdownModel()
        blocks.add(UiBlock.Markdown(index, model))
        return model
    }

    fun appendCard(index: Int, block: AnswerBlock) {
        cardBlocks[index] = block
        blocks.add(UiBlock.Card(index, block))
    }

    fun markdownModel(index: Int): StreamingMarkdownModel? =
        blocks.firstOrNull { it.index == index }?.let { (it as? UiBlock.Markdown)?.model }

    fun recordMarkdown(index: Int, text: String) {
        markdownTexts[index] = text
    }

    fun toDomain(): ChatMessage {
        val ordered = blocks.sortedBy { it.index }.map { block ->
            when (block) {
                is UiBlock.Markdown -> AnswerBlock.Markdown(markdownTexts[block.index] ?: block.model.text)
                is UiBlock.Card -> block.block
            }
        }
        return ChatMessage(
            id = id,
            role = role,
            text = text,
            blocks = ordered,
            status = if (status == MessageStatus.STREAMING || status == MessageStatus.THINKING) MessageStatus.DONE else status,
            intent = intent,
            createdAt = createdAt,
            errorMessage = errorMessage,
            attachments = attachments,
        )
    }

    companion object {
        /** 从持久化消息恢复（Markdown 直接终态渲染） */
        fun fromDomain(message: ChatMessage): ChatUiMessage {
            val ui = ChatUiMessage(message.id, message.role, message.text, message.createdAt, MessageStatus.DONE, message.intent, message.attachments)
            ui.errorMessage = message.errorMessage
            message.blocks.forEachIndexed { index, block ->
                if (block is AnswerBlock.Markdown) {
                    val model = ui.appendMarkdown(index)
                    model.setFinal(block.text)
                    ui.recordMarkdown(index, block.text)
                } else {
                    ui.appendCard(index, block)
                }
            }
            return ui
        }
    }
}
