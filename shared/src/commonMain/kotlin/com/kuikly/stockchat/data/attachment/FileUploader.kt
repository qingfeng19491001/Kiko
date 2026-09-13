package com.kuikly.stockchat.data.attachment

import com.tencent.kuikly.core.pager.IPager

interface FileUploader {
    fun upload(
        path: String,
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        callback: (body: String?, error: String?) -> Unit,
    )

    fun delete(url: String, headers: Map<String, String>, callback: (ok: Boolean) -> Unit)
}

class KuiklyFileUploader(private val pager: IPager) : FileUploader {
    override fun upload(
        path: String,
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        callback: (String?, String?) -> Unit,
    ) {
        val module = runCatching {
            pager.acquireModule<AttachmentModule>(AttachmentModule.MODULE_NAME)
        }.getOrNull()
        if (module == null) {
            callback(null, "当前平台无法上传文档")
            return
        }
        module.upload(path, url, headers, fields) { json ->
            val success = json?.optBoolean("success", false) == true
            val body = json?.optString("body").orEmpty()
            if (success) callback(body.ifBlank { null }, null)
            else callback(null, json?.optString("error").orEmpty().ifBlank { "文档上传失败" })
        }
    }

    override fun delete(url: String, headers: Map<String, String>, callback: (Boolean) -> Unit) {
        val module = runCatching {
            pager.acquireModule<AttachmentModule>(AttachmentModule.MODULE_NAME)
        }.getOrNull()
        if (module == null) {
            callback(false)
            return
        }
        module.deleteRemote(url, headers) { json ->
            callback(json?.optBoolean("success", false) == true)
        }
    }
}
