package com.kuikly.stockchat.data.attachment

import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.IPager

/** 已从本地读出的附件内容，供多模态请求组装。 */
data class LoadedAttachment(
    val attachment: Attachment,
    val dataUrl: String? = null,
    val textExcerpt: String? = null,
    val error: String? = null,
) {
    val hasImage: Boolean get() = !dataUrl.isNullOrBlank()
    val hasText: Boolean get() = !textExcerpt.isNullOrBlank()
}

interface FileContentReader {
    fun read(path: String, callback: (dataUrl: String?, text: String?, error: String?) -> Unit)
}

class KuiklyFileContentReader(private val pager: IPager) : FileContentReader {
    override fun read(path: String, callback: (String?, String?, String?) -> Unit) {
        val module = runCatching {
            pager.acquireModule<AttachmentModule>(AttachmentModule.MODULE_NAME)
        }.getOrNull()
        if (module == null) {
            callback(null, null, "当前平台无法读取本地附件")
            return
        }
        module.readFile(path) { json ->
            val payload = json ?: JSONObject()
            val success = payload.optBoolean("success", false)
            if (!success) {
                callback(null, null, payload.optString("error").ifEmpty { "读取附件失败" })
                return@readFile
            }
            val dataUrl = payload.optString("dataUrl").takeIf { it.isNotEmpty() }
            val text = payload.optString("text").takeIf { it.isNotEmpty() }
            callback(dataUrl, text, null)
        }
    }
}

object AttachmentLoader {
    fun loadAll(
        attachments: List<Attachment>,
        reader: FileContentReader?,
        callback: (List<LoadedAttachment>) -> Unit,
    ) {
        if (attachments.isEmpty()) {
            callback(emptyList())
            return
        }
        if (reader == null) {
            callback(attachments.map { LoadedAttachment(it, error = "无法读取本地附件") })
            return
        }
        loadAt(0, attachments, reader, mutableListOf(), callback)
    }

    private fun loadAt(
        index: Int,
        attachments: List<Attachment>,
        reader: FileContentReader,
        acc: MutableList<LoadedAttachment>,
        callback: (List<LoadedAttachment>) -> Unit,
    ) {
        if (index >= attachments.size) {
            callback(acc.toList())
            return
        }
        val item = attachments[index]
        if (item.localPath.isBlank()) {
            acc += LoadedAttachment(item, error = "附件路径缺失")
            loadAt(index + 1, attachments, reader, acc, callback)
            return
        }
        reader.read(item.localPath) { dataUrl, text, error ->
            val excerpt = text?.take(8000)
            acc += LoadedAttachment(
                attachment = item,
                dataUrl = if (item.kind == AttachmentKind.IMAGE) dataUrl else null,
                textExcerpt = excerpt,
                error = error,
            )
            loadAt(index + 1, attachments, reader, acc, callback)
        }
    }
}
