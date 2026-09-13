package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.domain.chat.ResearchProgress
import com.kuikly.stockchat.domain.chat.ResearchState
import com.kuikly.stockchat.domain.chat.ResearchStage
import com.kuikly.stockchat.domain.chat.ResearchSource
import com.kuikly.stockchat.domain.chat.ResearchSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchProgressTest {

    @Test
    fun completionKeepsRealEvidenceCountAndCollapsesTrace() {
        val state = ResearchState().update(
            ResearchProgress(
                stage = ResearchStage.SYNTHESIZING,
                detail = "行情读取完成，正在交叉分析",
                instrumentCount = 4,
                dataPointCount = 240,
                sources = listOf(
                    ResearchSource("实时行情快照", "4 个标的", ResearchSourceKind.QUOTE),
                    ResearchSource("历史日 K 数据", "240 条", ResearchSourceKind.HISTORY),
                ),
            ),
        ).complete(2_340L)

        assertEquals(ResearchStage.COMPLETE, state.stage)
        assertEquals("已检索 2 类数据源 · 2.3秒", state.detail)
        assertEquals(2_340L, state.elapsedMs)
        assertEquals(2, state.sources.size)
        assertFalse(state.expanded)
    }

    @Test
    fun progressNeverLosesPreviouslyCollectedEvidence() {
        val state = ResearchState()
            .update(ResearchProgress(ResearchStage.FETCHING_MARKET_DATA, "读取行情", 3, 180))
            .update(ResearchProgress(ResearchStage.SYNTHESIZING, "组织回答", 2, 120))

        assertEquals(3, state.instrumentCount)
        assertEquals(180, state.dataPointCount)
        assertTrue(state.expanded)
    }
}
