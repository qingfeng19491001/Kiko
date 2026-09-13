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
    fun get(
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        callback: (text: String?, error: String?) -> Unit,
    )

    /**
     * 发送 JSON body 的 POST 请求。
     * @param body JSON 请求体（将作为 request body 发送）
     * @param headers 自定义请求头（如 Authorization）
     * @param timeoutSeconds 超时时间（秒）
     * @param callback text 为原始响应字符串，失败时为 null
     */
    fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 30,
        callback: (text: String?, error: String?) -> Unit,
    )
}

/**
 * 基于 Kuikly [NetworkModule] 的实现。
 * NetworkModule 对非 JSON 回包会包装为 `{"data": "<raw>"}`，这里统一还原为字符串。
 */
class KuiklyHttpClient(private val pager: IPager) : HttpClient {

    override fun get(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String>,
        callback: (String?, String?) -> Unit,
    ) {
        val module = pager.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        val param = JSONObject().apply { params.forEach { (k, v) -> put(k, v) } }
        val headerJson = JSONObject().apply {
            put("User-Agent", "Mozilla/5.0 (StockChat Kuikly Demo)")
            if (headers.isEmpty()) {
                put("Referer", "https://gu.qq.com/")
            } else {
                headers.forEach { (k, v) -> put(k, v) }
            }
        }
        module.httpRequest(url, false, param, headerJson, null, 15) { data, success, errorMsg, _ ->
            if (!success) {
                callback(null, errorMsg.ifEmpty { "network error" })
                return@httpRequest
            }
            callback(unwrapNetworkText(data), null)
        }
    }

    override fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String>,
        timeoutSeconds: Int,
        callback: (String?, String?) -> Unit,
    ) {
        val module = pager.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        val headerJson = JSONObject().apply {
            put("Content-Type", "application/json")
            headers.forEach { (k, v) -> put(k, v) }
        }
        module.httpRequest(url, true, body, headerJson, null, timeoutSeconds) { data, success, errorMsg, _ ->
            if (!success) {
                callback(null, errorMsg.ifEmpty { "network error" })
                return@httpRequest
            }
            callback(unwrapNetworkText(data), null)
        }
    }
}

/**
 * NetworkModule 对非 JSON 回包装 `{"data":"<raw>"}`。鸿蒙实现常额外带 status/httpCode，
 * 若仍用 `toString()` 会把 `v_hk00700="..."` 嵌进 JSON，拆 `~` 时丢掉现价字段。
 */
internal fun unwrapNetworkText(data: JSONObject): String {
    if (data.has("data")) {
        val wrapped = data.optString("data")
        if (wrapped.startsWith("v_") || wrapped.contains("v_")) return wrapped
        if (data.length() == 1 && wrapped.isNotEmpty()) return wrapped
    }
    return data.toString()
}
