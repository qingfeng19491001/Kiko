package com.kuikly.stockchat.data.chat

import com.kuikly.stockchat.domain.analysis.InsightTag
import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.ChatMessage
import com.kuikly.stockchat.domain.chat.CompareRow
import com.kuikly.stockchat.domain.chat.Conversation
import com.kuikly.stockchat.domain.chat.Intent
import com.kuikly.stockchat.domain.chat.MessageStatus
import com.kuikly.stockchat.domain.chat.Metric
import com.kuikly.stockchat.domain.chat.Role
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 会话 / 消息 / 结构化块 与 JSON 的互转，用于本地持久化。
 */
object ChatCodec {

    // region Conversation

    fun encode(conversation: Conversation): JSONObject = JSONObject().apply {
        put("id", conversation.id)
        put("title", conversation.title)
        put("createdAt", conversation.createdAt)
        put("updatedAt", conversation.updatedAt)
        put("messages", JSONArray().apply { conversation.messages.forEach { put(encode(it)) } })
    }

    fun decodeConversation(json: JSONObject): Conversation? {
        val id = json.optString("id").ifEmpty { return null }
        val messages = json.optJSONArray("messages")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::decodeMessage) }
        } ?: emptyList()
        return Conversation(
            id = id,
            title = json.optString("title"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
            messages = messages,
        )
    }

    // endregion

    // region Message

    fun encode(message: ChatMessage): JSONObject = JSONObject().apply {
        put("id", message.id)
        put("role", message.role.name)
        put("text", message.text)
        put("status", message.status.name)
        put("intent", message.intent.name)
        put("createdAt", message.createdAt)
        put("error", message.errorMessage)
        put("blocks", JSONArray().apply { message.blocks.forEach { put(encode(it)) } })
    }

    fun decodeMessage(json: JSONObject): ChatMessage? {
        val id = json.optString("id").ifEmpty { return null }
        val role = enumOrNull<Role>(json.optString("role")) ?: return null
        val blocks = json.optJSONArray("blocks")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::decodeBlock) }
        } ?: emptyList()
        val status = enumOrNull<MessageStatus>(json.optString("status")) ?: MessageStatus.DONE
        return ChatMessage(
            id = id,
            role = role,
            text = json.optString("text"),
            blocks = blocks,
            // 未完成的流式消息恢复后一律视为完成
            status = if (status == MessageStatus.STREAMING || status == MessageStatus.THINKING) MessageStatus.DONE else status,
            intent = enumOrNull<Intent>(json.optString("intent")) ?: Intent.UNKNOWN,
            createdAt = json.optLong("createdAt"),
            errorMessage = json.optString("error"),
        )
    }

    // endregion

    // region Block

    fun encode(block: AnswerBlock): JSONObject = JSONObject().apply {
        when (block) {
            is AnswerBlock.Markdown -> {
                put("type", "markdown")
                put("text", block.text)
            }
            is AnswerBlock.StockCard -> {
                put("type", "stock")
                put("quote", encode(block.quote))
                put("sparkline", JSONArray().apply { block.sparkline.forEach { put(it) } })
                block.analysis?.let { put("analysis", encode(it)) }
            }
            is AnswerBlock.CompareCard -> {
                put("type", "compare")
                put("title", block.title)
                put("leftName", block.leftName)
                put("rightName", block.rightName)
                put("leftKey", block.leftKey)
                put("rightKey", block.rightKey)
                put("rows", JSONArray().apply {
                    block.rows.forEach { row ->
                        put(JSONObject().apply {
                            put("label", row.label); put("left", row.left); put("right", row.right)
                            put("diff", row.diff); put("tone", row.diffTone.name)
                        })
                    }
                })
            }
            is AnswerBlock.ChartCard -> {
                put("type", "chart")
                put("title", block.title)
                put("subtitle", block.subtitle)
                put("instrumentKey", block.instrumentKey)
                put("bars", encodeBars(block.bars))
            }
            is AnswerBlock.MetricGrid -> {
                put("type", "metrics")
                put("items", JSONArray().apply {
                    block.items.forEach { put(JSONObject().apply { put("label", it.label); put("value", it.value); put("tone", it.tone.name) }) }
                })
            }
            is AnswerBlock.Tags -> {
                put("type", "tags")
                put("tags", encodeTags(block.tags))
            }
            is AnswerBlock.Risk -> {
                put("type", "risk")
                put("title", block.title)
                put("body", block.body)
            }
            is AnswerBlock.FollowUps -> {
                put("type", "followups")
                put("items", JSONArray().apply { block.items.forEach { put(it) } })
            }
        }
    }

    fun decodeBlock(json: JSONObject): AnswerBlock? = when (json.optString("type")) {
        "markdown" -> AnswerBlock.Markdown(json.optString("text"))
        "stock" -> json.optJSONObject("quote")?.let(::decodeQuote)?.let { quote ->
            AnswerBlock.StockCard(
                quote = quote,
                sparkline = json.optJSONArray("sparkline")?.let { arr -> (0 until arr.length()).map { arr.optDouble(it) } } ?: emptyList(),
                analysis = json.optJSONObject("analysis")?.let(::decodeAnalysis),
            )
        }
        "compare" -> AnswerBlock.CompareCard(
            title = json.optString("title"),
            leftName = json.optString("leftName"),
            rightName = json.optString("rightName"),
            leftKey = json.optString("leftKey"),
            rightKey = json.optString("rightKey"),
            rows = json.optJSONArray("rows")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { row ->
                        CompareRow(
                            row.optString("label"), row.optString("left"), row.optString("right"), row.optString("diff"),
                            enumOrNull<TagTone>(row.optString("tone")) ?: TagTone.NEUTRAL,
                        )
                    }
                }
            } ?: emptyList(),
        )
        "chart" -> AnswerBlock.ChartCard(
            title = json.optString("title"),
            subtitle = json.optString("subtitle"),
            instrumentKey = json.optString("instrumentKey"),
            bars = json.optJSONArray("bars")?.let(::decodeBars) ?: emptyList(),
        )
        "metrics" -> AnswerBlock.MetricGrid(
            json.optJSONArray("items")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { Metric(it.optString("label"), it.optString("value"), enumOrNull<TagTone>(it.optString("tone")) ?: TagTone.NEUTRAL) }
                }
            } ?: emptyList(),
        )
        "tags" -> AnswerBlock.Tags(json.optJSONArray("tags")?.let(::decodeTags) ?: emptyList())
        "risk" -> AnswerBlock.Risk(json.optString("title"), json.optString("body"))
        "followups" -> AnswerBlock.FollowUps(
            json.optJSONArray("items")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it) } } ?: emptyList(),
        )
        else -> null
    }

    // endregion

    // region Quote / Analysis / Bars

    fun encode(quote: Quote): JSONObject = JSONObject().apply {
        put("key", quote.instrument.key)
        put("price", quote.price); put("prevClose", quote.prevClose); put("open", quote.open)
        put("high", quote.high); put("low", quote.low); put("change", quote.change); put("changePct", quote.changePct)
        put("volume", quote.volume); put("turnover", quote.turnover)
        quote.pe?.let { put("pe", it) }; quote.pb?.let { put("pb", it) }
        quote.marketCap?.let { put("marketCap", it) }; quote.totalMarketCap?.let { put("totalMarketCap", it) }
        quote.amplitude?.let { put("amplitude", it) }; quote.turnoverRate?.let { put("turnoverRate", it) }
        quote.high52w?.let { put("high52w", it) }; quote.low52w?.let { put("low52w", it) }
        put("updateTime", quote.updateTime); put("tradingStatus", quote.tradingStatus); put("isMock", quote.isMock)
    }

    fun decodeQuote(json: JSONObject): Quote? {
        val instrument = StockCatalog.findByKey(json.optString("key")) ?: return null
        fun opt(name: String): Double? = if (json.has(name)) json.optDouble(name) else null
        return Quote(
            instrument = instrument,
            price = json.optDouble("price"), prevClose = json.optDouble("prevClose"), open = json.optDouble("open"),
            high = json.optDouble("high"), low = json.optDouble("low"), change = json.optDouble("change"), changePct = json.optDouble("changePct"),
            volume = json.optDouble("volume"), turnover = json.optDouble("turnover"),
            pe = opt("pe"), pb = opt("pb"), marketCap = opt("marketCap"), totalMarketCap = opt("totalMarketCap"),
            amplitude = opt("amplitude"), turnoverRate = opt("turnoverRate"), high52w = opt("high52w"), low52w = opt("low52w"),
            updateTime = json.optString("updateTime"), tradingStatus = json.optString("tradingStatus"), isMock = json.optBoolean("isMock"),
        )
    }

    fun encode(a: TechnicalAnalysis): JSONObject = JSONObject().apply {
        a.ma5?.let { put("ma5", it) }; a.ma10?.let { put("ma10", it) }; a.ma20?.let { put("ma20", it) }; a.ma60?.let { put("ma60", it) }
        a.rsi14?.let { put("rsi14", it) }; a.support?.let { put("support", it) }; a.resistance?.let { put("resistance", it) }
        put("shortTrend", a.shortTermTrend.name); put("midTrend", a.midTermTrend.name)
        a.volatilityPct?.let { put("volatility", it) }; a.volumeRatio?.let { put("volumeRatio", it) }
        a.change5dPct?.let { put("change5d", it) }; a.change20dPct?.let { put("change20d", it) }; a.biasToMa20Pct?.let { put("bias20", it) }
        put("score", a.score)
        put("tags", encodeTags(a.tags))
    }

    fun decodeAnalysis(json: JSONObject): TechnicalAnalysis {
        fun opt(name: String): Double? = if (json.has(name)) json.optDouble(name) else null
        return TechnicalAnalysis(
            ma5 = opt("ma5"), ma10 = opt("ma10"), ma20 = opt("ma20"), ma60 = opt("ma60"), rsi14 = opt("rsi14"),
            support = opt("support"), resistance = opt("resistance"),
            shortTermTrend = enumOrNull<TrendBias>(json.optString("shortTrend")) ?: TrendBias.NEUTRAL,
            midTermTrend = enumOrNull<TrendBias>(json.optString("midTrend")) ?: TrendBias.NEUTRAL,
            volatilityPct = opt("volatility"), volumeRatio = opt("volumeRatio"),
            change5dPct = opt("change5d"), change20dPct = opt("change20d"), biasToMa20Pct = opt("bias20"),
            score = json.optInt("score", 50),
            tags = json.optJSONArray("tags")?.let(::decodeTags) ?: emptyList(),
        )
    }

    private fun encodeTags(tags: List<InsightTag>): JSONArray = JSONArray().apply {
        tags.forEach { put(JSONObject().apply { put("text", it.text); put("tone", it.tone.name) }) }
    }

    private fun decodeTags(array: JSONArray): List<InsightTag> =
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { InsightTag(it.optString("text"), enumOrNull<TagTone>(it.optString("tone")) ?: TagTone.NEUTRAL) }
        }

    private fun encodeBars(bars: List<KLineBar>): JSONArray = JSONArray().apply {
        bars.forEach { bar ->
            put(JSONArray().apply { put(bar.date); put(bar.open); put(bar.close); put(bar.high); put(bar.low); put(bar.volume) })
        }
    }

    private fun decodeBars(array: JSONArray): List<KLineBar> =
        (0 until array.length()).mapNotNull { i ->
            array.optJSONArray(i)?.let { row ->
                if (row.length() < 6) null
                else KLineBar(row.optString(0) ?: "", row.optDouble(1), row.optDouble(2), row.optDouble(3), row.optDouble(4), row.optDouble(5))
            }
        }

    // endregion

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
