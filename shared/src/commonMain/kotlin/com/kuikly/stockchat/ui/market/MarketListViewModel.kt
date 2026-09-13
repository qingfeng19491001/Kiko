package com.kuikly.stockchat.ui.market

import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.MarketRepository
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
    var loadGeneration = 0
    var overviewGeneration = 0

    val watchTabSelected: Boolean get() = board == MarketBoard.WATCH

    fun selectWatchTab() = selectBoard(MarketBoard.WATCH)

    fun selectQuoteTab() {
        if (board == MarketBoard.WATCH) selectBoard(lastQuoteBoard)
    }

    fun load(force: Boolean = false) {
        if (rows.isEmpty()) {
            StockCatalog.all.forEach { instrument ->
                InstrumentCache.put(instrument)
                rows += MarketQuoteRow(instrument)
            }
        }
        applyFilter()
        loadOverview()
        loadStats()
        if (loading && !force) return
        val generation = ++loadGeneration
        loading = true
        val instruments = if (board == MarketBoard.WATCH) {
            val watched = visibleRows.map { it.instrument }
            watched.ifEmpty { rows.map { it.instrument } }
        } else {
            rows.map { it.instrument }
        }
        marketRepository.loadQuotes(instruments) { quotes ->
            if (generation != loadGeneration) return@loadQuotes
            rows.forEach { row -> row.quote = quotes[row.instrument.key] }
            usedMock = quotes.size < instruments.size
            loading = false
            quoteEpoch += 1
            applyFilter()
        }
    }

    fun selectBoard(target: MarketBoard) {
        if (board == target) return
        if (target != MarketBoard.WATCH) lastQuoteBoard = target
        board = target
        applyFilter()
        loadOverview()
        loadStats()
    }

    fun onQueryChange(text: String) {
        query = text
        applyFilter()
    }

    fun toggleSearch() {
        searchOpen = !searchOpen
        if (!searchOpen && query.isNotEmpty()) {
            query = ""
            applyFilter()
        }
    }

    fun reloadWatchlist() {
        applyFilter()
    }

    fun ensureWatched(keys: List<String>) {
        keys.forEach { key ->
            if (key.isNotEmpty() && !watchlistRepository.contains(key)) {
                watchlistRepository.toggle(key)
            }
        }
        applyFilter()
    }

    private fun applyFilter() {
        val watchKeys = watchlistRepository.load().toSet()
        val overviewKeys = (
            MarketBoardOverview.indices(board) + MarketBoardOverview.statsInstruments(board)
            ).map { it.key }.toSet()
        val matched = rows.filter { row ->
            MarketListFilter.matches(row.instrument, board, query, watchKeys) &&
                (board == MarketBoard.WATCH || row.instrument.key !in overviewKeys)
        }
        visibleRows.clear()
        visibleRows.addAll(matched)
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
            overviewSnaps.clear()
            overviewSnaps.addAll(list.filter { !it.quote.isMock })
            overviewLoading = false
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
