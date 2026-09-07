package com.kuikly.stockchat.ui.components

import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.ViewContainer

/**
 * 流式 Markdown 状态。
 *
 * 平台无关的 API 表面：
 *  - [text]：当前累计文本
 *  - [update] / [setFinal]：把新文本灌入模型
 *
 * 实际渲染与中间状态（已解析块、解析器等）由各端 `actual` 提供。
 * - Android / iOS：使用 KuiklyMarkdown 流式解析
 * - OHOS（鸿蒙）：使用纯 Text 兜底（无 Markdown 富文本）
 */
expect class StreamingMarkdownModel() {
    val text: String
    fun update(newText: String, finished: Boolean, flushEvery: Int = 3)
    fun setFinal(newText: String)
}

/** 流式 Markdown 视图（带打字机效果时调用）。 */
expect fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel)

/** 静态 Markdown 视图（终态消息展示）。 */
expect fun ViewContainer<*, *>.StaticMarkdownView(text: String)

internal val markdownBubbleColor = AppTheme.aiBubble
