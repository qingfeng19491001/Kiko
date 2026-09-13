package com.tencent.kuiklybase.table.theme

import com.tencent.kuikly.core.views.TextAttr

/** Font weight tokens mapped onto Kuikly [TextAttr] helpers. */
enum class TableFontWeight {
    NORMAL,
    MEDIUM,
    SEMI_BOLD,
    BOLD,
}

internal fun TextAttr.applyTableFontWeight(weight: TableFontWeight): TextAttr {
    return when (weight) {
        TableFontWeight.NORMAL -> fontWeightNormal()
        TableFontWeight.MEDIUM -> fontWeightMedium()
        TableFontWeight.SEMI_BOLD -> fontWeightSemiBold()
        TableFontWeight.BOLD -> fontWeightBold()
    }
}
