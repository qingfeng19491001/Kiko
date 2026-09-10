package com.kuikly.stockchat.data.chat

import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.domain.attachment.AttachmentSource
import com.kuikly.stockchat.domain.chat.ChatMessage
import com.kuikly.stockchat.domain.chat.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatCodecAttachmentTest {
    @Test
    fun messageRoundTripPreservesAttachmentMetadata() {
        val attachment = Attachment(
            id = "a1",
            displayName = "chart.png",
            mimeType = "image/png",
            byteSize = 42,
            localPath = "/private/a1.png",
            thumbnailPath = "/private/a1-thumb.png",
            source = AttachmentSource.PHOTO_LIBRARY,
            kind = AttachmentKind.IMAGE,
        )
        val decoded = ChatCodec.decodeMessage(ChatCodec.encode(ChatMessage(
            id = "m1", role = Role.USER, text = "分析这张图", attachments = listOf(attachment), createdAt = 1,
        )))
        assertEquals(listOf(attachment), decoded?.attachments)
    }
}
