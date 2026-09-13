package com.kuikly.stockchat.ui.answer

import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.ChartCardHeader(
    title: String,
    subtitle: String,
) {
    View {
        attr { flexDirectionRow(); alignItemsCenter() }
        Icon(IconKind.CHART, 16f, AppTheme.ink)
        View {
            attr { flex(1f); marginLeft(6f) }
            Text { attr { text(title); fontSize(14f); fontWeight600(); color(AppTheme.textPrimary) } }
            if (subtitle.isNotEmpty()) {
                Text { attr { text(subtitle); fontSize(11f); color(AppTheme.textTertiary); marginTop(2f) } }
            }
        }
    }
}
