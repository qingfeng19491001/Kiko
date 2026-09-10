package com.kuikly.stockchat.data.market

import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.IntradaySeries
import com.kuikly.stockchat.domain.model.KLineBar
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.MinuteTick
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.util.DateUtil
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 离线演示数据：当网络不可用或接口失败时使用。
 * 使用确定性伪随机（按标的 key 取种子），保证同一标的多次进入页面数据一致。
 */
object MockMarketData {

    private data class Seed(
        val base: Double,
        val pe: Double?,
        val pb: Double?,
        val totalCapYi: Double?,
        val dailyVolume: Double,
        val trend: Double,
    )

    private val seeds: Map<String, Seed> = mapOf(
        "hk00700" to Seed(505.0, 22.5, 4.6, 46500.0, 1.9e7, 0.0009),
        "hk09988" to Seed(118.0, 17.8, 1.9, 22500.0, 3.4e7, -0.0004),
        "hk03690" to Seed(126.0, 21.5, 4.2, 7800.0, 2.7e7, -0.0008),
        "hk01810" to Seed(52.0, 42.0, 6.1, 13000.0, 9.6e7, 0.0012),
        "hk01211" to Seed(380.0, 28.0, 5.9, 11000.0, 6.5e6, 0.0006),
        "sh600519" to Seed(1480.0, 21.7, 7.9, 18600.0, 3.2e6, -0.0003),
        "sz300750" to Seed(255.0, 22.9, 4.8, 11200.0, 2.1e7, 0.0007),
        "sh601318" to Seed(55.0, 7.8, 1.0, 10000.0, 6.0e7, 0.0002),
        "usAAPL.OQ" to Seed(228.0, 34.5, 48.0, 34500.0, 5.6e7, 0.0005),
        "usTSLA.OQ" to Seed(345.0, 178.0, 15.0, 11000.0, 8.7e7, 0.0011),
        "usNVDA.OQ" to Seed(172.0, 52.0, 45.0, 42000.0, 1.9e8, 0.0014),
        "hkHSI" to Seed(23800.0, null, null, null, 1.6e11, 0.0004),
        "hkHSTECH" to Seed(5300.0, null, null, null, 6.4e10, 0.0006),
        "sh000001" to Seed(3420.0, null, null, null, 4.9e11, 0.0003),
        "sz399001" to Seed(10350.0, null, null, null, 6.6e11, 0.0002),
        "sz399006" to Seed(2080.0, null, null, null, 2.8e11, 0.0005),
    )

    fun snapshot(instrument: Instrument): MarketSnapshot {
        val bars = dailyBars(instrument, 320)
        val quote = quote(instrument, bars)
        return MarketSnapshot(quote, bars, intraday(instrument, quote))
    }

    fun dailyBars(instrument: Instrument, count: Int): List<KLineBar> {
        val seed = seeds[instrument.key] ?: Seed(100.0, 20.0, 2.0, 1000.0, 1e7, 0.0)
        val rnd = LcgRandom(instrument.key.hashCode().toLong())
        val bars = ArrayList<KLineBar>(count)
        // 反推起点，让最后一根 K 线收在 base 附近
        var price = seed.base * (1 - seed.trend * count * 0.9)
        val volatility = if (instrument.isIndex) 0.009 else 0.021
        val dates = tradingDates(count)
        for (i in 0 until count) {
            val cycle = sin(i / 23.0 * PI) * 0.004 + sin(i / 61.0 * PI) * 0.003
            val drift = seed.trend + cycle
            val shock = rnd.gaussian() * volatility
            val open = price * (1 + rnd.gaussian() * volatility * 0.35)
            val close = max(price * (1 + drift + shock), price * 0.82)
            val high = max(open, close) * (1 + abs(rnd.gaussian()) * volatility * 0.6)
            val low = min(open, close) * (1 - abs(rnd.gaussian()) * volatility * 0.6)
            val volume = seed.dailyVolume * (0.55 + abs(rnd.gaussian()) * 0.7 + abs(shock) * 18)
            bars += KLineBar(dates[i], round2(open), round2(close), round2(high), round2(low), volume.roundToInt().toDouble())
            price = close
        }
        return bars
    }

