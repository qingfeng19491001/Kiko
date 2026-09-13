package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.ResearchStage
import com.kuikly.stockchat.ui.answer.AnswerBlockView
import com.kuikly.stockchat.ui.components.BrandMark
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.StreamingMarkdownView
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.SelectionType

fun ViewContainer<*, *>.UserMessageView(message: ChatUiMessage) {
    View {
        attr {
            flexDirectionRow()
            justifyContentFlexEnd()
            paddingLeft(64f)
            paddingRight(AppTheme.pageHorizontalPadding)
            marginTop(20f)
        }
        View {
            attr {
                backgroundColor(AppTheme.userBubble)
                borderRadius(AppTheme.bubbleRadius)
                paddingLeft(14f); paddingRight(14f); paddingTop(10f); paddingBottom(10f)
            }
            Text {
                attr {
                    text(message.text)
                    fontSize(15f)
                    lineHeight(22f)
                    color(AppTheme.textPrimary)
                }
            }
            vif({ message.attachments.isNotEmpty() }) {
                View {
                    attr { flexDirectionRow(); flexWrapWrap(); marginTop(8f) }
                    message.attachments.forEach { attachment ->
                        PendingAttachmentCard(attachment, onRemove = null)
                    }
                }
            }
        }
    }
}

fun ViewContainer<*, *>.AssistantMessageView(
    message: ChatUiMessage,
    contentWidth: Float,
    onOpenInstrument: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    onRetry: () -> Unit,
    onSpeak: (ChatUiMessage) -> Unit,
    onFeedback: (ChatUiMessage, Int) -> Unit,
    onShare: (ChatUiMessage) -> Unit,
    onCopy: (String) -> Unit,
) {
    View {
        ref { message.selectionRef = it }
        attr {
            paddingLeft(AppTheme.pageHorizontalPadding)
            paddingRight(AppTheme.pageHorizontalPadding)
            marginTop(18f)
            selectable(SelectableOption.ENABLE)
            selectionColor(AppTheme.accent)
        }
        event {
            longPress { params ->
                if (params.state == "start") {
                    message.selectionMenuVisible = false
                    message.selectionRef?.view?.createSelection(params.x, params.y, SelectionType.WORD)
                }
            }
            selectEnd { frame ->
                message.selectionRef?.view?.getSelection { selection ->
                    message.selectedText = selection.content.joinToString("\n").trim()
                    message.selectionMenuX = frame.x.coerceIn(0f, (contentWidth - 72f).coerceAtLeast(0f))
                    message.selectionMenuY = (frame.y - 38f).coerceAtLeast(0f)
                    message.selectionMenuVisible = message.selectedText.isNotBlank()
                }
            }
            selectCancel {
                message.selectionMenuVisible = false
                message.selectedText = ""
            }
        }
        // 身份行
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
            BrandMark(20f)
            Text {
                attr {
                    text("Kiko")
                    fontSize(12f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                }
            }
        }
        ResearchTraceView(message)
        // 内容块
        vfor({ message.blocks }) { block ->
            View {
                attr {
                    opacity(if (block.revealed) 1f else 0f)
                    transform(Translate(0f, 0f, 0f, if (block.revealed) 0f else 12f))
                    animate(Animation.easeOut(0.26f), block.revealed)
                }
                when (block) {
                    is UiBlock.Markdown -> StreamingMarkdownView(block.model)
                    is UiBlock.Card -> AnswerBlockView(
                        block = block.block,
                        contentWidth = contentWidth,
                        onOpenInstrument = onOpenInstrument,
                        onFollowUp = onFollowUp,
                        probe = message.probe(block.index),
                        tableState = message.tableState(block.index),
                        chartState = message.chartState(block.index),
                    )
                }
            }
        }
        // 错误
        vif({ message.status == MessageStatus.ERROR }) {
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    marginTop(4f)
                    padding(10f)
                    borderRadius(8f)
                    border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                }
                Icon(IconKind.WIFI_OFF, 16f, AppTheme.down)
                Text {
                    attr {
                        text(message.errorMessage.ifEmpty { "回答生成失败" })
                        fontSize(13f)
                        color(AppTheme.textSecondary)
                        marginLeft(8f)
                        flex(1f)
                    }
                }
                View {
                    attr {
                        flexDirectionRow(); alignItemsCenter()
                        paddingLeft(10f); paddingRight(10f); paddingTop(5f); paddingBottom(5f)
                        borderRadius(6f)
                        backgroundColor(AppTheme.ink)
                    }
                    event { click { onRetry() } }
                    Icon(IconKind.REFRESH, 12f, Color.WHITE)
                    Text { attr { text("重试"); fontSize(12f); color(Color.WHITE); marginLeft(4f) } }
                }
            }
        }
        // 流式光标
        vif({ message.status == MessageStatus.STREAMING }) {
            View { attr { width(8f); height(14f); borderRadius(1f); backgroundColor(AppTheme.ink); marginTop(4f) } }
        }
        AnswerActionBar(message, onRetry, onSpeak, onFeedback, onShare)
        vif({ message.selectionMenuVisible }) {
            View {
                attr {
                    absolutePosition(left = message.selectionMenuX, top = message.selectionMenuY)
                    height(32f)
                    paddingLeft(14f); paddingRight(14f)
                    borderRadius(8f)
                    backgroundColor(AppTheme.ink)
                    allCenter()
                    zIndex(30, useOutline = false)
                    selectable(SelectableOption.DISABLE)
                }
                event {
                    click {
                        val text = message.selectedText
                        message.selectionMenuVisible = false
                        message.selectionRef?.view?.clearSelection()
                        onCopy(text)
                    }
                }
                Text { attr { text("复制"); fontSize(13f); fontWeight600(); color(Color.WHITE) } }
            }
        }
    }
}

