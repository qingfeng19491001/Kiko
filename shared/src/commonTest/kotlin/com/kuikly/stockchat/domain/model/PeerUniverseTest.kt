package com.kuikly.stockchat.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeerUniverseTest {

    @Test
    fun tencentPeersStayInHkInternetNames() {
        val peers = PeerUniverse.peersOf(StockCatalog.tencent, 3)
        assertEquals(3, peers.size)
        assertTrue(peers.all { it.market == Market.HK })
        assertTrue(peers.none { it.key == StockCatalog.tencent.key })
        assertTrue(peers.any { it.key == StockCatalog.alibaba.key || it.key == StockCatalog.baidu.key })
    }

    @Test
    fun rankingExpandsSingleNameToFour() {
        val loaded = PeerUniverse.forCompare(listOf(StockCatalog.tencent), "腾讯控股同业排名", 4)
        assertEquals(4, loaded.size)
        assertEquals(StockCatalog.tencent.key, loaded.first().key)
    }

    @Test
    fun namedPairDoesNotInventExtraPeers() {
        val loaded = PeerUniverse.forCompare(
            listOf(StockCatalog.tencent, StockCatalog.alibaba),
            "腾讯 vs 阿里",
            4,
        )
        assertEquals(2, loaded.size)
    }
}
