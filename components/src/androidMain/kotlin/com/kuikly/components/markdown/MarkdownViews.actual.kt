package com.kuikly.components.markdown

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

internal val chatMarkdownConfig: MarkdownConfig = MarkdownConfig(
    colors = MarkdownColors(
        text = MarkdownTheme.TEXT,
        codeBackground = MarkdownTheme.CODE_BACKGROUND,
        inlineCodeBackground = MarkdownTheme.INLINE_CODE_BACKGROUND,
        dividerColor = MarkdownTheme.DIVIDER,
        tableBackground = MarkdownTheme.TABLE_BACKGROUND,
        blockQuoteBar = MarkdownTheme.QUOTE_BAR,
        blockQuoteBackground = MarkdownTheme.QUOTE_BACKGROUND,
        linkColor = MarkdownTheme.LINK,
        codeText = MarkdownTheme.TEXT,
    ),
    typography = MarkdownTypography(
        text = TextStyleConfig(fontSize = MarkdownTheme.TEXT_SIZE, lineHeight = MarkdownTheme.LINE_HEIGHT),
        paragraph = TextStyleConfig(fontSize = MarkdownTheme.TEXT_SIZE, lineHeight = MarkdownTheme.LINE_HEIGHT),
        h1 = TextStyleConfig(fontSize = 20f, fontWeight = FontWeight.Bold),
        h2 = TextStyleConfig(fontSize = 18f, fontWeight = FontWeight.Bold),
        h3 = TextStyleConfig(fontSize = 16f, fontWeight = FontWeight.Bold),
        h4 = TextStyleConfig(fontSize = 15f, fontWeight = FontWeight.SemiBold, color = MarkdownTheme.HEADING),
        h5 = TextStyleConfig(fontSize = 14f, fontWeight = FontWeight.SemiBold),
        h6 = TextStyleConfig(fontSize = 14f, fontWeight = FontWeight.SemiBold),
        quote = TextStyleConfig(fontSize = 13f, color = MarkdownTheme.QUOTE_TEXT),
        ordered = TextStyleConfig(fontSize = MarkdownTheme.TEXT_SIZE, lineHeight = MarkdownTheme.LINE_HEIGHT),
        bullet = TextStyleConfig(fontSize = MarkdownTheme.TEXT_SIZE, lineHeight = MarkdownTheme.LINE_HEIGHT),
        list = TextStyleConfig(fontSize = MarkdownTheme.TEXT_SIZE, lineHeight = MarkdownTheme.LINE_HEIGHT),
        table = TextStyleConfig(fontSize = 12f),
        code = TextStyleConfig(fontSize = 12f, fontFamily = "monospace"),
        inlineCode = TextStyleConfig(fontSize = 13f, fontFamily = "monospace"),
        textLink = TextStyleConfig(fontSize = MarkdownTheme.TEXT_SIZE),
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

actual class StreamingMarkdownModel {
    actual val text: String get() = _text
    private var _text: String = ""
    val state = MarkdownStreamingState()
    var blocks by observableList<MarkdownBlock>()
    private var ticks = 0

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

actual fun ViewContainer<*, *>.StreamingMarkdownView(model: StreamingMarkdownModel) {
    View {
        vfor({ model.blocks }) { block ->
            View {
                KuiklyStreamingMarkdown(
                    state = model.state,
                    block = block,
                    config = chatMarkdownConfig,
                )
            }
        }
    }
}

actual fun ViewContainer<*, *>.StaticMarkdownView(text: String) {
    View {
        KuiklyMarkdown(content = text, config = chatMarkdownConfig)
    }
}