/**
 * 参考券商 AI 的“研究过程”交互，但每一步都由 AiService 的真实回调驱动。
 * 开始时展开，正文出现后自动收起；用户随时可以展开核对数据链路。
 */
internal fun ViewContainer<*, *>.ResearchTraceView(message: ChatUiMessage) {
    View {
        attr {
            backgroundColor(if (message.researchStage == ResearchStage.COMPLETE) AppTheme.upSoft else AppTheme.surfaceMuted)
            borderRadius(10f)
            paddingLeft(12f); paddingRight(12f); paddingTop(10f); paddingBottom(10f)
            marginBottom(10f)
            animate(Animation.easeOut(0.22f), message.researchStage)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            event { click { message.researchExpanded = !message.researchExpanded } }
            vif({ !message.researchInterrupted && (message.status == MessageStatus.THINKING || message.status == MessageStatus.STREAMING) }) {
                ActivityIndicator { attr { isGrayStyle(true) } }
            }
            velse {
                View {
                    attr {
                        size(17f, 17f)
                        borderRadius(8.5f)
                        backgroundColor(if (message.researchInterrupted || message.status == MessageStatus.ERROR) AppTheme.downSoft else AppTheme.upSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(if (message.researchInterrupted || message.status == MessageStatus.ERROR) "!" else "✓")
                            fontSize(10f)
                            fontWeight700()
                            color(if (message.researchInterrupted || message.status == MessageStatus.ERROR) AppTheme.down else AppTheme.up)
                        }
                    }
                }
            }
            View {
                attr { flex(1f); marginLeft(8f) }
                Text {
                    attr {
                        text(
                            if (message.status == MessageStatus.ERROR) {
                                "研究过程已中断"
                            } else if (message.researchInterrupted) {
                                "生成已停止"
                            } else {
                                when (message.status) {
                                    MessageStatus.THINKING -> message.researchDetail
                                    MessageStatus.STREAMING -> "正在生成回答"
                                    MessageStatus.DONE -> "已完成思考"
                                    MessageStatus.ERROR -> "研究过程已中断"
                                }
                            },
                        )
                        fontSize(13f)
                        fontWeight600()
                        color(AppTheme.textPrimary)
                    }
                }
                vif({ !message.researchExpanded }) {
                    Text {
                        attr {
                            text(message.researchDetail)
                            fontSize(10f)
                            color(AppTheme.textTertiary)
                            marginTop(2f)
                            lines(1)
                        }
                    }
                }
            }
            Icon(if (message.researchExpanded) IconKind.CHEVRON_DOWN else IconKind.CHEVRON_RIGHT, 14f, AppTheme.textTertiary)
        }
        vif({ message.researchExpanded }) {
            View {
                attr {
                    marginTop(9f)
                    paddingTop(8f)
                    borderTop(Border(0.5f, BorderStyle.SOLID, AppTheme.divider))
                }
                ResearchStepRow("理解问题与分析目标", { message.researchStage.ordinal > ResearchStage.UNDERSTANDING.ordinal }, { message.researchStage == ResearchStage.UNDERSTANDING })
                ResearchStepRow("识别股票、指数与对比范围", { message.researchStage.ordinal > ResearchStage.RESOLVING_INSTRUMENTS.ordinal }, { message.researchStage == ResearchStage.RESOLVING_INSTRUMENTS })
                ResearchStepRow("读取实时行情与历史走势", { message.researchStage.ordinal > ResearchStage.FETCHING_MARKET_DATA.ordinal }, { message.researchStage == ResearchStage.FETCHING_MARKET_DATA })
                ResearchStepRow("交叉分析并组织结构化回答", { message.researchStage == ResearchStage.COMPLETE }, { message.researchStage == ResearchStage.SYNTHESIZING })
                Text {
                    attr {
                        text(message.researchDetail)
                        fontSize(10f)
                        color(AppTheme.textTertiary)
                        marginLeft(22f)
                        marginTop(3f)
                    }
                }
                vif({ message.researchSources.isNotEmpty() }) {
                    View {
                        attr {
                            marginTop(10f); paddingTop(8f)
                            borderTop(Border(0.5f, BorderStyle.SOLID, AppTheme.divider))
                        }
                        Text {
                            attr {
                                text("数据来源（${message.researchSources.size}）")
                                fontSize(10f); fontWeight600(); color(AppTheme.textSecondary); marginBottom(4f)
                            }
                        }
                        vfor({ message.researchSources }) { source ->
                            View {
                                attr { flexDirectionRow(); paddingTop(5f); paddingBottom(5f) }
                                View {
                                    attr { size(20f, 20f); borderRadius(5f); backgroundColor(AppTheme.surface); allCenter() }
                                    Text {
                                        attr {
                                            text((message.researchSources.indexOf(source) + 1).toString().padStart(2, '0'))
                                            fontSize(8f); fontWeight600(); color(AppTheme.accent)
                                        }
                                    }
                                }
                                View {
                                    attr { flex(1f); marginLeft(8f) }
                                    Text { attr { text(source.title); fontSize(11f); fontWeight600(); color(AppTheme.textPrimary) } }
                                    Text { attr { text(source.detail); fontSize(9f); color(AppTheme.textTertiary); marginTop(2f) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.ResearchStepRow(
    label: String,
    done: () -> Boolean,
    active: () -> Boolean,
) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); height(25f) }
        View {
            attr {
                size(14f, 14f)
                borderRadius(7f)
                backgroundColor(if (done()) AppTheme.upSoft else if (active()) AppTheme.accentSoft else AppTheme.surface)
                border(Border(1f, BorderStyle.SOLID, if (done()) AppTheme.up else if (active()) AppTheme.accent else AppTheme.border))
                allCenter()
            }
            Text {
                attr {
                    text(if (done()) "✓" else if (active()) "·" else "")
                    fontSize(9f)
                    fontWeight700()
                    color(if (done()) AppTheme.up else AppTheme.accent)
                }
            }
        }
        Text {
            attr {
                text(label)
                fontSize(11f)
                color(if (done() || active()) AppTheme.textSecondary else AppTheme.textTertiary)
                marginLeft(8f)
            }
        }
    }
}

internal fun ViewContainer<*, *>.AnswerActionBar(
    message: ChatUiMessage,
    onRetry: () -> Unit,
    onSpeak: (ChatUiMessage) -> Unit,
    onFeedback: (ChatUiMessage, Int) -> Unit,
    onShare: (ChatUiMessage) -> Unit,
) {
    View {
        attr {
            flexDirectionRow(); alignItemsCenter()
            val visible = message.status == MessageStatus.DONE && message.blocks.isNotEmpty()
            height(if (visible) 43f else 0f)
            opacity(if (visible) 1f else 0f)
            transform(Translate(0f, 0f, 0f, if (visible) 0f else 8f))
            overflow(false)
            paddingTop(9f)
            borderTop(Border(0.5f, BorderStyle.SOLID, AppTheme.divider))
            animate(Animation.easeOut(0.24f), visible)
        }
        AnswerActionButton(if (message.isSpeaking) IconKind.SPEAKER_OFF else IconKind.SPEAKER, message.isSpeaking) { onSpeak(message) }
        AnswerActionButton(IconKind.THUMBS_UP, message.feedback == 1) { onFeedback(message, 1) }
        AnswerActionButton(IconKind.THUMBS_DOWN, message.feedback == -1) { onFeedback(message, -1) }
        AnswerActionButton(IconKind.SHARE, false) { onShare(message) }
        AnswerActionButton(IconKind.REFRESH, false, onRetry)
        View { attr { flex(1f) } }
        Text { attr { text("行情有时效性 · 不构成投资建议"); fontSize(8f); color(AppTheme.textTertiary); lines(1) } }
    }
}

internal fun ViewContainer<*, *>.AnswerActionButton(
    icon: IconKind,
    selected: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(32f, 30f); borderRadius(7f); allCenter(); marginRight(5f)
            backgroundColor(if (selected) AppTheme.accentSoft else Color.TRANSPARENT)
        }
        event { click { onClick() } }
        Icon(icon, 15f, if (selected) AppTheme.accent else AppTheme.textSecondary, 1.6f)
    }
}

// endregion

// region 输入框
