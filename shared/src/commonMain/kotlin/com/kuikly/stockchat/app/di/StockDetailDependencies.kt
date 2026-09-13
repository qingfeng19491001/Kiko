package com.kuikly.stockchat.app.di

import com.kuikly.stockchat.data.ai.StockPredictionService
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.DerivedMarketRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.ui.detail.StockDetailViewModel
import com.tencent.kuikly.core.pager.Pager

internal fun createStockDetailViewModel(pager: Pager, instrument: Instrument): StockDetailViewModel {
    val http = KuiklyHttpClient(pager)
    val store = KuiklyKeyValueStore(pager)
    return StockDetailViewModel(
        instrument = instrument,
        marketRepository = MarketRepository(http),
        watchlistRepository = WatchlistRepository(store),
        predictionService = StockPredictionService(http),
        derivedRepository = DerivedMarketRepository(http),
    )
}
