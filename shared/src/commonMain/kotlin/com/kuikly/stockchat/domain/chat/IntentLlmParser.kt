package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.StockCatalog
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 解析百炼意图识别回包。协议失败时返回 null，由调用方回退本地规则。
 */
object IntentLlmParser {
    fun parse(raw: String, originalText: String, contextInstruments: List<Instrument>): ParsedIntent? {
        val json = decodeObject(raw) ?: return null
        val intentName = json.optString("intent").uppercase()
        val intent = Intent.entries.firstOrNull { it.name == intentName } ?: return null
        val names = json.optJSONArray("names")
        val rawNames = buildList {
            if (names != null) {
                for (i in 0 until names.length()) {
                    val name = names.optString(i)?.trim().orEmpty()
                    if (name.isNotEmpty()) add(name)
                }
            }
        }
        val resolved = mutableListOf<Instrument>()
        val unresolved = mutableListOf<String>()
        rawNames.forEach { name ->
            val hit = StockCatalog.resolveName(name)
            if (hit != null) resolved += hit else unresolved += name
        }
        val local = IntentParser.parse(originalText, contextInstruments)
        val instruments = resolved.distinctBy { it.key }.ifEmpty { local.instruments }
        val resolvedIntent = if (intent == Intent.UNKNOWN && local.intent != Intent.UNKNOWN) local.intent else intent
        val topic = json.optString("topic").ifBlank { local.topic }
        return ParsedIntent(resolvedIntent, instruments, originalText, topic, unresolvedNames = unresolved)
    }

    private fun decodeObject(raw: String): JSONObject? = runCatching {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return@runCatching null
        JSONObject(trimmed.substring(start, end + 1))
    }.getOrNull()
}
