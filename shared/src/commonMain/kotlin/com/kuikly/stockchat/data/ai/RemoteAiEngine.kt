package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.chat.AiAnswer
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.AnswerComposer
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.ParsedIntent
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 远端大模型引擎：调用阿里云百炼（DashScope）OpenAI 兼容接口。
 *
 * 流程：意图 + 行情快照 → 构建 prompt → 调用大模型 → 把回包组装为 [AiAnswer]。
 *
 * 策略：
 * - 有行情数据时，保留本地编排的行情卡片（StockCard / CompareCard / ChartCard 等），
 *   只用大模型生成分析文案 Markdown，确保数字可解释、可复现。
 * - 无行情数据时（知识问答、问候等），整段回复交由大模型生成。
 * - 任一环节失败则降级到 [AnswerComposer] 本地规则引擎，保证可用性。
 */
class RemoteAiEngine(
    private val httpClient: HttpClient,
    private val baseUrl: String = AiConfig.BASE_URL,
    private val apiKey: String = AiConfig.API_KEY,
    private val model: String = AiConfig.MODEL,
    private val temperature: Double = AiConfig.TEMPERATURE,
    private val timeoutSeconds: Int = AiConfig.TIMEOUT_SECONDS,
) : AiEngine {

    override fun generate(parsed: ParsedIntent, snapshots: List<MarketSnapshot>, callback: (AiAnswer) -> Unit) {
        if (snapshots.isEmpty()) {
            // 无行情数据：整段回复交给大模型
            callModel(systemPrompt(), userPromptNoData(parsed)) { reply, error ->
                if (reply != null) {
                    callback(AiAnswer(
                        parsed.intent,
                        listOf(
                            AnswerBlock.Markdown(reply),
                            AnswerBlock.FollowUps(defaultFollowUps(parsed)),
                        ),
                        emptyList(),
                    ))
                } else {
                    callback(AnswerComposer.compose(parsed, snapshots))
                }
            }
            return
        }

        // 有行情数据：保留本地卡片，只让大模型生成分析文案
        val base = AnswerComposer.compose(parsed, snapshots)
        callModel(systemPrompt(), userPromptWithData(parsed, snapshots)) { reply, error ->
            if (reply != null) {
                callback(reassemble(base, reply))
            } else {
                callback(base)
            }
        }
    }

    // region Prompt 构建

    private fun systemPrompt(): String = """
        你是一位专业的中文股票市场分析助手，服务于散户投资者。请基于用户提供的实时行情数据，给出专业、客观、简洁的分析。

        要求：
        1. 只使用提供的真实行情数据进行分析，严禁编造任何不存在的数据、价格或事件
        2. 回答使用中文，采用 Markdown 格式（标题、列表、粗体等），但不要使用代码块
        3. 结构清晰，可包含盘面概览、技术面、估值（个股时）、风险提示等小节
        4. 语言简洁专业，避免冗长，单次回复控制在 400 字以内
        5. 保持中立客观，不要给出明确的买卖指令（如"买入""卖出"），应提示投资风险
        6. 如数据缺失，需明确说明"数据缺失"，不要臆测
        7. 结尾用一句话总结关注要点
    """.trimIndent()

    private fun userPromptNoData(parsed: ParsedIntent): String {
        val role = when (parsed.intent) {
            Intent.GREETING -> "用户在打招呼，请简短友好地介绍你能做什么。"
            Intent.KNOWLEDGE -> "用户在询问金融知识，请用通俗语言解释。"
            else -> "请回答用户的问题。"
        }
        return """
            $role

            用户输入：${parsed.rawText}
        """.trimIndent()
    }

    private fun userPromptWithData(parsed: ParsedIntent, snapshots: List<MarketSnapshot>): String {
        val task = when (parsed.intent) {
            Intent.COMPARE -> "请对以上两只标的做横向对比分析，包括涨跌表现、估值水平、技术面强弱。"
            Intent.TREND -> "请基于以上数据做趋势判断，分析短期与中期走势。"
            Intent.RISK -> "请梳理该标的主要风险点，按价格波动、估值、行业三个维度展开。"
            Intent.MARKET_OVERVIEW -> "请分析该指数/大盘的走势与短期展望。"
            Intent.STOCK_ANALYSIS -> "请对该标的做综合分析，涵盖盘面、技术面、估值与关注要点。"
            else -> "请回答用户的问题。"
        }
        return """
            用户问题：${parsed.rawText}

            以下是相关标的的最新行情数据（真实数据，请仅基于此分析）：
            ${buildDigest(snapshots)}

            $task
        """.trimIndent()
    }

    // endregion

    // region 行情数据摘要

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

    // endregion

    // region 重组 AiAnswer：保留卡片 + AI 文案

    private fun reassemble(base: AiAnswer, aiMarkdown: String): AiAnswer {
        val cards = base.blocks.filter { it !is AnswerBlock.Markdown && it !is AnswerBlock.FollowUps }
        val nonRisk = cards.filter { it !is AnswerBlock.Risk }
        val risk = cards.filter { it is AnswerBlock.Risk }
        val followUps = base.blocks.filterIsInstance<AnswerBlock.FollowUps>().firstOrNull()
            ?: AnswerBlock.FollowUps(defaultFollowUpsForIntent(base.intent, base.relatedSnapshots))

        val blocks = mutableListOf<AnswerBlock>()
        blocks += nonRisk
        blocks += AnswerBlock.Markdown(aiMarkdown)
        blocks += risk
        blocks += followUps
        return base.copy(blocks = blocks)
    }

    // endregion

    // region 调用百炼 API

    private fun callModel(system: String, user: String, callback: (reply: String?, error: String?) -> Unit) {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(JSONObject().apply { put("role", "user"); put("content", user) })
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", false)
            put("temperature", temperature)
        }
        val url = "$baseUrl/chat/completions"
        val headers = mapOf("Authorization" to "Bearer $apiKey")
        httpClient.postJson(url, body, headers, timeoutSeconds) { text, error ->
            if (text != null) {
                val reply = parseReply(text)
                if (reply != null) {
                    callback(reply, null)
                } else {
                    callback(null, "解析 AI 回复失败")
                }
            } else {
                callback(null, error ?: "AI 服务请求失败")
            }
        }
    }

    /** 从百炼 OpenAI 兼容响应中提取 choices[0].message.content */
    private fun parseReply(raw: String): String? {
        return runCatching {
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices") ?: return@runCatching null
            val first = choices.optJSONObject(0) ?: return@runCatching null
            val message = first.optJSONObject("message") ?: return@runCatching null
            message.optString("content").ifEmpty { null }
        }.getOrNull()
    }

    // endregion

    // region FollowUps 默认值

    private fun defaultFollowUps(parsed: ParsedIntent): List<String> {
        val ins = parsed.instruments.firstOrNull()
        return if (ins != null) {
            listOf("${ins.name}的趋势判断", "${ins.name}有哪些风险", "${ins.name}的估值贵吗")
        } else {
            listOf("腾讯控股后市如何", "恒生指数短期走势判断", "什么是市盈率")
        }
    }

    private fun defaultFollowUpsForIntent(intent: Intent, snapshots: List<MarketSnapshot>): List<String> {
        val ins = snapshots.firstOrNull()?.quote?.instrument
        return if (ins != null) {
            when (intent) {
                Intent.COMPARE -> snapshots.getOrNull(1)?.quote?.instrument?.let { peer ->
                    listOf("${ins.name}的趋势判断", "${ins.name} vs ${peer.name} 哪个更值得关注")
                } ?: listOf("${ins.name}的趋势判断", "${ins.name}有哪些风险")
                Intent.TREND -> listOf("${ins.name}有哪些风险", "${ins.name}后市如何", "什么是均线")
                Intent.RISK -> listOf("${ins.name}的趋势判断", "${ins.name}后市如何", "什么是 RSI")
                Intent.MARKET_OVERVIEW -> listOf("${ins.name}有哪些风险", "腾讯控股后市如何")
                else -> listOf("${ins.name}的趋势判断", "${ins.name}有哪些风险", "${ins.name}的估值贵吗")
            }
        } else {
            listOf("腾讯控股后市如何", "恒生指数短期走势判断", "什么是市盈率")
        }
    }

    // endregion
}
