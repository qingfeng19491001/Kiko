package com.kuikly.stockchat.data.attachment

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Native bridge for camera, photo library, documents, local files and multipart upload. */
class AttachmentModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun openCamera(callback: CallbackFn) = open("camera", callback)
    fun openPhotoLibrary(callback: CallbackFn) = open("photo_library", callback)
    fun openFilePicker(callback: CallbackFn) = open("file", callback)

    fun readFile(path: String, callback: CallbackFn) {
        asyncToNativeMethod("readFile", JSONObject().apply { put("path", path) }, callback)
    }

    fun deleteFile(path: String, callback: CallbackFn? = null) {
        asyncToNativeMethod("deleteFile", JSONObject().apply { put("path", path) }, callback)
    }

    fun fileExists(path: String, callback: CallbackFn) {
        asyncToNativeMethod("fileExists", JSONObject().apply { put("path", path) }, callback)
    }

    fun upload(
        path: String,
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        callback: CallbackFn,
    ) {
        asyncToNativeMethod(
            "upload",
            JSONObject().apply {
                put("path", path)
                put("url", url)
                put("headers", JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } })
                put("fields", JSONObject().apply { fields.forEach { (k, v) -> put(k, v) } })
            },
            callback,
        )
    }

    fun deleteRemote(url: String, headers: Map<String, String>, callback: CallbackFn? = null) {
        asyncToNativeMethod(
            "deleteRemote",
            JSONObject().apply {
                put("url", url)
                put("headers", JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } })
            },
            callback,
        )
    }

    private fun open(source: String, callback: CallbackFn) {
        asyncToNativeMethod("open", JSONObject().apply { put("source", source) }, callback)
    }

    companion object {
        const val MODULE_NAME = "KRAttachmentModule"
    }
}
