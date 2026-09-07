package com.kuikly.stockchat.ui.components

import com.kuikly.stockchat.domain.analysis.InsightTag
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 标的首字头像 */
fun ViewContainer<*, *>.InstrumentAvatar(instrument: Instrument, size: Float = 40f) {
    View {
        attr {
            size(size, size)
            borderRadius(size * 0.28f)
            backgroundColor(Color(instrument.logoColor))
            allCenter()
        }
        Text {
            attr {
                text(instrument.logoText)
                fontSize(size * 0.42f)
                fontWeight600()
                color(Color.WHITE)
            }
        }
    }
}

/**
 * 品牌标识：墨色方块 + 白色 K 线符号，兼作 AI 回复的身份标记。
 * 由 Canvas 绘制，不依赖图片资源。
 */
fun ViewContainer<*, *>.BrandMark(size: Float = 24f, background: Color = AppTheme.ink, glyph: Color = Color.WHITE) {
    View {
        attr {
            size(size, size)
            borderRadius(size * 0.28f)
            backgroundColor(background)
            allCenter()
        }
        Icon(IconKind.CANDLE, size * 0.7f, glyph, size * 0.075f)
    }
}

/** 涨跌幅标签（实色底，行情软件惯用样式） */
fun ViewContainer<*, *>.ChangeBadge(change: Double, text: String, fontSize: Float = 12f, filled: Boolean = false) {
    View {
        attr {
            backgroundColor(if (filled) AppTheme.changeColor(change) else AppTheme.changeSoftColor(change))
            borderRadius(4f)
            paddingLeft(6f)
            paddingRight(6f)
            paddingTop(2f)
            paddingBottom(2f)
        }
        Text {
            attr {
                text(text)
                fontSize(fontSize)
                fontWeight600()
                color(if (filled) Color.WHITE else AppTheme.changeColor(change))
            }
        }
    }
}

/** 语义标签 */
fun ViewContainer<*, *>.TagChip(tag: InsightTag, fontSize: Float = 11f) {
    View {
        attr {
            backgroundColor(AppTheme.toneSoftColor(tag.tone))
            borderRadius(4f)
            paddingLeft(7f)
            paddingRight(7f)
            paddingTop(2f)
            paddingBottom(2f)
            marginRight(6f)
            marginBottom(6f)
        }
        Text {
            attr {
                text(tag.text)
                fontSize(fontSize)
                fontWeight500()
                color(AppTheme.toneColor(tag.tone))
            }
        }
    }
}

/** 通用胶囊按钮（追问 / 快捷入口） */
fun ViewContainer<*, *>.PillButton(
    text: String,
    icon: IconKind? = null,
    background: Color = AppTheme.surface,
    textColor: Color = AppTheme.textPrimary,
    borderColor: Color? = AppTheme.border,
    onClick: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            backgroundColor(background)
            borderRadius(8f)
            paddingLeft(12f)
            paddingRight(12f)
            height(34f)
            borderColor?.let { border(Border(1f, BorderStyle.SOLID, it)) }
        }
        event { click { onClick() } }
        icon?.let {
            Icon(it, 16f, textColor, 1.8f)
            View { attr { width(6f) } }
        }
        Text {
            attr {
                text(text)
                fontSize(13f)
                fontWeight500()
                color(textColor)
            }
        }
    }
}

/** 卡片容器 */
fun ViewContainer<*, *>.Card(
    padding: Float = 14f,
    background: Color = AppTheme.surface,
    radius: Float = AppTheme.cardRadius,
    shadow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: DivView.() -> Unit,
) {
    View {
        attr {
            backgroundColor(background)
            borderRadius(radius)
            padding(padding)
            if (shadow) boxShadow(BoxShadow(0f, 1f, 6f, Color(0x0A0F172AL)))
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
        }
        onClick?.let { handler -> event { click { handler() } } }
        content()
    }
}

fun ViewContainer<*, *>.Divider(color: Color = AppTheme.divider, vertical: Float = 0f) {
    View {
        attr {
            height(0.5f)
            backgroundColor(color)
            if (vertical > 0) {
                marginTop(vertical)
                marginBottom(vertical)
            }
        }
    }
}

fun ViewContainer<*, *>.Spacer(size: Float) {
    View { attr { size(size, size) } }
}

fun ViewContainer<*, *>.HSpacer(width: Float) {
    View { attr { width(width) } }
}

/** 标签 + 数值（用于指标网格） */
fun ViewContainer<*, *>.LabelValue(
    label: String,
    value: String,
    valueColor: Color = AppTheme.textPrimary,
    alignRight: Boolean = false,
    valueSize: Float = 14f,
) {
    View {
        attr {
            if (alignRight) alignItemsFlexEnd()
        }
        Text {
            attr {
                text(label)
                fontSize(11f)
                color(AppTheme.textTertiary)
            }
        }
        Text {
            attr {
                text(value)
                fontSize(valueSize)
                fontWeight600()
                color(valueColor)
                marginTop(3f)
            }
        }
    }
}

/** 主按钮 */
fun ViewContainer<*, *>.PrimaryButton(
    text: String,
    icon: IconKind? = null,
    filled: Boolean = true,
    flex: Boolean = true,
    onClick: () -> Unit,
) {
    View {
        attr {
            if (flex) flex(1f)
            height(44f)
            borderRadius(10f)
            flexDirectionRow()
            allCenter()
            if (filled) backgroundColor(AppTheme.primary) else {
                backgroundColor(AppTheme.surface)
                border(Border(1f, BorderStyle.SOLID, AppTheme.primary))
            }
        }
        event { click { onClick() } }
        icon?.let {
            Icon(it, 18f, if (filled) Color.WHITE else AppTheme.primary, 1.8f)
            HSpacer(6f)
        }
        Text {
            attr {
                text(text)
                fontSize(15f)
                fontWeight600()
                color(if (filled) Color.WHITE else AppTheme.primary)
            }
        }
    }
}

/** 圆形图标按钮。shadow=true 时加轻阴影，对齐 Kimi 顶部悬浮圆钮。 */
fun ViewContainer<*, *>.IconButton(
    kind: IconKind,
    size: Float = 40f,
    iconSize: Float = 22f,
    color: Color = AppTheme.textPrimary,
    background: Color? = null,
    shadow: Boolean = false,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(size, size)
            borderRadius(size / 2)
            allCenter()
            background?.let { backgroundColor(it) }
            if (shadow) {
                boxShadow(BoxShadow(0f, 1f, 4f, Color(0x14000000L)))
                border(Border(0.5f, BorderStyle.SOLID, Color(0x0F000000L)))
            }
        }
        event { click { onClick() } }
        Icon(kind, iconSize, color)
    }
}
