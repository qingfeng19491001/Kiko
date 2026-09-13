package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.sin

enum class VoiceRecordZone { SEND, EDIT, CANCEL }

private val voiceSendBlue = Color(0xFF4D8EFFL)
private val voiceEditGray = Color(0xFFC4C4C4L)
private val voiceFingerFill = Color(0, 0, 0, 0.12f)

internal const val VOICE_OVERLAY_BODY = 360f

fun voiceRecordZone(
    pageX: Float,
    pageY: Float,
    pageWidth: Float,
    pageHeight: Float,
    bottomInset: Float,
): VoiceRecordZone {
    val fromBottom = pageHeight - pageY
    if (fromBottom <= 88f + bottomInset) return VoiceRecordZone.SEND
    if (fromBottom <= 300f + bottomInset) {
        val edge = pageWidth * 0.42f
        if (pageX < edge) return VoiceRecordZone.CANCEL
        if (pageX > pageWidth - edge) return VoiceRecordZone.EDIT
    }
    return VoiceRecordZone.SEND
}

/** Kimi 式长按录音浮层：面板升起，三态拖动，波形随区域变色。 */
fun ViewContainer<*, *>.VoiceRecordingOverlay(
    visible: () -> Boolean,
    phase: () -> Int,
    zone: () -> VoiceRecordZone,
    fingerX: () -> Float,
    fingerY: () -> Float,
    pageHeight: Float,
    bottomInset: Float,
) {
    val overlayHeight = VOICE_OVERLAY_BODY + bottomInset
    View {
        attr {
            val isVisible = visible()
            absolutePosition(left = 0f, right = 0f, bottom = 0f)
            height(overlayHeight)
            paddingBottom(bottomInset)
            backgroundColor(Color.WHITE)
            borderRadius(28f, 28f, 0f, 0f)
            boxShadow(BoxShadow(0f, -6f, 28f, Color(0x14000000L)))
            opacity(if (isVisible) 1f else 0f)
            transform(Translate(0f, 0f, 0f, if (isVisible) 0f else overlayHeight + 24f))
            animation(Animation.easeOut(0.26f), isVisible)
            touchEnable(false)
            zIndex(20, useOutline = false)
        }
        View {
            attr { alignItemsCenter(); paddingTop(56f) }
            VoiceWave(phase, zone)
            Text {
                attr {
                    val current = zone()
                    text(
                        when (current) {
                            VoiceRecordZone.SEND -> "松开发送"
                            VoiceRecordZone.EDIT -> "编辑文字"
                            VoiceRecordZone.CANCEL -> "取消输入"
                        },
                    )
                    fontSize(12f)
                    color(AppTheme.textTertiary)
                    marginTop(12f)
                    animation(Animation.easeOut(0.16f), current)
                }
            }
        }
        View { attr { flex(1f) } }
        View {
            attr {
                flexDirectionRow(); justifyContentSpaceBetween(); alignItemsCenter()
                paddingLeft(36f); paddingRight(36f); marginBottom(22f); height(48f)
            }
            VoiceTargetChip(
                armed = { zone() == VoiceRecordZone.CANCEL },
                danger = true,
                icon = IconKind.CLOSE,
                label = "取消",
            )
            VoiceTargetChip(
                armed = { zone() == VoiceRecordZone.EDIT },
                danger = false,
                icon = IconKind.EDIT,
                label = "编辑",
            )
        }
        View {
            attr {
                val sending = zone() == VoiceRecordZone.SEND
                height(56f)
                marginLeft(8f); marginRight(8f); marginBottom(8f)
                borderRadius(28f)
                allCenter()
                backgroundColor(if (sending) Color.WHITE else AppTheme.surfaceMuted)
                boxShadow(
                    if (sending) BoxShadow(0f, 4f, 18f, Color(0x18000000L))
                    else BoxShadow(0f, 0f, 0f, Color.TRANSPARENT),
                )
                animation(Animation.easeOut(0.18f), sending)
            }
            Text {
                attr {
                    val sending = zone() == VoiceRecordZone.SEND
                    text(if (sending) "松开发送" else "语音输入")
                    fontSize(15f); fontWeight600()
                    color(if (sending) voiceSendBlue else AppTheme.textTertiary)
                    animation(Animation.easeOut(0.18f), sending)
                }
            }
        }
        View {
            attr {
                val isVisible = visible()
                absolutePosition(
                    left = fingerX() - 19f,
                    top = fingerY() - (pageHeight - overlayHeight) - 19f,
                )
                size(38f, 38f)
                borderRadius(19f)
                backgroundColor(voiceFingerFill)
                opacity(if (isVisible) 1f else 0f)
                touchEnable(false)
                animation(Animation.linear(0.01f), fingerX() + fingerY())
            }
        }
    }
}

