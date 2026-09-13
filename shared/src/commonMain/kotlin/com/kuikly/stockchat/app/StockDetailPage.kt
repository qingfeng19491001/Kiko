package com.kuikly.stockchat.app

import com.kuikly.stockchat.app.di.createStockDetailViewModel
import com.kuikly.stockchat.domain.chat.ChartFollowUpPrompt
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.data.codec.InstrumentCodec
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.ui.detail.ChartMenu
import com.kuikly.stockchat.ui.detail.DetailTab
import com.kuikly.stockchat.ui.detail.FirstIndicator
import com.kuikly.stockchat.ui.detail.MainIndicator
import com.kuikly.stockchat.ui.detail.SecondIndicator
import com.kuikly.stockchat.ui.detail.StockDetailScreen
import com.kuikly.stockchat.ui.detail.StockDetailScreenHost
import com.kuikly.stockchat.ui.detail.StockDetailViewModel
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 个股 / 指数详情页：行情头部 + 周期栏 + 完整 K 线工作区（一主图二副图），
 * 基础行情下方用页内 Tab 承载诊股 / 简况 / 技术 / 资金 / 板块，避免长页堆叠。
 */
@Page("StockDetail")
internal class StockDetailPage : Pager(), StockDetailScreenHost {

    internal lateinit var vm: StockDetailViewModel
    internal lateinit var instrument: Instrument
    internal var instrumentMissing by observable(false)
    internal var selectedTab by observable(DetailTab.DIAGNOSIS)
    internal var selectedSubTab by observable(DetailTab.DIAGNOSIS.subTabs.first())

    override val viewModel get() = vm
    override val detailInstrument get() = instrument
    override val detailInstrumentMissing get() = instrumentMissing
    override var selectedDetailTab
        get() = selectedTab
        set(value) { selectedTab = value }
    override var selectedDetailSubTab
        get() = selectedSubTab
        set(value) { selectedSubTab = value }

    override var mainIndicator by observable(MainIndicator.MA)
    override var firstIndicator by observable(FirstIndicator.VOLUME)
    override var secondIndicator by observable(SecondIndicator.MACD)
    override var openMenu by observable<ChartMenu?>(null)
    override var priceHeaderTop by observable(0f)
    override var firstHeaderTop by observable(0f)
    override var secondHeaderTop by observable(0f)
    override val chartLoading get() = vm.chartLoading
    override val barsJson get() = vm.barsJson
    override val symbolCode get() = instrument.displayCode
    override val symbolName get() = instrument.name
    override val periodValue get() = vm.period.span
    override val periodUnit get() = vm.period.unit
    override val priceAsLine get() = vm.period.line

    override fun chartBindKey(): String = listOf(
        vm.period.name,
        mainIndicator.name,
        firstIndicator.name,
        secondIndicator.name,
        openMenu?.name ?: "none",
        vm.barsJson.hashCode(),
        vm.predictionState.name,
        vm.prediction?.points?.joinToString { it.date }.orEmpty(),
    ).joinToString("|")

    override fun onBarClick(index: Int) {
        vm.selectBarAtIndex(index)
    }

    override fun onCrosshairChange(timestamp: Long?, price: Double?) {
        timestamp?.let(vm::selectBarAtTimestamp)
    }

    override fun created() {
        super.created()
        val key = pageData.params.optString("instrumentKey")
        val resolved = InstrumentCodec.decode(pageData.params.optJSONObject("instrument"))
            ?: InstrumentCache.get(key)
            ?: StockCatalog.findByKey(key)
        instrumentMissing = resolved == null
        instrument = resolved ?: Instrument(code = "----", name = "未知标的", market = com.kuikly.stockchat.domain.model.Market.SH)
        InstrumentCache.put(instrument)
        vm = createStockDetailViewModel(this, instrument)
        if (instrumentMissing) {
            vm.loadState = StockDetailViewModel.LoadState.ERROR
        } else {
            vm.load()
        }
    }

    override fun pageWillDestroy() {
        if (::vm.isInitialized) vm.cancelPendingRequests()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder = StockDetailScreen(
        host = this,
        statusBarHeight = pagerData.statusBarHeight,
        bottomInset = pagerData.safeAreaInsets.bottom,
    )

    override fun onAskAi(prompt: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "StockChat",
            JSONObject().apply {
                put("prompt", prompt)
                put("instrumentKey", ctxInstrumentKey())
                put("instrument", InstrumentCodec.encode(instrument))
            },
        )
    }

    override fun onAskSelection() {
        val point = vm.selectedBar ?: return
        onAskAi(ChartFollowUpPrompt.build(instrument, point, vm.prediction))
    }

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

    override fun onSelectDetailTab(tab: DetailTab) {
        if (selectedTab == tab) return
        selectedTab = tab
        selectedSubTab = tab.subTabs.firstOrNull().orEmpty()
    }

    override fun onSelectPeriod(period: com.kuikly.stockchat.domain.model.KLinePeriod) = vm.selectPeriod(period)

    override fun onToggleWatch() = vm.toggleWatch()

    override fun onRetry() = vm.load()

    private fun ctxInstrumentKey(): String = instrument.key

    override fun onBack() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }
}
