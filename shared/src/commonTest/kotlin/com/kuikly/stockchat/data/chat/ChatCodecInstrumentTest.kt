package com.kuikly.stockchat.data.chat

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.Quote
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatCodecInstrumentTest {
    @Test
    fun quoteRoundTripKeepsOffCatalogInstrument() {
        val instrument = Instrument("600436", "片仔癀", Market.SH, sector = "中药")
        val quote = Quote(
            instrument = instrument,
            price = 200.0, prevClose = 198.0, open = 199.0, high = 201.0, low = 197.0,
            change = 2.0, changePct = 1.01, volume = 1.0, turnover = 1.0,
            updateTime = "09-10 15:00", tradingStatus = "已收盘",
        )
        val decoded = ChatCodec.decodeQuote(ChatCodec.encode(quote))
        assertEquals(instrument.key, decoded?.instrument?.key)
        assertEquals("片仔癀", decoded?.instrument?.name)
        assertEquals(Market.SH, decoded?.instrument?.market)
        assertEquals(200.0, decoded?.price)
    }
}
