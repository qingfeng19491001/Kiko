package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.Conversation
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.PrimaryButton
import com.kuikly.stockchat.ui.components.BrandMark
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 历史会话抽屉：左侧滑出，带遮罩。使用响应式 transform + animation 实现滑动动画。
 */
fun ViewContainer<*, *>.HistoryDrawerView(
    vm: ChatViewModel,
    pageWidth: Float,
    statusBarHeight: Float,
    bottomInset: Float,
    onOpen: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onNewChat: () -> Unit,
    onClearAll: () -> Unit,
) {
    val drawerWidth = (pageWidth * 0.8f).coerceAtMost(320f)
    // 遮罩
    View {
        attr {
            absolutePosition(0f, 0f, 0f, 0f)
            backgroundColor(AppTheme.scrim)
            opacity(if (vm.showHistory) 1f else 0f)
            animation(Animation.easeOut(0.25f), vm.showHistory)
            touchEnable(vm.showHistory)
            zIndex(20)
        }
        event { click { vm.showHistory = false } }
    }
    // 抽屉
    View {
        attr {
            absolutePosition(top = 0f, left = 0f, bottom = 0f)
            width(drawerWidth)
            backgroundColor(AppTheme.surface)
            transform(Translate(if (vm.showHistory) 0f else -1f, 0f))
            animation(Animation.easeOut(0.25f), vm.showHistory)
            zIndex(21)
            paddingTop(statusBarHeight)
            paddingBottom(bottomInset)
        }
        // 头部
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(16f); paddingRight(8f)
                height(AppTheme.navBarHeight)
            }
            BrandMark(22f)
            Text {
                attr {
                    text("历史对话")
                    fontSize(16f)
                    fontWeight700()
                    color(AppTheme.textPrimary)
                    marginLeft(8f)
                    flex(1f)
                }
            }
            IconButton(IconKind.CLOSE, iconSize = 20f, color = AppTheme.textSecondary) { vm.showHistory = false }
        }
        View {
            attr { paddingLeft(16f); paddingRight(16f); paddingTop(4f); paddingBottom(12f) }
            PrimaryButton("开始新对话", IconKind.PLUS, onClick = onNewChat)
        }
        View { attr { height(0.5f); backgroundColor(AppTheme.divider) } }
        // 列表
        vif({ vm.conversations.isEmpty() }) {
            View {
                attr { flex(1f); allCenter() }
                Icon(IconKind.MESSAGE, 40f, AppTheme.textTertiary, 1.4f)
                Text {
                    attr {
                        text("还没有历史对话")
                        fontSize(13f)
                        color(AppTheme.textTertiary)
                        marginTop(10f)
                    }
                }
            }
        }
        velse {
            View {
                attr { flex(1f) }
                List {
                    attr { flex(1f) }
                    vfor({ vm.conversations }) { conversation ->
                        ConversationItemView(conversation, vm.currentConversationId == conversation.id, onOpen, onDelete)
                    }
                }
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter(); justifyContentCenter()
                        height(44f)
                    }
                    event { click { onClearAll() } }
                    Icon(IconKind.TRASH, 14f, AppTheme.textTertiary)
                    Text { attr { text("清空全部记录"); fontSize(12f); color(AppTheme.textTertiary); marginLeft(4f) } }
                }
            }
        }
    }
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
            marginLeft(10f); marginRight(10f); marginTop(6f)
            paddingLeft(10f); paddingRight(4f); paddingTop(10f); paddingBottom(10f)
            borderRadius(10f)
            backgroundColor(if (isActive) AppTheme.surfaceMuted else Color.TRANSPARENT)
        }
        event { click { onOpen(conversation.id) } }
        Icon(IconKind.MESSAGE, 18f, if (isActive) AppTheme.textPrimary else AppTheme.textTertiary)
        View {
            attr { flex(1f); marginLeft(10f) }
            Text {
                attr {
                    text(conversation.title.ifEmpty { "新对话" })
                    fontSize(14f)
                    fontWeight500()
                    color(AppTheme.textPrimary)
                    lines(1)
                    textOverFlowTail()
                }
            }
            Text {
                attr {
                    text("${conversation.messages.size} 条消息 · ${relativeTime(conversation.updatedAt)}")
                    fontSize(11f)
                    color(AppTheme.textTertiary)
                    marginTop(3f)
                }
            }
        }
        IconButton(IconKind.TRASH, size = 32f, iconSize = 16f, color = AppTheme.textTertiary) { onDelete(conversation.id) }
    }
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 */
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
