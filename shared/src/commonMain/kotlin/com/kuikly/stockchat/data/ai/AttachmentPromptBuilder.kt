package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.attachment.LoadedAttachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

sealed class UserContent {
    data class Text(val value: String) : UserContent()
    data class Parts(val array: JSONArray) : UserContent()
}

object AttachmentPromptBuilder {
    fun build(userText: String, loaded: List<LoadedAttachment>): UserContent {
        val text = listOf(userText.trim(), notes(loaded)).filter { it.isNotBlank() }.joinToString("\n\n")
        val images = loaded.filter { it.hasImage }
        if (images.isEmpty()) return UserContent.Text(text)
        val parts = JSONArray()
        images.forEach { item ->
            parts.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply { put("url", item.dataUrl) })
            })
        }
        parts.put(JSONObject().apply {
            put("type", "text")
            put("text", text)
        })
        return UserContent.Parts(parts)
    }

    fun notes(loaded: List<LoadedAttachment>): String {
        if (loaded.isEmpty()) return ""
        return loaded.mapNotNull { item ->
            val name = item.attachment.displayName
            when {
                item.error != null -> "附件「$name」未能发给模型：${item.error}"
                item.attachment.kind == AttachmentKind.DOCUMENT && item.hasText ->
                    "附件「$name」文本摘录：\n${item.textExcerpt}"
                item.attachment.kind == AttachmentKind.DOCUMENT ->
                    "附件「$name」未能提取原文，模型看不到文档内容。"
                item.hasImage -> null
                else -> "附件「$name」未能编码为图片。"
            }
        }.joinToString("\n")
    }
}
