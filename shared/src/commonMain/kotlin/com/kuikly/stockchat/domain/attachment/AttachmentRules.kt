package com.kuikly.stockchat.domain.attachment

object AttachmentRules {
    fun canSend(text: String, attachments: List<Attachment>): Boolean =
        text.isNotBlank() || attachments.any { it.status == AttachmentStatus.READY }

    fun defaultPrompt(attachments: List<Attachment>): String {
        val hasImage = attachments.any { it.kind == AttachmentKind.IMAGE && it.status == AttachmentStatus.READY }
        val hasDocument = attachments.any { it.kind == AttachmentKind.DOCUMENT && it.status == AttachmentStatus.READY }
        return when {
            hasImage && hasDocument -> "请分析并解读附件内容"
            hasDocument -> "请解读附件内容"
            else -> "请分析附件内容"
        }
    }

    fun remove(attachments: List<Attachment>, id: String): List<Attachment> =
        attachments.filterNot { it.id == id }
}
