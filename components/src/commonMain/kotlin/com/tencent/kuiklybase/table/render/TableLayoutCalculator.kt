package com.tencent.kuiklybase.table.render

import com.tencent.kuiklybase.table.model.TableColumn

/** Pure column-width helpers shared by header and body rendering. */
internal object TableLayoutCalculator {
    fun columnWidth(column: TableColumn): Float {
        val minWidth = column.minWidth.coerceAtLeast(0f)
        val maxWidth = column.maxWidth.coerceAtLeast(minWidth)
        val preferredWidth = when {
            column.width > 0f -> column.width
            else -> minWidth
        }
        return preferredWidth.coerceIn(minWidth, maxWidth)
    }

    fun totalWidth(columns: List<TableColumn>): Float =
        columns.sumOf { columnWidth(it).toDouble() }.toFloat()

    fun needsHorizontalScroll(totalWidth: Float, viewportWidth: Float): Boolean =
        totalWidth > viewportWidth
}
