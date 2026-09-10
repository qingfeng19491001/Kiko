package com.kuikly.stockchat.data.attachment

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Native bridge for camera, photo library and document providers. */
class AttachmentModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun openCamera(callback: CallbackFn) = open("camera", callback)
    fun openPhotoLibrary(callback: CallbackFn) = open("photo_library", callback)
    fun openFilePicker(callback: CallbackFn) = open("file", callback)

    fun readFile(path: String, callback: CallbackFn) {
        asyncToNativeMethod("readFile", JSONObject().apply { put("path", path) }, callback)
    }

    private fun open(source: String, callback: CallbackFn) {
        asyncToNativeMethod("open", JSONObject().apply { put("source", source) }, callback)
    }

    companion object {
        const val MODULE_NAME = "KRAttachmentModule"
    }
}
