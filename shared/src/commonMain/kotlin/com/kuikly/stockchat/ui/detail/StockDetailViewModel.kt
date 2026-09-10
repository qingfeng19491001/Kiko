package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.chat.AnswerComposer
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.DateUtil
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 个股 / 指数详情页视图模型。
 */
class StockDetailViewModel(
    val instrument: Instrument,
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
) {
    enum class LoadState { LOADING, READY, ERROR }

    var loadState by observable(LoadState.LOADING)
    var quote: Quote? by observable<Quote?>(null)
    var analysis: TechnicalAnalysis? by observable<TechnicalAnalysis?>(null)
    var insight by observable("")
    var risks: List<String> = emptyList()
        private set
    var isMock by observable(false)

    var period by observable(KLinePeriod.MINUTE)
    var intraday: IntradaySeries? by observable<IntradaySeries?>(null)
    var barsJson by observable("")
    var chartLoading by observable(false)

    var isWatching by observable(false)

    private var snapshot: MarketSnapshot? = null
    private val barsCache = mutableMapOf<KLinePeriod, List<KLineBar>>()

    val dailyBars: List<KLineBar> get() = snapshot?.dailyBars ?: emptyList()

    fun load() {
        loadState = LoadState.LOADING
        isWatching = watchlistRepository.contains(instrument.key)
        marketRepository.loadSnapshot(instrument) { snap ->
            snapshot = snap
            quote = snap.quote
            isMock = snap.quote.isMock
            barsCache[KLinePeriod.DAY] = snap.dailyBars
            val result = AnalysisEngine.analyze(snap)
            analysis = result
            insight = AnswerComposer.detailInsight(snap, result)
            risks = AnswerComposer.riskItems(snap, result)
            intraday = snap.intraday
            loadState = LoadState.READY
            refreshChart()
        }
    }

    fun selectPeriod(target: KLinePeriod) {
        if (period == target) return
        period = target
        refreshChart()
    }

    fun toggleWatch() {
        isWatching = watchlistRepository.toggle(instrument.key)
    }

    private fun refreshChart() {
        val p = period
        if (p.isIntraday) {
            val q = quote ?: return
            if (intraday != null) {
                chartLoading = false
                return
            }
            chartLoading = true
            marketRepository.loadIntraday(instrument, q) { series, _ ->
                if (period == KLinePeriod.MINUTE) {
                    intraday = series
                    chartLoading = false
                }
            }
            return
        }
        barsCache[p]?.let {
            barsJson = encodeBars(it)
            chartLoading = false
            return
        }
        barsJson = ""
        chartLoading = true
        marketRepository.loadBarsOrMock(instrument, p, 160) { bars, _ ->
            barsCache[p] = bars
            if (period == p) {
                barsJson = encodeBars(bars)
                chartLoading = false
            }
        }
    }

    /** 转成 KuiklyKLineChart 需要的 JSON 数组 */
    private fun encodeBars(bars: List<KLineBar>): String {
        val sb = StringBuilder("[")
        var first = true
        bars.forEach { bar ->
            val ts = DateUtil.parseToEpochMillis(bar.date) ?: return@forEach
            if (!first) sb.append(',')
            first = false
            sb.append("{\"timestamp\":").append(ts)
                .append(",\"open\":").append(bar.open)
                .append(",\"high\":").append(bar.high)
                .append(",\"low\":").append(bar.low)
                .append(",\"close\":").append(bar.close)
                .append(",\"volume\":").append(bar.volume)
                .append('}')
        }
        sb.append(']')
        return sb.toString()
    }
}
