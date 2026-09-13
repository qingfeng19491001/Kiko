package com.kuikly.components.markdown

import com.tencent.kuikly.core.base.ViewContainer

expect class StreamingMarkdownModel() {
    val text: String
    fun update(newText: String, finished: Boolean, flushEvery: Int = 3)
    fun setFinal(newText: String)
}

expect fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel)

expect fun ViewContainer<*, *>.StaticMarkdownView(text: String)
