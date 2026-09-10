package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.StockCatalog

/** 用户意图 */
enum class Intent(val label: String) {
    /** 个股综合分析 */
    STOCK_ANALYSIS("行情分析"),
    /** 多标的对比 */
    COMPARE("对比行情"),
    /** 趋势 / 技术面判断 */
    TREND("趋势判断"),
    /** 风险提示 */
    RISK("风险提醒"),
    /** 大盘 / 指数 / 板块 */
    MARKET_OVERVIEW("大盘概览"),
    /** 连板梯队 / 涨停复盘 */
    LIMIT_UP_LADDER("连板梯队"),
    /** 个股资金流向 */
    CAPITAL_FLOW("资金流向"),
    /** 名词解释 / 知识问答 */
    KNOWLEDGE("知识问答"),
    /** 问候 / 闲聊 */
    GREETING("问候"),
    /** 无法识别 */
    UNKNOWN("其他"),
}

data class ParsedIntent(
    val intent: Intent,
    val instruments: List<Instrument>,
    val rawText: String,
    /** 知识问答时抽取到的关键词 */
    val topic: String = "",
    /** 模型点名但目录未命中的名称，交给搜索补全 */
    val unresolvedNames: List<String> = emptyList(),
) {
    val primary: Instrument? get() = instruments.firstOrNull()
    val secondary: Instrument? get() = instruments.getOrNull(1)
}

/**
 * 轻量意图识别：关键词规则 + 标的实体抽取 + 上下文补全。
 * 可替换为大模型 function calling，这里保证离线可用且结果可解释。
 */
object IntentParser {

    private val compareKeywords = listOf("对比", "比较", "vs", "VS", "和", "与", "哪个好", "谁更", "相比", "区别")
    private val trendKeywords = listOf("趋势", "走势", "技术面", "均线", "k线", "K线", "形态", "突破", "支撑", "压力", "短期", "中期", "判断")
    private val riskKeywords = listOf("风险", "注意", "隐患", "回撤", "亏", "跌", "危险", "止损", "警惕", "利空")
    private val marketKeywords = listOf("大盘", "指数", "板块", "市场", "行情怎么样", "今天市场", "恒指", "恒生", "上证", "沪指", "创业板", "深成指", "港股", "a股", "A股", "美股")
    private val ladderKeywords = listOf("连板", "梯队", "打板", "涨停复盘", "涨停板", "最高板", "龙头")
    private val flowKeywords = listOf("资金流向", "资金流", "主力", "净流入", "超大单", "大单")
    private val analysisKeywords = listOf("后市", "如何", "怎么看", "怎么样", "分析", "值得", "买", "卖", "持有", "行情", "估值", "怎样", "能不能", "可以")
    private val greetingKeywords = listOf("你好", "hello", "hi", "在吗", "你是谁", "介绍一下你", "能做什么", "帮我做什么")
    private val knowledgeKeywords = listOf("什么是", "是什么", "解释", "含义", "意思", "怎么算", "如何理解", "名词", "解读", "影响")

    fun parse(text: String, contextInstruments: List<Instrument> = emptyList()): ParsedIntent {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        var instruments = StockCatalog.extract(trimmed)

        val hasCompare = compareKeywords.any { lower.contains(it.lowercase()) }
        val hasTrend = trendKeywords.any { lower.contains(it.lowercase()) }
        val hasRisk = riskKeywords.any { lower.contains(it) }
        val hasMarket = marketKeywords.any { lower.contains(it.lowercase()) }
        val hasAnalysis = analysisKeywords.any { lower.contains(it) }
        val hasGreeting = greetingKeywords.any { lower.contains(it) }
        val hasKnowledge = knowledgeKeywords.any { lower.contains(it) }
        val hasLadder = ladderKeywords.any { lower.contains(it) }
        val hasFlow = flowKeywords.any { lower.contains(it) }

        // 上下文补全：本轮没提标的，但在追问（如“那它的风险呢”“主力资金呢”）
        if (instruments.isEmpty() && contextInstruments.isNotEmpty() && (hasTrend || hasRisk || hasAnalysis || hasCompare || hasFlow)) {
            instruments = if (hasCompare) contextInstruments.take(2) else contextInstruments.take(1)
        }

        val onlyIndices = instruments.isNotEmpty() && instruments.all { it.isIndex }

        val intent = when {
            hasGreeting && instruments.isEmpty() -> Intent.GREETING
            instruments.size >= 2 && (hasCompare || !hasKnowledge) -> Intent.COMPARE
            hasLadder -> Intent.LIMIT_UP_LADDER
            hasFlow -> Intent.CAPITAL_FLOW
            onlyIndices -> Intent.MARKET_OVERVIEW
            instruments.isNotEmpty() && hasRisk -> Intent.RISK
            instruments.isNotEmpty() && hasTrend -> Intent.TREND
            instruments.isNotEmpty() -> Intent.STOCK_ANALYSIS
            hasMarket -> Intent.MARKET_OVERVIEW
            hasKnowledge || KnowledgeBase.match(trimmed) != null -> Intent.KNOWLEDGE
            hasRisk -> Intent.RISK
            else -> Intent.UNKNOWN
        }

        return ParsedIntent(intent, instruments, trimmed, topic = KnowledgeBase.match(trimmed)?.title ?: "")
    }
}
