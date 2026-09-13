package com.tencent.kuiklybase.table.render

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.event.layoutFrameDidChange
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.*
import com.tencent.kuiklybase.table.TableConfig
import com.tencent.kuiklybase.table.model.TableCellContext
import com.tencent.kuiklybase.table.model.TableColumn
import com.tencent.kuiklybase.table.model.TableEmptyContext
import com.tencent.kuiklybase.table.model.TableRow
import com.tencent.kuiklybase.table.scroll.TableScrollDriver
import com.tencent.kuiklybase.table.scroll.TableScrollRefs
import com.tencent.kuiklybase.table.scroll.TableScrollSync
import com.tencent.kuiklybase.table.theme.TableTheme

internal data class TableRenderParams(
    val columns: () -> ObservableList<TableColumn>,
    val rows: () -> ObservableList<TableRow>,
    val config: TableConfig,
) {
    val theme: TableTheme get() = config.theme.resolved()
}

/** Shared render kernel for the table DSL. */
internal object TableRenderer {
    fun ViewContainer<*, *>.renderTable(params: TableRenderParams, refs: TableScrollRefs) {
        val theme = params.theme
        val layoutState = TableReactiveLayoutState(this)

        View {
            attr {
                flex(1f)
                flexDirection(FlexDirection.COLUMN)
                backgroundColor(Color(theme.cellBackgroundColor))
                borderRadius(theme.borderRadius)
                if (theme.borderRadius > 0f) {
                    overflow(true)
                }
                if (params.config.showOuterBorder) {
                    border(Border(theme.dividerWidth, BorderStyle.SOLID, Color(theme.borderColor)))
                } else {
                    border(Border(0f, BorderStyle.SOLID, Color(0x00000000)))
                }
            }
            event {
                layoutFrameDidChange { frame ->
                    layoutState.updateContainerWidth(frame.width)
                }
            }

            vif({ params.config.showHeader }) {
                renderHeaderArea(params, refs, layoutState, theme)
                vif({ params.config.showHeaderBorder }) {
                    renderDividerLine(theme.headerBorderColor, theme.dividerWidth)
                }
            }

            vif({ params.rows().isEmpty() }) {
                renderEmptyArea(params, theme)
            }
            velse {
                renderBodyArea(params, refs, layoutState, theme)
            }
        }
    }

    private fun ViewContainer<*, *>.renderHeaderArea(
        params: TableRenderParams,
        refs: TableScrollRefs,
        layoutState: TableReactiveLayoutState,
        theme: TableTheme,
    ) {
        Scroller {
            ref { refs.headerScrollerRef = it }
            attr {
                flexDirection(FlexDirection.ROW)
                height(theme.headerHeight)
                backgroundColor(Color(theme.headerBackgroundColor))
                showScrollerIndicator(false)
                bouncesEnable(false)
                val totalWidth = TableLayoutCalculator.totalWidth(params.columns())
                val viewport = layoutState.viewportWidth(getPager().pageData.pageViewWidth)
                scrollEnable(TableLayoutCalculator.needsHorizontalScroll(totalWidth, viewport))
                nestedScroll(KRNestedScrollMode.SELF_ONLY, KRNestedScrollMode.PARENT_FIRST)
            }
            event {
                dragBegin {
                    refs.beginDrive(TableScrollDriver.HEADER)
                }
                scroll {
                    refs.updateHeaderOffset(it.offsetX)
                    if (params.config.scrollSyncEnabled) {
                        TableScrollSync.syncHeaderToBody(refs, it.offsetX)
                    }
                    params.config.onScroll?.invoke(it.offsetX, refs.bodyListOffsetY)
                }
                scrollEnd {
                    refs.endDrive(TableScrollDriver.HEADER)
                }
                layoutFrameDidChange {
                    TableScrollSync.applyInitialOffsets(
                        refs,
                        params.config.initialContentOffsetX,
                        params.config.initialContentOffsetY,
                    )
                }
            }
            View {
                attr {
                    flexDirection(FlexDirection.ROW)
                    width(TableLayoutCalculator.totalWidth(params.columns()))
                    height(theme.headerHeight)
                }
                vforIndex({ params.columns() }) { column, _, _ ->
                    View {
                        attr {
                            width(TableLayoutCalculator.columnWidth(column))
                            height(theme.headerHeight)
                        }
                        vbind({ TableIndexResolver.columnIndex(params.columns(), column) }) {
                            val columnIndex = TableIndexResolver.columnIndex(params.columns(), column)
                                .coerceAtLeast(0)
                            renderHeaderCell(params, column, columnIndex, theme)
                        }
                    }
                }
            }
        }
    }

