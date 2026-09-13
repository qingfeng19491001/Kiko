package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.ui.components.IconKind

/** 首页推荐问题 */
data class QuickPrompt(
    val category: String,
    val prompt: String,
    val capsule: String = prompt,
)

/**
 * 欢迎语与输入框胶囊共用的提问库。
 * 「换一换」按页轮换，不随机，保证连续点击一定换到另一组。
 */
object PromptBank {

    const val WELCOME_PAGE_SIZE = 3
    const val CAPSULE_PAGE_SIZE = 6

    val welcomePrompts: List<QuickPrompt> = listOf(
        QuickPrompt("行情", "腾讯控股在同行业里排名怎么样"),
        QuickPrompt("估值", "帮我详细分析一下贵州茅台"),
        QuickPrompt("策略", "当下科技股，我该加仓还是减仓"),
        QuickPrompt("选股", "哪些个股属于高现金红利股？"),
        QuickPrompt("趋势", "怎样判断股票的长期趋势？"),
        QuickPrompt("知识", "股价创新高但成交量低说明什么？"),
        QuickPrompt("风险", "阿里巴巴有哪些主要风险"),
        QuickPrompt("对比", "比亚迪和特斯拉该怎么对比"),
        QuickPrompt("指数", "恒生指数短期会怎么走"),
        QuickPrompt("估值", "宁德时代现在能买吗？"),
        QuickPrompt("行情", "小米集团后市如何判断"),
        QuickPrompt("知识", "什么是市盈率，该怎么用"),
    )

    /** 输入框上方的功能胶囊：短名展示，点按发送对应提问。 */
    val composerCapsules: List<QuickPrompt> = listOf(
        QuickPrompt("选股", "帮我选几只近期值得关注的股票", "AI 陪我选"),
        QuickPrompt("策略", "给我一个适合当前行情的交易策略", "AI 策略搭子"),
        QuickPrompt("盯盘", "今天大盘怎么样？", "帮我盯"),
        QuickPrompt("研究", "帮我深度研究腾讯控股", "深度研究"),
        QuickPrompt("预测", "腾讯控股短期走势会怎么走？", "预期测算"),
        QuickPrompt("机会", "现在市场上有哪些投资机会？", "机会雷达"),
        QuickPrompt("诊股", "帮我诊断一下宁德时代的风险", "诊股"),
        QuickPrompt("机会", "帮我看看最近有什么布局机会", "拍照找机会"),
        QuickPrompt("盯盘", "帮我盯盘阿里巴巴", "盯盘"),
        QuickPrompt("资金", "贵州茅台资金流向怎么样", "资金流向"),
        QuickPrompt("连板", "连板梯队", "连板梯队"),
        QuickPrompt("估值", "贵州茅台现在能买吗？", "估值诊断"),
        QuickPrompt("风险", "特斯拉有哪些风险", "风险扫描"),
        QuickPrompt("指数", "上证指数短期走势判断", "指数解读"),
        QuickPrompt("对比", "比亚迪 vs 特斯拉 对比", "对比选股"),
        QuickPrompt("知识", "什么是市盈率", "知识问答"),
        QuickPrompt("板块", "港股科技板块趋势分析", "板块趋势"),
        QuickPrompt("宏观", "美联储利率影响解读", "宏观解读"),
    )

    fun pageCount(pageSize: Int = WELCOME_PAGE_SIZE, items: List<QuickPrompt> = welcomePrompts): Int {
        if (items.isEmpty() || pageSize <= 0) return 0
        return (items.size + pageSize - 1) / pageSize
    }

    fun nextPage(page: Int, pageSize: Int = WELCOME_PAGE_SIZE, items: List<QuickPrompt> = welcomePrompts): Int {
        val count = pageCount(pageSize, items)
        if (count == 0) return 0
        return (page + 1).mod(count)
    }

    fun welcomePage(page: Int, pageSize: Int = WELCOME_PAGE_SIZE): List<QuickPrompt> =
        pageOf(welcomePrompts, page, pageSize)

    fun capsulePage(page: Int, pageSize: Int = CAPSULE_PAGE_SIZE): List<QuickPrompt> =
        pageOf(composerCapsules, page, pageSize)

    private fun pageOf(items: List<QuickPrompt>, page: Int, pageSize: Int): List<QuickPrompt> {
        val count = pageCount(pageSize, items)
        if (count == 0) return emptyList()
        val start = page.mod(count) * pageSize
        val end = minOf(start + pageSize, items.size)
        return items.subList(start, end)
    }

    fun iconFor(category: String): IconKind = when (category) {
        "选股" -> IconKind.SPARKLE
        "策略" -> IconKind.STRATEGY
        "盯盘" -> IconKind.EYE
        "研究" -> IconKind.BOOK
        "机会" -> IconKind.RADAR
        "连板" -> IconKind.CANDLE
        "行情", "估值", "诊股", "资金" -> IconKind.CHART
        "对比" -> IconKind.COMPARE
        "风险" -> IconKind.ALERT
        "板块", "指数", "趋势", "预测" -> IconKind.TREND_UP
        "宏观", "知识" -> IconKind.BOOK
        else -> IconKind.CHART
    }

    const val SCHEDULE_PROMPT = "帮我设定每个交易日开盘后的自选股简报，用定时任务的方式说明怎么提醒我"
    const val MEMORY_PROMPT = "根据我的自选股和近期对话，整理一份投资记忆"
    const val RESEARCH_PROMPT = "帮我深度研究腾讯控股"
    const val PLAZA_PROMPT = "技能广场：你能做什么"

    fun memoryPrompt(watchlistNames: List<String>): String {
        val names = watchlistNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (names.isEmpty()) {
            "我还没有自选股。请说明可以从个股详情页加入自选，之后投资记忆会按自选整理。你现在能做什么？"
        } else {
            "根据我的自选股整理一份投资记忆：${names.joinToString("、")}。请分别给出后市怎么看和主要风险。"
        }
    }
}
