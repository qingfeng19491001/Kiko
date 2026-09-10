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

    /** 输入框上方的提问胶囊，文案更短，方便横滑点选。 */
    val composerCapsules: List<QuickPrompt> = listOf(
        QuickPrompt("行情", "腾讯控股后市如何？", "腾讯控股后市如何"),
        QuickPrompt("估值", "贵州茅台现在能买吗？", "茅台现在能买吗"),
        QuickPrompt("指数", "恒生指数短期走势判断", "恒指怎么走"),
        QuickPrompt("对比", "比亚迪 vs 特斯拉 对比", "比亚迪 vs 特斯拉"),
        QuickPrompt("风险", "阿里巴巴有哪些风险", "阿里巴巴风险"),
        QuickPrompt("知识", "什么是市盈率", "什么是市盈率"),
        QuickPrompt("宏观", "美联储利率影响解读", "美联储利率"),
        QuickPrompt("板块", "港股科技板块趋势分析", "港股科技板块"),
    )

    fun pageCount(pageSize: Int = WELCOME_PAGE_SIZE): Int {
        if (welcomePrompts.isEmpty() || pageSize <= 0) return 0
        return (welcomePrompts.size + pageSize - 1) / pageSize
    }

    fun nextPage(page: Int, pageSize: Int = WELCOME_PAGE_SIZE): Int {
        val count = pageCount(pageSize)
        if (count == 0) return 0
        return (page + 1).mod(count)
    }

    fun welcomePage(page: Int, pageSize: Int = WELCOME_PAGE_SIZE): List<QuickPrompt> {
        val count = pageCount(pageSize)
        if (count == 0) return emptyList()
        val start = page.mod(count) * pageSize
        val end = minOf(start + pageSize, welcomePrompts.size)
        return welcomePrompts.subList(start, end)
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
