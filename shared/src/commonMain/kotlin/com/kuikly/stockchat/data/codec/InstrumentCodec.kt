package com.kuikly.stockchat.data.codec

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.InstrumentType
import com.kuikly.stockchat.domain.model.Market
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

object InstrumentCodec {
    fun encode(instrument: Instrument): JSONObject = JSONObject().apply {
        put("code", instrument.code)
        put("name", instrument.name)
        put("market", instrument.market.name)
        put("type", instrument.type.name)
        put("sector", instrument.sector)
        put("logoColor", instrument.logoColor)
        put("logoText", instrument.logoText)
        instrument.tencentCodeOverrideOrNull()?.let { put("tencentCode", it) }
        if (instrument.aliases.isNotEmpty()) put("aliases", instrument.aliases.joinToString(","))
    }

    fun decode(json: JSONObject?): Instrument? {
        if (json == null) return null
        val code = json.optString("code").ifBlank { return null }
        val name = json.optString("name").ifBlank { return null }
        val market = runCatching { Market.valueOf(json.optString("market")) }.getOrNull() ?: return null
        val type = runCatching { InstrumentType.valueOf(json.optString("type")) }.getOrNull() ?: InstrumentType.STOCK
        val override = json.optString("tencentCode").ifBlank { null }
        val instrument = Instrument(
            code = code,
            name = name,
            market = market,
            type = type,
            aliases = json.optString("aliases").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            sector = json.optString("sector"),
            logoText = json.optString("logoText").ifBlank { name.take(1) },
            logoColor = json.optLong("logoColor", 0xFF2B6CF6),
            tencentCodeOverride = override,
        )
        InstrumentCache.put(instrument)
        return instrument
    }
}
