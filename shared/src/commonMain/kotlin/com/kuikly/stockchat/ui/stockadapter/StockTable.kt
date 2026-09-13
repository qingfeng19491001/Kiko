package com.kuikly.stockchat.ui.stockadapter

import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.CapitalFlow
import com.kuikly.stockchat.domain.chat.KeyLevel
import com.kuikly.stockchat.domain.chat.PeerRow
import com.kuikly.stockchat.ui.components.*
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.table.Table
import com.tencent.kuiklybase.table.model.TableColumn
import com.tencent.kuiklybase.table.model.TableRow

/** 行情表单元格：在 KuiklyTable 的 slot 里着色，不另做一套表格。 */
data class StockCell(
    val text: String,
    val colorArgb: Long = COLOR_INK,
    val bold: Boolean = false,
)

class StockTableState {
    var columns by observableList<TableColumn>()
    var rows by observableList<TableRow>()

    fun ensure(nextColumns: List<TableColumn>, nextRows: List<TableRow>) {
        if (columns.isEmpty()) columns.addAll(nextColumns)
        if (rows.isEmpty()) rows.addAll(nextRows)
    }
}

/**
 * 股票主题的 KuiklyTable：浅表头、斑马纹、数值右对齐。
 * 同业 / 对比 / 价位 / 资金都走这里。
 */
fun ViewContainer<*, *>.StockQuoteTable(
    state: StockTableState,
    width: Float,
    rowCount: Int,
    onCellClick: ((rowIndex: Int, columnKey: String, row: TableRow) -> Unit)? = null,
) {
    val headerH = 34f
    val rowH = 40f
    val tableH = headerH + rowCount.coerceAtLeast(1) * rowH
    View {
        attr {
            width(width)
            height(tableH)
        }
        Table(
            columns = { state.columns },
            rows = { state.rows },
        ) {
            showOuterBorder = false
            showColumnBorder = false
            showRowBorder = true
            showHeaderBorder = true
            stripeEnabled = true
            rowHoverEnabled = false
            rowSelectionEnabled = false
            theme {
                headerBackgroundColor = COLOR_MUTED
                headerTextColor = COLOR_TERTIARY
                headerBorderColor = COLOR_DIVIDER
                headerFontSize = 11f
                headerHeight = headerH
                cellBackgroundColor = COLOR_WHITE
                cellTextColor = COLOR_INK
                cellFontSize = 13f
                rowHeight = rowH
                cellPaddingH = 8f
                cellPaddingV = 0f
                stripeOddColor = COLOR_WHITE
                stripeEvenColor = COLOR_STRIPE
                borderColor = COLOR_DIVIDER
                dividerWidth = 0.5f
                borderRadius = 0f
            }
            slots {
                bodyCell = { container, context ->
                    val cell = context.value as? StockCell
                    container.Text {
                        attr {
                            text(cell?.text ?: context.value?.toString().orEmpty())
                            fontSize(13f)
                            color(Color(cell?.colorArgb ?: COLOR_INK))
                            if (cell?.bold == true) fontWeight600() else fontWeight400()
                            lines(1)
                        }
                    }
                }
            }
            this.onCellClick = onCellClick
        }
    }
}

fun peerTableColumns(): List<TableColumn> = listOf(
    TableColumn("name", "名称", width = 108f, minWidth = 96f, align = FlexAlign.FLEX_START),
    TableColumn("price", "现价", width = 68f, minWidth = 56f, align = FlexAlign.FLEX_END),
    TableColumn("changePct", "涨跌幅", width = 72f, minWidth = 64f, align = FlexAlign.FLEX_END),
    TableColumn("marketCap", "市值", width = 72f, minWidth = 64f, align = FlexAlign.FLEX_END),
    TableColumn("pe", "PE", width = 52f, minWidth = 44f, align = FlexAlign.FLEX_END),
    TableColumn("turnover", "成交额", width = 72f, minWidth = 64f, align = FlexAlign.FLEX_END),
)

fun peerTableRows(rows: List<PeerRow>): List<TableRow> = rows.map { row ->
    TableRow(
        id = row.instrumentKey,
        cells = mapOf(
            "name" to StockCell(row.name, COLOR_ACCENT, bold = true),
            "price" to StockCell(row.price),
            "changePct" to StockCell(row.changePct, changeArgb(row.change), bold = true),
            "marketCap" to StockCell(row.marketCap, COLOR_SECONDARY),
            "pe" to StockCell(row.pe, COLOR_SECONDARY),
            "turnover" to StockCell(row.turnover, COLOR_SECONDARY),
        ),
    )
}

