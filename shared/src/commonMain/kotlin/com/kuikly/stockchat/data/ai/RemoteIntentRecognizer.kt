package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.data.parser.IntentLlmParser
import com.kuikly.stockchat.domain.chat.IntentParser
import com.kuikly.stockchat.domain.chat.ParsedIntent
import com.kuikly.stockchat.domain.model.Instrument
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 用百炼做意图识别；失败或未配置 Key 时回退本地关键词规则。
 */
class RemoteIntentRecognizer(
    private val httpClient: HttpClient,
    private val baseUrl: String = AiConfig.BASE_URL,
    private val apiKey: String = AiConfig.API_KEY,
    private val model: String = AiConfig.MODEL,
) {
    fun recognize(text: String, contextInstruments: List<Instrument>, callback: (ParsedIntent) -> Unit) {
        val local = IntentParser.parse(text, contextInstruments)
        if (apiKey.isBlank()) {
            callback(local)
            return
        }
        val names = contextInstruments.joinToString("、") { it.name }.ifBlank { "无" }
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("stream", false)
            put("enable_thinking", false)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "上下文标的：$names\n用户输入：$text")
                    })
                },
            )
        }
        httpClient.postJson("$baseUrl/chat/completions", body, mapOf("Authorization" to "Bearer $apiKey"), 20) { raw, _ ->
            val parsed = raw?.let { IntentLlmParser.parse(DashScopeParser.messageContent(it).orEmpty(), text, contextInstruments) }
            callback(parsed ?: local)
        }
    }

    companion object {
        private val SYSTEM = """
            你是股票问答应用的意图分类器。只输出一个 JSON 对象，不要 Markdown：
            {"intent":"STOCK_ANALYSIS|COMPARE|TREND|RISK|MARKET_OVERVIEW|LIMIT_UP_LADDER|CAPITAL_FLOW|KNOWLEDGE|GREETING|UNKNOWN","names":["标的名称或代码"],"topic":""}
            names 只填用户本轮真正要查的股票/指数（名称或代码均可，不必限于热门目录）；没有则空数组。可使用上下文标的补全代词。
            不要编造不在用户问题或上下文中的公司；未点名的同业由客户端按板块补全。
            后市、怎么走、走势、趋势、均线 → TREND。
            同业、排名、对比、vs、对标 → COMPARE。
            诊股、全面分析、综合分析 → STOCK_ANALYSIS。
        """.trimIndent()
    }
}
