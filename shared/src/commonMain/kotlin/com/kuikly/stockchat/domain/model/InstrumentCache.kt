package com.kuikly.stockchat.domain.model

/**
 * 进程内标的缓存：目录外的搜索结果、会话里的 Quote 都放这里，
 * 详情页和历史解码不再只能查 [StockCatalog]。
 */
object InstrumentCache {
    private val extras = mutableMapOf<String, Instrument>()

    fun put(instrument: Instrument) {
        extras[instrument.key] = instrument
    }

    fun get(key: String): Instrument? {
        if (key.isBlank()) return null
        return extras[key] ?: StockCatalog.findByKey(key)
    }

    fun rememberAll(instruments: List<Instrument>) {
        instruments.forEach(::put)
    }
}
