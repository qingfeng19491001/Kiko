package com.tencent.kuiklybase.chart.core.cartesian

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

internal enum class AxisTickDomain {
    NUMERIC,
    TIME,
    CATEGORY_INDEX,
}

internal data class AxisTickCandidate(
    val value: Float,
    val text: String,
    val measuredWidth: Float,
)

/**
 * Deterministic inputs for axis tick planning.
 *
 * [fontSize] identifies the validated font configuration used when the caller measured the
 * candidate labels. The planner only requires it to be finite and positive; it never estimates
 * label widths from it. [AxisTickCandidate.measuredWidth] remains authoritative for layout.
 */
internal data class AxisTickPlanInput(
    val visibleMin: Float,
    val visibleMax: Float,
    val plotLeft: Float,
    val plotWidth: Float,
    val fontSize: Float,
    val minimumGap: Float = 6f,
    val edgePadding: Float = 2f,
    val minimumTargetCount: Int = 2,
    val domain: AxisTickDomain,
    val candidates: List<AxisTickCandidate>,
    val allowedSteps: List<Float> = emptyList(),
    val fallbackText: String? = null,
    val fallbackMeasuredWidth: Float = 0f,
)

internal data class PlannedAxisTick(
    val value: Float,
    val text: String,
    val x: Float,
    val left: Float,
    val right: Float,
)

internal object AxisTickPlanner {
    private val timeSteps = listOf(1f, 5f, 15f, 30f, 60f, 1440f, 7200f, 43200f, 525600f)

    fun plan(input: AxisTickPlanInput): List<PlannedAxisTick> {
        if (!isValidInput(input)) return emptyList()
        val visibleMin = minOf(input.visibleMin, input.visibleMax)
        val visibleMax = maxOf(input.visibleMin, input.visibleMax)
        val visible = input.candidates
            .asSequence()
            .filter {
                it.value.isFinite() &&
                    it.measuredWidth.isFinite() &&
                    it.measuredWidth > 0f &&
                    it.value in visibleMin..visibleMax
            }
            .distinctBy { it.value }
            .sortedBy { it.value }
            .toList()
        if (visible.isEmpty()) return emptyList()

        val range = visibleMax.toDouble() - visibleMin.toDouble()
        if (!range.isFinite()) return emptyList()
        if (range <= 0.0) {
            val fullLabelFallback = centeredFallback(input, visible.first())
            return if (fullLabelFallback != null) {
                listOf(fullLabelFallback)
            } else {
                listOfNotNull(compactFallback(input, visible, visibleMin, visibleMax))
            }
        }

        val maximumWidth = visible.maxOf { max(0f, it.measuredWidth) }
        val slotWidth = max(1f, maximumWidth + max(0f, input.minimumGap))
        val capacity = floor(max(0f, input.plotWidth) / slotWidth).toInt().coerceAtLeast(1)
        val targetCount = max(input.minimumTargetCount.coerceAtLeast(1), capacity)
            .coerceAtMost(visible.size)
        val estimatedStep = (range / max(1, targetCount - 1))
            .coerceAtMost(Float.MAX_VALUE.toDouble())
            .toFloat()
        val step = selectNiceStep(estimatedStep, input.domain, input.allowedSteps)
        val stepped = selectByStep(visible, step)
        var planned = collisionFilter(input, visibleMin, visibleMax, stepped, input.edgePadding)
        val desiredCount = input.minimumTargetCount.coerceAtLeast(1).coerceAtMost(visible.size)
        if (planned.size < desiredCount && stepped.size < visible.size) {
            val dense = collisionFilter(input, visibleMin, visibleMax, visible, input.edgePadding)
            if (dense.size > planned.size) planned = dense
        }

        return if (planned.isNotEmpty()) {
            planned
        } else {
            val padding = max(0f, input.edgePadding).toDouble()
            val availableWidth = input.plotWidth.toDouble() - padding * 2.0
            val fittingFallbacks = visible.filter { candidate ->
                candidate.measuredWidth.toDouble() <= availableWidth
            }
            val fullLabelFallback = fittingFallbacks
                .takeIf { it.isNotEmpty() }
                ?.let { centeredFallback(input, nearestToCenter(it, visibleMin, visibleMax)) }
            if (fullLabelFallback != null) {
                listOf(fullLabelFallback)
            } else {
                listOfNotNull(compactFallback(input, visible, visibleMin, visibleMax))
            }
        }
    }

    private fun isValidInput(input: AxisTickPlanInput): Boolean =
        input.visibleMin.isFinite() &&
            input.visibleMax.isFinite() &&
            input.plotLeft.isFinite() &&
            input.plotWidth.isFinite() &&
            input.plotWidth > 0f &&
            (input.plotLeft + input.plotWidth).isFinite() &&
            input.fontSize.isFinite() &&
            input.fontSize > 0f &&
            input.minimumGap.isFinite() &&
            input.edgePadding.isFinite()

    fun selectNiceStep(
        estimatedStep: Float,
        domain: AxisTickDomain,
        allowedSteps: List<Float> = emptyList(),
    ): Float {
        val validCustomSteps = allowedSteps.filter { it.isFinite() && it > 0f }.distinct().sorted()
        if (validCustomSteps.isNotEmpty()) {
            return validCustomSteps.firstOrNull { it >= estimatedStep } ?: validCustomSteps.last()
        }
        if (domain == AxisTickDomain.TIME) {
            return timeSteps.firstOrNull { it >= estimatedStep } ?: timeSteps.last()
        }
        return numericNiceStep(estimatedStep.toDouble())
            .coerceAtMost(Float.MAX_VALUE.toDouble())
            .toFloat()
    }

