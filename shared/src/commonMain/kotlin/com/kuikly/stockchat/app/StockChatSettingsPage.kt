package com.kuikly.stockchat.app

import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.chat.SettingsRepository
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconButton
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.AlertDialog
import com.tencent.kuikly.core.views.Switch
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("StockChatSettings")
internal class StockChatSettingsPage : Pager() {

    private lateinit var settings: SettingsRepository
    private lateinit var conversations: ConversationRepository
    private var ttsEnabled by observable(false)
    private var confirmClear by observable(false)

    override fun created() {
        super.created()
        val store = KuiklyKeyValueStore(this)
        settings = SettingsRepository(store)
        conversations = ConversationRepository(store)
        ttsEnabled = settings.isTtsEnabled()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val topInset = pagerData.statusBarHeight
        val bottomInset = pagerData.safeAreaInsets.bottom
        return {
            attr { backgroundColor(AppTheme.drawerBackground) }
            View {
                attr {
                    paddingTop(topInset)
                    backgroundColor(AppTheme.drawerBackground)
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        height(AppTheme.navBarHeight)
                        paddingLeft(8f)
                        paddingRight(16f)
                    }
                    IconButton(IconKind.BACK, size = 40f, iconSize = 20f, color = AppTheme.textPrimary) {
                        ctx.close()
                    }
                    Text {
                        attr {
                            text("设置")
                            fontSize(17f)
                            fontWeight700()
                            color(AppTheme.textPrimary)
                            marginLeft(4f)
                        }
                    }
                }
            }
            View {
                attr {
                    marginLeft(16f); marginRight(16f); marginTop(12f)
                    backgroundColor(Color.WHITE)
                    borderRadius(16f)
                    overflow(true)
                }
                SettingsToggleRow("语音播报", "回复完成后自动朗读") {
                    Switch {
                        attr {
                            isOn(ctx.ttsEnabled)
                            onColor(AppTheme.ink)
                            unOnColor(Color(0xFFE5E5E5L))
                            thumbColor(Color.WHITE)
                        }
                        event {
                            switchOnChanged { on ->
                                ctx.ttsEnabled = on
                                ctx.settings.setTtsEnabled(on)
                            }
                        }
                    }
                }
            }
            View {
                attr {
                    marginLeft(16f); marginRight(16f); marginTop(12f)
                    backgroundColor(Color.WHITE)
                    borderRadius(16f)
                    overflow(true)
                }
                event { click { ctx.confirmClear = true } }
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        paddingLeft(16f); paddingRight(16f)
                        height(56f)
                    }
                    Text {
                        attr {
                            text("清空聊天记录")
                            fontSize(16f)
                            color(AppTheme.down)
                            flex(1f)
                        }
                    }
                    Icon(IconKind.CHEVRON_RIGHT, 16f, AppTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text("行情数据来自腾讯证券，AI 内容仅供参考，不构成投资建议")
                    fontSize(12f)
                    color(AppTheme.textTertiary)
                    marginTop(20f)
                    marginLeft(24f)
                    marginRight(24f)
                    textAlignCenter()
                }
            }
            View { attr { height(bottomInset + 20f) } }
            AlertDialog {
                attr {
                    showAlert(ctx.confirmClear)
                    title("清空聊天记录")
                    message("将删除本机保存的全部对话，且无法恢复。")
                    actionButtons("取消", "清空")
                    inWindow(true)
                }
                event {
                    clickActionButton { index ->
                        if (index == 1) ctx.conversations.clear()
                        ctx.confirmClear = false
                    }
                    willDismiss { ctx.confirmClear = false }
                }
            }
        }
    }

    private fun close() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }
}

private fun ViewContainer<*, *>.SettingsToggleRow(
    title: String,
    subtitle: String,
    control: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            flexDirectionRow(); alignItemsCenter()
            paddingLeft(16f); paddingRight(16f)
            height(64f)
        }
        View {
            attr { flex(1f); marginRight(12f) }
            Text {
                attr {
                    text(title)
                    fontSize(16f)
                    color(AppTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(subtitle)
                    fontSize(12f)
                    color(AppTheme.textTertiary)
                    marginTop(4f)
                }
            }
        }
        control()
    }
}
