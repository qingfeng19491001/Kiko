package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.model.Quote
import com.tencent.kuikly.core.base.ViewContainer

fun ViewContainer<*, *>.ProfilePane(
    quote: Quote,
    analysis: TechnicalAnalysis?,
    subTab: String,
) = renderProfilePane(quote, analysis, subTab)

