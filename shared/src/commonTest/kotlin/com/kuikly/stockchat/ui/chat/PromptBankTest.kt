package com.kuikly.stockchat.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PromptBankTest {

    @Test
    fun firstPageShowsOpeningWelcomeQuestions() {
        val page = PromptBank.welcomePage(0)
        assertEquals(PromptBank.WELCOME_PAGE_SIZE, page.size)
        assertEquals("腾讯控股后市如何？", page[0].prompt)
        assertEquals("港股科技板块趋势分析", page[1].prompt)
        assertEquals("比亚迪 vs 特斯拉 对比", page[2].prompt)
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
}
