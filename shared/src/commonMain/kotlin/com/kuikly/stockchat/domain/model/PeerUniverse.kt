package com.kuikly.stockchat.domain.model

/**
 * 用目录里的真实标的补全同业，不编造报价或财务数据。
 * 客户端随后走腾讯行情接口拉现价 / K 线。
 */
object PeerUniverse {

    fun isRankingQuestion(text: String): Boolean {
        val lower = text.lowercase()
        return rankKeywords.any { lower.contains(it.lowercase()) }
    }

    fun peersOf(primary: Instrument, max: Int = 3): List<Instrument> {
        if (max <= 0 || primary.isIndex) return emptyList()
        val tokens = sectorTokens(primary.sector)
        return StockCatalog.stocks
            .filter { it.key != primary.key && it.market == primary.market }
            .sortedWith(
                compareByDescending<Instrument> { overlap(sectorTokens(it.sector), tokens) }
                    .thenBy { it.name },
            )
            .take(max)
    }

    /**
     * 对比 / 排名要加载的标的：用户点名的优先，排名问句且不足 4 只时按板块补同业。
     */
    fun forCompare(named: List<Instrument>, rawText: String, max: Int = 4): List<Instrument> {
        val unique = named.distinctBy { it.key }
        if (unique.isEmpty()) return emptyList()
        if (unique.size >= 2 && !isRankingQuestion(rawText)) return unique.take(max)
        val primary = unique.first()
        val extra = peersOf(primary, (max - unique.size).coerceAtLeast(0))
        return (unique + extra).distinctBy { it.key }.take(max)
    }

    private fun sectorTokens(sector: String): Set<String> =
        sector.split('/', '、', ',', '，', ' ')
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()

    private fun overlap(a: Set<String>, b: Set<String>): Int = a.count { it in b }

    private val rankKeywords = listOf(
        "排名", "同业", "同行业", "行业里", "行业中", "地位", "对标", "赛道", "板块里", "板块中",
    )
}
