package com.kuikly.stockchat.data.share

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 调起系统分享面板；具体平台能力由同名 Native Module 承接。 */
class ContentActionModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun share(title: String, text: String, callback: CallbackFn) {
        asyncToNativeMethod(
            "share",
            JSONObject().apply { put("title", title); put("text", text) },
            callback,
        )
    }

    fun copy(text: String, callback: CallbackFn) {
        asyncToNativeMethod(
            "copy",
            JSONObject().apply { put("text", text) },
            callback,
        )
    }

    companion object {
        const val MODULE_NAME = "KRContentActionModule"
    }
}
