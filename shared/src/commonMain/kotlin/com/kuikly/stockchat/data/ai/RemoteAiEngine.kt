package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.attachment.LoadedAttachment
import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.chat.AiAnswer
import com.kuikly.stockchat.domain.chat.AnswerAssembler
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.AnswerComposer
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.ParsedIntent
import com.kuikly.stockchat.domain.model.DerivedMarketData
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 远端大模型引擎：调用阿里云百炼（DashScope）OpenAI 兼容接口。
 *
 * 有行情时保留本地卡片/图表，模型按章节标题写解读，客户端把每段插到对应数据卡前面。
 * 图片附件以 OpenAI 兼容 `image_url` Data URL 发给视觉模型。调用失败时展示真实错误，不再静默换成本地套话。
 */
class RemoteAiEngine(
    private val httpClient: HttpClient,
    private val baseUrl: String = AiConfig.BASE_URL,
    private val apiKey: String = AiConfig.API_KEY,
    private val model: String = AiConfig.MODEL,
    private val temperature: Double = AiConfig.TEMPERATURE,
    private val timeoutSeconds: Int = AiConfig.TIMEOUT_SECONDS,
) : AiEngine {

    override fun generate(
        parsed: ParsedIntent,
        snapshots: List<MarketSnapshot>,
        derived: DerivedMarketData,
        attachments: List<LoadedAttachment>,
        callback: (AiAnswer) -> Unit,
    ) {
        if (apiKey.isBlank()) {
            callback(
                AiAnswer(
                    parsed.intent,
                    listOf(AnswerBlock.Markdown("尚未配置百炼 API Key，无法调用 qwen3.8-flash。请在本地 AiSecrets.kt 填写 API_KEY 后重新编译。")),
                    snapshots,
                ),
            )
            return
        }
        val hasVision = attachments.any { it.hasImage }
        if (snapshots.isEmpty() && derived.isEmpty) {
            callModel(systemPrompt(hasVision), userContentNoData(parsed, attachments)) { reply, error ->
                callback(
                    AiAnswer(
                        parsed.intent,
                        listOf(
                            AnswerBlock.Markdown(reply ?: modelFailureMarkdown(error)),
                            AnswerBlock.FollowUps(defaultFollowUps(parsed)),
                        ),
                        emptyList(),
                    ),
                )
            }
            return
        }

        val base = AnswerComposer.compose(parsed, snapshots, derived)
        callModel(systemPrompt(hasVision), userContentWithData(parsed, snapshots, attachments)) { reply, error ->
            callback(
                if (reply != null) AnswerAssembler.merge(base, reply)
                else AnswerAssembler.withFailureNotice(base, error),
            )
        }
    }

    private fun systemPrompt(hasVision: Boolean): String = buildString {
        appendLine("你是一位专业的中文股票市场分析助手，服务于散户投资者。请基于用户提供的实时行情数据，给出专业、客观、简洁的分析。")
        appendLine()
        appendLine("要求：")
        appendLine("1. 只使用提供的真实行情数据进行分析，严禁编造任何不存在的数据、价格或事件")
        appendLine("2. 回答使用中文 Markdown（标题、列表、粗体），不要使用代码块")
        appendLine("3. 必须按下面标题分段（不要省略标题）。客户端会把每段插到对应的实时数据卡/图前面：")
        appendLine("## 盘面概览")
        appendLine("## 技术面")
        appendLine("## 估值与规模")
        appendLine("## 核心结论")
        appendLine("指数或没有估值数据时可跳过「估值与规模」。每段 2～5 句，全文控制在 400 字以内。")
        appendLine("4. 行情卡片、评分和图表由客户端展示，不要再列报价表或重复价格网格")
        appendLine("5. 保持中立客观，不要给出明确买卖指令，应提示投资风险")
        appendLine("6. 如数据缺失，需明确说明「数据缺失」，不要臆测")
        if (hasVision) {
            appendLine("7. 用户可能附带图片（K 线截图、公告、研报页等），请结合图像内容回答，不要假装没看到图")
        }
    }.trim()

    private fun userContentNoData(parsed: ParsedIntent, attachments: List<LoadedAttachment>): UserContent {
        val role = when (parsed.intent) {
            Intent.GREETING -> "用户在打招呼，请简短友好地介绍你能做什么。"
            Intent.KNOWLEDGE -> "用户在询问金融知识，请用通俗语言解释。"
            else -> "请回答用户的问题。"
        }
        return AttachmentPromptBuilder.build("$role\n\n用户输入：${parsed.rawText}", attachments)
    }

    private fun userContentWithData(
        parsed: ParsedIntent,
        snapshots: List<MarketSnapshot>,
        attachments: List<LoadedAttachment>,
    ): UserContent {
        val task = when (parsed.intent) {
            Intent.COMPARE -> "请对以上两只标的做横向对比分析，包括涨跌表现、估值水平、技术面强弱。"
            Intent.TREND -> "请基于以上数据做趋势判断，分析短期与中期走势。"
            Intent.RISK -> "请梳理该标的主要风险点，按价格波动、估值、行业三个维度展开。"
            Intent.MARKET_OVERVIEW -> "请分析该指数/大盘的走势与短期展望。"
            Intent.STOCK_ANALYSIS -> "请对该标的做综合分析，涵盖盘面、技术面、估值与关注要点。"
            else -> "请回答用户的问题。"
        }
        return AttachmentPromptBuilder.build(
            """
            用户问题：${parsed.rawText}

            以下是相关标的的最新行情数据（真实数据，请仅基于此分析）：
            ${buildDigest(snapshots)}

            $task
            """.trimIndent(),
            attachments,
        )
    }

    private fun buildDigest(snapshots: List<MarketSnapshot>): String =
        snapshots.joinToString("\n---\n") { snapshotDigest(it) }

    private fun snapshotDigest(snapshot: MarketSnapshot): String {
        val q = snapshot.quote
        val ins = q.instrument
        val a = AnalysisEngine.analyze(snapshot)
        return buildString {
            appendLine("【${ins.name}】（${ins.displayCode}）")
            appendLine("市场：${ins.market.label}  板块：${ins.sector}  币种：${ins.market.currency}")
            appendLine("最新价：${NumberFormat.price(q.price)}")
            appendLine("涨跌额：${NumberFormat.signed(q.change)}  涨跌幅：${NumberFormat.signedPct(q.changePct)}")
            appendLine("今开：${NumberFormat.price(q.open)}  最高：${NumberFormat.price(q.high)}  最低：${NumberFormat.price(q.low)}  昨收：${NumberFormat.price(q.prevClose)}")
            appendLine("成交额：${NumberFormat.compact(q.turnover)}")
            q.amplitude?.let { appendLine("振幅：${NumberFormat.pct(it)}") }
            q.turnoverRate?.let { appendLine("换手率：${NumberFormat.pct(it)}") }
            if (!ins.isIndex) {
                appendLine("市盈率(PE)：${NumberFormat.ratio(q.pe)}  市净率(PB)：${NumberFormat.ratio(q.pb)}")
                appendLine("总市值：${NumberFormat.capFromYi(q.totalMarketCap)}")
                q.high52w?.let { high -> q.low52w?.let { low -> appendLine("52周区间：${NumberFormat.price(low)} ~ ${NumberFormat.price(high)}") } }
            }
            if (a.hasEnoughData) {
                appendLine("技术面：")
                appendLine("  MA5=${NumberFormat.price(a.ma5)}  MA10=${NumberFormat.price(a.ma10)}  MA20=${NumberFormat.price(a.ma20)}")
                a.ma60?.let { appendLine("  MA60=${NumberFormat.price(it)}") }
                a.rsi14?.let { appendLine("  RSI(14)=${NumberFormat.fixed(it, 1)}") }
                appendLine("  20日支撑=${NumberFormat.price(a.support)}  20日压力=${NumberFormat.price(a.resistance)}")
                a.change5dPct?.let { appendLine("  近5日涨跌：${NumberFormat.signedPct(it)}") }
                a.change20dPct?.let { appendLine("  近20日涨跌：${NumberFormat.signedPct(it)}") }
                a.volatilityPct?.let { appendLine("  年化波动率：${NumberFormat.fixed(it, 0)}%") }
                a.biasToMa20Pct?.let { appendLine("  偏离MA20：${NumberFormat.signedPct(it, 1)}") }
                appendLine("  综合评分：${a.score}/100（短期${a.shortTermTrend.label}，中期${a.midTermTrend.label}）")
            }
        }
    }

    private fun callModel(system: String, user: UserContent, callback: (reply: String?, error: String?) -> Unit) {
        val userMessage = JSONObject().apply {
            put("role", "user")
            when (user) {
                is UserContent.Text -> put("content", user.value)
                is UserContent.Parts -> put("content", user.array)
            }
        }
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(userMessage)
        }
        val requestTimeout = if (user is UserContent.Parts) timeoutSeconds.coerceAtLeast(60) else timeoutSeconds
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", false)
            put("temperature", temperature)
            put("enable_thinking", false)
        }
        httpClient.postJson("$baseUrl/chat/completions", body, mapOf("Authorization" to "Bearer $apiKey"), requestTimeout) { text, error ->
            if (text != null) {
                val reply = DashScopeParser.messageContent(text)
                callback(reply, if (reply == null) DashScopeParser.errorMessage(text) ?: "解析 AI 回复失败" else null)
            } else {
                callback(null, DashScopeParser.errorMessage(error) ?: error ?: "AI 服务请求失败")
            }
        }
    }

    private fun modelFailureMarkdown(error: String?): String = buildString {
        appendLine("百炼模型调用失败，没有生成分析文案。")
        appendLine()
        appendLine(error?.ifBlank { null } ?: "请检查 API Key、模型权限和网络后重试。")
        appendLine()
        appendLine("当前网关：`$baseUrl`，模型：`$model`。Token Plan 的 `sk-sp-` Key 必须走 token-plan 网关；按量 Key（`sk-`）必须走 dashscope 网关，两者不能混用。")
    }.trim()

    private fun defaultFollowUps(parsed: ParsedIntent): List<String> {
        val ins = parsed.instruments.firstOrNull()
        return if (ins != null) {
            listOf("${ins.name}的趋势判断", "${ins.name}有哪些风险", "${ins.name}的估值贵吗")
        } else {
            listOf("腾讯控股后市如何", "恒生指数短期走势判断", "什么是市盈率")
        }
    }
}
