package com.kuikly.stockchat.domain.attachment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentRulesTest {
    @Test
    fun textOrReadyAttachmentMakesMessageSendable() {
        assertFalse(AttachmentRules.canSend("", emptyList()))
        assertTrue(AttachmentRules.canSend("问题", emptyList()))
        assertTrue(AttachmentRules.canSend("", listOf(readyImage())))
        assertFalse(AttachmentRules.canSend("", listOf(readyImage().copy(status = AttachmentStatus.FAILED))))
    }

    @Test
    fun defaultPromptDependsOnAttachmentKind() {
        assertEquals("请分析附件内容", AttachmentRules.defaultPrompt(listOf(readyImage())))
        assertEquals("请解读附件内容", AttachmentRules.defaultPrompt(listOf(readyDocument())))
        assertEquals("请分析并解读附件内容", AttachmentRules.defaultPrompt(listOf(readyImage(), readyDocument())))
    }

    @Test
    fun removeByIdKeepsOriginalOrder() {
        val image = readyImage()
        val document = readyDocument()
        assertEquals(listOf(document), AttachmentRules.remove(listOf(image, document), image.id))
    }

    private fun readyImage() = Attachment(
        id = "image-1",
        displayName = "chart.png",
        mimeType = "image/png",
        byteSize = 128,
        localPath = "/private/chart.png",
        source = AttachmentSource.PHOTO_LIBRARY,
        kind = AttachmentKind.IMAGE,
    )

    private fun readyDocument() = Attachment(
        id = "document-1",
        displayName = "report.pdf",
        mimeType = "application/pdf",
        byteSize = 512,
        localPath = "/private/report.pdf",
        source = AttachmentSource.FILE,
        kind = AttachmentKind.DOCUMENT,
    )
}
