package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.ui.components.IconKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PromptBankTest {

    @Test
    fun firstPageShowsOpeningWelcomeQuestions() {
        val page = PromptBank.welcomePage(0)
        assertEquals(PromptBank.WELCOME_PAGE_SIZE, page.size)
        assertEquals("腾讯控股在同行业里排名怎么样", page[0].prompt)
        assertEquals("帮我详细分析一下贵州茅台", page[1].prompt)
        assertEquals("当下科技股，我该加仓还是减仓", page[2].prompt)
    }

    @Test
    fun shuffleAdvancesToADifferentSetOfQuestions() {
        val first = PromptBank.welcomePage(0).map { it.prompt }
        val nextIndex = PromptBank.nextPage(0)
        val second = PromptBank.welcomePage(nextIndex).map { it.prompt }
        assertNotEquals(first, second)
        assertTrue(first.intersect(second.toSet()).isEmpty())
    }

    @Test
    fun shuffleWrapsBackToTheFirstPage() {
        var page = 0
        repeat(PromptBank.pageCount()) { page = PromptBank.nextPage(page) }
        assertEquals(0, page)
        assertEquals(
            PromptBank.welcomePage(0).map { it.prompt },
            PromptBank.welcomePage(page).map { it.prompt },
        )
    }

    @Test
    fun featuredSkillIconsAreUnique() {
        val featured = listOf("选股", "策略", "盯盘", "研究")
        val icons = featured.map { PromptBank.iconFor(it) }
        assertEquals(featured.size, icons.toSet().size)
        assertEquals(IconKind.SPARKLE, PromptBank.iconFor("选股"))
        assertEquals(IconKind.STRATEGY, PromptBank.iconFor("策略"))
        assertEquals(IconKind.EYE, PromptBank.iconFor("盯盘"))
        assertEquals(IconKind.BOOK, PromptBank.iconFor("研究"))
        assertEquals(IconKind.RADAR, PromptBank.iconFor("机会"))
        assertNotEquals(PromptBank.iconFor("选股"), PromptBank.iconFor("机会"))
    }

    @Test
    fun composerCapsulesStayShortAndSendable() {
        assertTrue(PromptBank.composerCapsules.size >= 16)
        PromptBank.composerCapsules.forEach { item ->
            assertTrue(item.prompt.isNotBlank())
            assertTrue(item.capsule.isNotBlank())
            assertTrue(item.capsule.length <= item.prompt.length)
        }
        assertEquals(PromptBank.composerCapsules.size, PromptBank.composerCapsules.map { it.capsule }.toSet().size)
    }

    @Test
    fun capsuleShuffleShowsADifferentPage() {
        val first = PromptBank.capsulePage(0).map { it.capsule }
        val second = PromptBank.capsulePage(1).map { it.capsule }
        assertEquals(PromptBank.CAPSULE_PAGE_SIZE, first.size)
        assertEquals(PromptBank.CAPSULE_PAGE_SIZE, second.size)
        assertTrue(first.intersect(second.toSet()).isEmpty())
    }

    @Test
    fun memoryPromptUsesWatchlistOrExplainsEmpty() {
        val filled = PromptBank.memoryPrompt(listOf("腾讯控股", "贵州茅台"))
        assertTrue(filled.contains("腾讯控股"))
        assertTrue(filled.contains("贵州茅台"))
        assertTrue(filled.contains("投资记忆"))
        val empty = PromptBank.memoryPrompt(emptyList())
        assertTrue(empty.contains("没有自选"))
        assertTrue(empty.contains("能做什么"))
    }
}
