package com.kuikly.stockchat.ui.components

import com.kuikly.components.markdown.StaticMarkdownView as renderStaticMarkdown
import com.kuikly.components.markdown.StreamingMarkdownModel as ComponentStreamingMarkdownModel
import com.kuikly.components.markdown.StreamingMarkdownView as renderStreamingMarkdown
import com.tencent.kuikly.core.base.ViewContainer

typealias StreamingMarkdownModel = ComponentStreamingMarkdownModel

fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel) {
    renderStreamingMarkdown(model)
}

fun ViewContainer<*, *>.StaticMarkdownView(text: String) {
    renderStaticMarkdown(text)
}
