package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.Conversation
import com.kuikly.stockchat.domain.util.DateUtil
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
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
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 参考 Kimi：展开时露出约 20% 的主页。 */
internal fun chatDrawerWidth(pageWidth: Float): Float = pageWidth * 0.80f

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
 * 抽屉内容：顶栏图标 + 技能卡 + 按日分组会话 + 底部账号行。
 * 定位（absolutePosition / width / animation）由调用方在 StockChatPage 中处理。
 */
internal fun ViewContainer<*, *>.HistoryDrawerContent(
    vm: ChatViewModel,
    statusBarHeight: Float,
    bottomInset: Float,
    onClose: () -> Unit,
    onOpen: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onPan: (PanGestureParams) -> Unit,
) {
    View {
        attr {
            flex(1f)
            paddingTop(statusBarHeight + 4f)
            backgroundColor(DrawerMotion.DRAWER_BG)
        }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(8f); paddingRight(8f)
                height(AppTheme.navBarHeight)
            }
            IconButton(IconKind.MENU, size = 40f, iconSize = 20f, color = AppTheme.textPrimary, onClick = onClose)
            View { attr { flex(1f) } }
            IconButton(
                kind = IconKind.SEARCH,
                size = 40f,
                iconSize = 20f,
                color = AppTheme.textPrimary,
                onClick = {
                    vm.drawerSearchOpen = !vm.drawerSearchOpen
                    if (!vm.drawerSearchOpen) vm.onDrawerQueryChange("")
                },
            )
            IconButton(IconKind.MESSAGE_PLUS, size = 40f, iconSize = 20f, color = AppTheme.textPrimary, onClick = onNewChat)
        }
        vif({ vm.drawerSearchOpen }) {
            View {
                attr {
                    marginLeft(16f); marginRight(16f); marginTop(4f); marginBottom(8f)
                    height(40f)
                    borderRadius(12f)
                    backgroundColor(Color.WHITE)
                    flexDirectionRow(); alignItemsCenter()
                    paddingLeft(12f); paddingRight(12f)
                }
                Icon(IconKind.SEARCH, 15f, AppTheme.textTertiary)
                Input {
                    attr {
                        flex(1f)
                        height(24f)
                        marginLeft(8f)
                        fontSize(15f)
                        color(AppTheme.textPrimary)
                        placeholder("搜索对话")
                        placeholderColor(AppTheme.textTertiary)
                        autofocus(true)
                    }
                    event { textDidChange { vm.onDrawerQueryChange(it.text) } }
                }
            }
        }
        velse {
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    marginLeft(16f); marginRight(16f); marginTop(6f); marginBottom(8f)
                    height(52f)
                    borderRadius(16f)
                    backgroundColor(Color.WHITE)
                    paddingLeft(16f); paddingRight(14f)
                    boxShadow(BoxShadow(0f, 2f, 12f, Color(0x0F000000L)))
                }
                Icon(IconKind.LAYERS, 20f, AppTheme.textPrimary, 1.6f)
                Text {
                    attr {
                        text("全部技能")
                        fontSize(16f)
                        fontWeight600()
                        color(AppTheme.textPrimary)
                        marginLeft(10f)
                        flex(1f)
                    }
                }
                Icon(IconKind.CHEVRON_RIGHT, 16f, AppTheme.textTertiary)
            }
        }
        vif({ vm.drawerConversations.isEmpty() }) {
            View {
                attr { flex(1f); allCenter(); paddingBottom(bottomInset) }
                Icon(IconKind.MESSAGE, 36f, AppTheme.textTertiary, 1.4f)
                Text {
                    attr {
                        text(if (vm.drawerQuery.isBlank()) "还没有历史对话" else "没有匹配的对话")
                        fontSize(13f)
                        color(AppTheme.textTertiary)
                        marginTop(10f)
                    }
                }
                Text {
                    attr {
                        text(if (vm.drawerQuery.isBlank()) "点右上角开始新对话" else "换个关键词试试")
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
                    attr { flex(1f); paddingBottom(8f) }
                    vfor({ vm.drawerConversations }) { conversation ->
                        View {
                            attr { capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL)) }
                            event { followPan(onPan) }
                            if (isSectionStart(vm.drawerConversations, conversation)) {
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
            }
        }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(16f); paddingRight(10f)
                height(64f + bottomInset)
                paddingBottom(bottomInset)
                borderTop(Border(0.5f, BorderStyle.SOLID, Color(0xFFE8E8E8L)))
            }
            Image {
                attr {
                    size(36f, 36f)
                    borderRadius(18f)
                    src(ImageUri.commonAssets("robot.png"))
                }
            }
            Text {
                attr {
                    text("小财助手")
                    fontSize(15f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(10f)
                    flex(1f)
                    lines(1)
                    textOverFlowTail()
                }
            }
            IconButton(IconKind.SETTINGS, size = 40f, iconSize = 20f, color = AppTheme.textPrimary, onClick = onSettings)
        }
    }
}

private fun ViewContainer<*, *>.SectionTitle(title: String) {
    View {
        attr { paddingLeft(16f); paddingRight(16f); paddingTop(18f); paddingBottom(6f) }
        Text { attr { text(title); fontSize(12f); color(Color(0xFFB0B0B0L)) } }
    }
}

private fun bucketOf(updatedAt: Long): String {
    val (nowYear, nowMonth, nowDay) = DateUtil.civilFromEpochMillis(DateTime.currentTimestamp())
    val (year, month, day) = DateUtil.civilFromEpochMillis(updatedAt)
    // 按自然日计算天数差，避免相对时间漂移导致跨日分组错乱
    val dayDiff = DateUtil.daysFromCivil(nowYear, nowMonth, nowDay) - DateUtil.daysFromCivil(year, month, day)
    return when {
        dayDiff <= 0L -> "今天"
        dayDiff == 1L -> "昨天"
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
            paddingLeft(16f); paddingRight(16f); paddingTop(14f); paddingBottom(14f)
            borderBottom(Border(0.5f, BorderStyle.SOLID, Color(0xFFEBEBEBL)))
        }
        event {
            click { onOpen(conversation.id) }
            longPress { if (it.state == "start") onDelete(conversation.id) }
        }
        Text {
            attr {
                text(conversation.title.ifEmpty { "新对话" })
                fontSize(16f)
                if (isActive) fontWeight600() else fontWeight400()
                color(AppTheme.textPrimary)
                lines(1)
                textOverFlowTail()
            }
        }
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
