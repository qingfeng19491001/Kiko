package com.kuikly.stockchat.data.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VolcAsrTextTest {
    @Test
    fun extractsTextFromResultObject() {
        val json = """{"audio_info":{"duration":1000},"result":{"text":"腾讯控股怎么看","utterances":[]}}"""
        assertEquals("腾讯控股怎么看", VolcAsrText.extract(json))
    }

    @Test
    fun extractsTextFromResultArray() {
        val json = """{"result":[{"text":"中间结果"},{"text":"最终结果"}]}"""
        assertEquals("最终结果", VolcAsrText.extract(json))
    }

    @Test
    fun extractsWrappedPayload() {
        val json = """{"payload_msg":{"result":{"text":"上证指数"}}}"""
        assertEquals("上证指数", VolcAsrText.extract(json))
    }

    @Test
    fun returnsNullWhenEmpty() {
        assertNull(VolcAsrText.extract("""{"result":{"text":""}}"""))
        assertNull(VolcAsrText.extract(""))
    }
}