    private fun ViewContainer<*, *>.renderBodyArea(
        params: TableRenderParams,
        refs: TableScrollRefs,
        layoutState: TableReactiveLayoutState,
        theme: TableTheme,
    ) {
        Scroller {
            ref { refs.bodyScrollerRef = it }
            attr {
                flex(1f)
                flexDirection(FlexDirection.ROW)
                bouncesEnable(false)
                showScrollerIndicator(false)
                val totalWidth = TableLayoutCalculator.totalWidth(params.columns())
                val viewport = layoutState.viewportWidth(getPager().pageData.pageViewWidth)
                scrollEnable(TableLayoutCalculator.needsHorizontalScroll(totalWidth, viewport))
                nestedScroll(KRNestedScrollMode.SELF_ONLY, KRNestedScrollMode.PARENT_FIRST)
            }
            event {
                dragBegin {
                    refs.beginDrive(TableScrollDriver.BODY)
                }
                scroll {
                    refs.updateBodyOffset(it.offsetX)
                    if (params.config.scrollSyncEnabled) {
                        TableScrollSync.syncBodyToHeader(refs, it.offsetX)
                    }
                    params.config.onScroll?.invoke(it.offsetX, refs.bodyListOffsetY)
                }
                scrollEnd {
                    refs.endDrive(TableScrollDriver.BODY)
                }
                layoutFrameDidChange {
                    TableScrollSync.applyInitialOffsets(
                        refs,
                        params.config.initialContentOffsetX,
                        params.config.initialContentOffsetY,
                    )
                    if (params.config.scrollSyncEnabled) {
                        TableScrollSync.realignBodyToHeader(refs)
                    }
                }
            }
            View {
                attr {
                    flexDirection(FlexDirection.COLUMN)
                    width(TableLayoutCalculator.totalWidth(params.columns()))
                    flex(1f)
                }
                renderBodyList(params, refs, theme)
            }
        }
    }

    private fun ViewContainer<*, *>.renderBodyList(
        params: TableRenderParams,
        refs: TableScrollRefs,
        theme: TableTheme,
    ) {
        val lazyMaxLoadItem = params.config.lazyMaxLoadItem.coerceAtLeast(1)
        // A stable List tree preserves horizontal synchronization during row updates.
        List {
            ref { refs.bodyListRef = it }
            attr {
                flex(1f)
                width(TableLayoutCalculator.totalWidth(params.columns()))
                showScrollerIndicator(true)
                bouncesEnable(false)
                nestedScroll(params.config.nestedScrollForward, params.config.nestedScrollBackward)
            }
            event {
                scroll {
                    refs.updateBodyListOffset(it.offsetY)
                    params.config.onScroll?.invoke(refs.bodyScrollerOffsetX, it.offsetY)
                }
                layoutFrameDidChange {
                    TableScrollSync.applyInitialOffsets(
                        refs,
                        params.config.initialContentOffsetX,
                        params.config.initialContentOffsetY,
                    )
                    if (params.config.scrollSyncEnabled) {
                        TableScrollSync.realignBodyToHeader(refs)
                    }
                }
            }
            val maxLoadItem = if (params.rows().size > params.config.lazyRowThreshold) {
                lazyMaxLoadItem
            } else {
                params.rows().size.coerceAtLeast(lazyMaxLoadItem)
            }
            vforLazy({ params.rows() }, maxLoadItem = maxLoadItem) { row, _, _ ->
                renderDataRow(params, row, theme)
            }
        }
    }

