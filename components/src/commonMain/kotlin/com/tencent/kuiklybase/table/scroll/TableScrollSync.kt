package com.tencent.kuiklybase.table.scroll

import kotlin.math.abs

/**
 * One-way header↔body horizontal sync.
 *
 * Only the [TableScrollRefs.activeDriver] may push offset to the peer.
 * Slave scroll echoes (from programmatic [setContentOffset]) are ignored for
 * write-back, which breaks the classic dual-scroller feedback loop / jitter.
 * When no driver is set (e.g. programmatic restore), sync is still allowed but
 * skips redundant [setContentOffset] within [OFFSET_EPSILON].
 */
internal object TableScrollSync {
    private const val OFFSET_EPSILON = 0.5f

    fun syncHeaderToBody(refs: TableScrollRefs, offsetX: Float) {
        refs.updateHeaderOffset(offsetX)
        if (refs.activeDriver == TableScrollDriver.BODY) return
        applyBodyOffset(refs, offsetX)
    }

    fun syncBodyToHeader(refs: TableScrollRefs, offsetX: Float) {
        refs.updateBodyOffset(offsetX)
        if (refs.activeDriver == TableScrollDriver.HEADER) return
        applyHeaderOffset(refs, offsetX)
    }

    /**
     * After body content remounts (row reload / lazy path churn), push the
     * preserved header offset onto the body scroller so columns stay aligned.
     */
    fun realignBodyToHeader(refs: TableScrollRefs) {
        if (refs.activeDriver != null) return
        applyBodyOffset(refs, refs.headerScrollerOffsetX)
    }

    /**
     * Apply host-supplied offsets after a fresh Table mount (e.g. theme remount).
     * Retries until header/body (and list when needed) refs are available.
     */
    fun applyInitialOffsets(refs: TableScrollRefs, offsetX: Float, offsetY: Float) {
        if (refs.didApplyInitialOffset) return
        if (offsetX <= OFFSET_EPSILON && offsetY <= OFFSET_EPSILON) {
            refs.didApplyInitialOffset = true
            return
        }

        if (offsetX > OFFSET_EPSILON) {
            refs.updateHeaderOffset(offsetX)
            refs.updateBodyOffset(offsetX)
            refs.headerScrollerRef?.view?.setContentOffset(offsetX, 0f, animated = false)
            refs.bodyScrollerRef?.view?.setContentOffset(offsetX, 0f, animated = false)
        }
        if (offsetY > OFFSET_EPSILON) {
            refs.updateBodyListOffset(offsetY)
            refs.bodyListRef?.view?.setContentOffset(0f, offsetY, animated = false)
        }

        val headerReady = refs.headerScrollerRef?.view != null
        val bodyReady = refs.bodyScrollerRef?.view != null
        val listReady = offsetY <= OFFSET_EPSILON || refs.bodyListRef?.view != null
        if (headerReady && bodyReady && listReady) {
            refs.didApplyInitialOffset = true
        }
    }

    private fun applyBodyOffset(refs: TableScrollRefs, offsetX: Float) {
        if (abs(refs.bodyScrollerOffsetX - offsetX) < OFFSET_EPSILON) {
            refs.updateBodyOffset(offsetX)
            return
        }
        refs.updateBodyOffset(offsetX)
        refs.bodyScrollerRef?.view?.setContentOffset(offsetX, 0f, animated = false)
    }

    private fun applyHeaderOffset(refs: TableScrollRefs, offsetX: Float) {
        if (abs(refs.headerScrollerOffsetX - offsetX) < OFFSET_EPSILON) {
            refs.updateHeaderOffset(offsetX)
            return
        }
        refs.updateHeaderOffset(offsetX)
        refs.headerScrollerRef?.view?.setContentOffset(offsetX, 0f, animated = false)
    }
}
