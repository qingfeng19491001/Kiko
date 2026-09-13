package com.tencent.kuiklybase.chart.core.polar

import com.tencent.kuiklybase.chart.model.ChartSlice
import com.tencent.kuiklybase.chart.model.RadarDimension
import com.tencent.kuiklybase.chart.model.RadarSeries
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin

internal data class PolarLayout(
    val centerX: Float,
    val centerY: Float,
    val outerRadius: Float,
    val innerRadius: Float,
)

internal object PolarLayoutEngine {
    fun compute(
        width: Float,
        height: Float,
        innerRadiusRatio: Float = 0f,
        ringWidth: Float = 0f,
        isDonut: Boolean = false,
    ): PolarLayout {
        val size = minOf(width, height)
        val outer = size * 0.38f
        val inner = when {
            isDonut && ringWidth > 0f -> (outer - ringWidth).coerceAtLeast(0f)
            innerRadiusRatio > 0f -> outer * innerRadiusRatio
            else -> 0f
        }
        return PolarLayout(
            centerX = width / 2f,
            centerY = height / 2f,
            outerRadius = outer,
            innerRadius = inner,
        )
    }
}

internal object PolarHitTester {
    fun hitSlice(
        layout: PolarLayout,
        slices: List<ChartSlice>,
        startAngleDeg: Float,
        x: Float,
        y: Float,
    ): Int? {
        val dx = x - layout.centerX
        val dy = y - layout.centerY
        val dist = hypot(dx, dy)
        if (dist < layout.innerRadius || dist > layout.outerRadius + 8f) return null
        var angle = atan2(dy, dx)
        val start = startAngleDeg * PI.toFloat() / 180f
        var relative = angle - start
        while (relative < 0f) relative += (2 * PI).toFloat()
        while (relative >= (2 * PI).toFloat()) relative -= (2 * PI).toFloat()
        val total = slices.sumOf { it.value.coerceAtLeast(0f).toDouble() }
            .toFloat()
            .coerceAtLeast(1e-6f)
        var acc = 0f
        slices.forEachIndexed { idx, slice ->
            val sweep = slice.value.coerceAtLeast(0f) / total * (2 * PI).toFloat()
            if (relative >= acc && relative < acc + sweep) return idx
            acc += sweep
        }
        return null
    }
}

internal object PolarScale {
    fun hitRadarDimension(
        centerX: Float,
        centerY: Float,
        radius: Float,
        dimensionCount: Int,
        x: Float,
        y: Float,
        threshold: Float = 20f,
    ): Int? {
        if (dimensionCount <= 0) return null
        val angleStep = (2 * PI).toFloat() / dimensionCount
        val startAngle = -PI.toFloat() / 2f
        var bestIdx: Int? = null
        var bestDist = threshold
        for (i in 0 until dimensionCount) {
            val angle = startAngle + angleStep * i
            val vx = centerX + radius * kotlin.math.cos(angle)
            val vy = centerY + radius * kotlin.math.sin(angle)
            val dist = hypot(vx - x, vy - y)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    fun hitRadarPoint(
        centerX: Float,
        centerY: Float,
        radius: Float,
        dimensions: List<RadarDimension>,
        series: List<RadarSeries>,
        x: Float,
        y: Float,
        threshold: Float = 20f,
    ): Pair<Int, Int>? {
        if (dimensions.isEmpty() || series.isEmpty()) return null
        val angleStep = (2 * PI).toFloat() / dimensions.size
        var bestHit: Pair<Int, Int>? = null
        var bestDistance = threshold
        series.forEachIndexed { index, radarSeries ->
            dimensions.forEachIndexed { dimensionIndex, dimension ->
                val ratio = radarValueRatio(
                    radarSeries.values.getOrNull(dimensionIndex) ?: 0f,
                    dimension.maxValue,
                )
                val angle = -PI.toFloat() / 2f + angleStep * dimensionIndex
                val pointX = centerX + radius * ratio * cos(angle)
                val pointY = centerY + radius * ratio * sin(angle)
                val distance = hypot(pointX - x, pointY - y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestHit = index to dimensionIndex
                }
            }
        }
        return bestHit
    }

    /** Safe radar radius ratio: non-positive max or non-finite value maps to 0. */
    fun radarValueRatio(value: Float, maxValue: Float): Float {
        return if (maxValue > 0f && value.isFinite()) {
            (value / maxValue).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}
