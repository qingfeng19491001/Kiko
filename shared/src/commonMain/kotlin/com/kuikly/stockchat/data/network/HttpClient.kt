package com.kuikly.stockchat.data.network

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.IPager

/**
 * HTTP 抽象，方便数据层脱离 Kuikly 运行环境做单元测试。
 */
interface HttpClient {
    /**
     * @param callback text 为原始响应字符串（JSON 或纯文本），失败时为 null
     */
    fun get(url: String, params: Map<String, String> = emptyMap(), callback: (text: String?, error: String?) -> Unit)
}

/**
 * 基于 Kuikly [NetworkModule] 的实现。
 * NetworkModule 对非 JSON 响应会包装为 `{"data": "<raw>"}`，这里统一还原为字符串。
 */
class KuiklyHttpClient(private val pager: IPager) : HttpClient {

    override fun get(url: String, params: Map<String, String>, callback: (String?, String?) -> Unit) {
        val module = pager.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        val param = JSONObject().apply { params.forEach { (k, v) -> put(k, v) } }
        val headers = JSONObject().apply {
            put("Referer", "https://gu.qq.com/")
            put("User-Agent", "Mozilla/5.0 (StockChat Kuikly Demo)")
        }
        module.httpRequest(url, false, param, headers, null, 15) { data, success, errorMsg, _ ->
            if (!success) {
                callback(null, errorMsg.ifEmpty { "network error" })
                return@httpRequest
            }
            val text = if (data.length() == 1 && data.has("data")) data.optString("data") else data.toString()
            callback(text, null)
        }
    }
}
