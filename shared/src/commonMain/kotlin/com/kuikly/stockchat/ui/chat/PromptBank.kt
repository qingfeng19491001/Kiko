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
        QuickPrompt("行情", "腾讯控股后市如何？", "腾讯控股后市如何"),
        QuickPrompt("板块", "港股科技板块趋势分析", "港股科技板块"),
        QuickPrompt("对比", "比亚迪 vs 特斯拉 对比", "比亚迪 vs 特斯拉"),
        QuickPrompt("风险", "阿里巴巴有哪些风险", "阿里巴巴有哪些风险"),
        QuickPrompt("宏观", "美联储利率影响解读", "美联储利率影响"),
        QuickPrompt("知识", "什么是市盈率", "什么是市盈率"),
        QuickPrompt("估值", "贵州茅台现在能买吗？", "茅台现在能买吗"),
        QuickPrompt("指数", "恒生指数短期走势判断", "恒指怎么走"),
        QuickPrompt("行情", "宁德时代趋势分析", "宁德时代趋势"),
        QuickPrompt("行情", "小米集团后市如何？", "小米集团后市如何"),
        QuickPrompt("对比", "英伟达 vs 苹果 对比", "英伟达 vs 苹果"),
        QuickPrompt("知识", "什么是均线", "什么是均线"),
    )

    /** 输入框上方的提问胶囊，覆盖个股 / 对比 / 指数 / 知识，可换页。 */
    val composerCapsules: List<QuickPrompt> = listOf(
        QuickPrompt("行情", "腾讯控股后市如何？", "腾讯控股后市如何"),
        QuickPrompt("估值", "贵州茅台现在能买吗？", "茅台现在能买吗"),
        QuickPrompt("指数", "恒生指数短期走势判断", "恒指怎么走"),
        QuickPrompt("对比", "比亚迪 vs 特斯拉 对比", "比亚迪 vs 特斯拉"),
        QuickPrompt("风险", "阿里巴巴有哪些风险", "阿里巴巴风险"),
        QuickPrompt("知识", "什么是市盈率", "什么是市盈率"),
        QuickPrompt("宏观", "美联储利率影响解读", "美联储利率"),
        QuickPrompt("板块", "港股科技板块趋势分析", "港股科技板块"),
        QuickPrompt("行情", "美团近期走势怎么看？", "美团近期走势"),
        QuickPrompt("行情", "小米集团后市如何？", "小米集团后市"),
        QuickPrompt("行情", "宁德时代趋势分析", "宁德时代趋势"),
        QuickPrompt("对比", "腾讯 vs 阿里巴巴 对比", "腾讯 vs 阿里"),
        QuickPrompt("行情", "苹果最新行情分析", "苹果最新行情"),
        QuickPrompt("行情", "英伟达后市如何？", "英伟达后市如何"),
        QuickPrompt("指数", "上证指数短期走势判断", "上证指数怎么走"),
        QuickPrompt("知识", "什么是均线", "什么是均线"),
        QuickPrompt("行情", "中国平安估值贵吗？", "中国平安估值"),
        QuickPrompt("对比", "英伟达 vs 苹果 对比", "英伟达 vs 苹果"),
        QuickPrompt("指数", "创业板指趋势分析", "创业板指趋势"),
        QuickPrompt("知识", "什么是 RSI", "什么是 RSI"),
        QuickPrompt("风险", "特斯拉有哪些风险", "特斯拉有哪些风险"),
        QuickPrompt("行情", "比亚迪股份后市如何？", "比亚迪后市如何"),
        QuickPrompt("知识", "什么是市净率", "什么是市净率"),
        QuickPrompt("指数", "恒生科技指数趋势分析", "恒生科技指数"),
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
        "行情", "估值" -> IconKind.CHART
        "对比" -> IconKind.COMPARE
        "风险" -> IconKind.ALERT
        "板块", "指数" -> IconKind.TREND_UP
        "宏观", "知识" -> IconKind.BOOK
        else -> IconKind.SPARKLE
    }
}
