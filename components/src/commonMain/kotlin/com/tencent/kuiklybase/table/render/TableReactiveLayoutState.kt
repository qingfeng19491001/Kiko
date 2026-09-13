package com.tencent.kuiklybase.table.render

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * Reactive layout measurements owned by a single [TableRenderer.renderTable] tree.
 *
 * Created during page body build so [observable] can attach to the current ReactiveObserver.
 */
internal class TableReactiveLayoutState(scope: PagerScope) {
    var containerWidth by scope.observable(0f)

    fun updateContainerWidth(width: Float) {
        if (shouldUpdateContainerWidth(containerWidth, width)) {
            containerWidth = width
        }
    }

    fun viewportWidth(fallbackPageWidth: Float): Float {
        return resolveViewportWidth(containerWidth, fallbackPageWidth)
    }
}

internal fun shouldUpdateContainerWidth(current: Float, next: Float): Boolean =
    next > 0f && next != current

internal fun resolveViewportWidth(measured: Float, fallback: Float): Float =
    if (measured > 0f) measured else fallback
