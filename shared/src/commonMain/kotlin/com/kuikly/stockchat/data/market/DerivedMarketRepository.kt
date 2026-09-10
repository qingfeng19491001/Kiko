package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.DerivedMarketData
import com.kuikly.stockchat.domain.model.FlowItemData
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.LadderLevelData
import com.kuikly.stockchat.domain.model.LadderStockData
import com.kuikly.stockchat.domain.model.LimitUpLadderData
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.MarketBreadthData
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 衍生数据仓库：调用本地 AKShare 网关（scripts/ak_gateway.py）。
 *
 * 网关提供：市场广度 / 连板梯队 / 个股资金流向。
 * 全部请求失败容忍——返回 null 由上层决定是否降级文案。
 *
 * 注意：Android 模拟器访问宿主机需把 [baseUrl] 改为 `http://10.0.2.2:8790`。
 */
class DerivedMarketRepository(private val http: HttpClient) {

    /** 网关地址，本地调试用 127.0.0.1；真机 / 模拟器按需覆盖 */
    var baseUrl: String = "http://127.0.0.1:8790"

    var cacheTtlMs: Long = 60_000L

    private data class Cached<T>(val value: T, val at: Long)

    private var breadthCache: Cached<MarketBreadthData>? = null
    private var ladderCache: Cached<LimitUpLadderData>? = null
    private val flowCache = mutableMapOf<String, Cached<CapitalFlowData>>()

    // region 市场广度

    fun loadBreadth(callback: (MarketBreadthData?) -> Unit) {
        val now = DateTime.currentTimestamp()
        breadthCache?.let { if (now - it.at < cacheTtlMs) { callback(it.value); return } }
        http.get("$baseUrl/breadth") { text, _ ->
            val data = text?.let { raw -> runCatching { parseBreadth(JSONObject(raw)) }.getOrNull() }
            if (data != null) breadthCache = Cached(data, DateTime.currentTimestamp())
            callback(data)
        }
    }

    private fun parseBreadth(json: JSONObject): MarketBreadthData? {
        val advancing = json.optInt("advancing", -1)
        val declining = json.optInt("declining", -1)
        if (advancing < 0 || declining < 0) return null
        return MarketBreadthData(
            date = json.optString("date", ""),
            advancing = advancing,
            declining = declining,
            limitUp = json.optInt("limitUp", 0),
            limitDown = json.optInt("limitDown", 0),
            halted = json.optInt("halted", 0),
            activity = json.optDouble("activity", 0.0),
        )
    }

    // endregion

    // region 连板梯队

    fun loadLadder(callback: (LimitUpLadderData?) -> Unit) {
        val now = DateTime.currentTimestamp()
        ladderCache?.let { if (now - it.at < cacheTtlMs * 5) { callback(it.value); return } }
        http.get("$baseUrl/ladder") { text, _ ->
            val data = text?.let { raw -> runCatching { parseLadder(JSONObject(raw)) }.getOrNull() }
            if (data != null) ladderCache = Cached(data, DateTime.currentTimestamp())
            callback(data)
        }
    }

    private fun parseLadder(json: JSONObject): LimitUpLadderData? {
        val levelsJson = json.optJSONArray("levels") ?: return null
        val levels = ArrayList<LadderLevelData>(levelsJson.length())
        for (i in 0 until levelsJson.length()) {
            val levelJson = levelsJson.optJSONObject(i) ?: continue
            val stocksJson = levelJson.optJSONArray("stocks") ?: continue
            val stocks = ArrayList<LadderStockData>(stocksJson.length())
            for (j in 0 until stocksJson.length()) {
                val s = stocksJson.optJSONObject(j) ?: continue
                stocks += LadderStockData(
                    name = s.optString("name", ""),
                    code = s.optString("code", ""),
                    changePct = s.optDouble("changePct", 0.0),
                    marketCap = s.optDouble("marketCap", 0.0),
                )
            }
            if (stocks.isNotEmpty()) {
                levels += LadderLevelData(levelJson.optInt("level", 1), stocks)
            }
        }
        if (levels.isEmpty()) return null
        return LimitUpLadderData(json.optString("date", ""), levels)
    }

    // endregion

    // region 个股资金流向

    fun loadCapitalFlow(instrument: Instrument, callback: (CapitalFlowData?) -> Unit) {
        // 网关当前仅覆盖 A 股个股（代码 6 位数字）
        val code = instrument.code
        val isAShare = (instrument.market == Market.SH || instrument.market == Market.SZ) &&
            code.length == 6 && code.all { it.isDigit() }
        if (!isAShare) {
            callback(null)
            return
        }
        val now = DateTime.currentTimestamp()
        flowCache[code]?.let { if (now - it.at < cacheTtlMs * 10) { callback(it.value); return } }
        http.get("$baseUrl/flow", mapOf("code" to code)) { text, _ ->
            val data = text?.let { raw -> runCatching { parseFlow(code, JSONObject(raw)) }.getOrNull() }
            if (data != null) flowCache[code] = Cached(data, DateTime.currentTimestamp())
            callback(data)
        }
    }

    private fun parseFlow(code: String, json: JSONObject): CapitalFlowData? {
        val flowsJson = json.optJSONArray("flows") ?: return null
        val flows = ArrayList<FlowItemData>(flowsJson.length())
        for (i in 0 until flowsJson.length()) {
            val f = flowsJson.optJSONObject(i) ?: continue
            flows += FlowItemData(
                label = f.optString("label", ""),
                netInflow = f.optDouble("netInflow", 0.0),
                pct = f.optDouble("pct", 0.0),
            )
        }
        if (flows.isEmpty()) return null
        return CapitalFlowData(code, json.optString("date", ""), flows)
    }

    // endregion
}
