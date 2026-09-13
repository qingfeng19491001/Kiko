package com.tencent.kuiklybase.table.theme

/**
 * Immutable theme snapshot consumed by the render kernel.
 */
data class TableTheme(
    val headerBackgroundColor: Long = 0xFFF5F7FA,
    val headerTextColor: Long = 0xFF333333,
    val headerBorderColor: Long = 0xFFE5E7EB,
    val headerFontWeight: TableFontWeight = TableFontWeight.SEMI_BOLD,
    val headerFontFamily: String = "",
    val cellBackgroundColor: Long = 0xFFFFFFFF,
    val cellTextColor: Long = 0xFF333333,
    val borderColor: Long = 0xFFE5E7EB,
    val stripeOddColor: Long = 0xFFFFFFFF,
    val stripeEvenColor: Long = 0xFFF9FAFB,
    val hoverRowColor: Long = 0xFFEFF6FF,
    val selectedRowColor: Long = 0xFFDBEAFE,
    val emptyTextColor: Long = 0xFF999999,
    val headerHeight: Float = 44f,
    val rowHeight: Float = 48f,
    val cellPaddingH: Float = 12f,
    val cellPaddingV: Float = 8f,
    val dividerWidth: Float = 0.5f,
    val borderRadius: Float = 0f,
    val headerFontSize: Float = 14f,
    val cellFontSize: Float = 14f,
) {
    companion object {
        fun light(): TableTheme = TableTheme()

        fun dark(): TableTheme = TableTheme(
            headerBackgroundColor = 0xFF1F2937,
            headerTextColor = 0xFFF3F4F6,
            headerBorderColor = 0xFF4B5563,
            cellBackgroundColor = 0xFF111827,
            cellTextColor = 0xFFE5E7EB,
            borderColor = 0xFF374151,
            stripeOddColor = 0xFF111827,
            stripeEvenColor = 0xFF1F2937,
            hoverRowColor = 0xFF1E3A5F,
            selectedRowColor = 0xFF1E40AF,
            emptyTextColor = 0xFF9CA3AF,
        )
    }
}
