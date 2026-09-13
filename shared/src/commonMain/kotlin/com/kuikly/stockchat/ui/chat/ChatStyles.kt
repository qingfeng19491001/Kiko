package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color

/** 提问：内容宽矮气泡，轻阴影。 */
internal fun Attr.questionBubble() {
    backgroundColor(Color.WHITE)
    borderRadius(18f)
    boxShadow(BoxShadow(0f, 4f, 12f, Color(0, 0, 0, 0.06f)), useShadowPath = true)
}

/** 技能：浅灰胶囊，不加阴影，避免行高被裁切后叠出一层灰边。 */
internal fun Attr.skillChip() {
    backgroundColor(AppTheme.userBubble)
    borderRadius(16f)
}

/** 输入：通栏槽，细边 + 贴地阴影，不与技能同高同影。 */
internal fun Attr.composerSlot(radius: Float) {
    backgroundColor(Color.WHITE)
    borderRadius(radius)
    border(Border(1f, BorderStyle.SOLID, AppTheme.border))
    boxShadow(BoxShadow(0f, 1f, 4f, Color(0, 0, 0, 0.04f)), useShadowPath = true)
}
