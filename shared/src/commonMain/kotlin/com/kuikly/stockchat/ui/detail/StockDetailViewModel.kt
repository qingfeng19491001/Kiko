package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.data.ai.StockPredictionService
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
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.prediction.PredictionChartMapper
import com.kuikly.stockchat.domain.prediction.PredictionResult
import com.kuikly.stockchat.domain.prediction.StockPrediction
import com.kuikly.stockchat.domain.util.DateUtil
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 个股 / 指数详情页视图模型。
 */
class StockDetailViewModel(
    val instrument: Instrument,
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val predictionService: StockPredictionService? = null,
) {
    enum class LoadState { LOADING, READY, ERROR }
    enum class PredictionState { IDLE, LOADING, READY, UNAVAILABLE, FAILED }

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

    var predictionState by observable(PredictionState.IDLE)
    var prediction: StockPrediction? by observable<StockPrediction?>(null)
    var predictionMessage by observable("")
    var selectedBar by observable<SelectedChartPoint?>(null)

    private var snapshot: MarketSnapshot? = null
    private val barsCache = mutableMapOf<KLinePeriod, List<KLineBar>>()

    val dailyBars: List<KLineBar> get() = snapshot?.dailyBars ?: emptyList()

    val forecastBars: List<KLineBar>
        get() = if (period == KLinePeriod.DAY && predictionState == PredictionState.READY) {
            PredictionChartMapper.toForecastBars(dailyBars, prediction)
        } else {
            emptyList()
        }

    fun stripPoints(): List<SelectedChartPoint> {
        val history = when {
            period == KLinePeriod.DAY -> dailyBars
            else -> barsCache[period].orEmpty()
        }
        val recent = history.takeLast(8)
        val points = mutableListOf<SelectedChartPoint>()
        recent.forEachIndexed { index, bar ->
            val prev = if (index == 0) history.getOrNull(history.size - recent.size - 1)?.close else recent[index - 1].close
            points += SelectedChartPoint.fromBar(bar, prev, isForecast = false)
        }
        if (period == KLinePeriod.DAY) {
            val forecast = forecastBars
            forecast.forEachIndexed { index, bar ->
                val prev = if (index == 0) history.lastOrNull()?.close else forecast[index - 1].close
                points += SelectedChartPoint.fromBar(bar, prev, isForecast = true)
            }
        }
        return points
    }

    fun selectPoint(point: SelectedChartPoint) {
        selectedBar = point
    }

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
            ensureDefaultSelection()
            requestPrediction(snap)
        }
    }

    fun selectPeriod(target: KLinePeriod) {
        if (period == target) return
        period = target
        selectedBar = null
        refreshChart()
        ensureDefaultSelection()
    }

    fun toggleWatch() {
        isWatching = watchlistRepository.toggle(instrument.key)
    }

    private fun requestPrediction(snap: MarketSnapshot) {
        val service = predictionService
        if (service == null) {
            predictionState = PredictionState.UNAVAILABLE
            predictionMessage = "预测服务未接入"
            return
        }
        predictionState = PredictionState.LOADING
        prediction = null
        predictionMessage = ""
        service.predict(instrument, snap.quote, snap.dailyBars) { result ->
            when (result) {
                is PredictionResult.Success -> {
                    prediction = result.prediction
                    predictionState = PredictionState.READY
                    predictionMessage = ""
                    refreshChart()
                }
                is PredictionResult.Unavailable -> {
                    prediction = null
                    predictionState = PredictionState.UNAVAILABLE
                    predictionMessage = result.message
                }
                is PredictionResult.Failure -> {
                    prediction = null
                    predictionState = PredictionState.FAILED
                    predictionMessage = result.message
                }
            }
        }
    }

    private fun refreshChart() {
        val p = period
        if (p.isIntraday) {
            val q = quote ?: return
            if (intraday != null) {
                barsJson = encodeIntradayBars(intraday ?: return)
                chartLoading = false
                return
            }
            barsJson = ""
            chartLoading = true
            marketRepository.loadIntraday(instrument, q) { series, _ ->
                if (period == KLinePeriod.MINUTE) {
                    intraday = series
                    barsJson = encodeIntradayBars(series)
                    chartLoading = false
                }
            }
            return
        }
        barsCache[p]?.let {
            barsJson = encodeBars(it + forecastOverlay(p))
            chartLoading = false
            return
        }
        barsJson = ""
        chartLoading = true
        val count = if (p == KLinePeriod.FIVE_DAY) 240 else 160
        marketRepository.loadBarsOrMock(instrument, p, count) { bars, _ ->
            barsCache[p] = bars
            if (period == p) {
                barsJson = encodeBars(bars + forecastOverlay(p))
                chartLoading = false
                ensureDefaultSelection()
            }
        }
    }

    private fun forecastOverlay(p: KLinePeriod): List<KLineBar> {
        if (p != KLinePeriod.DAY || predictionState != PredictionState.READY) return emptyList()
        return PredictionChartMapper.toForecastBars(barsCache[KLinePeriod.DAY] ?: dailyBars, prediction)
    }

    private fun ensureDefaultSelection() {
        if (selectedBar != null) return
        stripPoints().lastOrNull { !it.isForecast }?.let { selectedBar = it }
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

    /**
     * 分时序列 → KLineChart bars。与官方 Demo 一致：分时也用 KLineChart line 模式渲染，
     * 每个 tick 转成一根 open=high=low=close 的分钟 bar。
     * 成交量口径兼容：mock 是累计量，单调不减时转成每根差分，避免成交量副图单边递增。
     */
    private fun encodeIntradayBars(series: IntradaySeries): String {
        val ticks = series.ticks
        if (ticks.isEmpty()) return ""
        val digits = series.date.filter { it.isDigit() }
        val year = snapshot?.dailyBars?.lastOrNull()?.date?.take(4) ?: "2026"
        val ymd = when {
            series.date.length >= 10 && series.date[4] == '-' -> series.date.substring(0, 10)
            digits.length == 8 ->
                "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            digits.length == 4 -> "$year-${digits.substring(0, 2)}-${digits.substring(2, 4)}"
            else -> return ""
        }
        val isCumulative = ticks.size > 2 &&
            ticks.last().volume >= ticks.first().volume &&
            ticks.zipWithNext().all { (a, b) -> b.volume >= a.volume }
        val sb = StringBuilder("[")
        var first = true
        var prevVolume = 0.0
        ticks.forEach { tick ->
            val ts = DateUtil.parseToEpochMillis("$ymd ${tick.time}") ?: return@forEach
            val volume = if (isCumulative) (tick.volume - prevVolume).coerceAtLeast(0.0) else tick.volume
            prevVolume = tick.volume
            if (!first) sb.append(',')
            first = false
            sb.append("{\"timestamp\":").append(ts)
                .append(",\"open\":").append(tick.price)
                .append(",\"high\":").append(tick.price)
                .append(",\"low\":").append(tick.price)
                .append(",\"close\":").append(tick.price)
                .append(",\"volume\":").append(volume)
                .append('}')
        }
        sb.append(']')
        return sb.toString()
    }
}
