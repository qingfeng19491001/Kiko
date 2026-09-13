package com.kuikly.stockchat.data.attachment

import com.kuikly.stockchat.domain.chat.Conversation

/** Reference counting for local attachment files kept beside chat history. */
object AttachmentStore {
    fun pathsOf(conversation: Conversation): Set<String> =
        conversation.messages.flatMap { message ->
            message.attachments.flatMap { listOfNotNull(it.localPath, it.thumbnailPath) }
        }.filter { it.isNotBlank() }.toSet()

    fun referencedPaths(conversations: List<Conversation>): Set<String> =
        conversations.flatMap(::pathsOf).toSet()

    fun orphanedAfterDelete(deleted: Conversation, remaining: List<Conversation>): Set<String> =
        pathsOf(deleted) - referencedPaths(remaining)

    fun orphanedAfterPrune(pruned: List<Conversation>, remaining: List<Conversation>): Set<String> =
        pruned.flatMap(::pathsOf).toSet() - referencedPaths(remaining)
}
