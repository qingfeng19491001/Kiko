package com.tencent.kuiklybase.table.model

import com.tencent.kuikly.core.layout.FlexAlign

/**
 * Defines one table column.
 *
 * [width] is clamped to the normalized [minWidth] and [maxWidth] range.
 */
data class TableColumn(
    val key: String,
    val title: String,
    val width: Float = 120f,
    val minWidth: Float = 72f,
    val maxWidth: Float = Float.MAX_VALUE,
    val align: FlexAlign = FlexAlign.FLEX_START,
    val headerAlign: FlexAlign = align,
    val cellPaddingH: Float? = null,
    val cellPaddingV: Float? = null,
    val cellBackgroundColor: Long? = null,
    val headerBackgroundColor: Long? = null,
)
