package com.tencent.kuiklybase.table.render

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuiklybase.table.model.TableCellContext
import com.tencent.kuiklybase.table.theme.applyTableFontWeight

internal object DefaultCellRenderer {
    fun renderHeader(container: ViewContainer<*, *>, context: TableCellContext) {
        val theme = context.theme
        container.Text {
            attr {
                text(context.column.title)
                fontSize(theme.headerFontSize)
                applyTableFontWeight(theme.headerFontWeight)
                if (theme.headerFontFamily.isNotBlank()) {
                    fontFamily(theme.headerFontFamily)
                }
                color(Color(theme.headerTextColor))
            }
        }
    }

    fun renderBody(container: ViewContainer<*, *>, context: TableCellContext) {
        container.Text {
            attr {
                text(context.value?.toString() ?: "")
                fontSize(context.theme.cellFontSize)
                color(Color(context.theme.cellTextColor))
            }
        }
    }
}
