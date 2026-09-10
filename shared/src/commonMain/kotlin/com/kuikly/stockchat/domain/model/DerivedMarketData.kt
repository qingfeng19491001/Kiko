package com.kuikly.stockchat.domain.model

/**
 * 衍生市场数据：来自本地 AKShare 网关（scripts/ak_gateway.py）。
 * 与实时行情解耦，任意一项缺失（网关未启动 / 接口失败）时不影响主流程。
 */
data class DerivedMarketData(
    val breadth: MarketBreadthData? = null,
    val ladder: LimitUpLadderData? = null,
    val flow: CapitalFlowData? = null,
) {
    val isEmpty: Boolean get() = breadth == null && ladder == null && flow == null
}

/** 市场广度（A 股全市场） */
data class MarketBreadthData(
    val date: String,
    val advancing: Int,
    val declining: Int,
    val limitUp: Int,
    val limitDown: Int,
    val halted: Int,
    /** 活跃度百分比数值，如 27.89 */
    val activity: Double,
)

/** 连板梯队 */
data class LimitUpLadderData(
    val date: String,
    val levels: List<LadderLevelData>,
)

data class LadderLevelData(
    val level: Int,
    val stocks: List<LadderStockData>,
)

data class LadderStockData(
    val name: String,
    val code: String,
    val changePct: Double,
    /** 流通市值，单位元 */
    val marketCap: Double,
)

/** 个股资金流向（最新一个交易日） */
data class CapitalFlowData(
    val code: String,
    val date: String,
    val flows: List<FlowItemData>,
)

data class FlowItemData(
    val label: String,
    /** 净流入，单位元 */
    val netInflow: Double,
    /** 净占比百分比数值 */
    val pct: Double,
)
