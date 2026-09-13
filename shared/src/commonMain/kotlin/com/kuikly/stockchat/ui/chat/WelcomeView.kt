package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.ChangeBadge
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.WelcomeView(
    pageWidth: Float,
    compact: () -> Boolean,
    promptPage: () -> Int,
    onShuffle: () -> Unit,
    onPrompt: (String) -> Unit,
    onOpenInstrument: (String) -> Unit,
) {
    View {
        attr {
            flex(1f)
            paddingLeft(AppTheme.pageHorizontalPadding)
            paddingRight(AppTheme.pageHorizontalPadding)
            overflow(false)
        }
        View {
            attr {
                val isCompact = compact()
                flex(1f)
                opacity(if (isCompact) 0f else 1f)
                transform(Translate(0f, 0f, 0f, if (isCompact) 36f else 0f))
                animation(Animation.easeOut(0.2f), isCompact)
                overflow(false)
                alignItemsFlexStart()
                paddingTop(36f)
                paddingBottom(8f)
            }
            Image {
                attr {
                    size(80f, 80f)
                    resizeContain()
                    src(ImageUri.commonAssets("robot.png"))
                }
            }
            Text {
                attr {
                    text("你好，我是 Kiko")
                    fontSize(26f)
                    fontWeight700()
                    color(AppTheme.textPrimary)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text("今天有什么可以帮到你？")
                    fontSize(14f)
                    color(AppTheme.textTertiary)
                    marginTop(8f)
                }
            }
            (0 until PromptBank.WELCOME_PAGE_SIZE).forEach { index ->
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        marginTop(if (index == 0) 28f else 12f)
                        paddingLeft(16f); paddingRight(16f)
                        paddingTop(10f); paddingBottom(10f)
                        maxWidth(pageWidth - AppTheme.pageHorizontalPadding * 2)
                        questionBubble()
                    }
                    event {
                        click {
                            PromptBank.welcomePage(promptPage()).getOrNull(index)?.prompt?.let(onPrompt)
                        }
                    }
                    Text {
                        attr {
                            val item = PromptBank.welcomePage(promptPage()).getOrNull(index)
                            text(item?.prompt ?: "")
                            fontSize(14f)
                            color(AppTheme.textPrimary)
                            lines(1)
                        }
                    }
                }
            }
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    marginTop(18f)
                    height(20f)
                }
                event { click { onShuffle() } }
                Text {
                    attr {
                        text("换一换")
                        fontSize(13f)
                        color(AppTheme.textTertiary)
                    }
                }
                View { attr { width(4f) } }
                Icon(IconKind.REFRESH, 12f, AppTheme.textTertiary, 1.6f)
            }
            View { attr { flex(1f) } }
        }
    }
}

/** 首页功能入口卡片 */
internal fun ViewContainer<*, *>.FunctionCard(
    width: Float,
    icon: IconKind,
    iconBg: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    View {
        attr {
            width(width)
            height(104f)
            backgroundColor(AppTheme.surface)
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
            borderRadius(AppTheme.cardRadius)
            padding(16f)
            justifyContentCenter()
        }
        event { click { onClick() } }
        View {
            attr {
                size(40f, 40f)
                borderRadius(10f)
                backgroundColor(iconBg)
                allCenter()
            }
            Icon(icon, 22f, iconColor, 1.8f)
        }
        Text {
            attr {
                text(title)
                fontSize(15f)
                fontWeight600()
                color(AppTheme.textPrimary)
                marginTop(10f)
            }
        }
        Text {
            attr {
                text(subtitle)
                fontSize(12f)
                color(AppTheme.textTertiary)
                marginTop(2f)
            }
        }
    }
}

internal fun ViewContainer<*, *>.SectionHeader(title: String, trailing: String) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); marginTop(24f); marginBottom(10f) }
        Text {
            attr {
                text(title)
                fontSize(13f)
                fontWeight600()
                color(AppTheme.textSecondary)
                flex(1f)
            }
        }
        if (trailing.isNotEmpty()) {
            Text { attr { text(trailing); fontSize(11f); color(AppTheme.textTertiary) } }
        }
    }
}

internal fun ViewContainer<*, *>.IndexTile(snap: MarketSnapshot, width: Float, onClick: () -> Unit) {
    val q = snap.quote
    val color = AppTheme.changeColor(q.change)
    View {
        attr {
            width(width)
            backgroundColor(AppTheme.surface)
            borderRadius(AppTheme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
            padding(12f)
        }
        event { click { onClick() } }
        Text {
            attr {
                text(q.instrument.name)
                fontSize(12f)
                color(AppTheme.textSecondary)
                lines(1)
            }
        }
        Text {
            attr {
                text(NumberFormat.price(q.price))
                fontSize(17f)
                fontWeight700()
                color(color)
                marginTop(6f)
            }
        }
        Text {
            attr {
                text("${NumberFormat.signed(q.change)}  ${NumberFormat.signedPct(q.changePct)}")
                fontSize(11f)
                fontWeight500()
                color(color)
                marginTop(2f)
                lines(1)
            }
        }
    }
}

internal fun ViewContainer<*, *>.IndexTileSkeleton(width: Float) {
    View {
        attr {
            width(width)
            height(84f)
            backgroundColor(AppTheme.surface)
            borderRadius(AppTheme.cardRadius)
            border(Border(1f, BorderStyle.SOLID, AppTheme.border))
            padding(12f)
        }
        View { attr { width(48f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted) } }
        View { attr { width(72f); height(16f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(10f) } }
        View { attr { width(56f); height(10f); borderRadius(3f); backgroundColor(AppTheme.surfaceMuted); marginTop(8f) } }
    }
}

internal fun ViewContainer<*, *>.HotStockRow(snap: MarketSnapshot, pageWidth: Float, onClick: () -> Unit) {
    val q = snap.quote
    val ins = q.instrument
    val color = AppTheme.changeColor(q.change)
    View {
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                height(56f)
                paddingLeft(14f); paddingRight(14f)
            }
            event { click { onClick() } }
            View {
                attr { width(pageWidth * 0.32f) }
                Text { attr { text(ins.name); fontSize(14f); fontWeight600(); color(AppTheme.textPrimary); lines(1) } }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(3f) }
                    MarketTag(ins.market.suffix)
                    Text { attr { text(ins.displayCode); fontSize(11f); color(AppTheme.textTertiary); marginLeft(4f) } }
                }
            }
            View {
                attr { flex(1f); alignItemsCenter() }
                if (snap.sparkline.size >= 2) {
                    SparklineChart(snap.sparkline, 72f, 28f, color, Color.TRANSPARENT)
                }
            }
            View {
                attr { alignItemsFlexEnd(); width(96f) }
                Text { attr { text(NumberFormat.price(q.price)); fontSize(15f); fontWeight600(); color(color) } }
                View { attr { marginTop(3f) }; ChangeBadge(q.change, NumberFormat.signedPct(q.changePct), 11f, filled = true) }
            }
        }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider); marginLeft(14f) } }
    }
}

/** 市场小标签（HK / SH / US） */
fun ViewContainer<*, *>.MarketTag(label: String) {
    View {
        attr {
            paddingLeft(4f); paddingRight(4f); paddingTop(1f); paddingBottom(1f)
            borderRadius(3f)
            backgroundColor(AppTheme.inkSoft)
        }
        Text { attr { text(label); fontSize(9f); fontWeight600(); color(AppTheme.textSecondary) } }
    }
}

// endregion

// region 消息项
