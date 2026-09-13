package com.tencent.kuiklybase.table.slot

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuiklybase.table.model.TableCellContext
import com.tencent.kuiklybase.table.model.TableEmptyContext

typealias TableHeaderCellSlot = (
    container: ViewContainer<*, *>,
    context: TableCellContext,
) -> Unit

typealias TableBodyCellSlot = (
    container: ViewContainer<*, *>,
    context: TableCellContext,
) -> Unit

typealias TableEmptySlot = (
    container: ViewContainer<*, *>,
    context: TableEmptyContext,
) -> Unit

/** Optional render slots for table content. */
class TableSlots {
    var headerCell: TableHeaderCellSlot? = null
    var bodyCell: TableBodyCellSlot? = null
    var emptyContent: TableEmptySlot? = null
    private val headerCellByKey = mutableMapOf<String, TableHeaderCellSlot>()
    private val bodyCellByKey = mutableMapOf<String, TableBodyCellSlot>()

    fun headerCell(columnKey: String, renderer: TableHeaderCellSlot) {
        headerCellByKey[columnKey] = renderer
    }

    fun bodyCell(columnKey: String, renderer: TableBodyCellSlot) {
        bodyCellByKey[columnKey] = renderer
    }

    internal fun headerRenderer(columnKey: String): TableHeaderCellSlot? = headerCellByKey[columnKey]
    internal fun bodyRenderer(columnKey: String): TableBodyCellSlot? = bodyCellByKey[columnKey]
}
