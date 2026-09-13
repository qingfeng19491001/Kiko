package com.tencent.kuiklybase.table.model

import com.tencent.kuiklybase.table.theme.TableTheme

/** Immutable data exposed to header and body cell slots. */
data class TableCellContext(
    val row: TableRow?,
    val rowIndex: Int,
    val column: TableColumn,
    val columnIndex: Int,
    val value: Any?,
    val theme: TableTheme,
    val isHeader: Boolean,
    val isStripeRow: Boolean,
)
