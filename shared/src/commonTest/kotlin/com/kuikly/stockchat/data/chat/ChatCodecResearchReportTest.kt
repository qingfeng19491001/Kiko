package com.kuikly.stockchat.data.chat

import com.kuikly.stockchat.domain.chat.ChatMessage
import com.kuikly.stockchat.domain.chat.ResearchReport
import com.kuikly.stockchat.domain.chat.ResearchSource
import com.kuikly.stockchat.domain.chat.ResearchSourceKind
import com.kuikly.stockchat.domain.chat.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatCodecResearchReportTest {
    @Test
    fun researchEvidenceRoundTripsWithMessage() {
        val report = ResearchReport(
            elapsedMs = 1_820L,
            instrumentCount = 2,
            dataPointCount = 120,
            sources = listOf(
                ResearchSource("实时行情快照", "2 个标的", ResearchSourceKind.QUOTE),
                ResearchSource("历史日 K 数据", "120 条", ResearchSourceKind.HISTORY),
            ),
        )
        val original = ChatMessage("m1", Role.ASSISTANT, createdAt = 1L, researchReport = report)

        val decoded = requireNotNull(ChatCodec.decodeMessage(ChatCodec.encode(original)))

        assertEquals(report, decoded.researchReport)
    }
}
