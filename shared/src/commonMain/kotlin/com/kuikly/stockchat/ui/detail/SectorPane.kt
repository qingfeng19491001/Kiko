package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.model.Instrument
import com.tencent.kuikly.core.base.ViewContainer

fun ViewContainer<*, *>.SectorPane(
    instrument: Instrument,
    onOpen: (Instrument) -> Unit,
    onAsk: (String) -> Unit,
) = renderSectorPane(instrument, onOpen, onAsk)

