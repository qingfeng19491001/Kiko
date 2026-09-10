package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.data.ai.AiConfig
import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 行情仓库：优先请求腾讯证券公开接口，失败时降级为离线演示数据。
 * 内存缓存 60s，避免同一轮对话中重复请求。
 */
class MarketRepository(private val http: HttpClient) {

    private data class Cached<T>(val value: T, val at: Long)

    private val quoteCache = mutableMapOf<String, Cached<Quote>>()
    private val barsCache = mutableMapOf<String, Cached<List<KLineBar>>>()
    private val intradayCache = mutableMapOf<String, Cached<IntradaySeries>>()

    var cacheTtlMs: Long = 60_000L

    /** 是否优先使用离线数据（用于演示 / 无网环境） */
    var offlineMode: Boolean = false

    // region 快照

    fun loadSnapshot(instrument: Instrument, callback: (MarketSnapshot) -> Unit) {
        var quote: Quote? = null
        var bars: List<KLineBar>? = null
        var pending = 2
        fun finish() {
            pending -= 1
            if (pending > 0) return
            val liveBars = bars
            val liveQuote = quote
            when {
                liveQuote != null && liveBars != null -> callback(MarketSnapshot(liveQuote, liveBars, null))
                liveQuote != null -> callback(MarketSnapshot(liveQuote, MockMarketData.dailyBars(instrument, 320), null))
                liveBars != null -> callback(MarketSnapshot(quoteFromBars(instrument, liveBars), liveBars, null))
                else -> callback(MockMarketData.snapshot(instrument))
            }
        }
        loadQuote(instrument) { quote = it; finish() }
        loadBars(instrument, KLinePeriod.DAY, 320) { bars = it; finish() }
    }

    fun loadSnapshots(instruments: List<Instrument>, callback: (List<MarketSnapshot>) -> Unit) {
        if (instruments.isEmpty()) {
            callback(emptyList())
            return
        }
        val results = arrayOfNulls<MarketSnapshot>(instruments.size)
        var pending = instruments.size
        instruments.forEachIndexed { index, instrument ->
            loadSnapshot(instrument) { snapshot ->
                results[index] = snapshot
                pending -= 1
                if (pending == 0) callback(results.filterNotNull())
            }
        }
    }

    // endregion

    // region 实时行情

    fun loadQuote(instrument: Instrument, callback: (Quote?) -> Unit) {
        val now = DateTime.currentTimestamp()
        quoteCache[instrument.key]?.let { if (now - it.at < cacheTtlMs) { callback(it.value); return } }
        if (offlineMode) {
            callback(null)
            return
        }
        http.get(TencentMarketParser.quoteUrl(listOf(instrument))) { text, _ ->
            val quote = text?.let { raw ->
                runCatching {
                    val fields = TencentMarketParser.quoteFields(raw, instrument)
                    fields?.let { TencentMarketParser.parseQuote(instrument, it) }
                }.getOrNull()
            }
            if (quote != null) quoteCache[instrument.key] = Cached(quote, DateTime.currentTimestamp())
            callback(quote)
        }
    }

    // endregion

    // region K 线

    fun loadBars(instrument: Instrument, period: KLinePeriod, count: Int, callback: (List<KLineBar>?) -> Unit) {
        val actualPeriod = if (period == KLinePeriod.MINUTE) KLinePeriod.DAY else period
        val cacheKey = "${instrument.key}#${actualPeriod.apiKey}#$count"
        val now = DateTime.currentTimestamp()
        barsCache[cacheKey]?.let { if (now - it.at < cacheTtlMs * 5) { callback(it.value); return } }
        if (offlineMode) {
            callback(null)
            return
        }
        http.get(
            TencentMarketParser.KLINE_URL,
            mapOf("param" to TencentMarketParser.klineParam(instrument, actualPeriod, count)),
        ) { text, _ ->
            val bars = text?.let { raw ->
                runCatching { TencentMarketParser.parseKLine(instrument, actualPeriod, JSONObject(raw)) }.getOrNull()
            }
            if (bars != null) barsCache[cacheKey] = Cached(bars, DateTime.currentTimestamp())
            callback(bars)
        }
    }

    /** 带离线兜底的 K 线加载，供详情页周期切换使用 */
    fun loadBarsOrMock(instrument: Instrument, period: KLinePeriod, count: Int, callback: (bars: List<KLineBar>, isMock: Boolean) -> Unit) {
        loadBars(instrument, period, count) { bars ->
            if (bars != null) callback(bars, false) else callback(MockMarketData.bars(instrument, period, count), true)
        }
    }

    // endregion

    // region 分时

    fun loadIntraday(instrument: Instrument, quote: Quote, callback: (series: IntradaySeries, isMock: Boolean) -> Unit) {
        val now = DateTime.currentTimestamp()
        intradayCache[instrument.key]?.let { if (now - it.at < cacheTtlMs) { callback(it.value, false); return } }
        if (offlineMode) {
            callback(MockMarketData.intraday(instrument, quote), true)
            return
        }
        val token = AiConfig.ITICK_TOKEN
        if (token.isNotBlank()) {
            http.get(
                ITickKlineParser.BASE_URL + ITickKlineParser.path(instrument),
                ITickKlineParser.query(instrument),
                mapOf("accept" to "application/json", "token" to token),
            ) { text, _ ->
                val series = text?.let { raw ->
                    runCatching { ITickKlineParser.parseIntraday(raw, instrument, quote.prevClose) }.getOrNull()
                }
                if (series != null) {
                    intradayCache[instrument.key] = Cached(series, DateTime.currentTimestamp())
                    callback(series, false)
                } else {
                    loadTencentIntraday(instrument, quote, callback)
                }
            }
        } else {
            loadTencentIntraday(instrument, quote, callback)
        }
    }

    private fun loadTencentIntraday(
        instrument: Instrument,
        quote: Quote,
        callback: (series: IntradaySeries, isMock: Boolean) -> Unit,
    ) {
        http.get(TencentMarketParser.MINUTE_URL, mapOf("code" to instrument.tencentSymbol)) { text, _ ->
            val series = text?.let { raw ->
                runCatching { TencentMarketParser.parseMinute(instrument, quote.prevClose, JSONObject(raw)) }.getOrNull()
            }
            if (series != null) {
                intradayCache[instrument.key] = Cached(series, DateTime.currentTimestamp())
                callback(series, false)
            } else {
                callback(MockMarketData.intraday(instrument, quote), true)
            }
        }
    }

    // endregion

    /** 实时行情失败但日 K 成功时，用最后两根 K 线推导报价，不再混入演示 PE / 市值。 */
    private fun quoteFromBars(instrument: Instrument, bars: List<KLineBar>): Quote {
        if (bars.size < 2) return MockMarketData.snapshot(instrument).quote
        val last = bars.last()
        val prev = bars[bars.size - 2]
        val change = last.close - prev.close
        return Quote(
            instrument = instrument,
            price = last.close,
            prevClose = prev.close,
            open = last.open,
            high = last.high,
            low = last.low,
            change = change,
            changePct = if (prev.close > 0) change / prev.close * 100 else 0.0,
            volume = last.volume,
            turnover = last.volume * (last.high + last.low) / 2,
            amplitude = if (prev.close > 0) (last.high - last.low) / prev.close * 100 else null,
            updateTime = last.date.takeLast(5).replace('/', '-') + " 收盘",
            tradingStatus = "${instrument.market.label}已收盘",
            isMock = false,
        )
    }
}