fun compareTableColumns(leftName: String, rightName: String): List<TableColumn> = listOf(
    TableColumn("label", "指标", width = 72f, minWidth = 64f, align = FlexAlign.FLEX_START),
    TableColumn("left", leftName, width = 88f, minWidth = 72f, align = FlexAlign.CENTER),
    TableColumn("right", rightName, width = 88f, minWidth = 72f, align = FlexAlign.CENTER),
    TableColumn("diff", "差值", width = 72f, minWidth = 64f, align = FlexAlign.FLEX_END),
)

fun compareTableRows(block: AnswerBlock.CompareCard): List<TableRow> = block.rows.mapIndexed { index, row ->
    TableRow(
        id = "cmp-$index",
        cells = mapOf(
            "label" to StockCell(row.label, COLOR_SECONDARY),
            "left" to StockCell(row.left, bold = true),
            "right" to StockCell(row.right, bold = true),
            "diff" to StockCell(row.diff, toneArgb(row.diffTone), bold = true),
        ),
    )
}

fun keyLevelColumns(): List<TableColumn> = listOf(
    TableColumn("label", "价位", width = 64f, minWidth = 56f, align = FlexAlign.FLEX_START),
    TableColumn("value", "数值", width = 76f, minWidth = 64f, align = FlexAlign.FLEX_END),
    TableColumn("note", "含义", width = 160f, minWidth = 120f, align = FlexAlign.FLEX_START),
)

fun keyLevelRows(levels: List<KeyLevel>): List<TableRow> = levels.mapIndexed { index, level ->
    TableRow(
        id = "lv-$index",
        cells = mapOf(
            "label" to StockCell(level.label, COLOR_SECONDARY, bold = true),
            "value" to StockCell(level.value, toneArgb(level.tone), bold = true),
            "note" to StockCell(level.note, COLOR_TERTIARY),
        ),
    )
}

fun capitalFlowColumns(): List<TableColumn> = listOf(
    TableColumn("label", "类型", width = 96f, minWidth = 72f, align = FlexAlign.FLEX_START),
    TableColumn("netInflow", "净流入", width = 96f, minWidth = 80f, align = FlexAlign.FLEX_END),
    TableColumn("pct", "占比", width = 72f, minWidth = 56f, align = FlexAlign.FLEX_END),
)

fun capitalFlowRows(flows: List<CapitalFlow>): List<TableRow> = flows.mapIndexed { index, flow ->
    TableRow(
        id = "flow-$index",
        cells = mapOf(
            "label" to StockCell(flow.label, bold = true),
            "netInflow" to StockCell(flow.netInflow, toneArgb(flow.tone), bold = true),
            "pct" to StockCell(flow.pct, toneArgb(flow.tone)),
        ),
    )
}

private fun changeArgb(change: Double): Long = when {
    change > 0 -> COLOR_UP
    change < 0 -> COLOR_DOWN
    else -> COLOR_FLAT
}

private fun toneArgb(tone: TagTone): Long = when (tone) {
    TagTone.POSITIVE -> COLOR_UP
    TagTone.NEGATIVE -> COLOR_DOWN
    TagTone.WARNING -> COLOR_WARNING
    TagTone.NEUTRAL -> COLOR_SECONDARY
}

internal const val COLOR_INK = 0xFF191919L
internal const val COLOR_ACCENT = 0xFF2F54EBL
internal const val COLOR_UP = 0xFF12924AL
internal const val COLOR_DOWN = 0xFFD93B3BL
internal const val COLOR_FLAT = 0xFF64748BL
internal const val COLOR_WARNING = 0xFFB45309L
internal const val COLOR_SECONDARY = 0xFF666666L
internal const val COLOR_TERTIARY = 0xFF999999L
internal const val COLOR_MUTED = 0xFFF7F7F7L
internal const val COLOR_STRIPE = 0xFFFAFBFCL
internal const val COLOR_WHITE = 0xFFFFFFFFL
internal const val COLOR_DIVIDER = 0xFFEBEDF0L
