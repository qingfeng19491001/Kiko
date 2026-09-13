package com.kuikly.stockchat.ui.chat

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.DivAttr
import com.tencent.kuikly.core.views.DivEvent
import com.tencent.kuikly.core.views.DivView

/**
 * 文本选区能力。三端均走 Kuikly 2.26 选区 API。
 */
expect fun DivAttr.enableMessageTextSelection(color: Color)

expect fun DivAttr.disableMessageTextSelection()

expect fun DivEvent.bindMessageTextSelection(
    onLongPressStart: (x: Float, y: Float) -> Unit,
    onSelectEnd: (x: Float, y: Float) -> Unit,
    onSelectCancel: () -> Unit,
)

expect fun DivView.createWordSelectionAt(x: Float, y: Float)

expect fun DivView.readSelectedText(onResult: (String) -> Unit)

expect fun DivView.clearTextSelection()
