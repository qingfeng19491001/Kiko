package com.tencent.kuiklybase.chart.stock

import com.tencent.kuiklybase.chart.model.OhlcPoint
import com.tencent.kuiklybase.chart.model.hasValidOhlcSemantics
import kotlin.math.sqrt

internal data class StockLineResult(val name: String, val color: Long, val values: List<Float?>)

internal data class MacdResult(val diff: List<Float?>, val dea: List<Float?>, val histogram: List<Float?>)

internal data class KdjResult(val k: List<Float?>, val d: List<Float?>, val j: List<Float?>)

internal fun stockAmount(points: List<OhlcPoint>): List<Float?> = points.map { point ->
    point.volume?.takeIf { point.hasValidOhlcSemantics() && it.isFinite() }
        ?.let { finiteOrNull(point.close * it) }
}

internal fun ema(values: List<Float>, period: Int): List<Float?> {
    require(period > 0) { "period must be positive" }
    val alpha = 2f / (period + 1f)
    var current: Float? = null
    return values.map { value ->
        if (!value.isFinite()) {
            null
        } else {
            current = current?.let { previous -> finiteOrNull(alpha * value + (1f - alpha) * previous) } ?: value
            current
        }
    }
}

internal fun alignToNullableSource(source: List<Float?>, compact: List<Float?>): List<Float?> {
    var compactIndex = 0
    return source.map { value ->
        if (value == null) null else compact.getOrNull(compactIndex++)
    }
}

internal fun smoothedNullable(values: List<Float?>, period: Int, seed: Float): List<Float?> {
    require(period > 0) { "period must be positive" }
    require(seed.isFinite()) { "seed must be finite" }
    var previous = seed
    return values.map { value ->
        if (value == null || !value.isFinite()) {
            null
        } else {
            val next = finiteOrNull((previous * (period - 1) + value) / period)
            if (next != null) previous = next
            next
        }
    }
}

internal fun line(name: String, values: List<Float?>): StockLineResult =
    StockLineResult(name, stockIndicatorColor(name), values.map { it?.let(::finiteOrNull) })

internal fun stockIndicatorColor(name: String): Long = when (name) {
    "MA5", "EXPMA12", "BOLL", "DIFF", "K", "RSI6" -> 0xFF14A9D6
    "MA10", "EXPMA50", "UP", "DEA", "D", "RSI12" -> 0xFFE7B900
    "MA20", "DN", "J", "RSI24" -> 0xFFE24AE3
    "UPPER" -> 0xFFFF8A34
    "ENE" -> 0xFF14A9D6
    "LOWER" -> 0xFF36B37E
    "BBD", "BBD5" -> 0xFFFF6B6B
    else -> 0xFF7B6FE8
}

internal fun ma(close: List<Float>, period: Int): List<Float?> {
    require(period > 0) { "period must be positive" }
    var sum = 0.0
    var invalidCount = 0
    return close.indices.map { index ->
        val added = close[index]
        if (added.isFinite()) sum += added else invalidCount++
        if (index >= period) {
            val removed = close[index - period]
            if (removed.isFinite()) sum -= removed else invalidCount--
        }
        if (index + 1 < period || invalidCount > 0) null else finiteOrNull((sum / period).toFloat())
    }
}

internal fun stockMaLines(points: List<OhlcPoint>): List<StockLineResult> =
    stockLinesByValidRuns(points) { close ->
        listOf(5, 10, 20, 30).map { period -> line("MA$period", ma(close, period)) }
    }

internal fun boll(close: List<Float>, period: Int = 20, multiplier: Float = 2f): List<StockLineResult> {
    require(period > 0) { "period must be positive" }
    var sum = 0.0
    var sumSquares = 0.0
    var invalidCount = 0
    val middle = ArrayList<Float?>(close.size)
    val deviation = ArrayList<Float?>(close.size)
    close.indices.forEach { index ->
        val added = close[index]
        if (added.isFinite()) {
            sum += added
            sumSquares += added.toDouble() * added
        } else {
            invalidCount++
        }
        if (index >= period) {
            val removed = close[index - period]
            if (removed.isFinite()) {
                sum -= removed
                sumSquares -= removed.toDouble() * removed
            } else {
                invalidCount--
            }
        }
        if (index + 1 < period || invalidCount > 0) {
            middle += null
            deviation += null
        } else {
            val mean = sum / period
            val variance = (sumSquares / period - mean * mean).coerceAtLeast(0.0)
            middle += finiteOrNull(mean.toFloat())
            deviation += finiteOrNull(sqrt(variance).toFloat())
        }
    }
    return listOf(
        line("BOLL", middle),
        line("UP", combine(middle, deviation) { mid, sd -> mid + multiplier * sd }),
        line("DN", combine(middle, deviation) { mid, sd -> mid - multiplier * sd }),
    )
}

