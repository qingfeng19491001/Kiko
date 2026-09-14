package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

fun ViewContainer<*, *>.ComposerCapsulesView(
    pageWidth: Float,
    promptPage: () -> Int,
    visible: () -> Boolean,
    onShuffle: () -> Unit,
    onPrompt: (String) -> Unit,
) {
    View {
        attr {
            val isVisible = visible()
            width(pageWidth)
            height(if (isVisible) 32f else 0f)
            opacity(if (isVisible) 1f else 0f)
            overflow(false)
            animation(Animation.easeOut(0.2f), isVisible)
        }
        Scroller {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                height(32f)
                showScrollerIndicator(false)
                bouncesEnable(true)
            }
            PromptBank.composerCapsules.forEachIndexed { index, item ->
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        height(32f)
                        paddingLeft(12f); paddingRight(14f)
                        skillChip()
                        // 首颗与标题/提问/输入对齐 16；末项 16。横滑时左右都会露出半颗。
                        marginLeft(if (index == 0) AppTheme.pageHorizontalPadding else 8f)
                        marginRight(
                            if (index == PromptBank.composerCapsules.lastIndex) {
                                AppTheme.pageHorizontalPadding
                            } else {
                                0f
                            },
                        )
                    }
                    event { click { onPrompt(item.prompt) } }
                    Icon(PromptBank.iconFor(item.category), 15f, AppTheme.textPrimary, 1.7f)
                    Text {
                        attr {
                            text(item.capsule)
                            fontSize(13f)
                            fontWeight500()
                            color(AppTheme.textPrimary)
                            marginLeft(6f)
                            lines(1)
                        }
                    }
                }
            }
        }
    }
}