    private fun ViewContainer<*, *>.renderEmptyArea(params: TableRenderParams, theme: TableTheme) {
        vif({ params.config.slots.emptyContent != null }) {
            params.config.slots.emptyContent?.invoke(
                this,
                TableEmptyContext(params.config.emptyText, theme),
            )
        }
        velse {
            View {
                attr {
                    flex(1f)
                    allCenter()
                }
                Text {
                    attr {
                        text(params.config.emptyText)
                        fontSize(theme.cellFontSize)
                        color(Color(theme.emptyTextColor))
                    }
                }
            }
        }
    }

    private fun ViewContainer<*, *>.renderHeaderCell(
        params: TableRenderParams,
        column: TableColumn,
        columnIndex: Int,
        theme: TableTheme,
    ) {
        val columnWidth = TableLayoutCalculator.columnWidth(column)
        val context = TableCellContext(
            row = null,
            rowIndex = -1,
            column = column,
            columnIndex = columnIndex,
            value = column.title,
            theme = theme,
            isHeader = true,
            isStripeRow = false,
        )
        View {
            attr {
                width(columnWidth)
                height(theme.headerHeight)
                padding(
                    column.cellPaddingV ?: theme.cellPaddingV,
                    column.cellPaddingH ?: theme.cellPaddingH,
                    column.cellPaddingV ?: theme.cellPaddingV,
                    column.cellPaddingH ?: theme.cellPaddingH,
                )
                justifyContent(FlexJustifyContent.CENTER)
                alignItems(column.headerAlign)
                val headerBg = params.config.bindHeaderBackgroundColor?.invoke()
                    ?: column.headerBackgroundColor
                    ?: theme.headerBackgroundColor
                backgroundColor(Color(headerBg))
                borderRadius(0f)
                overflow(true)
            }
            vif({
                params.config.slots.headerRenderer(column.key) != null ||
                    params.config.slots.headerCell != null
            }) {
                val slot = params.config.slots.headerRenderer(column.key)
                    ?: params.config.slots.headerCell
                slot?.invoke(this, context)
            }
            velse {
                DefaultCellRenderer.renderHeader(this, context)
            }
            vif({
                params.config.showColumnBorder &&
                    columnIndex < params.columns().size - 1
            }) {
                renderColumnDivider(theme.headerBorderColor, theme)
            }
            if (params.config.onHeaderClick != null) {
                event {
                    click { params.config.onHeaderClick?.invoke(column.key) }
                }
            }
        }
    }

    private fun ViewContainer<*, *>.renderDataRow(
        params: TableRenderParams,
        row: TableRow,
        theme: TableTheme,
    ) {
        // Loop directives require a concrete root node.
        View {
            vbind({ TableIndexResolver.rowIndex(params.rows(), row) }) {
                val rowIndex = TableIndexResolver.rowIndex(params.rows(), row).coerceAtLeast(0)
                val rowKey = rowKey(row, rowIndex)

                View {
                    attr {
                        flexDirection(FlexDirection.ROW)
                        height(theme.rowHeight)
                        backgroundColor(Color(rowSurfaceColor(params, row, rowIndex, theme)))
                    }
                    event {
                        if (params.config.rowHoverEnabled) {
                            touchDown {
                                params.config.updateHoveredRowId(rowKey)
                            }
                            touchUp {
                                if (params.config.hoveredRowId == rowKey) {
                                    params.config.updateHoveredRowId(null)
                                }
                            }
                        }
                    }
                    vforIndex({ params.columns() }) { column, _, _ ->
                        View {
                            attr {
                                width(TableLayoutCalculator.columnWidth(column))
                                height(theme.rowHeight)
                            }
                            vbind({ TableIndexResolver.columnIndex(params.columns(), column) }) {
                                val columnIndex = TableIndexResolver.columnIndex(params.columns(), column)
                                    .coerceAtLeast(0)
                                renderBodyCell(params, row, rowIndex, column, columnIndex, theme)
                            }
                        }
                    }
                }
                vif({ params.config.showRowBorder }) {
                    renderDividerLine(theme.borderColor, theme.dividerWidth)
                }
            }
        }
    }

    private fun rowKey(row: TableRow, rowIndex: Int): String {
        return row.id.ifEmpty { "row-$rowIndex" }
    }

