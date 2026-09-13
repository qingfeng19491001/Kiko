package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.attachment.FileUploader
import com.kuikly.stockchat.data.attachment.LoadedAttachment
import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * Uploads documents to DashScope `/files`, extracts text with qwen-long, then deletes remote copies.
 */
class DashScopeDocumentClient(
    private val http: HttpClient,
    private val uploader: FileUploader,
    private val apiKey: String = AiConfig.API_KEY,
    private val baseUrl: String = AiConfig.BASE_URL,
    private val longModel: String = AiConfig.LONG_MODEL,
) {
    fun enrich(loaded: List<LoadedAttachment>, callback: (List<LoadedAttachment>, List<String>) -> Unit) {
        val pending = loaded.mapIndexedNotNull { index, item ->
            if (item.attachment.kind == AttachmentKind.DOCUMENT &&
                !item.hasText &&
                item.error == null &&
                item.attachment.localPath.isNotBlank()
            ) index to item else null
        }
        if (pending.isEmpty() || apiKey.isBlank()) {
            callback(loaded, emptyList())
            return
        }
        uploadAt(0, pending, loaded.toMutableList(), mutableListOf(), callback)
    }

    fun deleteRemote(ids: List<String>) {
        ids.forEach { id ->
            uploader.delete(
                "$baseUrl/files/$id",
                mapOf("Authorization" to "Bearer $apiKey"),
            ) {}
        }
    }

    private fun uploadAt(
        cursor: Int,
        pending: List<Pair<Int, LoadedAttachment>>,
        acc: MutableList<LoadedAttachment>,
        remoteIds: MutableList<String>,
        callback: (List<LoadedAttachment>, List<String>) -> Unit,
    ) {
        if (cursor >= pending.size) {
            callback(acc.toList(), remoteIds.toList())
            return
        }
        val (index, item) = pending[cursor]
        uploader.upload(
            path = item.attachment.localPath,
            url = "$baseUrl/files",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            fields = mapOf("purpose" to "file-extract"),
        ) { body, error ->
            val fileId = parseFileId(body)
            if (fileId == null) {
                acc[index] = item.copy(error = error ?: "文档上传失败")
                uploadAt(cursor + 1, pending, acc, remoteIds, callback)
                return@upload
            }
            remoteIds += fileId
            extract(fileId) { text, extractError ->
                acc[index] = item.copy(
                    textExcerpt = text?.take(12_000),
                    error = if (text.isNullOrBlank()) extractError ?: "未能从文档提取文本" else null,
                )
                uploadAt(cursor + 1, pending, acc, remoteIds, callback)
            }
        }
    }

    private fun extract(fileId: String, callback: (String?, String?) -> Unit) {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "fileid://$fileId")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", "请提取该文档的全部正文，保持原有段落与表格结构，不要总结。")
            })
        }
        val body = JSONObject().apply {
            put("model", longModel)
            put("messages", messages)
            put("stream", false)
        }
        http.postJson(
            "$baseUrl/chat/completions",
            body,
            mapOf("Authorization" to "Bearer $apiKey"),
            90,
        ) { text, error ->
            val reply = text?.let(DashScopeParser::messageContent)
            callback(reply, if (reply.isNullOrBlank()) DashScopeParser.errorMessage(text) ?: error else null)
        }
    }

    companion object {
        fun parseFileId(body: String?): String? {
            if (body.isNullOrBlank()) return null
            return runCatching { JSONObject(body).optString("id") }.getOrNull()?.ifBlank { null }
        }
    }
}
