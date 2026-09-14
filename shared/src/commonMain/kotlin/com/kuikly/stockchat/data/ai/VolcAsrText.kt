package com.kuikly.stockchat.data.ai

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 解析火山引擎 ASR JSON：官方示例里 `result` 是对象，部分文档写成 list。 */
object VolcAsrText {
    fun extract(payloadJson: String): String? {
        if (payloadJson.isBlank()) return null
        val root = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return null
        val payload = root.optJSONObject("payload_msg") ?: root
        textOf(payload)?.let { return it }
        return textOf(root)
    }

    private fun textOf(node: JSONObject): String? {
        node.optJSONObject("result")?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        node.optJSONArray("result")?.let { array ->
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.optString("text") }
                .lastOrNull { it.isNotBlank() }
                ?.let { return it }
        }
        return node.optString("text").takeIf { it.isNotBlank() }
    }
}
