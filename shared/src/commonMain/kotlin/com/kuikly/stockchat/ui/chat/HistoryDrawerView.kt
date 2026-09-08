package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.Conversation
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 实机参考：展开时露出约 15% 的主页。 */
internal fun chatDrawerWidth(pageWidth: Float): Float = pageWidth * 0.85f

/**
 * 抽屉进度。0=关闭 1=打开。
 * progress 始终是实际显示进度；拖拽和 Bezier 收尾共享它。
 */
internal class DrawerState {
    var progress by observable(0f)
    var dragging by observable(false)
    var settling by observable(false)
}

/**
 * 浮动主页式抽屉参数。
 * 抽屉进度同时驱动抽屉、主页形变和遮罩，拖拽时完全跟手。
 *
 * 动画曲线由 DrawerPhysics.ease 统一计算。
 * 手势 API：`docs/API/components/basic-attr-event.md` pan
 */
internal object DrawerMotion {
    const val SNAP_THRESHOLD = 0.5f
    const val SCRIM_ALPHA = 0.62f
    const val FLING_VELOCITY = 680f
    const val HOME_MIN_SCALE = 0.96f
    const val HOME_MAX_RADIUS = 32f
    val DRAWER_BG = AppTheme.drawerBackground

    /** 抽屉左偏移：关闭=-drawerWidth（屏外），打开=0。 */
    fun drawerShift(progress: Float, drawerWidth: Float): Float = (progress - 1f) * drawerWidth
    fun travelDistance(drawerWidth: Float): Float = drawerWidth.coerceAtLeast(1f)

}

internal fun Event.followPan(handler: (PanGestureParams) -> Unit) {
    register(EventName.PAN.value, { handler(PanGestureParams.decode(it)) }, isSync = true)
}

/**
 * 抽屉内容：header + 会话列表 + 底部清空。
 * 定位（absolutePosition / width / animation）由调用方在 StockChatPage 中处理。
 */
internal fun ViewContainer<*, *>.HistoryDrawerContent(
    vm: ChatViewModel,
    statusBarHeight: Float,
    bottomInset: Float,
    onOpen: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onNewChat: () -> Unit,
    onClearAll: () -> Unit,
    onPan: (PanGestureParams) -> Unit,
) {
    View {
        attr {
            flex(1f)
            paddingTop(statusBarHeight + 24f)
            backgroundColor(DrawerMotion.DRAWER_BG)
        }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(16f); paddingRight(12f)
                height(AppTheme.navBarHeight)
            }
            View {
                attr {
                    size(40f, 40f)
                    borderRadius(20f)
                    backgroundColor(Color(0xFFEEEEEEL))
                    allCenter()
                }
                Icon(IconKind.CANDLE, 22f, AppTheme.textSecondary, 1.6f)
            }
            View {
                attr { flex(1f); marginLeft(12f); justifyContentCenter() }
                Text {
                    attr {
                        text("StockChat")
                        fontSize(18f)
                        fontWeight700()
                        color(AppTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("历史对话")
                        fontSize(12f)
                        color(AppTheme.textTertiary)
                        marginTop(2f)
                    }
                }
            }
            IconButton(
                kind = IconKind.PLUS,
                size = 40f,
                iconSize = 18f,
                color = AppTheme.textPrimary,
                background = Color.WHITE,
                shadow = true,
                onClick = onNewChat,
            )
        }
        vif({ vm.conversations.isEmpty() }) {
            View {
                attr { flex(1f); allCenter(); paddingBottom(bottomInset) }
                Icon(IconKind.MESSAGE, 40f, AppTheme.textTertiary, 1.4f)
                Text {
                    attr {
                        text("还没有历史对话")
                        fontSize(13f)
                        color(AppTheme.textTertiary)
                        marginTop(10f)
                    }
                }
                Text {
                    attr {
                        text("点右上角开始新对话")
                        fontSize(12f)
                        color(AppTheme.textTertiary)
                        marginTop(6f)
                    }
                }
            }
        }
        velse {
            View {
                attr { flex(1f) }
                List {
                    attr {
                        flex(1f)
                        paddingBottom(8f)
                    }
                    vfor({ vm.conversations }) { conversation ->
                        View {
                            attr { capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL)) }
                            event { followPan(onPan) }
                            if (isSectionStart(vm.conversations, conversation)) {
                                SectionTitle(bucketOf(conversation.updatedAt))
                            }
                            ConversationItemView(
                                conversation,
                                vm.currentConversationId == conversation.id,
                                onOpen,
                                onDelete,
                            )
                        }
                    }
                }
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter(); justifyContentCenter()
                        height(48f + bottomInset)
                        paddingBottom(bottomInset)
                    }
                    event { click { onClearAll() } }
                    Icon(IconKind.TRASH, 14f, AppTheme.textTertiary)
                    Text { attr { text("清空全部记录"); fontSize(12f); color(AppTheme.textTertiary); marginLeft(8f) } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.SectionTitle(title: String) {
    View {
        attr { paddingLeft(16f); paddingRight(16f); paddingTop(14f); paddingBottom(8f) }
        Text { attr { text(title); fontSize(12f); fontWeight500(); color(AppTheme.textTertiary) } }
    }
}

private fun bucketOf(updatedAt: Long): String {
    val diff = DateTime.currentTimestamp() - updatedAt
    return when {
        diff < 24L * 3600_000L -> "今天"
        diff < 48L * 3600_000L -> "昨天"
        diff < 7L * 24 * 3600_000L -> "7 天内"
        else -> "更早"
    }
}

private fun isSectionStart(list: List<Conversation>, conversation: Conversation): Boolean {
    val index = list.indexOfFirst { it.id == conversation.id }
    if (index <= 0) return true
    return bucketOf(list[index - 1].updatedAt) != bucketOf(conversation.updatedAt)
}

private fun ViewContainer<*, *>.ConversationItemView(
    conversation: Conversation,
    isActive: Boolean,
    onOpen: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    View {
        attr {
            flexDirectionRow(); alignItemsCenter()
            marginLeft(12f); marginRight(12f); marginTop(4f)
            paddingLeft(12f); paddingRight(6f); paddingTop(12f); paddingBottom(12f)
            borderRadius(8f)
            backgroundColor(if (isActive) Color.WHITE else Color.TRANSPARENT)
        }
        event { click { onOpen(conversation.id) } }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text(conversation.title.ifEmpty { "新对话" })
                    fontSize(15f)
                    if (isActive) fontWeight700() else fontWeight500()
                    color(AppTheme.textPrimary)
                    lines(1)
                    textOverFlowTail()
                }
            }
            Text {
                attr {
                    text("${conversation.messages.size} 条消息 · ${relativeTime(conversation.updatedAt)}")
                    fontSize(12f)
                    color(AppTheme.textTertiary)
                    marginTop(3f)
                }
            }
        }
        IconButton(IconKind.TRASH, size = 32f, iconSize = 16f, color = AppTheme.textTertiary) { onDelete(conversation.id) }
    }
}

fun relativeTime(timestamp: Long): String {
    val now = DateTime.currentTimestamp()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}
