package com.kuikly.stockchat.data.parser

import com.kuikly.stockchat.domain.prediction.PredictionPoint
import com.kuikly.stockchat.domain.prediction.StockPrediction

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

object StockPredictionParser {
    fun parse(
        raw: String,
        sourceUpdatedAt: String,
        historyCount: Int,
        expectedDates: List<String>,
        latestClose: Double,
    ): StockPrediction? {
        val json = decodeObject(raw) ?: return null
        if (json.optString("sourceUpdatedAt") != sourceUpdatedAt) return null
        if (json.optInt("historyPointCount") != historyCount) return null
        val direction = json.optString("direction").trim()
        if (direction.isEmpty()) return null
        val confidence = json.optDouble("confidence").toFloat()
        if (confidence.isNaN() || confidence !in 0f..1f) return null
        val rationale = json.optString("rationale").trim()
        if (rationale.isEmpty()) return null
        val forecast = json.optJSONArray("forecast") ?: return null
        if (forecast.length() != expectedDates.size) return null
        val lowerBound = latestClose * 0.01
        val upperBound = latestClose * 100.0
        val points = mutableListOf<PredictionPoint>()
        for (i in 0 until forecast.length()) {
            val row = forecast.optJSONObject(i) ?: return null
            val date = row.optString("date")
            if (date != expectedDates[i]) return null
            val price = row.optDouble("price")
            if (!price.isFinite() || price <= 0.0 || price !in lowerBound..upperBound) return null
            val low = row.optDouble("low", Double.NaN).takeIf { it.isFinite() }
            val high = row.optDouble("high", Double.NaN).takeIf { it.isFinite() }
            if (low != null && (low > price || low <= 0)) return null
            if (high != null && (high < price || high <= 0)) return null
            points += PredictionPoint(date, price, low, high)
        }
        return StockPrediction(direction, confidence, rationale, sourceUpdatedAt, historyCount, points)
    }

    private fun decodeObject(raw: String): JSONObject? = runCatching {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return@runCatching null
        JSONObject(trimmed.substring(start, end + 1))
    }.getOrNull()
}