    private fun selectByStep(candidates: List<AxisTickCandidate>, step: Float): List<AxisTickCandidate> {
        if (!step.isFinite() || step <= 0f) return candidates
        val tolerance = max(1e-4f, step * 1e-4f)
        val selected = mutableListOf<AxisTickCandidate>()
        candidates.forEach { candidate ->
            if (selected.isEmpty() || candidate.value - selected.last().value + tolerance >= step) {
                selected += candidate
            }
        }
        return selected
    }

    private fun collisionFilter(
        input: AxisTickPlanInput,
        visibleMin: Float,
        visibleMax: Float,
        candidates: List<AxisTickCandidate>,
        edgePadding: Float,
    ): List<PlannedAxisTick> {
        val plotRight = input.plotLeft + max(0f, input.plotWidth)
        val innerLeft = input.plotLeft + max(0f, edgePadding)
        val innerRight = plotRight - max(0f, edgePadding)
        val minimumGap = max(0f, input.minimumGap)
        val result = mutableListOf<PlannedAxisTick>()
        val fitting = candidates
            .mapNotNull { candidate -> plannedTick(input, visibleMin, visibleMax, candidate) }
            .filter { tick -> tick.left >= innerLeft && tick.right <= innerRight }
            .sortedWith(
                compareBy<PlannedAxisTick> { it.right }
                    .thenBy { it.value }
                    .thenBy { it.text },
            )

        fitting.forEach { tick ->
            if (result.isEmpty() || result.last().right + minimumGap <= tick.left) {
                result += tick
            }
        }
        return result.sortedWith(
            compareBy<PlannedAxisTick> { it.x }
                .thenBy { it.value }
                .thenBy { it.text },
        )
    }

    private fun plannedTick(
        input: AxisTickPlanInput,
        visibleMin: Float,
        visibleMax: Float,
        candidate: AxisTickCandidate,
    ): PlannedAxisTick? {
        val span = visibleMax.toDouble() - visibleMin.toDouble()
        if (!span.isFinite() || span <= 0.0) return null
        val ratio = (candidate.value.toDouble() - visibleMin.toDouble()) / span
        val x = (input.plotLeft.toDouble() + ratio * max(0f, input.plotWidth).toDouble()).toFloat()
        val halfWidth = max(0f, candidate.measuredWidth) / 2f
        val left = x - halfWidth
        val right = x + halfWidth
        if (!x.isFinite() || !left.isFinite() || !right.isFinite()) return null
        return PlannedAxisTick(candidate.value, candidate.text, x, left, right)
    }

    private fun centeredFallback(input: AxisTickPlanInput, candidate: AxisTickCandidate): PlannedAxisTick? {
        val padding = max(0f, input.edgePadding)
        val availableWidth = input.plotWidth - padding * 2f
        if (!availableWidth.isFinite() || availableWidth <= 0f || candidate.measuredWidth > availableWidth) {
            return null
        }
        val x = input.plotLeft + max(0f, input.plotWidth) / 2f
        val halfWidth = max(0f, candidate.measuredWidth) / 2f
        val left = x - halfWidth
        val right = x + halfWidth
        if (!x.isFinite() || !left.isFinite() || !right.isFinite()) return null
        return PlannedAxisTick(candidate.value, candidate.text, x, left, right)
    }

    private fun compactFallback(
        input: AxisTickPlanInput,
        candidates: List<AxisTickCandidate>,
        visibleMin: Float,
        visibleMax: Float,
    ): PlannedAxisTick? {
        val text = input.fallbackText?.takeIf { it.isNotEmpty() } ?: return null
        val width = input.fallbackMeasuredWidth
        if (!width.isFinite() || width <= 0f) return null
        val source = nearestToCenter(candidates, visibleMin, visibleMax)
        return centeredFallback(input, AxisTickCandidate(source.value, text, width))
    }

    private fun nearestToCenter(
        candidates: List<AxisTickCandidate>,
        visibleMin: Float,
        visibleMax: Float,
    ): AxisTickCandidate {
        val center = visibleMin.toDouble() + (visibleMax.toDouble() - visibleMin.toDouble()) / 2.0
        return candidates.minWith(
            compareBy<AxisTickCandidate> { abs(it.value.toDouble() - center) }.thenBy { it.value },
        )
    }
}

internal fun numericNiceStep(estimatedStep: Double): Double {
    if (!estimatedStep.isFinite() || estimatedStep <= 0.0) return 1.0
    val exponent = floor(kotlin.math.log10(estimatedStep)).toInt()
    val baseSteps = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0)
    return ((exponent - 1)..(exponent + 1))
        .asSequence()
        .flatMap { power ->
            val scale = 10.0.pow(power.toDouble())
            baseSteps.asSequence().map { it * scale }
        }
        .filter { it.isFinite() && it > 0.0 }
        .distinct()
        .sorted()
        .firstOrNull { it >= estimatedStep }
        ?: 10.0.pow((exponent + 2).toDouble())
}