    fun bars(instrument: Instrument, period: KLinePeriod, count: Int): List<KLineBar> {
        val daily = dailyBars(instrument, 320)
        return when (period) {
            KLinePeriod.DAY, KLinePeriod.MINUTE -> daily.takeLast(count)
            KLinePeriod.WEEK -> aggregate(daily, 5).takeLast(count)
            KLinePeriod.MONTH -> aggregate(daily, 21).takeLast(count)
            KLinePeriod.QUARTER -> aggregate(daily, 63).takeLast(count)
            KLinePeriod.YEAR -> aggregate(daily, 250).takeLast(count)
            // 五日 = 5 分钟级别连续走势；分钟级周期按各自分钟跨度生成
            KLinePeriod.FIVE_DAY -> minuteBars(instrument, 5, count)
            KLinePeriod.MIN_1 -> minuteBars(instrument, 1, count)
            KLinePeriod.MIN_5 -> minuteBars(instrument, 5, count)
            KLinePeriod.MIN_15 -> minuteBars(instrument, 15, count)
            KLinePeriod.MIN_30 -> minuteBars(instrument, 30, count)
            KLinePeriod.MIN_60 -> minuteBars(instrument, 60, count)
            KLinePeriod.MIN_120 -> minuteBars(instrument, 120, count)
        }
    }

    /** 分钟级演示数据：以最近日线收盘价为锚点，按分钟跨度生成连续随机游走 */
    private fun minuteBars(instrument: Instrument, stepMinutes: Int, count: Int): List<KLineBar> {
        val seed = seeds[instrument.key] ?: Seed(100.0, 20.0, 2.0, 1000.0, 1e7, 0.0)
        val rnd = LcgRandom(instrument.key.hashCode().toLong() * 131 + stepMinutes)
        val anchor = dailyBars(instrument, 320).last().close
        val baseMs = DateUtil.parseToEpochMillis("2026-09-08 15:00") ?: 0L
        val volPerBar = seed.dailyVolume / 240.0 * stepMinutes
        var price = anchor * 0.985
        val bars = ArrayList<KLineBar>(count)
        for (i in 0 until count) {
            val ts = baseMs - (count - 1 - i) * stepMinutes * 60_000L
            val open = price
            val close = max(price * (1 + rnd.gaussian() * 0.0012), price * 0.9)
            val high = max(open, close) * (1 + abs(rnd.gaussian()) * 0.0008)
            val low = min(open, close) * (1 - abs(rnd.gaussian()) * 0.0008)
            val volume = volPerBar * (0.5 + abs(rnd.gaussian()))
            bars += KLineBar(
                DateUtil.formatEpochMinutes(ts),
                round2(open), round2(close), round2(high), round2(low),
                volume.roundToInt().toDouble(),
            )
            price = close
        }
        return bars
    }

    private fun aggregate(daily: List<KLineBar>, size: Int): List<KLineBar> =
        daily.chunked(size).map { chunk ->
            KLineBar(
                date = chunk.last().date,
                open = chunk.first().open,
                close = chunk.last().close,
                high = chunk.maxOf { it.high },
                low = chunk.minOf { it.low },
                volume = chunk.sumOf { it.volume },
            )
        }