internal fun stockBollLines(points: List<OhlcPoint>): List<StockLineResult> =
    stockLinesByValidRuns(points, ::boll)

internal fun expma(close: List<Float>): List<StockLineResult> = listOf(
    line("EXPMA12", ema(close, 12)),
    line("EXPMA50", ema(close, 50)),
)

internal fun stockExpmaLines(points: List<OhlcPoint>): List<StockLineResult> =
    stockLinesByValidRuns(points, ::expma)

internal fun bbi(close: List<Float>): List<Float?> {
    val averages = listOf(3, 6, 12, 24).map { ma(close, it) }
    return close.indices.map { index ->
        val ma3 = averages[0][index]
        val ma6 = averages[1][index]
        val ma12 = averages[2][index]
        val ma24 = averages[3][index]
        if (ma3 == null || ma6 == null || ma12 == null || ma24 == null) {
            null
        } else {
            finiteOrNull((ma3 + ma6 + ma12 + ma24) / 4f)
        }
    }
}

internal fun stockBbiLines(points: List<OhlcPoint>): List<StockLineResult> =
    stockLinesByValidRuns(points) { close -> listOf(line("BBI", bbi(close))) }

internal fun ene(close: List<Float>, period: Int = 10): List<StockLineResult> {
    require(period > 0) { "period must be positive" }
    val middle = ma(close, period)
    return listOf(
        line("UPPER", middle.map { it?.let { value -> finiteOrNull(value * 1.11f) } }),
        line("ENE", middle),
        line("LOWER", middle.map { it?.let { value -> finiteOrNull(value * 0.91f) } }),
    )
}

internal fun stockEneLines(points: List<OhlcPoint>): List<StockLineResult> =
    stockLinesByValidRuns(points, ::ene)

internal fun macd(close: List<Float>): MacdResult {
    val fast = ema(close, 12)
    val slow = ema(close, 26)
    val diff = combine(fast, slow) { a, b -> a - b }
    val dea = alignToNullableSource(diff, ema(diff.filterNotNull(), 9))
    val histogram = combine(diff, dea) { value, signal -> (value - signal) * 2f }
    return MacdResult(diff, dea, histogram)
}

internal fun stockMacd(points: List<OhlcPoint>): MacdResult = MacdResult(
    diff = closeValuesByValidRuns(points) { macd(it).diff },
    dea = closeValuesByValidRuns(points) { macd(it).dea },
    histogram = closeValuesByValidRuns(points) { macd(it).histogram },
)

internal fun kdj(points: List<OhlcPoint>, period: Int = 9): KdjResult {
    require(period > 0) { "period must be positive" }
    val rsv = points.indices.map { index ->
        if (index + 1 < period) {
            null
        } else {
            var low = Float.POSITIVE_INFINITY
            var high = Float.NEGATIVE_INFINITY
            var valid = true
            for (windowIndex in index - period + 1..index) {
                val point = points[windowIndex]
                if (!point.hasValidOhlcSemantics()) {
                    valid = false
                    break
                }
                if (point.low < low) low = point.low
                if (point.high > high) high = point.high
            }
            if (!valid) {
                null
            } else {
                if (high == low) 50f else finiteOrNull((points[index].close - low) / (high - low) * 100f)
            }
        }
    }
    val k = smoothedNullable(rsv, period = 3, seed = 50f)
    val d = smoothedNullable(k, period = 3, seed = 50f)
    val j = combine(k, d) { kv, dv -> 3f * kv - 2f * dv }
    return KdjResult(k, d, j)
}

internal fun stockKdj(points: List<OhlcPoint>, period: Int = 9): KdjResult {
    require(period > 0) { "period must be positive" }
    val k = MutableList<Float?>(points.size) { null }
    val d = MutableList<Float?>(points.size) { null }
    val j = MutableList<Float?>(points.size) { null }
    forEachValidOhlcRun(points) { start, run ->
        val result = kdj(run, period)
        result.k.forEachIndexed { offset, value -> k[start + offset] = value }
        result.d.forEachIndexed { offset, value -> d[start + offset] = value }
        result.j.forEachIndexed { offset, value -> j[start + offset] = value }
    }
    return KdjResult(k, d, j)
}

internal fun rsi(close: List<Float>, period: Int): List<Float?> {
    require(period > 0) { "period must be positive" }
    var averageGain: Float? = null
    var averageLoss: Float? = null
    return close.indices.map { index ->
        if (index < period) return@map null
        val change = close[index] - close[index - 1]
        if (!change.isFinite()) {
            averageGain = null
            averageLoss = null
            return@map null
        }
        if (averageGain == null || averageLoss == null) {
            var gainSum = 0.0
            var lossSum = 0.0
            for (changeIndex in index - period + 1..index) {
                val seedChange = close[changeIndex] - close[changeIndex - 1]
                if (!seedChange.isFinite()) return@map null
                gainSum += maxOf(seedChange, 0f)
                lossSum += maxOf(-seedChange, 0f)
            }
            averageGain = (gainSum / period).toFloat()
            averageLoss = (lossSum / period).toFloat()
        } else {
            averageGain = (averageGain!! * (period - 1) + maxOf(change, 0f)) / period
            averageLoss = (averageLoss!! * (period - 1) + maxOf(-change, 0f)) / period
        }
        rsiValue(averageGain!!, averageLoss!!)
    }
}

