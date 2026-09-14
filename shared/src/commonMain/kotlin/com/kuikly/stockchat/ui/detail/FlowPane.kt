package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.Quote
import com.tencent.kuikly.core.base.ViewContainer

fun ViewContainer<*, *>.FlowPane(
    quote: Quote,
    flow: CapitalFlowData?,
    loading: Boolean,
    onAsk: () -> Unit,
    volumeRatio: Double? = null,
) = renderFlowPane(quote, flow, loading, onAsk, volumeRatio)

