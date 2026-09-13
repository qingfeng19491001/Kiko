package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.data.parser.IntentLlmParser
import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentLlmParserTest {
    @Test
    fun parsesTrendIntentAndResolvesName() {
        val parsed = IntentLlmParser.parse(
            """{"intent":"TREND","names":["腾讯控股"],"topic":""}""",
            "它后市怎么走",
            emptyList(),
        )
        assertEquals(Intent.TREND, parsed?.intent)
        assertEquals(StockCatalog.tencent.key, parsed?.instruments?.single()?.key)
    }

    @Test
    fun fallsBackToLocalInstrumentsWhenNamesEmpty() {
        val parsed = IntentLlmParser.parse(
            """{"intent":"RISK","names":[],"topic":""}""",
            "腾讯控股有什么风险",
            emptyList(),
        )
        assertEquals(Intent.RISK, parsed?.intent)
        assertTrue(parsed?.instruments?.any { it.key == StockCatalog.tencent.key } == true)
    }

    @Test
    fun keepsUnresolvedNamesOutsideCatalog() {
        val parsed = IntentLlmParser.parse(
            """{"intent":"STOCK_ANALYSIS","names":["片仔癀"],"topic":""}""",
            "片仔癀后市如何",
            emptyList(),
        )
        assertEquals(Intent.STOCK_ANALYSIS, parsed?.intent)
        assertTrue(parsed?.instruments.isNullOrEmpty())
        assertEquals(listOf("片仔癀"), parsed?.unresolvedNames)
    }

    @Test
    fun rejectsMalformedPayload() {
        assertNull(IntentLlmParser.parse("not json", "你好", emptyList()))
    }
}
