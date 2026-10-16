package com.kuikly.stockchat.ui.market

import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.market.TencentSymbolSearch
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

internal class MarketQuoteRow(val instrument: Instrument) {
    var quote by observable<Quote?>(null)
}

internal class MarketListViewModel(
    private val marketRepository: MarketRepository,
    private val watchlistRepository: WatchlistRepository,
    private val symbolSearch: TencentSymbolSearch? = null,
) {
    var rows by observableList<MarketQuoteRow>()
    var visibleRows by observableList<MarketQuoteRow>()
    var overviewSnaps by observableList<MarketSnapshot>()
    var overviewStats by observable<MarketOverviewStats?>(null)

    var board by observable(MarketBoard.ALL)
    var lastQuoteBoard by observable(MarketBoard.ALL)
    var query by observable("")
    var searchOpen by observable(false)
    var loading by observable(false)
    var overviewLoading by observable(false)
    var usedMock by observable(false)
    var quoteEpoch by observable(0)
    var overviewEpoch by observable(0)
    var loadGeneration = 0
    var overviewGeneration = 0
    private var searchGeneration = 0
    private var listedGeneration = 0
    private val searchRows = mutableListOf<MarketQuoteRow>()
    private val listedKeys = linkedSetOf<String>()

    val watchTabSelected: Boolean get() = board == MarketBoard.WATCH

    fun selectWatchTab() = selectBoard(MarketBoard.WATCH)

    fun selectQuoteTab() {
        if (board == MarketBoard.WATCH) selectBoard(lastQuoteBoard)
    }

    fun load(force: Boolean = false) {
        seedIndexRows()
        loadOverview()
        loadStats()
        when (board) {
            MarketBoard.WATCH -> {
                listedKeys.clear()
                ensureWatchRows()
                applyFilter()
                if (loading && !force) return
                refreshVisibleQuotes()
            }
            MarketBoard.INDEX -> {
                listedKeys.clear()
                applyFilter()
                if (loading && !force) return
                refreshVisibleQuotes()
            }
            else -> loadListed()
        }
    }

    fun selectBoard(target: MarketBoard) {
        if (board == target) return
        if (target != MarketBoard.WATCH) lastQuoteBoard = target
        board = target
        loadOverview()
        loadStats()
        when (target) {
            MarketBoard.WATCH -> {
                listedKeys.clear()
                ensureWatchRows()
                applyFilter()
                refreshVisibleQuotes()
            }
            MarketBoard.INDEX -> {
                listedKeys.clear()
                applyFilter()
                refreshVisibleQuotes()
            }
            else -> loadListed()
        }
    }

    fun onQueryChange(text: String) {
        query = text
        applyFilter()
        val needle = text.trim()
        if (needle.isEmpty()) {
            searchGeneration += 1
            searchRows.clear()
            applyFilter()
            return
        }
        val search = symbolSearch ?: return
        val generation = ++searchGeneration
        search.search(needle) { hits ->
            if (generation != searchGeneration) return@search
            val catalogByKey = rows.associateBy { it.instrument.key }
            searchRows.clear()
            hits.take(24).forEach { instrument ->
                InstrumentCache.put(instrument)
                searchRows += catalogByKey[instrument.key] ?: MarketQuoteRow(instrument)
            }
            applyFilter()
            val needQuotes = searchRows.filter { it.quote == null }.map { it.instrument }
            if (needQuotes.isEmpty()) return@search
            marketRepository.loadQuotes(needQuotes) { quotes ->
                if (generation != searchGeneration) return@loadQuotes
                applyQuotes(quotes)
                usedMock = quotes.size < needQuotes.size
                quoteEpoch += 1
                applyFilter()
            }
        }
    }

    fun toggleSearch() {
        searchOpen = !searchOpen
        if (!searchOpen && query.isNotEmpty()) {
            searchGeneration += 1
            query = ""
            searchRows.clear()
            applyFilter()
        }
    }

    fun reloadWatchlist() {
        ensureWatchRows()
        applyFilter()
    }

    fun ensureWatched(keys: List<String>) {
        keys.forEach { key ->
            if (key.isNotEmpty() && !watchlistRepository.contains(key)) {
                watchlistRepository.toggle(key)
            }
        }
        ensureWatchRows()
        applyFilter()
    }

    private fun seedIndexRows() {
        if (rows.isNotEmpty()) return
        StockCatalog.indices.forEach { instrument ->
            InstrumentCache.put(instrument)
            rows += MarketQuoteRow(instrument)
        }
    }

    private fun ensureWatchRows() {
        watchlistRepository.load().forEach { key ->
            if (key.isEmpty() || rows.any { it.instrument.key == key }) return@forEach
            val instrument = InstrumentCache.get(key) ?: return@forEach
            rows += MarketQuoteRow(instrument)
        }
    }

    private fun loadListed() {
        val kind = boardKind() ?: return
        val generation = ++listedGeneration
        loading = true
        marketRepository.loadBoardInstruments(kind) { remote ->
            if (generation != listedGeneration) return@loadBoardInstruments
            listedKeys.clear()
            remote.forEach { instrument ->
                listedKeys += instrument.key
                InstrumentCache.put(instrument)
                if (rows.none { it.instrument.key == instrument.key }) {
                    rows += MarketQuoteRow(instrument)
                }
            }
            applyFilter()
            refreshVisibleQuotes()
        }
    }

    private fun boardKind(): MarketRepository.BoardKind? = when (board) {
        MarketBoard.ALL -> MarketRepository.BoardKind.ALL
        MarketBoard.CN -> MarketRepository.BoardKind.CN
        MarketBoard.HK -> MarketRepository.BoardKind.HK
        MarketBoard.HK_CONNECT -> MarketRepository.BoardKind.HK_CONNECT
        MarketBoard.US -> MarketRepository.BoardKind.US
        else -> null
    }

    private fun applyFilter() {
        val watchKeys = watchlistRepository.load().toSet()
        val overviewKeys = (
            MarketBoardOverview.indices(board) + MarketBoardOverview.statsInstruments(board)
            ).map { it.key }.toSet()
        val matched = when {
            board == MarketBoard.WATCH || board == MarketBoard.INDEX || query.isNotBlank() ->
                sourceRows().filter { row ->
                    MarketListFilter.matches(row.instrument, board, query, watchKeys) &&
                        (board == MarketBoard.WATCH || row.instrument.key !in overviewKeys)
                }
            else -> {
                val byKey = rows.associateBy { it.instrument.key }
                listedKeys.mapNotNull { byKey[it] }.filter { row ->
                    MarketListFilter.matches(row.instrument, board, query, watchKeys) &&
                        row.instrument.key !in overviewKeys
                }
            }
        }
        visibleRows.clear()
        visibleRows.addAll(matched)
    }

    private fun sourceRows(): List<MarketQuoteRow> {
        if (query.isBlank() || searchRows.isEmpty()) return rows.toList()
        val byKey = linkedMapOf<String, MarketQuoteRow>()
        rows.forEach { byKey[it.instrument.key] = it }
        searchRows.forEach { byKey.putIfAbsent(it.instrument.key, it) }
        return byKey.values.toList()
    }

    private fun refreshVisibleQuotes() {
        val instruments = visibleRows.map { it.instrument }
        if (instruments.isEmpty()) {
            loading = false
            return
        }
        val generation = ++loadGeneration
        loading = true
        marketRepository.loadQuotes(instruments) { quotes ->
            if (generation != loadGeneration) return@loadQuotes
            applyQuotes(quotes)
            usedMock = quotes.size < instruments.size
            loading = false
            quoteEpoch += 1
            applyFilter()
        }
    }

    private fun applyQuotes(quotes: Map<String, Quote>) {
        rows.forEach { row -> quotes[row.instrument.key]?.let { row.quote = it } }
        searchRows.forEach { row -> quotes[row.instrument.key]?.let { row.quote = it } }
    }

    private fun loadOverview() {
        val targets = MarketBoardOverview.indices(board)
        val generation = ++overviewGeneration
        overviewSnaps.clear()
        if (targets.isEmpty()) {
            overviewLoading = false
            return
        }
        overviewLoading = true
        targets.forEach { InstrumentCache.put(it) }
        marketRepository.loadSnapshots(targets, allowMock = false) { list ->
            if (generation != overviewGeneration) return@loadSnapshots
            val live = list.filter { !it.quote.isMock }
            overviewSnaps.clear()
            overviewSnaps.addAll(live)
            overviewLoading = false
            overviewEpoch += 1
            attachLiveIntraday(generation, live)
        }
    }

    private fun attachLiveIntraday(generation: Int, snaps: List<MarketSnapshot>) {
        if (snaps.isEmpty()) return
        val merged = snaps.toTypedArray()
        var pending = snaps.size
        snaps.forEachIndexed { index, snap ->
            marketRepository.loadIntraday(snap.quote.instrument, snap.quote) { series, isMock ->
                if (generation != overviewGeneration) return@loadIntraday
                if (!isMock && series.ticks.size >= 2) {
                    merged[index] = snap.copy(intraday = series)
                }
                pending -= 1
                if (pending == 0) {
                    overviewSnaps.clear()
                    overviewSnaps.addAll(merged.toList())
                    overviewEpoch += 1
                }
            }
        }
    }

    private fun loadStats() {
        if (!MarketBoardOverview.showsStats(board)) {
            overviewStats = null
            return
        }
        val targets = MarketBoardOverview.statsInstruments(board)
        targets.forEach { InstrumentCache.put(it) }
        val generation = overviewGeneration
        marketRepository.loadQuotes(targets) { quotes ->
            if (generation != overviewGeneration) return@loadQuotes
            val stats = MarketOverviewStats.from(board, quotes)
            overviewStats = stats.takeUnless { it.isEmpty }
        }
    }
}
