package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Market
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TencentBoardListTest {

    @Test
    fun parsesAShareTurnoverRankAndSkipsBeijing() {
        val raw = """{"code":0,"msg":"ok","data":{"rank_list":[
            {"code":"sz300308","name":"中际旭创"},
            {"code":"sh600519","name":"贵州茅台"},
            {"code":"bj920895","name":"花溪科技"}
        ],"total":3}}"""
        val hits = TencentBoardList.parseAShareRank(raw)
        assertEquals(listOf("sz300308", "sh600519"), hits.map { it.key })
        assertEquals("中际旭创", hits[0].name)
        assertEquals(Market.SZ, hits[0].market)
    }

    @Test
    fun parsesHongKongAmountRank() {
        val raw = """list_data={"code":0,"data":{"page_data":["00700~腾讯控股~426.00","09988~阿里巴巴-W~105.10"]}}"""
        val hits = TencentBoardList.parseHkRank(raw)
        assertEquals("00700", hits[0].code)
        assertEquals(Market.HK, hits[0].market)
        assertEquals("腾讯控股", hits[0].name)
        assertTrue(hits[0].stockConnect)
    }

    @Test
    fun parsesSouthboundConnectCodes() {
        val raw = """{"code":0,"msg":"OK","data":{"data":["hk00700 互联网","hk09988 电商"],"pagenum":"2"}}"""
        val hits = TencentBoardList.parseGgt(raw)
        assertEquals(listOf("hk00700", "hk09988"), hits.map { it.key })
        assertTrue(hits.all { it.stockConnect })
    }

    @Test
    fun parsesSinaUsMarketCapList() {
        val raw = """/*<script>location.href='//sina.com';</script>*/
cb({"count":"2","data":[
  {"name":"NVIDIA Corporation","cname":"英伟达公司","symbol":"NVDA","market":"NASDAQ"},
  {"name":"Apple Inc.","cname":"苹果公司","symbol":"AAPL","market":"NASDAQ"}
]})"""
        val hits = SinaUsMarketList.parse(raw)
        assertEquals(listOf("NVDA", "AAPL"), hits.map { it.code })
        assertEquals(Market.US, hits[0].market)
        assertEquals("英伟达公司", hits[0].name)
        assertEquals("NVDA.OQ", hits[0].tencentCodeOverrideOrNull())
    }
}
