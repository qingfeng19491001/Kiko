package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// region 导航栏

fun ViewContainer<*, *>.ChatNavBar(
    statusBarHeight: Float,
    ttsEnabled: Boolean,
    onMenu: () -> Unit,
    onTts: () -> Unit,
    onMarket: (() -> Unit)? = null,
) {
    View {
        attr {
            backgroundColor(Color.WHITE)
            paddingTop(statusBarHeight)
        }
        View {
            attr {
                height(AppTheme.navBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(AppTheme.pageHorizontalPadding)
                paddingRight(AppTheme.pageHorizontalPadding)
            }
            IconButton(
                kind = IconKind.MENU,
                size = 36f,
                iconSize = 18f,
                color = AppTheme.textPrimary,
                background = Color.WHITE,
                shadow = true,
                onClick = onMenu,
            )
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter(); paddingLeft(8f) }
                Text {
                    attr {
                        text("Kiko")
                        fontSize(17f)
                        fontWeight600()
                        color(AppTheme.textPrimary)
                    }
                }
            }
            if (onMarket != null) {
                ChatNavTrailingCluster(
                    ttsEnabled = ttsEnabled,
                    onTts = onTts,
                    onClose = onMarket,
                )
            } else {
                IconButton(
                    kind = if (ttsEnabled) IconKind.SPEAKER else IconKind.SPEAKER_OFF,
                    size = 36f,
                    iconSize = 18f,
                    color = AppTheme.textPrimary,
                    background = Color.WHITE,
                    shadow = true,
                    onClick = onTts,
                )
            }
        }
    }
}

private fun ViewContainer<*, *>.ChatNavTrailingCluster(
    ttsEnabled: Boolean,
    onTts: () -> Unit,
    onClose: () -> Unit,
) {
    View {
        attr {
            height(36f)
            flexDirectionRow()
            alignItemsCenter()
            borderRadius(18f)
            backgroundColor(Color.WHITE)
            border(Border(0.8f, BorderStyle.SOLID, Color(0x1A000000L)))
        }
        View {
            attr { width(40f); height(36f); allCenter() }
            event { click { onTts() } }
            Icon(
                if (ttsEnabled) IconKind.SPEAKER else IconKind.SPEAKER_OFF,
                18f,
                AppTheme.textPrimary,
                1.8f,
            )
        }
        View {
            attr {
                width(0.8f)
                height(16f)
                backgroundColor(Color(0x1A000000L))
            }
        }
        View {
            attr { width(40f); height(36f); allCenter() }
            event { click { onClose() } }
            Icon(IconKind.CLOSE, 15f, AppTheme.textPrimary, 1.8f)
        }
    }
}

// endregion

// region 首页（欢迎态）
