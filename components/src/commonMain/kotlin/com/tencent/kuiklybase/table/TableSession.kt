package com.tencent.kuiklybase.table

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuiklybase.table.model.TableColumn
import com.tencent.kuiklybase.table.model.TableRow
import com.tencent.kuiklybase.table.render.TableRenderParams
import com.tencent.kuiklybase.table.render.TableRenderer
import com.tencent.kuiklybase.table.scroll.TableScrollRefs

/**
 * Adds a table using the full Config DSL.
 *
 * [columns] and [rows] must return Kuikly [ObservableList] instances so list
 * mutations can participate in reactive rendering.
 */
fun ViewContainer<*, *>.Table(
    columns: () -> ObservableList<TableColumn>,
    rows: () -> ObservableList<TableRow>,
    config: TableConfig.() -> Unit = {},
) {
    val cfg = TableConfig().apply(config)
    val params = TableRenderParams(
        columns = columns,
        rows = rows,
        config = cfg,
    )
    TableRenderer.run { renderTable(params, TableScrollRefs()) }
}
