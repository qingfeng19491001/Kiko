package com.kuikly.stockchat.app

import com.kuikly.stockchat.app.di.createMarketListViewModel
import com.kuikly.stockchat.data.codec.InstrumentCodec
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.ui.market.MarketBoard
import com.kuikly.stockchat.ui.market.MarketListScreen
import com.kuikly.stockchat.ui.market.MarketListScreenHost
import com.kuikly.stockchat.ui.market.MarketListViewModel
import com.kuikly.stockchat.ui.market.MarketQuoteRow
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager

@Page("MarketList")
internal class MarketListPage : Pager(), MarketListScreenHost {

    override lateinit var viewModel: MarketListViewModel

    override fun created() {
        super.created()
        viewModel = createMarketListViewModel(this)
        viewModel.load()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        if (::viewModel.isInitialized) {
            viewModel.reloadWatchlist()
            viewModel.load(force = true)
        }
    }

    override fun body(): ViewBuilder = MarketListScreen(
        host = this,
        statusBarHeight = pagerData.statusBarHeight,
        bottomInset = pagerData.safeAreaInsets.bottom,
    )

    override fun onOpenSearch() = viewModel.toggleSearch()

    override fun onOpenAi() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockChat",
            JSONObject().apply { put("from", "market") },
        )
    }

    override fun onOpenDetail(row: MarketQuoteRow) = onOpenInstrument(row.instrument)

    override fun onOpenInstrument(instrument: Instrument) {
        InstrumentCache.put(instrument)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockDetail",
            JSONObject().apply {
                put("instrumentKey", instrument.key)
                put("instrument", InstrumentCodec.encode(instrument))
            },
        )
    }

    override fun onSelectBoard(board: MarketBoard) = viewModel.selectBoard(board)
}
