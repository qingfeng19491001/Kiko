package com.tencent.kuiklybase.chart.model

data class ChartViewport(
    val xMin: Float,
    val xMax: Float,
    val yMin: Float,
    val yMax: Float,
) {
    companion object {
        fun fromSeries(
            series: List<ChartSeries>,
            isCategoryX: Boolean = false,
            paddingRatio: Float = 0.08f,
            stacked: Boolean = false,
            /** 水平条形图：类目用点的 x，数值用点的 y。 */
            horizontal: Boolean = false,
            /** 柱状图需要零轴；相对净值折线不应强行从 0 起，否则曲线被压到顶部。 */
            includeZero: Boolean = true,
        ): ChartViewport {
            if (series.isEmpty() || series.all { it.points.isEmpty() }) {
                return ChartViewport(0f, 1f, 0f, 1f)
            }
            var xMin = Float.POSITIVE_INFINITY
            var xMax = Float.NEGATIVE_INFINITY
            var yMin = Float.POSITIVE_INFINITY
            var yMax = Float.NEGATIVE_INFINITY

            if (stacked) {
                val categories = series.maxOf { it.points.size }
                for (cIdx in 0 until categories) {
                    var sum = 0f
                    var category = Float.NaN
                    series.forEach { s ->
                        val p = s.points.getOrNull(cIdx) ?: return@forEach
                        sum += p.y
                        category = p.x
                    }
                    if (!category.isFinite()) continue
                    if (horizontal) {
                        yMin = minOf(yMin, category)
                        yMax = maxOf(yMax, category)
                        xMin = minOf(xMin, 0f)
                        xMax = maxOf(xMax, sum)
                    } else {
                        xMin = minOf(xMin, category)
                        xMax = maxOf(xMax, category)
                        yMin = minOf(yMin, 0f)
                        yMax = maxOf(yMax, sum)
                    }
                }
            } else {
                series.forEach { s ->
                    s.points.forEach { p ->
                        if (!p.x.isFinite() || !p.y.isFinite()) return@forEach
                        if (horizontal) {
                            yMin = minOf(yMin, p.x)
                            yMax = maxOf(yMax, p.x)
                            xMin = minOf(xMin, 0f, p.y)
                            xMax = maxOf(xMax, p.y)
                        } else {
                            xMin = minOf(xMin, p.x)
                            xMax = maxOf(xMax, p.x)
                            yMin = minOf(yMin, p.y)
                            yMax = maxOf(yMax, p.y)
                        }
                    }
                }
            }

            if (!xMin.isFinite() || !xMax.isFinite() || !yMin.isFinite() || !yMax.isFinite()) {
                return ChartViewport(0f, 1f, 0f, 1f)
            }

            if (horizontal || isCategoryX) {
                if (horizontal) {
                    yMin -= 0.5f
                    yMax += 0.5f
                } else {
                    xMin -= 0.5f
                    xMax += 0.5f
                }
            } else if (xMin == xMax) {
                xMin -= 0.5f
                xMax += 0.5f
            }
            if (yMin == yMax) {
                yMin -= 1f
                yMax += 1f
            }
            val xPad = (xMax - xMin) * paddingRatio
            val yPad = (yMax - yMin) * paddingRatio
            val paddedYMin = yMin - yPad
            val paddedYMax = yMax + yPad
            return ChartViewport(
                xMin = xMin - xPad,
                xMax = xMax + xPad,
                yMin = when {
                    horizontal -> paddedYMin
                    includeZero -> minOf(0f, paddedYMin)
                    else -> paddedYMin
                },
                yMax = when {
                    horizontal -> paddedYMax
                    includeZero -> maxOf(0f, paddedYMax)
                    else -> paddedYMax
                },
            )
        }

        fun fromOhlc(
            points: List<OhlcPoint>,
            paddingRatio: Float = 0.08f,
        ): ChartViewport {
            if (points.isEmpty()) return ChartViewport(0f, 1f, 0f, 1f)
            var xMin = Float.POSITIVE_INFINITY
            var xMax = Float.NEGATIVE_INFINITY
            var yMin = Float.POSITIVE_INFINITY
            var yMax = Float.NEGATIVE_INFINITY
            points.forEach { p ->
                if (!p.hasValidOhlcSemantics()) return@forEach
                xMin = minOf(xMin, p.x)
                xMax = maxOf(xMax, p.x)
                yMin = minOf(yMin, p.low, p.open, p.close)
                yMax = maxOf(yMax, p.high, p.open, p.close)
            }
            if (!xMin.isFinite() || !xMax.isFinite() || !yMin.isFinite() || !yMax.isFinite()) {
                return ChartViewport(0f, 1f, 0f, 1f)
            }
            xMin -= 0.5f
            xMax += 0.5f
            if (yMin == yMax) {
                yMin -= 1f
                yMax += 1f
            }
            val xPad = (xMax - xMin) * paddingRatio
            val yPad = (yMax - yMin) * paddingRatio
            return ChartViewport(
                xMin = xMin - xPad,
                xMax = xMax + xPad,
                yMin = yMin - yPad,
                yMax = yMax + yPad,
            )
        }
    }

    /** 在全量范围内裁出一段初始可见 X 窗口，使开局即可单指平移。 */
    fun focusedXWindow(
        ratio: Float,
        anchor: com.tencent.kuiklybase.chart.config.VisibleAnchor =
            com.tencent.kuiklybase.chart.config.VisibleAnchor.END,
    ): ChartViewport {
        val r = ratio.coerceIn(0.15f, 1f)
        if (r >= 0.999f) return this
        val fullSpan = (xMax - xMin).coerceAtLeast(1e-6f)
        val span = fullSpan * r
        val start = when (anchor) {
            com.tencent.kuiklybase.chart.config.VisibleAnchor.START -> xMin
            com.tencent.kuiklybase.chart.config.VisibleAnchor.CENTER ->
                ((xMin + xMax) / 2f - span / 2f).coerceIn(xMin, xMax - span)
            com.tencent.kuiklybase.chart.config.VisibleAnchor.END -> xMax - span
        }
        return copy(xMin = start, xMax = start + span)
    }
}