    private fun stripeColor(params: TableRenderParams, rowIndex: Int, theme: TableTheme): Long {
        return when {
            !params.config.stripeEnabled -> theme.cellBackgroundColor
            rowIndex % 2 == 1 -> theme.stripeEvenColor
            else -> theme.stripeOddColor
        }
    }

    private fun rowHighlightColor(
        params: TableRenderParams,
        row: TableRow,
        rowIndex: Int,
        theme: TableTheme,
    ): Long? {
        val key = rowKey(row, rowIndex)
        if (params.config.rowSelectionEnabled && params.config.selectedRowId == key) {
            return theme.selectedRowColor
        }
        if (params.config.rowHoverEnabled && params.config.hoveredRowId == key) {
            return theme.hoverRowColor
        }
        return null
    }

    private fun rowSurfaceColor(
        params: TableRenderParams,
        row: TableRow,
        rowIndex: Int,
        theme: TableTheme,
    ): Long {
        return rowHighlightColor(params, row, rowIndex, theme) ?: stripeColor(params, rowIndex, theme)
    }

    private fun cellSurfaceColor(
        params: TableRenderParams,
        row: TableRow,
        rowIndex: Int,
        column: TableColumn,
        theme: TableTheme,
    ): Long {
        rowHighlightColor(params, row, rowIndex, theme)?.let { return it }
        return column.cellBackgroundColor ?: stripeColor(params, rowIndex, theme)
    }

    private fun ViewContainer<*, *>.renderDividerLine(color: Long, height: Float) {
        View {
            attr {
                height(height)
                backgroundColor(Color(color))
            }
        }
    }

    private fun ViewContainer<*, *>.renderColumnDivider(color: Long, theme: TableTheme) {
        View {
            attr {
                absolutePosition(top = 0f, right = 0f, bottom = 0f)
                width(theme.dividerWidth)
                backgroundColor(Color(color))
            }
        }
    }

    private fun ViewContainer<*, *>.renderBodyCell(
        params: TableRenderParams,
        row: TableRow,
        rowIndex: Int,
        column: TableColumn,
        columnIndex: Int,
        theme: TableTheme,
    ) {
        val columnWidth = TableLayoutCalculator.columnWidth(column)
        val value = row.cells[column.key]
        val context = TableCellContext(
            row = row,
            rowIndex = rowIndex,
            column = column,
            columnIndex = columnIndex,
            value = value,
            theme = theme,
            isHeader = false,
            isStripeRow = rowIndex % 2 == 1,
        )
        View {
            attr {
                width(columnWidth)
                height(theme.rowHeight)
                padding(
                    column.cellPaddingV ?: theme.cellPaddingV,
                    column.cellPaddingH ?: theme.cellPaddingH,
                    column.cellPaddingV ?: theme.cellPaddingV,
                    column.cellPaddingH ?: theme.cellPaddingH,
                )
                justifyContent(FlexJustifyContent.CENTER)
                alignItems(column.align)
                backgroundColor(Color(cellSurfaceColor(params, row, rowIndex, column, theme)))
                borderRadius(0f)
                overflow(true)
            }
            vif({
                params.config.slots.bodyRenderer(column.key) != null ||
                    params.config.slots.bodyCell != null
            }) {
                val slot = params.config.slots.bodyRenderer(column.key)
                    ?: params.config.slots.bodyCell
                slot?.invoke(this, context)
            }
            velse {
                DefaultCellRenderer.renderBody(this, context)
            }
            vif({
                params.config.showColumnBorder &&
                    columnIndex < params.columns().size - 1
            }) {
                renderColumnDivider(theme.borderColor, theme)
            }
            event {
                click {
                    val liveRowIndex = TableIndexResolver.rowIndex(params.rows(), row).coerceAtLeast(0)
                    val key = rowKey(row, liveRowIndex)
                    if (params.config.rowSelectionEnabled) {
                        params.config.updateSelectedRowId(key)
                        params.config.onRowSelect?.invoke(liveRowIndex, row)
                    }
                    params.config.onCellClick?.invoke(liveRowIndex, column.key, row)
                }
            }
        }
    }
}
