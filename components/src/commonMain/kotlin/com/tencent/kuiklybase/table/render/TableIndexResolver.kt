package com.tencent.kuiklybase.table.render

import com.tencent.kuiklybase.table.model.TableColumn
import com.tencent.kuiklybase.table.model.TableRow

/** Resolves current positions for items reused by Kuikly loop directives. */
internal object TableIndexResolver {
    fun rowIndex(rows: List<TableRow>, row: TableRow): Int {
        val byIdentity = rows.indexOfFirst { it === row }
        if (byIdentity >= 0) {
            return byIdentity
        }
        if (row.id.isNotEmpty()) {
            return rows.indexOfFirst { it.id == row.id }
        }
        return -1
    }

    fun columnIndex(columns: List<TableColumn>, column: TableColumn): Int {
        val byIdentity = columns.indexOfFirst { it === column }
        if (byIdentity >= 0) {
            return byIdentity
        }
        if (column.key.isNotEmpty()) {
            return columns.indexOfFirst { it.key == column.key }
        }
        return -1
    }
}
