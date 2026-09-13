package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.util.NumberFormat

/**
 * 用真实日 K 对齐多标的序列：相对净值折线、月涨跌柱。
 */
object PeerSeriesMapper {

    fun indexedCloses(snapshots: List<MarketSnapshot>, lookback: Int = 60): AnswerBlock.SeriesChartCard? {
        if (snapshots.size < 2) return null
        val windows = snapshots.map { it to it.dailyBars.takeLast(lookback) }
        if (windows.any { it.second.size < 8 }) return null
        val dates = windows.minBy { it.second.size }.second.map { normalizeDate(it.date) }
        val aligned = dates.mapNotNull { date ->
            val closes = windows.map { (_, bars) -> bars.firstOrNull { normalizeDate(it.date) == date }?.close }
            if (closes.any { it == null }) null else date to closes.map { it!! }
        }
        if (aligned.size < 8) return null
        val bases = snapshots.indices.map { i -> aligned.first().second[i] }.map { if (it <= 0) 1.0 else it }
        val series = snapshots.mapIndexed { i, snapshot ->
            val ins = snapshot.quote.instrument
            ChartSeries(
                name = ins.name,
                instrumentKey = ins.key,
                colorArgb = ins.logoColor,
                values = aligned.map { (_, closes) ->
                    val indexed: Double? = closes[i] / bases[i] * 100.0
                    indexed
                },
            )
        }
        return AnswerBlock.SeriesChartCard(
            title = "${nameList(snapshots)}近 ${aligned.size} 个交易日相对走势",
            subtitle = "起点复权为 100，用日 K 收盘价",
            unit = "",
            kind = SeriesChartKind.LINE,
            categories = aligned.map { shortDate(it.first) },
            series = series,
        )
    }

    fun monthlyReturns(snapshots: List<MarketSnapshot>, months: Int = 6): AnswerBlock.SeriesChartCard? {
        if (snapshots.size < 2) return null
        val monthCloses = snapshots.map { snapshot ->
            snapshot.dailyBars
                .groupBy { yearMonth(it.date) }
                .mapValues { (_, bars) -> bars.last().close }
        }
        val keys = monthCloses.flatMap { it.keys }.distinct().sorted()
        if (keys.size < 3) return null
        val tail = keys.toList().takeLast(months + 1)
        val monthKeys = tail.drop(1)
        if (monthKeys.isEmpty()) return null
        val series = snapshots.mapIndexed { i, snapshot ->
            val ins = snapshot.quote.instrument
            val closes = monthCloses[i]
            ChartSeries(
                name = ins.name,
                instrumentKey = ins.key,
                colorArgb = ins.logoColor,
                values = monthKeys.map { month ->
                    val prev = tail[tail.indexOf(month) - 1]
                    val cur = closes[month]
                    val base = closes[prev]
                    if (cur == null || base == null || base == 0.0) null else (cur - base) / base * 100.0
                },
            )
        }
        if (series.all { it.values.all { v -> v == null } }) return null
        return AnswerBlock.SeriesChartCard(
            title = "${nameList(snapshots)}近 ${monthKeys.size} 个月涨跌幅对比",
            subtitle = "各月最后一个交易日相对上月",
            unit = "%",
            kind = SeriesChartKind.GROUPED_BAR,
            categories = monthKeys.map { it.takeLast(5) },
            series = series,
        )
    }

    fun highlights(snapshots: List<MarketSnapshot>): List<String> {
        if (snapshots.size < 2) return emptyList()
        val items = mutableListOf<String>()
        val today = snapshots.maxBy { it.quote.changePct }
        val todayWeak = snapshots.minBy { it.quote.changePct }
        items += if (today.quote.instrument.key == todayWeak.quote.instrument.key) {
            "今日涨跌接近，${today.quote.instrument.name} ${NumberFormat.signedPct(today.quote.changePct)}"
        } else {
            "今日最强是 **${today.quote.instrument.name}**（${NumberFormat.signedPct(today.quote.changePct)}），最弱是 ${todayWeak.quote.instrument.name}（${NumberFormat.signedPct(todayWeak.quote.changePct)}）"
        }
        val scored = snapshots.map { it to (AnalysisEngine.analyze(it).change20dPct ?: 0.0) }
        val best20 = scored.maxBy { it.second }
        val worst20 = scored.minBy { it.second }
        items += "近 20 日 ${best20.first.quote.instrument.name} ${NumberFormat.signedPct(best20.second)}，${worst20.first.quote.instrument.name} ${NumberFormat.signedPct(worst20.second)}"
        val withPe = snapshots.mapNotNull { s -> s.quote.pe?.takeIf { it > 0 }?.let { s to it } }
        items += if (withPe.size >= 2) {
            val cheap = withPe.minBy { it.second }
            val rich = withPe.maxBy { it.second }
            "估值上 ${cheap.first.quote.instrument.name} PE ${NumberFormat.ratio(cheap.second)} 倍更低，${rich.first.quote.instrument.name} ${NumberFormat.ratio(rich.second)} 倍更高"
        } else {
            val cap = snapshots.maxBy { it.quote.totalMarketCap ?: it.quote.marketCap ?: 0.0 }
            "${cap.quote.instrument.name} 体量最大，总市值 ${NumberFormat.capFromYi(cap.quote.totalMarketCap ?: cap.quote.marketCap)}"
        }
        return items.take(3)
    }

    fun followUps(snapshots: List<MarketSnapshot>): List<String> {
        val primary = snapshots.firstOrNull()?.quote?.instrument ?: return emptyList()
        val peer = snapshots.getOrNull(1)?.quote?.instrument
        val weakest = snapshots.minByOrNull { it.quote.changePct }?.quote?.instrument
        return listOfNotNull(
            peer?.let { "对比${it.name}近60日相对走势" },
            "${primary.name}后市怎么走",
            weakest?.takeIf { it.key != primary.key }?.let { "${it.name}今天为什么更弱" }
                ?: peer?.let { "对比${it.name}市盈率" },
        ).distinct().take(3)
    }

    private fun nameList(snapshots: List<MarketSnapshot>): String =
        snapshots.joinToString("、") { it.quote.instrument.name }

    private fun normalizeDate(date: String): String = date.replace('/', '-').take(10)

    private fun yearMonth(date: String): String {
        val d = normalizeDate(date)
        return if (d.length >= 7) d.take(7) else d
    }

    private fun shortDate(date: String): String {
        val d = normalizeDate(date)
        return if (d.length >= 10) d.substring(5, 10) else d
    }
}
