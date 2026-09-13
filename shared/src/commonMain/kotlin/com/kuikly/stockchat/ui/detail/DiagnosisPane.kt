package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.model.Instrument
import com.tencent.kuikly.core.base.ViewContainer

fun ViewContainer<*, *>.DiagnosisPane(
    vm: StockDetailViewModel,
    instrument: Instrument,
    subTab: String,
    onAsk: (String) -> Unit,
    onAskSelection: () -> Unit,
    fromChat: Boolean = false,
    chatSummary: String = "",
) = renderDiagnosisPane(vm, instrument, subTab, onAsk, onAskSelection, fromChat, chatSummary)

