package com.kuikly.stockchat.domain.chat

/**
 * 一次 AI 研究任务的真实阶段。UI 只展示服务已经走到的阶段，避免用定时器伪造进度。
 */
enum class ResearchStage {
    UNDERSTANDING,
    RESOLVING_INSTRUMENTS,
    FETCHING_MARKET_DATA,
    SYNTHESIZING,
    COMPLETE,
}

data class ResearchProgress(
    val stage: ResearchStage,
    val detail: String,
    /** 本次实际读取的标的数。 */
    val instrumentCount: Int = 0,
    /** 本次实际用于分析的日 K 数据点数。 */
    val dataPointCount: Int = 0,
    /** 本轮已经实际读取的数据来源；不把模型常识伪装成外部检索。 */
    val sources: List<ResearchSource> = emptyList(),
)

/**
 * 可脱离 Pager 运行的研究过程状态。
 * 响应式 UI 模型只负责投影该状态，归并规则留在纯领域层以便跨端复用和测试。
 */
data class ResearchState(
    val stage: ResearchStage = ResearchStage.UNDERSTANDING,
    val detail: String = "正在理解问题与分析目标",
    val instrumentCount: Int = 0,
    val dataPointCount: Int = 0,
    val elapsedMs: Long = 0L,
    val sources: List<ResearchSource> = emptyList(),
    val expanded: Boolean = true,
    val interrupted: Boolean = false,
) {
    fun update(progress: ResearchProgress): ResearchState = copy(
        stage = progress.stage,
        detail = progress.detail,
        instrumentCount = maxOf(instrumentCount, progress.instrumentCount),
        dataPointCount = maxOf(dataPointCount, progress.dataPointCount),
        sources = progress.sources.ifEmpty { sources },
        interrupted = false,
    )

    fun complete(elapsedMs: Long = this.elapsedMs): ResearchState = copy(
        stage = ResearchStage.COMPLETE,
        detail = completionDetail(elapsedMs),
        elapsedMs = elapsedMs,
        expanded = false,
        interrupted = false,
    )

    fun interrupt(detail: String, elapsedMs: Long = this.elapsedMs): ResearchState = copy(
        detail = detail,
        elapsedMs = elapsedMs,
        expanded = true,
        interrupted = true,
    )

    private fun completionDetail(elapsedMs: Long): String = when {
        sources.isNotEmpty() -> "已检索 ${sources.size} 类数据源 · ${elapsedText(elapsedMs)}"
        instrumentCount > 0 && dataPointCount > 0 ->
            "已分析 $instrumentCount 个标的 · $dataPointCount 条走势数据"
        instrumentCount > 0 -> "已分析 $instrumentCount 个标的并生成结构化解读"
        else -> "已完成分析并生成回答"
    }
}

fun elapsedText(elapsedMs: Long): String = when {
    elapsedMs <= 0L -> "耗时统计中"
    elapsedMs < 1_000L -> "${elapsedMs}ms"
    else -> "${elapsedMs / 100 / 10.0}秒"
}

enum class ResearchSourceKind { QUOTE, HISTORY, DERIVED, ATTACHMENT }

data class ResearchSource(
    val title: String,
    val detail: String,
    val kind: ResearchSourceKind,
)

/** 回答完成后固化到会话历史中的研究凭据。 */
data class ResearchReport(
    val elapsedMs: Long = 0L,
    val instrumentCount: Int = 0,
    val dataPointCount: Int = 0,
    val sources: List<ResearchSource> = emptyList(),
)