fun ViewContainer<*, *>.ComposerView(
    vm: ChatViewModel,
    expanded: () -> Boolean,
    keyboardVisible: () -> Boolean,
    attachmentPanelVisible: () -> Boolean,
    voiceMode: () -> Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onKeyboardHeight: (Float) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onInputRef: (ViewRef<InputView>) -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onVoiceLongPress: (LongPressParams) -> Unit,
    onPickCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
        View {
            attr {
                backgroundColor(Color.WHITE)
                flexDirectionColumn()
                overflow(false)
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                val isExpanded = expanded() && !attachmentPanelVisible()
                paddingLeft(AppTheme.pageHorizontalPadding)
                paddingRight(AppTheme.pageHorizontalPadding)
                paddingTop(4f)
                paddingBottom(if (isExpanded) 8f else 0f)
                animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
            }
            // 聚焦时由单行胶囊展开为上下两层，输入节点本身保持不变，避免丢失焦点。
            View {
                attr {
                    val isExpanded = expanded() && !attachmentPanelVisible()
                    flex(1f)
                    val hasAttachments = vm.pendingAttachments.isNotEmpty()
                    // 展开或有附件时进入“编辑器”两层布局；收起态为单行胶囊。
                    height(
                        if (isExpanded) 82f + if (hasAttachments) 56f else 0f else AppTheme.composerHeight,
                    )
                    minHeight(if (isExpanded) 82f else AppTheme.composerHeight)
                    composerSlot(if (isExpanded) 22f else AppTheme.composerHeight / 2f)
                    animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
                }
                vif({ vm.pendingAttachments.isNotEmpty() }) {
                    View {
                        attr {
                            absolutePosition(left = 10f, top = 8f, right = 10f)
                            height(42f)
                            flexDirectionRow()
                            overflow(true)
                        }
                        vm.pendingAttachments.forEach { attachment ->
                            PendingAttachmentCard(attachment) { onRemoveAttachment(attachment.id) }
                        }
                    }
                }
                View {
                    attr {
                        val isExpanded = expanded() && !attachmentPanelVisible()
                        absolutePosition(left = 6f, top = if (isExpanded) 40f + if (vm.pendingAttachments.isNotEmpty()) 56f else 0f else 6f)
                        size(32f, 32f)
                        borderRadius(16f)
                        allCenter()
                        backgroundColor(Color.TRANSPARENT)
                        animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
                    }
                    event { click { onAttachClick() } }
                    Icon(if (attachmentPanelVisible()) IconKind.CLOSE else IconKind.PLUS, 18f, AppTheme.textSecondary, 1.8f)
                }
                Input {
                    ref { onInputRef(it) }
                    attr {
                        val isExpanded = expanded() && !attachmentPanelVisible()
                        absolutePosition(
                            left = if (isExpanded) 12f else 42f,
                            top = if (isExpanded) 10f + if (vm.pendingAttachments.isNotEmpty()) 56f else 0f else 8f,
                            right = if (isExpanded) 12f else 42f,
                        )
                        height(28f)
                        opacity(if (voiceMode()) 0f else 1f)
                        touchEnable(!voiceMode())
                        fontSize(16f)
                        color(AppTheme.textPrimary)
                        placeholder("输入或按住说话...")
                        placeholderColor(AppTheme.textTertiary)
                        returnKeyTypeSend()
                        maxTextLength(200)
                        animation(Animation.easeOut(0.24f), if (attachmentPanelVisible()) false else isExpanded)
                    }
                    event {
                        textDidChange { vm.inputText = it.text }
                        inputReturn { onSend(it.text) }
                        inputFocus {
                            if (attachmentPanelVisible()) onAttachClick()
                            onFocusChange(true)
                        }
                        inputBlur { onFocusChange(false) }
                        keyboardHeightChange { onKeyboardHeight(it.height) }
                    }
                }
                View {
                    attr {
                        absolutePosition(left = 42f, top = if (vm.pendingAttachments.isNotEmpty()) 56f else 0f, right = 42f, bottom = 0f)
                        allCenter()
                        opacity(if (voiceMode()) 1f else 0f)
                        touchEnable(voiceMode())
                    }
                    event {
                        click { }
                        longPress { onVoiceLongPress(it) }
                    }
                    Text {
                        attr {
                            text("按住说话")
                            fontSize(15f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                        }
                    }
                }
                View {
                    attr {
                        val isExpanded = expanded() && !attachmentPanelVisible()
                        absolutePosition(right = 6f, top = if (isExpanded) 40f + if (vm.pendingAttachments.isNotEmpty()) 56f else 0f else 6f)
                        size(32f, 32f)
                        borderRadius(16f)
                        allCenter()
                        backgroundColor(
                            when {
                                voiceMode() -> AppTheme.surfaceMuted
                                vm.isGenerating -> AppTheme.ink
                                vm.inputText.isNotBlank() || vm.pendingAttachments.isNotEmpty() -> AppTheme.ink
                                else -> Color.TRANSPARENT
                            },
                        )
                        animation(Animation.easeOut(0.24f), isExpanded)
                    }
                    event {
                        click {
                            when {
                                attachmentPanelVisible() -> onAttachClick()
                                voiceMode() -> onVoiceClick()
                                vm.isGenerating -> onStop()
                                vm.inputText.isNotBlank() -> onSend(vm.inputText)
                                else -> onVoiceClick()
                            }
                        }
                    }
                    vif({ attachmentPanelVisible() }) { Icon(IconKind.CLOSE, 18f, AppTheme.textSecondary) }
                    velse {
                        vif({ voiceMode() }) { Icon(IconKind.KEYBOARD, 18f, AppTheme.textSecondary) }
                        velse {
                            vif({ vm.isGenerating }) { Icon(IconKind.STOP, 14f, Color.WHITE) }
                            velse {
                                vif({ vm.inputText.isNotBlank() || vm.pendingAttachments.isNotEmpty() }) {
                                    Icon(IconKind.ARROW_UP, 18f, Color.WHITE, 2.4f)
                                }
                                velse { Icon(IconKind.VOICE, 20f, AppTheme.textSecondary, 1.8f) }
                            }
                        }
                    }
                }
            }
        }
        // 附件面板：嵌入输入框下方，由 ComposerView 统一管理布局，与输入框紧密贴合。
        View {
            attr {
                val isVisible = attachmentPanelVisible()
                // 面板高度包含底部留白，避免入口卡片被 Android 导航栏裁切。
                height(if (isVisible) 144f else 0f)
                opacity(if (isVisible) 1f else 0f)
                overflow(true)
                marginTop(0f)
                zIndex(5, useOutline = false)
                paddingLeft(AppTheme.pageHorizontalPadding)
                paddingRight(AppTheme.pageHorizontalPadding)
                paddingTop(0f)
                paddingBottom(20f)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsStretch()
                    justifyContentSpaceBetween()
                    paddingLeft(10f); paddingRight(10f); paddingTop(10f); paddingBottom(10f)
                }
                AttachmentActionCard(IconKind.CAMERA, "拍照", onPickCamera)
                AttachmentActionCard(IconKind.IMAGE, "相册", onPickPhoto)
                AttachmentActionCard(IconKind.FOLDER, "文件", onPickFile)
            }
        }
        // 键盘或附件面板状态下直接不创建免责声明节点，避免零高度动画节点仍叠加到输入框。
        vif({ !expanded() && !keyboardVisible() && !attachmentPanelVisible() }) {
            View {
                attr { height(16f); allCenter(); marginTop(2f) }
                Text {
                    attr {
                        text("部分内容由AI生成，不构成投资建议")
                        fontSize(10f)
                        color(AppTheme.textTertiary)
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.PendingAttachmentCard(attachment: Attachment, onRemove: (() -> Unit)?) {
    val preview = attachment.thumbnailPath?.ifBlank { null }
        ?: attachment.localPath.takeIf { attachment.kind == AttachmentKind.IMAGE && it.isNotBlank() }
    View {
        attr {
            width(if (attachment.kind == AttachmentKind.IMAGE) 42f else 128f)
            height(40f)
            marginRight(6f)
            borderRadius(9f)
            backgroundColor(AppTheme.surfaceMuted)
            paddingLeft(if (preview != null) 4f else 8f); paddingRight(6f)
            flexDirectionRow(); alignItemsCenter()
            overflow(true)
        }
        if (preview != null) {
            Image {
                attr {
                    size(32f, 32f)
                    borderRadius(6f)
                    resizeCover()
                    src(ImageUri.file(preview))
                }
            }
        } else {
            Text {
                attr {
                    text(if (attachment.kind == AttachmentKind.IMAGE) "图片" else attachment.displayName)
                    fontSize(10f); color(AppTheme.textSecondary); lines(1); flex(1f)
                }
            }
        }
        if (onRemove != null) {
            View {
                attr { size(18f, 18f); allCenter() }
                event { click { onRemove() } }
                Text { attr { text("×"); fontSize(13f); color(AppTheme.textTertiary) } }
            }
        }
    }
}