    private fun quote(instrument: Instrument, bars: List<KLineBar>): Quote {
        val seed = seeds[instrument.key] ?: Seed(100.0, 20.0, 2.0, 1000.0, 1e7, 0.0)
        val last = bars.last()
        val prev = bars[bars.size - 2]
        val change = last.close - prev.close
        val changePct = change / prev.close * 100
        val year = bars.takeLast(250)
        val closeTime = when (instrument.market) {
            Market.HK -> "16:08"
            Market.SH, Market.SZ -> "15:00"
            Market.US -> "16:00"
        }
        val monthDay = last.date.takeLast(5).replace('/', '-')
        val turnoverRate = seed.totalCapYi?.let { cap -> if (cap > 0) last.volume * last.close / (cap * 1_0000_0000.0) * 100 else null }
        return Quote(
            instrument = instrument,
            price = last.close,
            prevClose = prev.close,
            open = last.open,
            high = last.high,
            low = last.low,
            change = round2(change),
            changePct = round2(changePct),
            volume = last.volume,
            turnover = last.volume * (last.high + last.low) / 2,
            pe = seed.pe,
            pb = seed.pb,
            marketCap = seed.totalCapYi?.let { it * 0.92 },
            totalMarketCap = seed.totalCapYi,
            amplitude = round2((last.high - last.low) / prev.close * 100),
            turnoverRate = turnoverRate?.let { round2(it) },
            high52w = year.maxOf { it.high },
            low52w = year.minOf { it.low },
            updateTime = "$monthDay $closeTime",
            tradingStatus = "${instrument.market.label}已收盘 · 演示数据",
            isMock = true,
        )
    }

    fun intraday(instrument: Instrument, quote: Quote): IntradaySeries {
        val rnd = LcgRandom(instrument.key.hashCode().toLong() * 31 + 7)
        val slots = tradingMinutes(instrument.market)
        val ticks = ArrayList<MinuteTick>(slots.size)
        val start = quote.open
        val end = quote.price
        var cumulative = 0.0
        val vol = if (instrument.isIndex) 0.0009 else 0.0016
        var price = start
        slots.forEachIndexed { index, time ->
            val progress = index.toDouble() / (slots.size - 1)
            val target = start + (end - start) * progress
            price += (target - price) * 0.18 + price * rnd.gaussian() * vol
            price = price.coerceIn(quote.low * 0.998, quote.high * 1.002)
            cumulative += quote.volume / slots.size * (0.4 + abs(rnd.gaussian()) * 1.1)
            ticks += MinuteTick(time, round2(price), cumulative)
        }
        return IntradaySeries(quote.updateTime.substringBefore(' '), quote.prevClose, ticks)
    }

    private fun tradingMinutes(market: Market): List<String> {
        val ranges = when (market) {
            Market.HK -> listOf(9 * 60 + 30 to 12 * 60, 13 * 60 to 16 * 60)
            Market.SH, Market.SZ -> listOf(9 * 60 + 30 to 11 * 60 + 30, 13 * 60 to 15 * 60)
            Market.US -> listOf(9 * 60 + 30 to 16 * 60)
        }
        val list = mutableListOf<String>()
        ranges.forEach { (from, to) ->
            var m = from
            while (m <= to) {
                list += "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
                m += 1
            }
        }
        return list
    }

    /** 生成最近 count 个交易日日期（跳过周末，基准日固定，保证离线数据稳定） */
    private fun tradingDates(count: Int): List<String> {
        val dates = ArrayList<String>(count)
        var year = 2026
        var month = 9
        var day = 2
        var weekday = 3 // 2026-09-02 周三
        val temp = ArrayList<String>(count)
        while (temp.size < count) {
            if (weekday in 1..5) {
                temp += "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
            }
            day -= 1
            weekday = (weekday + 6) % 7
            if (day == 0) {
                month -= 1
                if (month == 0) {
                    month = 12
                    year -= 1
                }
                day = daysInMonth(year, month)
            }
        }
        dates.addAll(temp.asReversed())
        return dates
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    }

    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0

    /** 线性同余伪随机，KMP common 下可用且结果确定 */
    private class LcgRandom(seed: Long) {
        private var state = seed xor 0x5DEECE66DL

        fun next(): Double {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            val bits = (state ushr 11) and ((1L shl 53) - 1)
            return bits.toDouble() / (1L shl 53).toDouble()
        }

        /** Box-Muller 近似：用 12 个均匀分布之和 */
        fun gaussian(): Double {
            var sum = 0.0
            repeat(12) { sum += next() }
            return sum - 6.0
        }
    }
}
