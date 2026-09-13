package com.kuikly.stockchat.ui.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog

enum class MarketBoard(val label: String) {
    ALL("全球"),
    WATCH("自选"),
    INDEX("指数"),
    CN("A股"),
    HK("港股"),
    HK_CONNECT("港股通"),
    US("美股"),
}

object MarketBoardOverview {
    fun indices(board: MarketBoard): List<Instrument> = when (board) {
        MarketBoard.ALL -> listOf(StockCatalog.sse, StockCatalog.hsi, StockCatalog.dji)
        MarketBoard.CN -> listOf(StockCatalog.sse, StockCatalog.szse, StockCatalog.chinext)
        MarketBoard.HK, MarketBoard.HK_CONNECT -> listOf(StockCatalog.hsi, StockCatalog.hstech, StockCatalog.hscei)
        MarketBoard.US -> listOf(StockCatalog.dji, StockCatalog.ixic, StockCatalog.ndx)
        MarketBoard.WATCH, MarketBoard.INDEX -> emptyList()
    }

    fun statsInstruments(board: MarketBoard): List<Instrument> = when (board) {
        MarketBoard.ALL, MarketBoard.CN -> listOf(
            StockCatalog.sse, StockCatalog.szse,
            StockCatalog.csi300, StockCatalog.csi500, StockCatalog.csi1000,
        )
        MarketBoard.HK, MarketBoard.HK_CONNECT -> listOf(
            StockCatalog.hsi, StockCatalog.hstech, StockCatalog.hscei,
        )
        else -> emptyList()
    }

    fun showsStats(board: MarketBoard): Boolean = statsInstruments(board).isNotEmpty()

    fun indicesTitle(board: MarketBoard): String = when (board) {
        MarketBoard.CN -> "A股指数"
        MarketBoard.HK, MarketBoard.HK_CONNECT -> "港股指数"
        MarketBoard.US -> "美股指数"
        MarketBoard.ALL -> "市场指数"
        else -> "指数"
    }

    fun statsTitle(board: MarketBoard): String = when (board) {
        MarketBoard.CN -> "A股总览"
        MarketBoard.HK, MarketBoard.HK_CONNECT -> "港股总览"
        MarketBoard.ALL -> "市场总览"
        else -> "总览"
    }

    fun listTitle(board: MarketBoard): String = when (board) {
        MarketBoard.WATCH -> "自选"
        MarketBoard.CN -> "A股列表"
        MarketBoard.HK -> "港股列表"
        MarketBoard.HK_CONNECT -> "港股通"
        MarketBoard.US -> "美股列表"
        MarketBoard.ALL -> "股票列表"
        else -> "股票列表"
    }
}

internal data class MarketOverviewStats(
    val turnoverYuan: Double?,
    val turnoverPrefix: String,
    val sizeTitle: String,
    val largeLabel: String,
    val midLabel: String,
    val smallLabel: String,
    val largePct: Double?,
    val midPct: Double?,
    val smallPct: Double?,
    val turnoverInstrument: Instrument,
    val sizeInstrument: Instrument,
) {
    val hasTurnover: Boolean get() = turnoverYuan != null && turnoverYuan > 0
    val hasSize: Boolean get() = largePct != null || midPct != null || smallPct != null
    val isEmpty: Boolean get() = !hasTurnover && !hasSize

    val sizeHeadline: String
        get() {
            val ranked = listOf(
                largeLabel to largePct,
                midLabel to midPct,
                smallLabel to smallPct,
            ).mapNotNull { (name, pct) -> pct?.let { name to it } }
            if (ranked.isEmpty()) return sizeTitle
            val leadDown = ranked.minBy { it.second }
            val leadUp = ranked.maxBy { it.second }
            return when {
                leadDown.second < 0 && leadDown.second <= leadUp.second -> "${leadDown.first}领跌"
                leadUp.second > 0 -> "${leadUp.first}领涨"
                else -> "涨跌分化"
            }
        }

    companion object {
        fun from(board: MarketBoard, quotes: Map<String, Quote>): MarketOverviewStats {
            return if (board == MarketBoard.HK || board == MarketBoard.HK_CONNECT) fromHongKong(quotes)
            else fromAShare(quotes)
        }

        fun fromAShare(quotes: Map<String, Quote>): MarketOverviewStats {
            val sse = quotes[StockCatalog.sse.key]
            val szse = quotes[StockCatalog.szse.key]
            val turnover = listOfNotNull(sse?.turnover, szse?.turnover).takeIf { it.size == 2 }?.sum()
            return MarketOverviewStats(
                turnoverYuan = turnover,
                turnoverPrefix = "两市共",
                sizeTitle = "大小盘对比",
                largeLabel = "大盘股",
                midLabel = "中盘股",
                smallLabel = "小盘股",
                largePct = quotes[StockCatalog.csi300.key]?.changePct,
                midPct = quotes[StockCatalog.csi500.key]?.changePct,
                smallPct = quotes[StockCatalog.csi1000.key]?.changePct,
                turnoverInstrument = StockCatalog.sse,
                sizeInstrument = StockCatalog.csi300,
            )
        }

        fun fromHongKong(quotes: Map<String, Quote>): MarketOverviewStats {
            val hsi = quotes[StockCatalog.hsi.key]
            // 恒指 qt 成交额是“万港元”，个股才是原币种
            val turnover = hsi?.turnover?.takeIf { it > 0 }?.times(10_000.0)
            return MarketOverviewStats(
                turnoverYuan = turnover,
                turnoverPrefix = "港股",
                sizeTitle = "指数对比",
                largeLabel = "恒指",
                midLabel = "恒科",
                smallLabel = "国企",
                largePct = hsi?.changePct,
                midPct = quotes[StockCatalog.hstech.key]?.changePct,
                smallPct = quotes[StockCatalog.hscei.key]?.changePct,
                turnoverInstrument = StockCatalog.hsi,
                sizeInstrument = StockCatalog.hsi,
            )
        }
    }
}

object MarketListFilter {
    fun matches(
        instrument: Instrument,
        board: MarketBoard,
        query: String,
        watchKeys: Set<String>,
    ): Boolean {
        if (board == MarketBoard.WATCH && instrument.key !in watchKeys) return false
        if (board == MarketBoard.INDEX && !instrument.isIndex) return false
        if (board == MarketBoard.CN && instrument.market != Market.SH && instrument.market != Market.SZ) return false
        if (board == MarketBoard.HK && instrument.market != Market.HK) return false
        if (board == MarketBoard.HK_CONNECT && !instrument.stockConnect) return false
        if (board == MarketBoard.US && instrument.market != Market.US) return false
        val needle = query.trim()
        if (needle.isEmpty()) return true
        val lower = needle.lowercase()
        return instrument.name.contains(needle, ignoreCase = true) ||
            instrument.code.contains(needle, ignoreCase = true) ||
            instrument.displayCode.contains(needle, ignoreCase = true) ||
            instrument.aliases.any { it.contains(needle, ignoreCase = true) } ||
            instrument.key.lowercase().contains(lower)
    }
}
