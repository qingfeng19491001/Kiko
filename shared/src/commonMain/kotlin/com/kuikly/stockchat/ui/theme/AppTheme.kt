package com.kuikly.stockchat.ui.theme

import com.kuikly.stockchat.domain.analysis.TagTone
import com.tencent.kuikly.core.base.Color

/**
 * 全局设计令牌。
 *
 * 视觉方向：券商行情终端式的克制风格——白底、墨色文字、细线分隔、
 * 涨跌色作为页面上几乎唯一的彩色，动作按钮使用墨色而非高饱和品牌蓝。
 * 涨跌色采用国际惯例（涨绿跌红），与原型保持一致。
 */
object AppTheme {

    // 墨色（主要动作 / 标题）与链接蓝（少量使用）
    val ink = Color(0xFF0F172AL)
    val inkSoft = Color(0xFFEEF0F4L)
    val primary = Color(0xFF0F172AL)
    val primaryDark = Color(0xFF020617L)
    val primaryLight = Color(0xFFF1F3F7L)
    val primarySoft = Color(0xFFD9DEE8L)
    val accent = Color(0xFF2F54EBL)
    val accentSoft = Color(0xFFEDF1FEL)

    // 涨跌
    val up = Color(0xFF12924AL)
    val upSoft = Color(0xFFE6F4EBL)
    val down = Color(0xFFD93B3BL)
    val downSoft = Color(0xFFFCEBEBL)
    val flat = Color(0xFF64748BL)

    // 语义色
    val warning = Color(0xFFB45309L)
    val warningSoft = Color(0xFFFFF7EAL)
    val purple = Color(0xFF6D28D9L)
    val purpleSoft = Color(0xFFF3EEFDL)

    // 中性色（对齐 Kimi：整页近白，白控件靠轻阴影浮起，不要灰条分区）
    val background = Color(0xFFFAFAFAL)
    val surface = Color.WHITE
    val surfaceMuted = Color(0xFFF2F3F5L)
    val divider = Color(0xFFEBEDF0L)
    val border = Color(0xFFE8E8EAL)
    val textPrimary = Color(0xFF0F172AL)
    val textSecondary = Color(0xFF475569L)
    val textTertiary = Color(0xFF94A3B8L)
    val textOnPrimary = Color.WHITE
    val scrim = Color(0, 0, 0, 0.4f)

    // 聊天气泡：用户消息浅灰气泡；AI 回复不加底色，直接铺在页面上
    val userBubble = Color(0xFFF1F3F6L)
    val aiBubble = Color.WHITE

    // 尺寸
    const val pageHorizontalPadding = 16f
    const val cardRadius = 12f
    const val bubbleRadius = 14f
    const val navBarHeight = 48f
    const val composerHeight = 50f

    fun changeColor(change: Double): Color = when {
        change > 0 -> up
        change < 0 -> down
        else -> flat
    }

    fun changeSoftColor(change: Double): Color = when {
        change > 0 -> upSoft
        change < 0 -> downSoft
        else -> surfaceMuted
    }

    fun toneColor(tone: TagTone): Color = when (tone) {
        TagTone.POSITIVE -> up
        TagTone.NEGATIVE -> down
        TagTone.WARNING -> warning
        TagTone.NEUTRAL -> textSecondary
    }

    fun toneSoftColor(tone: TagTone): Color = when (tone) {
        TagTone.POSITIVE -> upSoft
        TagTone.NEGATIVE -> downSoft
        TagTone.WARNING -> warningSoft
        TagTone.NEUTRAL -> surfaceMuted
    }
}
