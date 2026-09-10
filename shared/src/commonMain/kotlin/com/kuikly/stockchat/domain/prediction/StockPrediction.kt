package com.kuikly.stockchat.domain.prediction

data class PredictionPoint(
    val date: String,
    val price: Double,
    val lower: Double? = null,
    val upper: Double? = null,
)

data class StockPrediction(
    val direction: String,
    val confidence: Float,
    val rationale: String,
    val sourceUpdatedAt: String,
    val historyPointCount: Int,
    val points: List<PredictionPoint>,
)

sealed class PredictionResult {
    data class Success(val prediction: StockPrediction) : PredictionResult()
    data class Unavailable(val message: String) : PredictionResult()
    data class Failure(val message: String) : PredictionResult()
}
