package com.tencent.kuiklybase.table

import com.tencent.kuikly.core.views.KRNestedScrollMode
import com.tencent.kuiklybase.table.model.TableRow
import com.tencent.kuiklybase.table.slot.TableSlots
import com.tencent.kuiklybase.table.theme.TableThemeOptions

/** Configuration for the [Table] DSL. */
class TableConfig {
    val theme = TableThemeOptions()
    val slots = TableSlots()

    var showHeader: Boolean = true

    private var rowBorderProvider: () -> Boolean = { true }
    private var columnBorderProvider: () -> Boolean = { true }
    private var outerBorderProvider: () -> Boolean = { true }
    private var headerBorderProvider: () -> Boolean = { true }

    /**
     * Horizontal divider under each data row (tr bottom).
     *
     * Assign a constant for static config, or use [bindRowBorder] so toggles
     * update without remounting the table.
     */
    var showRowBorder: Boolean
        get() = rowBorderProvider()
        set(value) {
            rowBorderProvider = { value }
        }

    /** Vertical divider on the right of each cell except the last column (td right). */
    var showColumnBorder: Boolean
        get() = columnBorderProvider()
        set(value) {
            columnBorderProvider = { value }
        }

    /** Outer frame around the whole table. */
    var showOuterBorder: Boolean
        get() = outerBorderProvider()
        set(value) {
            outerBorderProvider = { value }
        }

    /** Independent separator between header and body. */
    var showHeaderBorder: Boolean
        get() = headerBorderProvider()
        set(value) {
            headerBorderProvider = { value }
        }

    fun bindRowBorder(provider: () -> Boolean) {
        rowBorderProvider = provider
    }

    fun bindColumnBorder(provider: () -> Boolean) {
        columnBorderProvider = provider
    }

    fun bindOuterBorder(provider: () -> Boolean) {
        outerBorderProvider = provider
    }

    fun bindHeaderBorder(provider: () -> Boolean) {
        headerBorderProvider = provider
    }

    private var stripeEnabledProvider: () -> Boolean = { true }

    /**
     * Alternating row background colors.
     *
     * Use [bindStripeEnabled] for reactive toggles without remounting the table.
     */
    var stripeEnabled: Boolean
        get() = stripeEnabledProvider()
        set(value) {
            stripeEnabledProvider = { value }
        }

    fun bindStripeEnabled(provider: () -> Boolean) {
        stripeEnabledProvider = provider
    }

    /** Highlight row background while the finger is down on a row. */
    var rowHoverEnabled: Boolean = true

    /** Highlight and track the last clicked row. */
    var rowSelectionEnabled: Boolean = true

    private var selectedRowIdValue: String? = null
    private var selectedRowIdGet: () -> String? = { selectedRowIdValue }
    private var selectedRowIdSet: (String?) -> Unit = { selectedRowIdValue = it }
    private var hoveredRowIdValue: String? = null
    private var hoveredRowIdGet: () -> String? = { hoveredRowIdValue }
    private var hoveredRowIdSet: (String?) -> Unit = { hoveredRowIdValue = it }

    var selectedRowId: String?
        get() = selectedRowIdGet()
        set(value) {
            selectedRowIdSet(value)
        }

    var hoveredRowId: String?
        get() = hoveredRowIdGet()
        set(value) {
            hoveredRowIdSet(value)
        }

    fun bindSelectedRowId(get: () -> String?, set: (String?) -> Unit) {
        selectedRowIdGet = get
        selectedRowIdSet = set
    }

    fun bindHoveredRowId(get: () -> String?, set: (String?) -> Unit) {
        hoveredRowIdGet = get
        hoveredRowIdSet = set
    }

    internal fun updateSelectedRowId(id: String?) {
        selectedRowIdSet(id)
    }

    internal fun updateHoveredRowId(id: String?) {
        hoveredRowIdSet(id)
    }

    var scrollSyncEnabled: Boolean = true
    var lazyRowThreshold: Int = 50
    var lazyMaxLoadItem: Int = 40
    var emptyText: String = "暂无数据"

    /**
     * Horizontal / vertical offsets applied once after mount.
     * Hosts that remount Table for theme changes should re-supply the last
     * [onScroll] values so the viewport does not jump back to the first column.
     */
    var initialContentOffsetX: Float = 0f
    var initialContentOffsetY: Float = 0f

    /**
     * Optional reactive override for header cell background.
     * When non-null and returning a color, wins over theme / column defaults
     * without remounting the table body.
     */
    var bindHeaderBackgroundColor: (() -> Long?)? = null

    var nestedScrollForward: KRNestedScrollMode = KRNestedScrollMode.SELF_FIRST
    var nestedScrollBackward: KRNestedScrollMode = KRNestedScrollMode.SELF_FIRST

    var onCellClick: ((rowIndex: Int, columnKey: String, row: TableRow) -> Unit)? = null
    var onScroll: ((offsetX: Float, offsetY: Float) -> Unit)? = null
    var onHeaderClick: ((columnKey: String) -> Unit)? = null
    var onRowSelect: ((rowIndex: Int, row: TableRow) -> Unit)? = null

    fun theme(block: TableThemeOptions.() -> Unit) {
        theme.apply(block)
    }

    fun slots(block: TableSlots.() -> Unit) {
        slots.apply(block)
    }
}
