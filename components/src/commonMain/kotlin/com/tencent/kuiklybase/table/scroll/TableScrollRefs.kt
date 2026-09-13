package com.tencent.kuiklybase.table.scroll

import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.ScrollerView

internal enum class TableScrollDriver {
    HEADER,
    BODY,
}

internal class TableScrollRefs {
    var headerScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    var bodyScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    var bodyListRef: ViewRef<ListView<*, *>>? = null
    var headerScrollerOffsetX: Float = 0f
        private set
    var bodyScrollerOffsetX: Float = 0f
        private set
    var bodyListOffsetY: Float = 0f
        private set
    var didApplyInitialOffset: Boolean = false

    /**
     * Scroller currently owned by user gesture / fling.
     * Only this side may push [setContentOffset] to the peer; the peer's scroll
     * echoes must not write back, or the two scrollers fight and jitter.
     */
    var activeDriver: TableScrollDriver? = null
        private set

    fun beginDrive(driver: TableScrollDriver) {
        activeDriver = driver
    }

    fun endDrive(driver: TableScrollDriver) {
        if (activeDriver == driver) {
            activeDriver = null
        }
    }

    fun updateHeaderOffset(offsetX: Float) {
        headerScrollerOffsetX = offsetX
    }

    fun updateBodyOffset(offsetX: Float) {
        bodyScrollerOffsetX = offsetX
    }

    fun updateBodyListOffset(offsetY: Float) {
        bodyListOffsetY = offsetY
    }
}
