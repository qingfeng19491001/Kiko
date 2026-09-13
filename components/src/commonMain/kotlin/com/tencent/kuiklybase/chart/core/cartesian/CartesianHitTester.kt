package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuiklybase.chart.model.ChartSeries
import kotlin.math.abs
import kotlin.math.hypot

internal object CartesianHitTester {
    data class HitResult(val seriesIndex: Int, val pointIndex: Int)

    fun nearestPoint(
        series: List<ChartSeries>,
        scale: CartesianScale,
        x: Float,
        y: Float,
        threshold: Float = 16f,
    ): HitResult? {
        var best: HitResult? = null
        var bestDist = threshold
        series.forEachIndexed { sIdx, s ->
            s.points.forEachIndexed { pIdx, p ->
                val px = scale.toPixelX(p.x)
                val py = scale.toPixelY(p.y)
                val dist = hypot(px - x, py - y)
                if (dist <= bestDist) {
                    bestDist = dist
                    best = HitResult(sIdx, pIdx)
                }
            }
        }
        return best
    }

    /** 兼容旧调用：按类目命中，默认首系列。 */
    fun nearestCategory(
        series: List<ChartSeries>,
        scale: CartesianScale,
        x: Float,
        threshold: Float = 24f,
    ): HitResult? {
        if (series.isEmpty()) return null
        val points = series.first().points
        var best: HitResult? = null
        var bestDist = threshold
        points.forEachIndexed { pIdx, p ->
            val px = scale.toPixelX(p.x)
            val dist = abs(px - x)
            if (dist <= bestDist) {
                bestDist = dist
                best = HitResult(0, pIdx)
            }
        }
        return best
    }

    /** 分组柱：按类目 + 纵向距离选择最近柱。 */
    fun nearestBar(
        series: List<ChartSeries>,
        scale: CartesianScale,
        x: Float,
        y: Float,
        threshold: Float = 28f,
        stacked: Boolean = false,
        grouped: Boolean = true,
    ): HitResult? {
        if (series.isEmpty()) return null
        var best: HitResult? = null
        var bestScore = Float.POSITIVE_INFINITY
        val stackBases = mutableMapOf<Int, Float>()
        val categories = series.first().points.size.coerceAtLeast(1)
        val groupWidth = scale.plot.width / categories
        val barWidth = groupWidth * 0.7f / series.size.coerceAtLeast(1)
        series.forEachIndexed { sIdx, s ->
            s.points.forEachIndexed { pIdx, p ->
                val px = scale.toPixelX(p.x)
                val base = if (stacked) stackBases[pIdx] ?: 0f else 0f
                val end = base + p.y
                if (stacked) stackBases[pIdx] = end
                val top = scale.toPixelY(end)
                val bottom = scale.toPixelY(base)
                val barLeft = if (stacked) {
                    px - groupWidth * 0.3f
                } else if (grouped) {
                    px - groupWidth * 0.35f + barWidth * sIdx + groupWidth * 0.15f / series.size
                } else {
                    px - barWidth / 2f
                }
                val barRight = if (stacked) px + groupWidth * 0.3f else barLeft + if (grouped) barWidth else barWidth
                val dx = when {
                    x in barLeft..barRight -> 0f
                    else -> minOf(abs(x - barLeft), abs(x - barRight))
                }
                if (dx > threshold) return@forEachIndexed
                if (y !in minOf(top, bottom)..maxOf(top, bottom)) return@forEachIndexed
                val score = dx
                if (score < bestScore) {
                    bestScore = score
                    best = HitResult(sIdx, pIdx)
                }
            }
        }
        return best
    }

    /** 水平条：类目在 Y（点的 x），数值在 X（点的 y）。 */
    fun nearestHorizontalBar(
        series: List<ChartSeries>,
        scale: CartesianScale,
        x: Float,
        y: Float,
        threshold: Float = 28f,
        stacked: Boolean = false,
    ): HitResult? {
        if (series.isEmpty()) return null
        var best: HitResult? = null
        var bestScore = Float.POSITIVE_INFINITY
        val stackBases = mutableMapOf<Int, Float>()
        series.forEachIndexed { sIdx, s ->
            s.points.forEachIndexed { pIdx, p ->
                val py = scale.toPixelY(p.x)
                val base = if (stacked) stackBases[pIdx] ?: 0f else 0f
                val end = base + p.y
                if (stacked) stackBases[pIdx] = end
                val left = scale.toPixelX(base)
                val right = scale.toPixelX(end)
                val dy = abs(py - y)
                if (dy > threshold) return@forEachIndexed
                if (x !in minOf(left, right)..maxOf(left, right)) return@forEachIndexed
                val score = dy
                if (score < bestScore) {
                    bestScore = score
                    best = HitResult(sIdx, pIdx)
                }
            }
        }
        return best
    }
}
