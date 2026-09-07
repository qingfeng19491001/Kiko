package com.kuikly.stockchat.ui.components

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownDimens
import com.tencent.kuiklybase.config.MarkdownPadding
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.streaming.MarkdownBlock
import com.tencent.kuiklybase.streaming.MarkdownStreamingState

/** 应用统一的 Markdown 样式：字号略小于默认，贴合聊天气泡。 */
object ChatMarkdownStyle {
    val config: MarkdownConfig = MarkdownConfig(
        colors = MarkdownColors(
            text = 0xFF0F172A,
            codeBackground = 0xFFF6F7F9,
            inlineCodeBackground = 0xFFEEF0F4,
            dividerColor = 0xFFE2E5EA,
            tableBackground = 0xFFFFFFFF,
            blockQuoteBar = 0xFF0F172A,
            blockQuoteBackground = 0xFFF6F7F9,
            linkColor = 0xFF2F54EB,
            codeText = 0xFF0F172A,
        ),
        typography = MarkdownTypography(
            text = TextStyleConfig(fontSize = 14f, lineHeight = 22f),
            paragraph = TextStyleConfig(fontSize = 14f, lineHeight = 22f),
            h1 = TextStyleConfig(fontSize = 20f, fontWeight = FontWeight.Bold),
            h2 = TextStyleConfig(fontSize = 18f, fontWeight = FontWeight.Bold),
            h3 = TextStyleConfig(fontSize = 16f, fontWeight = FontWeight.Bold),
            h4 = TextStyleConfig(fontSize = 15f, fontWeight = FontWeight.SemiBold, color = 0xFF111827),
            h5 = TextStyleConfig(fontSize = 14f, fontWeight = FontWeight.SemiBold),
            h6 = TextStyleConfig(fontSize = 14f, fontWeight = FontWeight.SemiBold),
            quote = TextStyleConfig(fontSize = 13f, color = 0xFF4B5563),
            ordered = TextStyleConfig(fontSize = 14f, lineHeight = 22f),
            bullet = TextStyleConfig(fontSize = 14f, lineHeight = 22f),
            list = TextStyleConfig(fontSize = 14f, lineHeight = 22f),
            table = TextStyleConfig(fontSize = 12f),
            code = TextStyleConfig(fontSize = 12f, fontFamily = "monospace"),
            inlineCode = TextStyleConfig(fontSize = 13f, fontFamily = "monospace"),
            textLink = TextStyleConfig(fontSize = 14f),
        ),
        dimens = MarkdownDimens(
            tableCellWidth = 92f,
            tableCellPadding = 8f,
            tableCornerSize = 8f,
            blockQuoteThickness = 3f,
            blockQuoteCornerSize = 6f,
        ),
        padding = MarkdownPadding(
            block = 3f,
            list = 2f,
            listItemTop = 2f,
            listItemBottom = 2f,
            blockQuotePaddingLeft = 10f,
            blockQuoteTextVertical = 8f,
        ),
    )
}

/**
 * 流式 Markdown 模型：持有解析器与驱动 vfor 的块列表。
 * 每条 AI 消息中的每个 Markdown 块对应一个实例。
 */
actual class StreamingMarkdownModel {
    actual val text: String get() = _text
    private var _text: String = ""
    val state = MarkdownStreamingState()
    var blocks by observableList<MarkdownBlock>()
    private var ticks = 0

    /**
     * @param flushEvery 每 N 次增量解析一次（降低解析频率），finished 时强制解析
     */
    actual fun update(newText: String, finished: Boolean, flushEvery: Int) {
        _text = newText
        ticks += 1
        if (!finished && ticks % flushEvery != 0) return
        val parsed = state.update(newText, force = finished) ?: return
        blocks.diffUpdate(parsed) { old, new -> old.id == new.id }
    }

    actual fun setFinal(newText: String) {
        _text = newText
        val parsed = state.update(newText, force = true) ?: return
        blocks.diffUpdate(parsed) { old, new -> old.id == new.id }
    }
}

/** 流式 Markdown 视图 */
actual fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel) {
    View {
        vfor({ model.blocks }) { block ->
            View {
                KuiklyStreamingMarkdown(
                    state = model.state,
                    block = block,
                    config = ChatMarkdownStyle.config,
                )
            }
        }
    }
}

/** 静态 Markdown 视图（历史消息） */
actual fun ViewContainer<*, *>.StaticMarkdownView(text: String) {
    View {
        KuiklyMarkdown(content = text, config = ChatMarkdownStyle.config)
    }
}
