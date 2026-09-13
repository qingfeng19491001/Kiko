package com.tencent.kuiklybase.table.theme

/** Mutable theme options resolved into an immutable [TableTheme]. */
class TableThemeOptions {
    var headerBackgroundColor: Long = 0xFFF5F7FA
    var headerTextColor: Long = 0xFF333333
    var headerBorderColor: Long = 0xFFE5E7EB
    var headerFontWeight: TableFontWeight = TableFontWeight.SEMI_BOLD
    var headerFontFamily: String = ""
    var cellBackgroundColor: Long = 0xFFFFFFFF
    var cellTextColor: Long = 0xFF333333
    var borderColor: Long = 0xFFE5E7EB
    var stripeOddColor: Long = 0xFFFFFFFF
    var stripeEvenColor: Long = 0xFFF9FAFB
    var hoverRowColor: Long = 0xFFEFF6FF
    var selectedRowColor: Long = 0xFFDBEAFE
    var emptyTextColor: Long = 0xFF999999
    var headerHeight: Float = 44f
    var rowHeight: Float = 48f
    var cellPaddingH: Float = 12f
    var cellPaddingV: Float = 8f
    var dividerWidth: Float = 0.5f
    var borderRadius: Float = 0f
    var headerFontSize: Float = 14f
    var cellFontSize: Float = 14f

    fun useLightTheme() {
        applyTheme(TableTheme.light())
    }

    fun useDarkTheme() {
        applyTheme(TableTheme.dark())
    }

    fun applyTheme(theme: TableTheme) {
        headerBackgroundColor = theme.headerBackgroundColor
        headerTextColor = theme.headerTextColor
        headerBorderColor = theme.headerBorderColor
        headerFontWeight = theme.headerFontWeight
        headerFontFamily = theme.headerFontFamily
        cellBackgroundColor = theme.cellBackgroundColor
        cellTextColor = theme.cellTextColor
        borderColor = theme.borderColor
        stripeOddColor = theme.stripeOddColor
        stripeEvenColor = theme.stripeEvenColor
        hoverRowColor = theme.hoverRowColor
        selectedRowColor = theme.selectedRowColor
        emptyTextColor = theme.emptyTextColor
        headerHeight = theme.headerHeight
        rowHeight = theme.rowHeight
        cellPaddingH = theme.cellPaddingH
        cellPaddingV = theme.cellPaddingV
        dividerWidth = theme.dividerWidth
        borderRadius = theme.borderRadius
        headerFontSize = theme.headerFontSize
        cellFontSize = theme.cellFontSize
    }

    fun resolved(): TableTheme = TableTheme(
        headerBackgroundColor = headerBackgroundColor,
        headerTextColor = headerTextColor,
        headerBorderColor = headerBorderColor,
        headerFontWeight = headerFontWeight,
        headerFontFamily = headerFontFamily,
        cellBackgroundColor = cellBackgroundColor,
        cellTextColor = cellTextColor,
        borderColor = borderColor,
        stripeOddColor = stripeOddColor,
        stripeEvenColor = stripeEvenColor,
        hoverRowColor = hoverRowColor,
        selectedRowColor = selectedRowColor,
        emptyTextColor = emptyTextColor,
        headerHeight = headerHeight,
        rowHeight = rowHeight,
        cellPaddingH = cellPaddingH,
        cellPaddingV = cellPaddingV,
        dividerWidth = dividerWidth,
        borderRadius = borderRadius,
        headerFontSize = headerFontSize,
        cellFontSize = cellFontSize,
    )
}
