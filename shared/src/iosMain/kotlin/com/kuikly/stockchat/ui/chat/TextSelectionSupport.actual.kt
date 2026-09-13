package com.kuikly.stockchat.ui.chat

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.DivAttr
import com.tencent.kuikly.core.views.DivEvent
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.SelectionType

actual fun DivAttr.enableMessageTextSelection(color: Color) {
    selectable(SelectableOption.ENABLE)
    selectionColor(color)
}

actual fun DivAttr.disableMessageTextSelection() {
    selectable(SelectableOption.DISABLE)
}

actual fun DivEvent.bindMessageTextSelection(
    onLongPressStart: (x: Float, y: Float) -> Unit,
    onSelectEnd: (x: Float, y: Float) -> Unit,
    onSelectCancel: () -> Unit,
) {
    longPress { params ->
        if (params.state == "start") {
            onLongPressStart(params.x, params.y)
        }
    }
    selectEnd { frame ->
        onSelectEnd(frame.x, frame.y)
    }
    selectCancel {
        onSelectCancel()
    }
}

actual fun DivView.createWordSelectionAt(x: Float, y: Float) {
    createSelection(x, y, SelectionType.WORD)
}

actual fun DivView.readSelectedText(onResult: (String) -> Unit) {
    getSelection { selection ->
        onResult(selection.content.joinToString("\n").trim())
    }
}

actual fun DivView.clearTextSelection() {
    clearSelection()
}
