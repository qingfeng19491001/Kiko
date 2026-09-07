package com.kuikly.stockchat.ui.components

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.kuikly.stockchat.ui.theme.AppTheme

/**
 * 流式 Markdown 模型（OHOS 兜底实现）。
 *
 * 鸿蒙 Kuikly core 不含 kuiklybase.markdown 模块，故这里仅保留简单文本。
 * 实际效果：AI 回复以纯文本形式展示，不做富文本解析；
 * 仍然支持流式更新、最终化、文本查询等核心 API。
 */
actual class StreamingMarkdownModel {
    actual val text: String get() = _text
    private var _text: String = ""

    actual fun update(newText: String, finished: Boolean, flushEvery: Int) {
        _text = newText
    }

    actual fun setFinal(newText: String) {
        _text = newText
    }
}

/** 流式 Markdown 视图（OHOS 兜底：纯 Text 整段重渲）。 */
actual fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel) {
    View {
        Text {
            attr {
                text(model.text)
                fontSize(14f)
                color(AppTheme.textPrimary)
            }
        }
    }
}

/** 静态 Markdown 视图（OHOS 兜底：纯 Text）。 */
actual fun ViewContainer<*, *>.StaticMarkdownView(text: String) {
    View {
        Text {
            attr {
                text(text)
                fontSize(14f)
                color(AppTheme.textPrimary)
            }
        }
    }
}