internal fun ViewContainer<*, *>.VoiceTargetChip(
    armed: () -> Boolean,
    danger: Boolean,
    icon: IconKind,
    label: String,
) {
    View {
        attr {
            val isArmed = armed()
            height(48f)
            width(if (isArmed) 112f else 44f)
            borderRadius(24f)
            paddingLeft(13f)
            flexDirectionRow()
            alignItemsCenter()
            overflow(true)
            backgroundColor(if (isArmed) Color.WHITE else Color(0x00FFFFFFL))
            animation(Animation.easeOut(0.18f), isArmed)
        }
        vif({ armed() }) {
            Icon(icon, 18f, if (danger) AppTheme.down else AppTheme.textPrimary)
        }
        velse {
            Icon(icon, 18f, AppTheme.textTertiary)
        }
        Text {
            attr {
                val isArmed = armed()
                text(label)
                fontSize(14f)
                fontWeight600()
                color(if (danger) AppTheme.down else AppTheme.textPrimary)
                marginLeft(6f)
                opacity(if (isArmed) 1f else 0f)
                animation(Animation.easeOut(0.18f), isArmed)
            }
        }
    }
}

internal fun ViewContainer<*, *>.VoiceWave(phase: () -> Int, zone: () -> VoiceRecordZone) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); height(18f) }
        repeat(16) { index ->
            View {
                attr {
                    val p = phase()
                    val current = zone()
                    width(4f)
                    height(waveBarHeight(index, p, current))
                    borderRadius(2f)
                    marginRight(if (index == 15) 0f else 3.5f)
                    backgroundColor(waveColor(current))
                    animation(Animation.easeInOut(0.12f), p)
                }
            }
        }
    }
}

internal fun waveBarHeight(index: Int, phase: Int, zone: VoiceRecordZone): Float {
    val wave = ((sin((phase * 0.28f + index * 0.62f).toDouble()) + 1.0) * 0.5).toFloat()
    val amplitude = if (zone == VoiceRecordZone.SEND) 6f else 3.2f
    return 7f + wave * amplitude
}

internal fun waveColor(zone: VoiceRecordZone): Color = when (zone) {
    VoiceRecordZone.SEND -> voiceSendBlue
    VoiceRecordZone.EDIT -> voiceEditGray
    VoiceRecordZone.CANCEL -> AppTheme.down
}

internal fun ViewContainer<*, *>.AttachmentActionCard(icon: IconKind, title: String, onClick: () -> Unit) {
    View {
        attr {
            flex(1f)
            height(96f)
            alignItemsCenter()
            justifyContentCenter()
            marginLeft(4f); marginRight(4f)
        }
        event { click { onClick() } }
        View {
            attr {
                width(96f)
                height(96f)
                borderRadius(14f)
                flexDirectionColumn()
                allCenter()
                backgroundColor(AppTheme.surfaceMuted)
            }
            View {
                attr {
                    width(28f)
                    height(28f)
                    allCenter()
                }
                Icon(icon, 24f, AppTheme.textSecondary, 1.6f)
            }
            Text { attr { text(title); fontSize(13f); color(AppTheme.textSecondary); marginTop(8f) } }
        }
    }
}

// endregion
