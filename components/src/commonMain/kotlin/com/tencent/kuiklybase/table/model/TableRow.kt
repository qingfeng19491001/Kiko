package com.tencent.kuiklybase.table.model

/** A row whose [cells] are addressed by [TableColumn.key]. */
data class TableRow(
    val id: String = "",
    val cells: Map<String, Any?> = emptyMap(),
) {
    operator fun get(key: String): Any? = cells[key]
}
