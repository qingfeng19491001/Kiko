package com.kuikly.stockchat.data.attachment

import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.domain.attachment.AttachmentSource
import com.kuikly.stockchat.domain.chat.ChatMessage
import com.kuikly.stockchat.domain.chat.Conversation
import com.kuikly.stockchat.domain.chat.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachmentStoreTest {
    @Test
    fun deleteRemovesOnlyUnreferencedFiles() {
        val shared = image("shared.png", "/a/shared.png", "/a/shared-thumb.png")
        val onlyInDeleted = image("gone.png", "/a/gone.png", null)
        val deleted = conversation("c1", listOf(shared, onlyInDeleted))
        val remaining = listOf(conversation("c2", listOf(shared)))
        assertEquals(setOf("/a/gone.png"), AttachmentStore.orphanedAfterDelete(deleted, remaining))
    }

    @Test
    fun pruneKeepsFilesStillReferenced() {
        val kept = image("keep.png", "/a/keep.png", "/a/keep-thumb.png")
        val dropped = image("old.pdf", "/a/old.pdf", null).copy(kind = AttachmentKind.DOCUMENT, mimeType = "application/pdf")
        val pruned = listOf(conversation("old", listOf(dropped, kept)))
        val remaining = listOf(conversation("live", listOf(kept)))
        assertEquals(setOf("/a/old.pdf"), AttachmentStore.orphanedAfterPrune(pruned, remaining))
        assertTrue("/a/keep.png" in AttachmentStore.referencedPaths(remaining))
    }

    private fun image(name: String, path: String, thumb: String?) = Attachment(
        id = name,
        displayName = name,
        mimeType = "image/png",
        byteSize = 10,
        localPath = path,
        thumbnailPath = thumb,
        source = AttachmentSource.FILE,
        kind = AttachmentKind.IMAGE,
    )

    private fun conversation(id: String, attachments: List<Attachment>) = Conversation(
        id = id,
        title = id,
        createdAt = 1,
        updatedAt = 1,
        messages = listOf(ChatMessage(id = "m$id", role = Role.USER, text = "hi", createdAt = 1, attachments = attachments)),
    )
}
