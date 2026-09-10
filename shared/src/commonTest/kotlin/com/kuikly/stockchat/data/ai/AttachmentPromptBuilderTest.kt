package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.attachment.LoadedAttachment
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.domain.attachment.AttachmentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachmentPromptBuilderTest {
    @Test
    fun textOnlyKeepsStringContent() {
        val content = AttachmentPromptBuilder.build("分析腾讯", emptyList())
        assertTrue(content is UserContent.Text)
        assertEquals("分析腾讯", (content as UserContent.Text).value)
    }

    @Test
    fun imageUsesImageUrlParts() {
        val loaded = LoadedAttachment(
            attachment = sample(AttachmentKind.IMAGE, "chart.png"),
            dataUrl = "data:image/png;base64,abc",
        )
        val content = AttachmentPromptBuilder.build("请看图", listOf(loaded))
        assertTrue(content is UserContent.Parts)
        val parts = (content as UserContent.Parts).array
        assertEquals(2, parts.length())
        assertEquals("image_url", parts.optJSONObject(0)?.optString("type"))
        assertEquals("data:image/png;base64,abc", parts.optJSONObject(0)?.optJSONObject("image_url")?.optString("url"))
        assertEquals("text", parts.optJSONObject(1)?.optString("type"))
        assertTrue(parts.optJSONObject(1)?.optString("text")?.contains("请看图") == true)
    }

    @Test
    fun unreadDocumentAddsNoticeInsteadOfImage() {
        val loaded = LoadedAttachment(
            attachment = sample(AttachmentKind.DOCUMENT, "report.pdf"),
            error = null,
        )
        val notes = AttachmentPromptBuilder.notes(listOf(loaded))
        assertTrue(notes.contains("qwen-long"))
        val content = AttachmentPromptBuilder.build("解读", listOf(loaded))
        assertTrue(content is UserContent.Text)
    }

    private fun sample(kind: AttachmentKind, name: String) = Attachment(
        id = name,
        displayName = name,
        mimeType = if (kind == AttachmentKind.IMAGE) "image/png" else "application/pdf",
        byteSize = 10,
        localPath = "/tmp/$name",
        source = AttachmentSource.FILE,
        kind = kind,
    )
}
