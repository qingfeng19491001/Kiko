package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.StockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentParserTest {

    @Test
    fun outlookQuestionIsTrendNotFullDiagnosis() {
        val parsed = IntentParser.parse("腾讯控股后市会怎么走")
        assertEquals(Intent.TREND, parsed.intent)
        assertEquals(StockCatalog.tencent.key, parsed.instruments.single().key)
    }

    @Test
    fun rankingQuestionIsCompareEvenWithOneName() {
        val parsed = IntentParser.parse("腾讯控股在同行业里排名怎么样")
        assertEquals(Intent.COMPARE, parsed.intent)
        assertEquals(StockCatalog.tencent.key, parsed.instruments.single().key)
    }

    @Test
    fun vsKeepsCompare() {
        val parsed = IntentParser.parse("腾讯 vs 阿里巴巴")
        assertEquals(Intent.COMPARE, parsed.intent)
        assertEquals(2, parsed.instruments.size)
    }

    @Test
    fun diagnosisQuestionStaysStockAnalysis() {
        val parsed = IntentParser.parse("腾讯控股全面分析一下")
        assertEquals(Intent.STOCK_ANALYSIS, parsed.intent)
    }

    @Test
    fun plazaPromptIsGreeting() {
        val parsed = IntentParser.parse("技能广场：你能做什么")
        assertEquals(Intent.GREETING, parsed.intent)
        assertTrue(parsed.instruments.isEmpty())
    }
}
