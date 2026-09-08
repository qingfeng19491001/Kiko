package com.kuikly.stockchat.ui.components

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.streaming.MarkdownBlock
import com.tencent.kuiklybase.streaming.MarkdownStreamingState

/** 鸿蒙端使用 KuiklyMarkdown 的 ohosArm64 实现，保持三端富文本行为一致。 */
private val chatMarkdownConfig = MarkdownConfig()

actual class StreamingMarkdownModel {
    actual val text: String get() = content
    private var content = ""
    val state = MarkdownStreamingState()
    var blocks by observableList<MarkdownBlock>()
    private var ticks = 0

    actual fun update(newText: String, finished: Boolean, flushEvery: Int) {
        content = newText
        ticks += 1
        if (!finished && ticks % flushEvery != 0) return
        state.update(newText, force = finished)?.let { blocks.diffUpdate(it) { old, new -> old.id == new.id } }
    }

    actual fun setFinal(newText: String) {
        content = newText
        state.update(newText, force = true)?.let { blocks.diffUpdate(it) { old, new -> old.id == new.id } }
    }
}

actual fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel) {
    View {
        vfor({ model.blocks }) { block ->
            View { KuiklyStreamingMarkdown(state = model.state, block = block, config = chatMarkdownConfig) }
        }
    }
}

actual fun ViewContainer<*, *>.StaticMarkdownView(text: String) {
    View { KuiklyMarkdown(content = text, config = chatMarkdownConfig) }
}
