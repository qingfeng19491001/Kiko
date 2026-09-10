package com.kuikly.stockchat.domain.chat

/**
 * 把本地骨架（实时行情卡片 / 图表）和模型分段解读穿插成一篇研报：
 *
 * 章节标题 → 模型对该段的文字 → 对应数据卡/图
 *
 * 避免「卡片全在上、长文全在下」。
 */
object AnswerAssembler {

    fun merge(base: AiAnswer, modelMarkdown: String): AiAnswer {
        val parsed = parseModelSections(modelMarkdown)
        if (parsed.isBlank) return base
        if (base.blocks.none { it is AnswerBlock.Markdown || it is AnswerBlock.SectionHeader }) {
            return base.copy(blocks = insertBeforeTail(base.blocks, AnswerBlock.Markdown(parsed.full)))
        }

        val used = mutableSetOf<SectionKey>()
        var currentKey: SectionKey = SectionKey.Preamble
        var pendingSectionText = false
        val out = mutableListOf<AnswerBlock>()

        fun flushSectionText() {
            if (!pendingSectionText) return
            pendingSectionText = false
            val text = parsed.take(currentKey, used)
            if (text.isNotBlank()) out += AnswerBlock.Markdown(text)
        }

        for ((index, block) in base.blocks.withIndex()) {
            when (block) {
                is AnswerBlock.SectionHeader -> {
                    flushSectionText()
                    currentKey = classifyHeader(block.title)
                    val window = base.blocks.drop(index + 1).takeWhile { it !is AnswerBlock.SectionHeader }
                    val hasVisual = window.any { it !is AnswerBlock.Markdown }
                    val hasText = parsed.peek(currentKey, used)
                    if (hasVisual || hasText) {
                        out += block
                        pendingSectionText = true
                    }
                }
                is AnswerBlock.Markdown -> {
                    pendingSectionText = false
                    val text = parsed.take(currentKey, used)
                    if (text.isNotBlank()) out += AnswerBlock.Markdown(text)
                }
                else -> {
                    flushSectionText()
                    out += block
                }
            }
        }
        flushSectionText()

        val leftovers = parsed.unused(used)
        if (leftovers.isNotBlank()) insertBeforeTail(out, AnswerBlock.Markdown(leftovers))
        return base.copy(blocks = out)
    }

    fun withFailureNotice(base: AiAnswer, error: String?): AiAnswer {
        val notice = buildString {
            appendLine("模型分析暂不可用，下面先给出基于实时行情的结构化解读。")
            if (!error.isNullOrBlank()) {
                appendLine()
                appendLine(error)
            }
        }.trim()
        return base.copy(blocks = listOf(AnswerBlock.Markdown(notice)) + base.blocks)
    }

    internal fun parseModelSections(raw: String): ParsedAnswer {
        val text = raw.trim()
        if (text.isEmpty()) return ParsedAnswer()

        val parts = LinkedHashMap<SectionKey, StringBuilder>()
        var current = SectionKey.Preamble
        parts[current] = StringBuilder()

        for (line in text.lineSequence()) {
            val heading = headingTitle(line)
            if (heading != null) {
                current = classifyHeader(heading)
                parts.getOrPut(current) { StringBuilder() }
                continue
            }
            val bucket = parts.getOrPut(current) { StringBuilder() }
            if (bucket.isNotEmpty()) bucket.append('\n')
            bucket.append(line)
        }

        val map = parts.mapValues { it.value.toString().trim() }.filterValues { it.isNotEmpty() }
        return ParsedAnswer(map, text)
    }

    private fun headingTitle(line: String): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        MARKDOWN_HEADING.matchEntire(t)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val bold = BOLD_HEADING.matchEntire(t)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (bold.isNotEmpty() && looksLikeSection(bold)) return bold
        val numbered = NUMBERED_HEADING.matchEntire(t)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (numbered.isNotEmpty() && looksLikeSection(numbered)) return numbered
        return null
    }

    internal fun classifyHeader(title: String): SectionKey {
        val t = title.trim()
        return when {
            t.contains("技术") || t.contains("趋势") || t.contains("均线") -> SectionKey.Technical
            t.contains("估值") || t.contains("规模") || t.contains("基本面") -> SectionKey.Valuation
            t.contains("结论") || t.contains("总结") || t.contains("要点") || t.contains("后市") || t.contains("展望") -> SectionKey.Conclusion
            t.contains("风险") -> SectionKey.Risk
            t.contains("盘面") || t.contains("概览") || t.contains("行情") ||
                t.contains("观点") || t.contains("核心") -> SectionKey.Overview
            else -> SectionKey.Other
        }
    }

    private fun looksLikeSection(title: String): Boolean =
        listOf("技术", "趋势", "均线", "估值", "规模", "基本面", "结论", "总结", "要点", "后市", "展望", "风险", "盘面", "概览", "行情", "观点", "核心")
            .any { title.contains(it) }

    private fun insertBeforeTail(blocks: List<AnswerBlock>, extra: AnswerBlock): List<AnswerBlock> {
        val out = blocks.toMutableList()
        insertBeforeTail(out, extra)
        return out
    }

    private fun insertBeforeTail(blocks: MutableList<AnswerBlock>, extra: AnswerBlock) {
        val idx = blocks.indexOfFirst { it is AnswerBlock.Risk || it is AnswerBlock.FollowUps }
        if (idx < 0) blocks += extra else blocks.add(idx, extra)
    }

    private val MARKDOWN_HEADING = Regex("""^#{1,3}\s+(.+?)\s*$""")
    private val BOLD_HEADING = Regex("""^\*\*(.+?)\*\*\s*$""")
    private val NUMBERED_HEADING = Regex(
        """^(?:#{0,3}\s*)?(?:\d+|第?[一二三四五六七八九十]+)[\.、．]\s*(.+?)\s*$""",
    )
}

internal enum class SectionKey {
    Preamble,
    Overview,
    Technical,
    Valuation,
    Conclusion,
    Risk,
    Other,
}

internal data class ParsedAnswer(
    val sections: Map<SectionKey, String> = emptyMap(),
    val full: String = "",
) {
    val isBlank: Boolean get() = full.isBlank()
    private val structured: Boolean get() = sections.keys.any { it != SectionKey.Preamble }

    fun peek(key: SectionKey, used: Set<SectionKey>): Boolean = resolve(key, used) != null

    fun take(key: SectionKey, used: MutableSet<SectionKey>): String {
        val hit = resolve(key, used) ?: return ""
        used += hit.first
        return hit.second
    }

    fun unused(used: Set<SectionKey>): String =
        sections.filterKeys { it !in used && it != SectionKey.Risk }
            .values
            .joinToString("\n\n")
            .trim()

    private fun resolve(key: SectionKey, used: Set<SectionKey>): Pair<SectionKey, String>? {
        when (key) {
            SectionKey.Preamble -> {
                val text = sections[SectionKey.Preamble]
                if (!text.isNullOrBlank() && SectionKey.Preamble !in used && structured) {
                    return SectionKey.Preamble to text
                }
                return null
            }
            SectionKey.Overview -> {
                sections[SectionKey.Overview]
                    ?.takeIf { it.isNotBlank() && SectionKey.Overview !in used }
                    ?.let { return SectionKey.Overview to it }
                if (!structured) {
                    sections[SectionKey.Preamble]
                        ?.takeIf { it.isNotBlank() && SectionKey.Preamble !in used }
                        ?.let { return SectionKey.Preamble to it }
                }
                return null
            }
            else -> {
                val text = sections[key]
                if (!text.isNullOrBlank() && key !in used) return key to text
                return null
            }
        }
    }
}
