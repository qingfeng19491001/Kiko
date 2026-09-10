package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.prediction.PredictionResult
import com.kuikly.stockchat.domain.prediction.StockPredictionParser
import com.kuikly.stockchat.domain.util.DateUtil
import com.kuikly.stockchat.domain.util.NumberFormat
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

class StockPredictionService(
    private val httpClient: HttpClient,
    private val baseUrl: String = AiConfig.BASE_URL,
    private val apiKey: String = AiConfig.API_KEY,
    private val model: String = AiConfig.MODEL,
) {
    fun predict(
        instrument: Instrument,
        quote: Quote,
        dailyBars: List<KLineBar>,
        callback: (PredictionResult) -> Unit,
    ) {
        if (apiKey.isBlank()) {
            callback(PredictionResult.Unavailable("尚未配置百炼 API Key，未生成预测曲线。"))
            return
        }
        if (quote.isMock) {
            callback(PredictionResult.Unavailable("当前为离线演示行情，未向模型请求预测。"))
            return
        }
        val history = dailyBars.takeLast(MIN_HISTORY)
        if (history.size < MIN_HISTORY) {
            callback(PredictionResult.Unavailable("历史 K 线不足 $MIN_HISTORY 根，未生成预测。"))
            return
        }
        val sourceUpdatedAt = quote.updateTime.ifBlank { history.last().date }
        val expectedDates = DateUtil.nextWeekdays(history.last().date.take(10), HORIZON)
        if (expectedDates.size != HORIZON) {
            callback(PredictionResult.Unavailable("无法推算预测日期。"))
            return
        }
        val latest = history.last().close
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("stream", false)
            put("enable_thinking", false)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", SYSTEM) })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt(instrument, quote, history, sourceUpdatedAt, expectedDates))
                    })
                },
            )
        }
        httpClient.postJson("$baseUrl/chat/completions", body, mapOf("Authorization" to "Bearer $apiKey"), 45) { raw, error ->
            if (raw == null) {
                callback(PredictionResult.Failure(error ?: "预测请求失败"))
                return@postJson
            }
            val content = DashScopeParser.messageContent(raw)
            val parsed = content?.let {
                StockPredictionParser.parse(it, sourceUpdatedAt, history.size, expectedDates, latest)
            }
            callback(
                if (parsed != null) PredictionResult.Success(parsed)
                else PredictionResult.Failure("模型返回的预测未通过校验，未绘制预测曲线。"),
            )
        }
    }

    private fun userPrompt(
        instrument: Instrument,
        quote: Quote,
        history: List<KLineBar>,
        sourceUpdatedAt: String,
        expectedDates: List<String>,
    ): String {
        val series = history.joinToString("\n") { bar ->
            "${bar.date} O${NumberFormat.price(bar.open)} H${NumberFormat.price(bar.high)} L${NumberFormat.price(bar.low)} C${NumberFormat.price(bar.close)}"
        }
        return """
            标的：${instrument.name}（${instrument.displayCode}）
            最新价：${NumberFormat.price(quote.price)}  涨跌幅：${NumberFormat.signedPct(quote.changePct)}
            sourceUpdatedAt：$sourceUpdatedAt
            historyPointCount：${history.size}
            必须按此日期输出 forecast：${expectedDates.joinToString(",")}
            日K（从旧到新）：
            $series
        """.trimIndent()
    }

    companion object {
        private const val MIN_HISTORY = 8
        private const val HORIZON = 5
        private val SYSTEM = """
            你是股票走势情景分析助手。只输出 JSON，不要 Markdown：
            {"direction":"偏多|偏空|中性","confidence":0.0,"rationale":"简述依据","sourceUpdatedAt":"必须原样回传","historyPointCount":0,"forecast":[{"date":"yyyy-MM-dd","price":0,"low":0,"high":0}]}
            forecast 数量和日期必须与用户给出的日期列表完全一致。价格必须基于提供的收盘价做小幅情景外推，禁止暴涨暴跌幻想。
            这是演示信息，不是投资建议。
        """.trimIndent()
    }
}
