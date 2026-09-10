package com.kuikly.stockchat.data.ai

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 解析百炼 OpenAI 兼容接口的成功回包与错误信息。 */
internal object DashScopeParser {
    fun messageContent(raw: String): String? = runCatching {
        val message = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: return@runCatching null
        message.optString("content").ifEmpty { null }
            ?: message.optString("reasoning_content").ifEmpty { null }
    }.getOrNull()

    fun errorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")?.ifEmpty { null }
                ?: json.optString("message").ifEmpty { null }
        }.getOrNull() ?: raw.trim().take(240).ifEmpty { null }
    }
}
