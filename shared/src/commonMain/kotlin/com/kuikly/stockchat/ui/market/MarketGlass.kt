package com.kuikly.stockchat.ui.market

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Blur
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.HeaderActionChip(
    radius: Float,
    onClick: () -> Unit,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            overflow(true)
            borderRadius(radius)
        }
        event { click { onClick() } }
        Blur {
            attr {
                absolutePositionAllZero()
                blurRadius(12.5f)
            }
        }
        View {
            attr {
                absolutePositionAllZero()
                borderRadius(radius)
                backgroundColor(Color(255, 255, 255, 0.46f))
                border(Border(0.6f, BorderStyle.SOLID, Color(255, 255, 255, 0.82f)))
            }
        }
        View {
            attr {
                positionAbsolute()
                left(2f)
                right(2f)
                top(0.5f)
                height(1f)
                borderRadius(0.5f)
                backgroundColor(Color(255, 255, 255, 0.7f))
            }
        }
        content()
    }
}
