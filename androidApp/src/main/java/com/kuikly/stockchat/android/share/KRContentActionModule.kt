package com.kuikly.stockchat.android.share

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject

class KRContentActionModule : KuiklyRenderBaseModule() {
    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        if (method == "copy") {
            val payload = JSONObject(params ?: "{}")
            val host = activity
            if (host == null) {
                callback?.invoke(JSONObject().put("success", false).put("error", "当前页面无法复制").toString())
                return null
            }
            host.runOnUiThread {
                runCatching {
                    val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Kiko AI 回复", payload.optString("text")))
                }.onSuccess {
                    callback?.invoke(JSONObject().put("success", true).toString())
                }.onFailure {
                    callback?.invoke(JSONObject().put("success", false).put("error", it.message ?: "复制失败").toString())
                }
            }
            return null
        }
        if (method != "share") return super.call(method, params, callback)
        val host = activity
        if (host == null) {
            callback?.invoke(JSONObject().put("success", false).put("error", "当前页面无法分享").toString())
            return null
        }
        val payload = JSONObject(params ?: "{}")
        host.runOnUiThread {
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, payload.optString("title"))
                    putExtra(Intent.EXTRA_TEXT, payload.optString("text"))
                }
                host.startActivity(Intent.createChooser(intent, "分享 AI 解读"))
            }.onSuccess {
                callback?.invoke(JSONObject().put("success", true).toString())
            }.onFailure {
                callback?.invoke(JSONObject().put("success", false).put("error", it.message ?: "分享失败").toString())
            }
        }
        return null
    }

    companion object { const val MODULE_NAME = "KRContentActionModule" }
}
