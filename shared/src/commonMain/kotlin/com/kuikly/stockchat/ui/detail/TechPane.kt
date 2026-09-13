package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.Quote
import com.tencent.kuikly.core.base.ViewContainer

fun ViewContainer<*, *>.TechPane(
    quote: Quote,
    analysis: TechnicalAnalysis,
    period: KLinePeriod,
) = renderTechPane(quote, analysis, period)

