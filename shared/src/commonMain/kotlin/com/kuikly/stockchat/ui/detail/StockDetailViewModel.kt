package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.data.ai.StockPredictionService
import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.kline.MarketKLineDataSource
import com.kuikly.stockchat.data.market.DerivedMarketRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.chat.AnswerComposer
import com.kuikly.stockchat.domain.model.CapitalFlowData
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
    private val derivedRepository: DerivedMarketRepository? = null,
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
    var capitalFlow: CapitalFlowData? by observable<CapitalFlowData?>(null)
    var capitalFlowLoading by observable(false)

    private var snapshot: MarketSnapshot? = null
    private val barsCache = mutableMapOf<KLinePeriod, List<KLineBar>>()
    private var loadGeneration = 0
    private var chartGeneration = 0
    private var predictionGeneration = 0
    private var capitalFlowGeneration = 0

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

    fun selectBarAtIndex(index: Int) {
        val history = currentHistory()
        val all = history + forecastBars
        val bar = all.getOrNull(index) ?: return
        val prev = all.getOrNull(index - 1)?.close
        selectedBar = SelectedChartPoint.fromBar(bar, prev, isForecast = index >= history.size)
    }

    fun selectBarAtTimestamp(timestamp: Long) {
        val history = currentHistory()
        val all = history + forecastBars
        val index = all.indexOfFirst { DateUtil.parseToEpochMillis(it.date) == timestamp }
        if (index >= 0) selectBarAtIndex(index)
    }

    fun load() {
        cancelPendingRequests()
        val generation = loadGeneration
        loadState = LoadState.LOADING
        isWatching = watchlistRepository.contains(instrument.key)
        marketRepository.loadSnapshot(instrument) { snap ->
            if (generation != loadGeneration) return@loadSnapshot
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
            loadCapitalFlow()
            if (snap.dailyBars.isEmpty()) {
                marketRepository.loadBarsOrMock(instrument, KLinePeriod.DAY, 320) { bars, mock ->
                    if (generation != loadGeneration) return@loadBarsOrMock
                    if (bars.isEmpty()) {
                        requestPrediction(snap)
                        return@loadBarsOrMock
                    }
                    barsCache[KLinePeriod.DAY] = bars
                    isMock = mock
                    val filled = snap.copy(dailyBars = bars)
                    snapshot = filled
                    val result = AnalysisEngine.analyze(filled)
                    analysis = result
                    insight = AnswerComposer.detailInsight(filled, result)
                    risks = AnswerComposer.riskItems(filled, result)
                    refreshChart()
                    ensureDefaultSelection()
                    requestPrediction(filled)
                }
            } else {
                requestPrediction(snap)
            }
        }
    }

    fun selectPeriod(target: KLinePeriod) {
        if (period == target) return
        period = target
        selectedBar = null
        refreshChart()
        ensureDefaultSelection()
    }

    /**
     * NetworkModule 不提供传输层取消句柄；通过推进代次让所有在途回调立即失效。
     * 页面重载、切周期和销毁时调用，避免旧请求更新响应式状态。
     */
    fun cancelPendingRequests() {
        loadGeneration += 1
        chartGeneration += 1
        predictionGeneration += 1
        capitalFlowGeneration += 1
    }

    fun toggleWatch() {
        isWatching = watchlistRepository.toggle(instrument.key)
    }

    private fun loadCapitalFlow() {
        val generation = ++capitalFlowGeneration
        val repo = derivedRepository
        if (repo == null) {
            capitalFlow = null
            capitalFlowLoading = false
            return
        }
        capitalFlowLoading = true
        repo.loadCapitalFlow(instrument) { data ->
            if (generation != capitalFlowGeneration) return@loadCapitalFlow
            capitalFlow = data
            capitalFlowLoading = false
        }
    }

    private fun requestPrediction(snap: MarketSnapshot) {
        val generation = ++predictionGeneration
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
            if (generation != predictionGeneration) return@predict
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
        val generation = ++chartGeneration
        val p = period
        if (p.isIntraday) {
            val q = quote ?: return
            if (intraday != null) {
                barsJson = encodeIntraday(intraday ?: return)
                chartLoading = false
                return
            }
            barsJson = ""
            chartLoading = true
            marketRepository.loadIntraday(instrument, q) { series, _ ->
                if (generation == chartGeneration && period == KLinePeriod.MINUTE) {
                    intraday = series
                    barsJson = encodeIntraday(series)
                    chartLoading = false
                }
            }
            return
        }
        barsCache[p]?.let {
            barsJson = MarketKLineDataSource.barsJson(it + forecastOverlay(p))
            chartLoading = false
            return
        }
        barsJson = ""
        chartLoading = true
        val count = when {
            p == KLinePeriod.FIVE_DAY -> 8
            p.isMinuteBar -> 320
            p == KLinePeriod.YEAR || p == KLinePeriod.QUARTER -> 200
            else -> 160
        }
        marketRepository.loadBarsOrMock(instrument, p, count) { bars, _ ->
            if (generation != chartGeneration) return@loadBarsOrMock
            barsCache[p] = bars
            if (period == p) {
                barsJson = MarketKLineDataSource.barsJson(bars + forecastOverlay(p))
                chartLoading = false
                ensureDefaultSelection()
            }
        }
    }

    private fun encodeIntraday(series: IntradaySeries): String {
        val yearHint = snapshot?.dailyBars?.lastOrNull()?.date?.take(4) ?: "2026"
        return MarketKLineDataSource.intradayBarsJson(series, yearHint)
    }

    private fun forecastOverlay(p: KLinePeriod): List<KLineBar> {
        if (p != KLinePeriod.DAY || predictionState != PredictionState.READY) return emptyList()
        return PredictionChartMapper.toForecastBars(barsCache[KLinePeriod.DAY] ?: dailyBars, prediction)
    }

    private fun ensureDefaultSelection() {
        if (selectedBar != null) return
        stripPoints().lastOrNull { !it.isForecast }?.let { selectedBar = it }
    }

    private fun currentHistory(): List<KLineBar> = when {
        period == KLinePeriod.DAY -> barsCache[KLinePeriod.DAY] ?: dailyBars
        else -> barsCache[period].orEmpty()
    }

}
