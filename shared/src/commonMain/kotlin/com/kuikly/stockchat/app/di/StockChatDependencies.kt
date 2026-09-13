package com.kuikly.stockchat.app.di

import com.kuikly.stockchat.data.ai.AiService
import com.kuikly.stockchat.data.ai.DashScopeDocumentClient
import com.kuikly.stockchat.data.ai.RemoteAiEngine
import com.kuikly.stockchat.data.ai.RemoteIntentRecognizer
import com.kuikly.stockchat.data.attachment.KuiklyFileContentReader
import com.kuikly.stockchat.data.attachment.KuiklyFileUploader
import com.kuikly.stockchat.data.chat.ConversationRepository
import com.kuikly.stockchat.data.chat.KuiklyKeyValueStore
import com.kuikly.stockchat.data.chat.SettingsRepository
import com.kuikly.stockchat.data.chat.WatchlistRepository
import com.kuikly.stockchat.data.market.DerivedMarketRepository
import com.kuikly.stockchat.data.market.MarketRepository
import com.kuikly.stockchat.data.market.TencentSymbolSearch
import com.kuikly.stockchat.data.network.KuiklyHttpClient
import com.kuikly.stockchat.domain.model.InstrumentResolver
import com.kuikly.stockchat.ui.chat.ChatViewModel
import com.kuikly.stockchat.ui.market.MarketListViewModel
import com.tencent.kuikly.core.pager.Pager

internal fun createMarketListViewModel(pager: Pager): MarketListViewModel {
    val http = KuiklyHttpClient(pager)
    val store = KuiklyKeyValueStore(pager)
    return MarketListViewModel(
        marketRepository = MarketRepository(http),
        watchlistRepository = WatchlistRepository(store),
    )
}

internal fun createChatViewModel(pager: Pager): ChatViewModel {
    val http = KuiklyHttpClient(pager)
    val store = KuiklyKeyValueStore(pager)
    val marketRepository = MarketRepository(http)
    val derivedRepository = DerivedMarketRepository(http)
    return ChatViewModel(
        aiService = AiService(
            pager.pagerId,
            marketRepository,
            RemoteAiEngine(http),
            derivedRepository,
            RemoteIntentRecognizer(http),
            KuiklyFileContentReader(pager),
            InstrumentResolver(TencentSymbolSearch(http)::search),
            DashScopeDocumentClient(http, KuiklyFileUploader(pager)),
        ),
        conversationRepository = ConversationRepository(store),
        settingsRepository = SettingsRepository(store),
        marketRepository = marketRepository,
        watchlistRepository = WatchlistRepository(store),
    )
}