internal fun stockRsiLines(points: List<OhlcPoint>): List<StockLineResult> =
    stockLinesByValidRuns(points) { close ->
        listOf(6, 12, 24).map { period -> line("RSI$period", rsi(close, period)) }
    }

internal fun wr(points: List<OhlcPoint>, period: Int = 10): List<Float?> {
    require(period > 0) { "period must be positive" }
    return points.indices.map { index ->
        if (index + 1 < period) {
            null
        } else {
            var low = Float.POSITIVE_INFINITY
            var high = Float.NEGATIVE_INFINITY
            var valid = true
            for (windowIndex in index - period + 1..index) {
                val point = points[windowIndex]
                if (!point.hasValidOhlcSemantics()) {
                    valid = false
                    break
                }
                if (point.low < low) low = point.low
                if (point.high > high) high = point.high
            }
            if (!valid) {
                null
            } else {
                if (high == low) 0f else finiteOrNull((high - points[index].close) / (high - low) * 100f)
            }
        }
    }
}

/**
 * BBD (主力资金净流向) = 成交量 × (收盘价 - MA5) / MA5
 * 正值表示主力净流入，负值表示主力净流出。
 */
internal fun bbd(points: List<OhlcPoint>): List<Float?> {
    val ma5Values = ma(points.mapNotNull { it.takeIf { p -> p.hasValidOhlcSemantics() }?.close }, 5)
    val alignedMa5 = alignToNullableSource(
        points.map { if (it.hasValidOhlcSemantics()) it.close else null },
        ma5Values,
    )
    return points.indices.map { index ->
        val close = points[index].close
        val volume = points[index].volume
        val ma5 = alignedMa5.getOrNull(index)
        if (!close.isFinite() || volume == null || !volume.isFinite() || ma5 == null || ma5 == 0f) {
            null
        } else {
            finiteOrNull(volume * (close - ma5) / ma5)
        }
    }
}

private inline fun combine(
    first: List<Float?>,
    second: List<Float?>,
    operation: (Float, Float) -> Float,
): List<Float?> = first.zip(second).map { (a, b) ->
    if (a == null || b == null) null else finiteOrNull(operation(a, b))
}

private fun stockLinesByValidRuns(
    points: List<OhlcPoint>,
    calculate: (List<Float>) -> List<StockLineResult>,
): List<StockLineResult> {
    val template = calculate(emptyList())
    val aligned = template.map { MutableList<Float?>(points.size) { null } }
    forEachValidCloseRun(points) { start, close ->
        calculate(close).forEachIndexed { lineIndex, result ->
            result.values.forEachIndexed { offset, value -> aligned[lineIndex][start + offset] = value }
        }
    }
    return template.mapIndexed { index, result -> result.copy(values = aligned[index]) }
}

private fun closeValuesByValidRuns(
    points: List<OhlcPoint>,
    calculate: (List<Float>) -> List<Float?>,
): List<Float?> {
    val aligned = MutableList<Float?>(points.size) { null }
    forEachValidCloseRun(points) { start, close ->
        calculate(close).forEachIndexed { offset, value -> aligned[start + offset] = value }
    }
    return aligned
}

private inline fun forEachValidCloseRun(
    points: List<OhlcPoint>,
    action: (start: Int, close: List<Float>) -> Unit,
) = forEachValidOhlcRun(points) { start, run ->
    action(start, run.map { it.close })
}

private inline fun forEachValidOhlcRun(
    points: List<OhlcPoint>,
    action: (start: Int, run: List<OhlcPoint>) -> Unit,
) {
    var index = 0
    while (index < points.size) {
        while (index < points.size && !points[index].hasValidOhlcSemantics()) index++
        val start = index
        while (index < points.size && points[index].hasValidOhlcSemantics()) index++
        if (start < index) action(start, points.subList(start, index))
    }
}

private fun rsiValue(averageGain: Float, averageLoss: Float): Float? = finiteOrNull(when {
    averageLoss == 0f && averageGain == 0f -> 50f
    averageLoss == 0f -> 100f
    else -> 100f - 100f / (1f + averageGain / averageLoss)
})

private fun finiteOrNull(value: Float): Float? = value.takeIf {
    it.isFinite() && it <= Float.MAX_VALUE && it >= -Float.MAX_VALUE
}
