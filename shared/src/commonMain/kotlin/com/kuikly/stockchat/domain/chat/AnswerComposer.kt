package com.kuikly.stockchat.domain.chat

import com.kuikly.stockchat.domain.analysis.AnalysisEngine
import com.kuikly.stockchat.domain.analysis.InsightTag
import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.DerivedMarketData
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.LimitUpLadderData
import com.kuikly.stockchat.domain.model.Market
import com.kuikly.stockchat.domain.model.MarketBreadthData
import com.kuikly.stockchat.domain.model.MarketSnapshot
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.domain.util.NumberFormat
import kotlin.math.abs

/**
 * 回答编排器：把“意图 + 真实行情 + 技术分析”组织成结构化回答。
 * 文案模板化生成，数字全部来自 [MarketSnapshot]，可解释、可复现。
 */
object AnswerComposer {

    fun compose(parsed: ParsedIntent, snapshots: List<MarketSnapshot>, derived: DerivedMarketData = DerivedMarketData()): AiAnswer {
        return when (parsed.intent) {
            Intent.STOCK_ANALYSIS -> snapshots.firstOrNull()?.let { stockAnalysis(it, snapshots.getOrNull(1)) } ?: fallback(parsed)
            Intent.TREND -> snapshots.firstOrNull()?.let { trend(it) } ?: fallback(parsed)
            Intent.RISK -> snapshots.firstOrNull()?.let { risk(it) } ?: generalRisk()
            Intent.COMPARE -> if (snapshots.size >= 2) compare(snapshots[0], snapshots[1]) else snapshots.firstOrNull()?.let { stockAnalysis(it, null) } ?: fallback(parsed)
            Intent.MARKET_OVERVIEW -> snapshots.firstOrNull()?.let { marketOverview(it, snapshots.drop(1), derived.breadth) } ?: fallback(parsed)
            Intent.LIMIT_UP_LADDER -> limitUpLadder(derived.ladder)
            Intent.CAPITAL_FLOW -> capitalFlow(derived.flow, snapshots.firstOrNull())
            Intent.KNOWLEDGE -> knowledge(parsed)
            Intent.GREETING -> greeting()
            Intent.UNKNOWN -> fallback(parsed)
        }
    }

    // region 个股分析

    private fun stockAnalysis(snapshot: MarketSnapshot, peer: MarketSnapshot?): AiAnswer {
        val quote = snapshot.quote
        val ins = quote.instrument
        val analysis = AnalysisEngine.analyze(snapshot)
        val blocks = mutableListOf<AnswerBlock>()

        blocks += AnswerBlock.Markdown("好的，以下是 **${ins.name}**（${ins.displayCode}）的最新分析：")
        blocks += AnswerBlock.SectionHeader(1, "盘面概览")
        blocks += AnswerBlock.Markdown(overviewNarrative(snapshot, analysis))
        blocks += AnswerBlock.StockCard(quote, snapshot.sparkline, analysis)
        gaugeBlock(analysis)?.let { blocks += it }
        blocks += AnswerBlock.SectionHeader(2, "技术面")
        blocks += AnswerBlock.Markdown(technicalNarrative(snapshot, analysis))
        if (snapshot.dailyBars.size >= 20) {
            blocks += AnswerBlock.ChartCard(
                title = "${ins.name} 日K",
                subtitle = "近 60 个交易日 · MA5 / MA10 / MA20",
                bars = snapshot.dailyBars.takeLast(60),
                instrumentKey = ins.key,
            )
        }
        keyLevelsBlock(quote.price, analysis)?.let { blocks += it }
        recentChangeBars(snapshot)?.let { bars ->
            blocks += AnswerBlock.BarChartCard("近 ${bars.size} 日涨跌幅", "单日涨跌幅", bars, "%")
        }
        var section = 3
        if (!ins.isIndex) {
            blocks += AnswerBlock.SectionHeader(section++, "估值与规模")
            blocks += AnswerBlock.Markdown(valuationNarrative(snapshot))
        }
        if (peer != null) {
            blocks += AnswerBlock.CompareCard(
                title = "${ins.name} vs ${peer.quote.instrument.name}（${peer.quote.instrument.displayCode}）",
                leftName = ins.name,
                rightName = peer.quote.instrument.name,
                rows = compareRows(quote, peer.quote),
                leftKey = ins.key,
                rightKey = peer.quote.instrument.key,
            )
        }
        blocks += AnswerBlock.SectionHeader(section, "核心结论")
        blocks += AnswerBlock.SummaryCallout(summaryComment(ins, analysis))
        blocks += AnswerBlock.Risk("风险提醒", riskBody(snapshot, analysis))
        blocks += AnswerBlock.FollowUps(
            listOf(
                "${ins.name}的趋势判断",
                "${ins.name}有哪些风险",
                if (peer != null) "${ins.name} vs ${peer.quote.instrument.name} 哪个更值得关注" else "${ins.name}的估值贵吗",
            ),
        )
        return AiAnswer(Intent.STOCK_ANALYSIS, blocks, listOfNotNull(snapshot, peer))
    }

    /** 技术面评分仪表盘（数据不足时不展示） */
    private fun gaugeBlock(a: TechnicalAnalysis): AnswerBlock.GaugeCard? {
        if (!a.hasEnoughData) return null
        return AnswerBlock.GaugeCard(
            title = "技术面评分",
            value = a.score.toFloat(),
            label = a.shortTermTrend.label,
            description = "综合均线排列、RSI 动能与区间位置，评分越高技术面越强",
        )
    }

    /** 关键价位表（支撑 / 压力 / 均线） */
    private fun keyLevelsBlock(price: Double, a: TechnicalAnalysis): AnswerBlock.KeyLevelsCard? {
        if (!a.hasEnoughData) return null
        val levels = mutableListOf<KeyLevel>()
        a.resistance?.let { levels += KeyLevel("压力位", NumberFormat.price(it), "近 20 日最高，突破需放量", TagTone.NEGATIVE) }
        a.ma5?.let { levels += KeyLevel("MA5", NumberFormat.price(it), if (price >= it) "股价在其上方，短线偏强" else "股价在其下方，短线偏弱") }
        a.ma10?.let { levels += KeyLevel("MA10", NumberFormat.price(it), if (price >= it) "股价在其上方，波段偏多" else "股价在其下方，波段承压") }
        a.ma20?.let { levels += KeyLevel("MA20", NumberFormat.price(it), "中期趋势分水岭") }
        a.support?.let { levels += KeyLevel("支撑位", NumberFormat.price(it), "近 20 日最低，跌破需减仓", TagTone.POSITIVE) }
        return if (levels.isEmpty()) null else AnswerBlock.KeyLevelsCard("关键价位", levels)
    }

    /** 近 N 日单日涨跌幅柱状图数据 */
    private fun recentChangeBars(snapshot: MarketSnapshot, days: Int = 10): List<BarEntry>? {
        val tail = snapshot.dailyBars.takeLast(days + 1)
        if (tail.size < 3) return null
        val entries = tail.zipWithNext().map { (prev, cur) ->
            val pct = if (prev.close > 0) (cur.close - prev.close) / prev.close * 100 else 0.0
            BarEntry(shortMd(cur.date), pct, if (pct >= 0) BarColor.UP else BarColor.DOWN)
        }
        return entries.takeIf { it.isNotEmpty() }
    }

    /** 2024-06-21 → 06-21 */
    private fun shortMd(date: String): String =
        date.replace('/', '-').let { if (it.length >= 10) it.substring(5, 10) else it }

    private fun overviewNarrative(snapshot: MarketSnapshot, a: TechnicalAnalysis): String {
        val q = snapshot.quote
        val ins = q.instrument
        return "${ins.name}最新报 **${NumberFormat.price(q.price)} ${ins.market.currency}**，" +
            "${if (q.isUp) "上涨" else if (q.isDown) "下跌" else "平收"} ${NumberFormat.signedPct(q.changePct)}，" +
            "日内振幅 ${NumberFormat.pct(q.amplitude)}，成交额 ${NumberFormat.compact(q.turnover)}。" +
            (a.volumeRatio?.let { " 量比 ${NumberFormat.ratio(it)}，${volumeComment(it, q.isUp)}" } ?: "")
    }

    private fun technicalNarrative(snapshot: MarketSnapshot, a: TechnicalAnalysis): String {
        val q = snapshot.quote
        val sb = StringBuilder()
        if (a.hasEnoughData) {
            sb.appendLine("- 均线：MA5 ${NumberFormat.price(a.ma5)} / MA10 ${NumberFormat.price(a.ma10)} / MA20 ${NumberFormat.price(a.ma20)}，${maComment(q.price, a)}")
            a.rsi14?.let { sb.appendLine("- RSI(14) 为 **${NumberFormat.fixed(it, 1)}**，${rsiComment(it)}") }
            sb.appendLine("- 近 20 日区间 **${NumberFormat.price(a.support)} ~ ${NumberFormat.price(a.resistance)}**，${rangeComment(q.price, a)}")
            a.change20dPct?.let { sb.append("- 近 5 日 ${NumberFormat.signedPct(a.change5dPct)}，近 20 日 ${NumberFormat.signedPct(it)}，中期${a.midTermTrend.label}") }
        } else {
            sb.append("- 历史数据不足，暂不提供均线与区间判断")
        }
        return sb.toString()
    }

    private fun valuationNarrative(snapshot: MarketSnapshot): String {
        val q = snapshot.quote
        val ins = q.instrument
        val sb = StringBuilder()
        sb.appendLine(
            "- 市盈率 ${NumberFormat.ratio(q.pe)} 倍" + (q.pb?.let { "，市净率 ${NumberFormat.ratio(it)} 倍" } ?: "") +
                (q.totalMarketCap?.let { "，总市值 ${NumberFormat.capFromYi(it)} ${ins.market.currency}" } ?: ""),
        )
        q.high52w?.let { high ->
            q.low52w?.let { low ->
                val pos = if (high > low) (q.price - low) / (high - low) * 100 else 50.0
                sb.append("- 52 周区间 ${NumberFormat.price(low)} ~ ${NumberFormat.price(high)}，当前处于区间 **${NumberFormat.fixed(pos, 0)}%** 分位")
            }
        }
        return sb.toString()
    }

    private fun volumeComment(ratio: Double, isUp: Boolean): String = when {
        ratio >= 1.5 && isUp -> "放量上涨，资金参与度较高。"
        ratio >= 1.5 && !isUp -> "放量下跌，需警惕抛压释放。"
        ratio <= 0.7 -> "成交明显缩量，观望情绪偏重。"
        else -> "成交量与近期均值相当。"
    }

    private fun maComment(price: Double, a: TechnicalAnalysis): String {
        val ma5 = a.ma5 ?: return "均线数据不足。"
        val ma20 = a.ma20 ?: return "均线数据不足。"
        return when {
            price > ma5 && ma5 > ma20 -> "股价站上短中期均线，**多头排列**格局延续。"
            price < ma5 && ma5 < ma20 -> "股价运行在短中期均线之下，**空头排列**，反弹需先收复 MA5。"
            price > ma20 -> "股价在 MA20 上方运行，短线均线有所纠结，属于强势整理。"
            else -> "股价跌破 MA20，中期趋势转弱，需观察能否快速收回。"
        }
    }

    private fun rsiComment(rsi: Double): String = when {
        rsi >= 70 -> "已进入超买区，短线追高风险上升。"
        rsi <= 30 -> "处于超卖区，技术性反弹概率增加。"
        rsi >= 55 -> "多方力量占优，动能偏强。"
        rsi <= 45 -> "空方略占上风，动能偏弱。"
        else -> "多空相对均衡。"
    }

    private fun rangeComment(price: Double, a: TechnicalAnalysis): String {
        val support = a.support ?: return ""
        val resistance = a.resistance ?: return ""
        if (resistance <= support) return ""
        val pos = (price - support) / (resistance - support)
        return when {
            pos >= 0.9 -> "现价贴近区间上沿，**${NumberFormat.price(resistance)}** 附近压力明显，突破需成交量配合。"
            pos <= 0.15 -> "现价靠近区间下沿，**${NumberFormat.price(support)}** 是关键支撑，跌破则趋势转弱。"
            else -> "现价位于区间中部，上方压力 ${NumberFormat.price(resistance)}，下方支撑 ${NumberFormat.price(support)}。"
        }
    }

    private fun summaryComment(ins: Instrument, a: TechnicalAnalysis): String {
        val strength = when {
            a.score >= 65 -> "技术面**偏强**"
            a.score <= 38 -> "技术面**偏弱**"
            else -> "技术面**中性**"
        }
        val action = when {
            a.score >= 65 -> "已持有者可继续持有，关注 ${NumberFormat.price(a.resistance)} 附近的突破情况；未持有者不宜追高，可等待回踩 MA5/MA10 附近再考虑分批介入。"
            a.score <= 38 -> "短线不宜盲目抄底，可等待 RSI 回到 30 以下或股价在 ${NumberFormat.price(a.support)} 附近出现止跌信号后再评估。"
            else -> "建议以区间思维应对：靠近 ${NumberFormat.price(a.support)} 支撑关注承接，靠近 ${NumberFormat.price(a.resistance)} 压力注意兑现。"
        }
        return "${ins.name}当前${strength}（综合评分 ${a.score}/100），短期${a.shortTermTrend.label}、中期${a.midTermTrend.label}。$action"
    }

    private fun riskBody(snapshot: MarketSnapshot, a: TechnicalAnalysis): String =
        riskItems(snapshot, a).joinToString("；") + "，投资需谨慎。"

    /** 风险要点列表（详情页逐条展示） */
    fun riskItems(snapshot: MarketSnapshot, a: TechnicalAnalysis): List<String> {
        val q = snapshot.quote
        val ins = q.instrument
        val items = mutableListOf<String>()
        a.volatilityPct?.let { if (it > 35) items += "近 20 日年化波动率 ${NumberFormat.fixed(it, 0)}%，波动偏大" }
        a.rsi14?.let { if (it > 70) items += "RSI 超买，存在技术性回调压力" }
        a.rsi14?.let { if (it < 30) items += "RSI 超卖，下跌动能虽有衰减但趋势尚未确认反转" }
        a.biasToMa20Pct?.let { if (it > 8) items += "偏离 MA20 达 ${NumberFormat.fixed(it, 1)}%，短线过热" }
        if (q.pe != null && q.pe > 40) items += "市盈率 ${NumberFormat.ratio(q.pe)} 倍，估值处于高位"
        a.change5dPct?.let { if (it < -8) items += "近 5 日累计下跌 ${NumberFormat.fixed(-it, 1)}%，短线情绪偏弱" }
        items += sectorRisk(ins)
        return items
    }

    private fun sectorRisk(ins: Instrument): String = when {
        ins.isIndex -> "指数走势受宏观政策、海外流动性与汇率波动等多重因素影响"
        ins.sector.contains("互联网") -> "互联网行业受监管政策、市场竞争、宏观经济波动等因素影响，可能影响公司业绩"
        ins.sector.contains("新能源") || ins.sector.contains("电动车") || ins.sector.contains("锂电") -> "新能源行业受补贴政策、原材料价格与行业价格战影响，盈利波动较大"
        ins.sector.contains("白酒") || ins.sector.contains("消费") -> "消费板块受居民消费意愿、渠道库存与政策导向影响"
        ins.sector.contains("金融") -> "金融行业受利率周期、资产质量与资本市场景气度影响"
        ins.sector.contains("半导体") || ins.sector.contains("科技") -> "科技行业受产业周期、出口管制与技术迭代影响，估值波动较大"
        else -> "个股受行业景气度、公司经营与市场情绪等因素影响"
    }

    // endregion

    // region 趋势判断

    private fun trend(snapshot: MarketSnapshot): AiAnswer {
        val q = snapshot.quote
        val ins = q.instrument
        val a = AnalysisEngine.analyze(snapshot)
        val blocks = mutableListOf<AnswerBlock>()
        blocks += AnswerBlock.Markdown("我基于**${ins.name}**近 60 个交易日的日 K 数据，从均线、动能、区间三个维度做了趋势判断：")
        val sb = StringBuilder()
        sb.appendLine("- **短期（1~2 周）：${a.shortTermTrend.label}**。${maComment(q.price, a)}")
        sb.appendLine("- **中期（1~3 月）：${a.midTermTrend.label}**。近 20 日累计 ${NumberFormat.signedPct(a.change20dPct)}" + (a.ma60?.let { "，MA60 位于 ${NumberFormat.price(it)}，${if (q.price > it) "股价在其上方运行" else "股价仍在其下方"}" } ?: "") + "。")
        a.rsi14?.let { sb.appendLine("- **动能**：RSI(14) ${NumberFormat.fixed(it, 1)}，${rsiComment(it)}") }
        blocks += AnswerBlock.SectionHeader(1, "趋势结论")
        blocks += AnswerBlock.Markdown(sb.toString().trimEnd())
        blocks += AnswerBlock.ChartCard(
            title = "${ins.name} 日K",
            subtitle = "MA5 / MA10 / MA20 · 前复权",
            bars = snapshot.dailyBars.takeLast(60),
            instrumentKey = ins.key,
        )
        blocks += AnswerBlock.Tags(a.tags)
        gaugeBlock(a)?.let { blocks += it }
        blocks += AnswerBlock.SectionHeader(2, "关键价位")
        keyLevelsBlock(q.price, a)?.let { blocks += it }
        recentChangeBars(snapshot)?.let { bars ->
            blocks += AnswerBlock.BarChartCard("近 ${bars.size} 日涨跌幅", "单日涨跌幅", bars, "%")
        }
        blocks += AnswerBlock.SummaryCallout(
            "${ins.name}短期${a.shortTermTrend.label}、中期${a.midTermTrend.label}，" +
                "主要博弈区间 ${NumberFormat.price(a.support)} ~ ${NumberFormat.price(a.resistance)}。" +
                "趋势判断基于历史价格统计，不构成投资建议。",
        )
        blocks += AnswerBlock.FollowUps(listOf("${ins.name}后市如何", "${ins.name}有哪些风险", "什么是均线"))
        return AiAnswer(Intent.TREND, blocks, listOf(snapshot))
    }

    // endregion

    // region 风险

    private fun risk(snapshot: MarketSnapshot): AiAnswer {
        val q = snapshot.quote
        val ins = q.instrument
        val a = AnalysisEngine.analyze(snapshot)
        val blocks = mutableListOf<AnswerBlock>()
        blocks += AnswerBlock.Markdown("以下是 **${ins.name}**（${ins.displayCode}）当前需要关注的风险点，我按“价格 → 估值 → 行业”三层来梳理：")
        blocks += AnswerBlock.StockCard(q, snapshot.sparkline, a)
        val sb = StringBuilder()
        sb.appendLine("#### 1. 价格与波动风险")
        a.volatilityPct?.let { sb.appendLine("- 近 20 日年化波动率 **${NumberFormat.fixed(it, 0)}%**，${if (it > 35) "高于多数蓝筹，短线波动剧烈" else if (it > 22) "处于中等水平" else "相对温和"}") }
        a.biasToMa20Pct?.let { sb.appendLine("- 现价偏离 MA20 ${NumberFormat.signedPct(it, 1)}，${if (abs(it) > 8) "偏离过大，存在均值回归压力" else "处于正常范围"}") }
        sb.appendLine("- 若跌破近 20 日支撑 **${NumberFormat.price(a.support)}**，短期趋势将转弱")
        sb.appendLine()
        if (!ins.isIndex) {
            sb.appendLine("#### 2. 估值风险")
            sb.appendLine("- 市盈率 ${NumberFormat.ratio(q.pe)} 倍" + (q.pb?.let { "，市净率 ${NumberFormat.ratio(it)} 倍" } ?: "") + "，${peComment(q.pe)}")
            q.high52w?.let { high -> q.low52w?.let { low -> if (high > low) sb.appendLine("- 距 52 周高点 ${NumberFormat.price(high)} 还有 ${NumberFormat.pct((high - q.price) / q.price * 100, 1)} 空间，距低点 ${NumberFormat.price(low)} 有 ${NumberFormat.pct((q.price - low) / q.price * 100, 1)} 回撤空间") } }
            sb.appendLine()
        }
        sb.appendLine("#### 3. 行业与外部风险")
        sb.appendLine("- ${sectorRisk(ins)}")
        sb.appendLine("- 海外流动性、汇率变化与地缘事件可能带来系统性波动")
        blocks += AnswerBlock.Markdown(sb.toString())
        blocks += AnswerBlock.Risk("风险提示", "以上分析基于公开行情数据与历史统计，不构成任何投资建议。市场有风险，请根据自身风险承受能力独立决策。")
        blocks += AnswerBlock.FollowUps(listOf("${ins.name}的趋势判断", "${ins.name}后市如何", "什么是 RSI"))
        return AiAnswer(Intent.RISK, blocks, listOf(snapshot))
    }

    private fun peComment(pe: Double?): String = when {
        pe == null || pe <= 0 -> "盈利为负或数据缺失，PE 无参考意义"
        pe > 40 -> "估值处于高位，隐含较高成长预期"
        pe > 20 -> "估值处于合理偏高区间"
        else -> "估值处于合理偏低区间"
    }

    private fun generalRisk(): AiAnswer {
        val md = """
            |### 当前市场需要关注的几类风险
            |
            |1. **流动性风险**：海外利率路径与汇率波动会直接影响港股与 A 股的外资流向
            |2. **政策风险**：行业监管、财政与货币政策的边际变化往往主导板块轮动
            |3. **估值风险**：高景气板块估值透支后，业绩不达预期容易引发剧烈回调
            |4. **个股风险**：业绩变脸、大股东减持、股权质押等事件性冲击
            |
            |告诉我你关注的具体标的，我可以结合实时行情给出更具体的风险提示，例如“**腾讯控股有哪些风险**”。
        """.trimMargin()
        return AiAnswer(
            Intent.RISK,
            listOf(
                AnswerBlock.Markdown(md),
                AnswerBlock.FollowUps(listOf("腾讯控股有哪些风险", "恒生指数短期走势判断", "什么是市盈率")),
            ),
        )
    }

    // endregion

    // region 对比

    private fun compare(left: MarketSnapshot, right: MarketSnapshot): AiAnswer {
        val lq = left.quote
        val rq = right.quote
        val la = AnalysisEngine.analyze(left)
        val ra = AnalysisEngine.analyze(right)
        val blocks = mutableListOf<AnswerBlock>()
        blocks += AnswerBlock.Markdown("下面把**${lq.instrument.name}**与**${rq.instrument.name}**放在同一张表里对比：")
        blocks += AnswerBlock.CompareCard(
            title = "${lq.instrument.name} vs ${rq.instrument.name}（${rq.instrument.displayCode}）",
            leftName = lq.instrument.name,
            rightName = rq.instrument.name,
            rows = compareRows(lq, rq),
            leftKey = lq.instrument.key,
            rightKey = rq.instrument.key,
        )
        val sb = StringBuilder()
        sb.appendLine("#### 对比解读")
        sb.appendLine("- **涨跌表现**：${betterBy(lq, rq) { it.changePct }.let { "${it.instrument.name}今日相对更强（${NumberFormat.signedPct(it.changePct)}）" }}，近 20 日 ${lq.instrument.name} ${NumberFormat.signedPct(la.change20dPct)}，${rq.instrument.name} ${NumberFormat.signedPct(ra.change20dPct)}")
        if (!lq.instrument.isIndex && !rq.instrument.isIndex) {
            sb.appendLine("- **估值水平**：${lq.instrument.name} PE ${NumberFormat.ratio(lq.pe)} 倍，${rq.instrument.name} PE ${NumberFormat.ratio(rq.pe)} 倍，${cheaper(lq, rq)}")
            sb.appendLine("- **规模**：总市值分别为 ${NumberFormat.capFromYi(lq.totalMarketCap)} 与 ${NumberFormat.capFromYi(rq.totalMarketCap)}" + if (lq.instrument.market != rq.instrument.market) "（币种不同，仅供量级参考）" else "")
        }
        sb.appendLine("- **技术面评分**：${lq.instrument.name} ${la.score} 分（${la.shortTermTrend.label}），${rq.instrument.name} ${ra.score} 分（${ra.shortTermTrend.label}）")
        sb.appendLine()
        sb.appendLine("#### 结论")
        val winner = if (la.score >= ra.score) lq else rq
        val loser = if (winner === lq) rq else lq
        sb.append("从技术面看 **${winner.instrument.name}** 当前更占优；${loser.instrument.name}若能收复 MA20 则有望迎头赶上。两者所处行业不同，更适合作为组合中的互补配置而非二选一。")
        blocks += AnswerBlock.Markdown(sb.toString())
        blocks += AnswerBlock.Risk("风险提醒", "跨市场对比需注意币种、交易时段与会计准则差异；以上对比基于最新收盘数据，不构成投资建议。")
        blocks += AnswerBlock.FollowUps(listOf("${lq.instrument.name}后市如何", "${rq.instrument.name}的趋势判断", "${lq.instrument.name}有哪些风险"))
        return AiAnswer(Intent.COMPARE, blocks, listOf(left, right))
    }

    private inline fun betterBy(a: Quote, b: Quote, selector: (Quote) -> Double): Quote =
        if (selector(a) >= selector(b)) a else b

    private fun cheaper(a: Quote, b: Quote): String {
        val pa = a.pe
        val pb = b.pe
        if (pa == null || pb == null || pa <= 0 || pb <= 0) return "其中一方盈利为负或数据缺失，估值不可直接比较"
        return if (pa < pb) "${a.instrument.name}估值相对更低" else "${b.instrument.name}估值相对更低"
    }

    private fun compareRows(l: Quote, r: Quote): List<CompareRow> {
        val rows = mutableListOf<CompareRow>()
        rows += CompareRow("最新价", NumberFormat.price(l.price), NumberFormat.price(r.price), diffText(l.price - r.price, digits = 2), toneOf(l.price - r.price))
        rows += CompareRow("涨跌幅", NumberFormat.signedPct(l.changePct), NumberFormat.signedPct(r.changePct), diffText(l.changePct - r.changePct, suffix = "%"), toneOf(l.changePct - r.changePct))
        rows += CompareRow("成交额", NumberFormat.compact(l.turnover), NumberFormat.compact(r.turnover), diffCompact(l.turnover - r.turnover), toneOf(l.turnover - r.turnover))
        if (!l.instrument.isIndex && !r.instrument.isIndex) {
            rows += CompareRow("市盈率(PE)", NumberFormat.ratio(l.pe), NumberFormat.ratio(r.pe), diffText((l.pe ?: 0.0) - (r.pe ?: 0.0), digits = 2), toneOf(-((l.pe ?: 0.0) - (r.pe ?: 0.0))))
            rows += CompareRow("市值", NumberFormat.capFromYi(l.marketCap), NumberFormat.capFromYi(r.marketCap), diffCompact(((l.marketCap ?: 0.0) - (r.marketCap ?: 0.0)) * 1_0000_0000.0), toneOf((l.marketCap ?: 0.0) - (r.marketCap ?: 0.0)))
            rows += CompareRow("总市值", NumberFormat.capFromYi(l.totalMarketCap), NumberFormat.capFromYi(r.totalMarketCap), diffCompact(((l.totalMarketCap ?: 0.0) - (r.totalMarketCap ?: 0.0)) * 1_0000_0000.0), toneOf((l.totalMarketCap ?: 0.0) - (r.totalMarketCap ?: 0.0)))
        } else {
            rows += CompareRow("振幅", NumberFormat.pct(l.amplitude), NumberFormat.pct(r.amplitude), diffText((l.amplitude ?: 0.0) - (r.amplitude ?: 0.0), suffix = "%"), TagTone.NEUTRAL)
        }
        return rows
    }

    private fun diffText(diff: Double, digits: Int = 2, suffix: String = ""): String = NumberFormat.signed(diff, digits) + suffix

    private fun diffCompact(diff: Double): String = (if (diff > 0) "+" else "") + NumberFormat.compact(diff)

    private fun toneOf(diff: Double): TagTone = when {
        diff > 0 -> TagTone.POSITIVE
        diff < 0 -> TagTone.NEGATIVE
        else -> TagTone.NEUTRAL
    }

    // endregion

    // region 大盘

    private fun marketOverview(index: MarketSnapshot, others: List<MarketSnapshot>, breadth: MarketBreadthData?): AiAnswer {
        val q = index.quote
        val ins = q.instrument
        val a = AnalysisEngine.analyze(index)
        val blocks = mutableListOf<AnswerBlock>()
        blocks += AnswerBlock.Markdown("这是**${ins.name}**的最新表现与短期走势判断：")
        val sb = StringBuilder()
        sb.appendLine("- 指数${if (q.isUp) "收涨" else if (q.isDown) "收跌" else "平收"} ${NumberFormat.signedPct(q.changePct)}，报 ${NumberFormat.price(q.price)} 点，${maComment(q.price, a)}")
        a.rsi14?.let { sb.appendLine("- RSI(14) ${NumberFormat.fixed(it, 1)}，${rsiComment(it)}") }
        sb.append("- 近 20 日波动区间 ${NumberFormat.price(a.support)} ~ ${NumberFormat.price(a.resistance)} 点，${rangeComment(q.price, a)}")
        blocks += AnswerBlock.SectionHeader(1, "盘面概览")
        blocks += AnswerBlock.Markdown(sb.toString())
        blocks += AnswerBlock.StockCard(q, index.sparkline, a)
        gaugeBlock(a)?.let { blocks += it }
        blocks += AnswerBlock.MetricGrid(
            listOf(
                Metric("今开", NumberFormat.price(q.open)),
                Metric("最高", NumberFormat.price(q.high), TagTone.POSITIVE),
                Metric("最低", NumberFormat.price(q.low), TagTone.NEGATIVE),
                Metric("成交额", NumberFormat.compact(q.turnover)),
            ),
        )
        breadth?.let { b ->
            val total = b.advancing + b.declining
            val limitUpRate = if (total > 0) NumberFormat.fixed(b.limitUp * 100.0 / total, 2) + "%" else "--"
            blocks += AnswerBlock.MarketBreadthCard(
                title = "市场广度（A 股 · ${b.date.take(10)}）",
                advancing = b.advancing,
                declining = b.declining,
                limitUp = b.limitUp,
                limitDown = b.limitDown,
                halted = b.halted,
                limitUpRate = limitUpRate,
            )
        }
        blocks += AnswerBlock.SectionHeader(2, "短期展望")
        blocks += AnswerBlock.SummaryCallout(indexOutlook(ins, a))
        recentChangeBars(index)?.let { bars ->
            blocks += AnswerBlock.BarChartCard("近 ${bars.size} 日涨跌幅", "单日涨跌幅", bars, "%")
        }
        blocks += AnswerBlock.ChartCard("${ins.name} 日K", "近 60 个交易日 · MA5 / MA10 / MA20", index.dailyBars.takeLast(60), ins.key)
        blocks += AnswerBlock.Risk("风险提醒", "指数走势受宏观政策、海外流动性、汇率及地缘因素影响较大，短期判断存在不确定性，不构成投资建议。")
        val follow = mutableListOf("${ins.name}有哪些风险")
        if (ins.market == Market.HK) follow += listOf("腾讯控股后市如何", "港股科技板块趋势分析")
        else follow += listOf("贵州茅台后市如何", "宁德时代的趋势判断")
        blocks += AnswerBlock.FollowUps(follow.distinct().take(3))
        return AiAnswer(Intent.MARKET_OVERVIEW, blocks, listOf(index) + others)
    }

    private fun indexOutlook(ins: Instrument, a: TechnicalAnalysis): String = when (a.shortTermTrend) {
        TrendBias.BULLISH -> "${ins.name}短期维持多头结构，若量能延续，有望挑战 ${NumberFormat.price(a.resistance)} 点一线；回踩 MA10 不破可视为健康整理。"
        TrendBias.BEARISH -> "${ins.name}短期承压，反弹需先站回 MA5（${NumberFormat.price(a.ma5)}）并伴随成交放大；若失守 ${NumberFormat.price(a.support)} 点，需防范进一步调整。"
        TrendBias.NEUTRAL -> "${ins.name}处于震荡整理阶段，${NumberFormat.price(a.support)} ~ ${NumberFormat.price(a.resistance)} 点是主要博弈区间，方向选择需等待成交量确认。"
    }

    // endregion

    // region 连板梯队 / 资金流向（AKShare 网关）

    private fun limitUpLadder(ladder: LimitUpLadderData?): AiAnswer {
        if (ladder == null || ladder.levels.isEmpty()) {
            return derivedUnavailable(
                Intent.LIMIT_UP_LADDER,
                "连板梯队",
                listOf("今天大盘怎么样", "上证指数走势判断"),
            )
        }
        val maxLevel = ladder.levels.maxOf { it.level }
        val blocks = mutableListOf<AnswerBlock>()
        blocks += AnswerBlock.Markdown("这是 **${ladder.date}** 的涨停连板梯队：")
        blocks += AnswerBlock.LimitUpLadderCard(
            title = "连板梯队（${ladder.date}）",
            maxLevel = maxLevel,
            levels = ladder.levels.map { lv ->
                LadderLevel(
                    level = lv.level,
                    stocks = lv.stocks.map { s ->
                        LadderStock(
                            name = s.name,
                            code = s.code,
                            changePct = signedPctText(s.changePct),
                            marketCap = NumberFormat.compact(s.marketCap),
                        )
                    },
                )
            },
        )
        val top = ladder.levels.firstOrNull { it.level == maxLevel }?.stocks?.firstOrNull()
        val comment = buildString {
            append("当前市场最高 **${maxLevel} 连板**")
            top?.let { append("，高度股为 **${it.name}**（${it.code}）") }
            append("。连板高度反映短线情绪强弱：高度打开说明赚钱效应仍在，反之需警惕情绪退潮。")
        }
        blocks += AnswerBlock.SummaryCallout(comment)
        blocks += AnswerBlock.Risk("风险提醒", "连板股波动剧烈、封单随时可能打开，数据仅为收盘统计，不构成投资建议。")
        blocks += AnswerBlock.FollowUps(listOf("今天大盘怎么样", "上证指数走势判断", "什么是连板"))
        return AiAnswer(Intent.LIMIT_UP_LADDER, blocks, emptyList())
    }

    private fun capitalFlow(flow: CapitalFlowData?, snapshot: MarketSnapshot?): AiAnswer {
        val ins = snapshot?.quote?.instrument
        if (flow == null || ins == null) {
            return derivedUnavailable(
                Intent.CAPITAL_FLOW,
                "资金流向",
                listOfNotNull(ins?.let { "${it.name}后市如何" }, "今天大盘怎么样", "连板梯队"),
            )
        }
        val blocks = mutableListOf<AnswerBlock>()
        blocks += AnswerBlock.Markdown("这是 **${ins.name}**（${ins.displayCode}）最近一个交易日（${flow.date}）的资金流向：")
        blocks += AnswerBlock.CapitalFlowCard(
            title = "${ins.name} 资金流向（${flow.date}）",
            flows = flow.flows.map { f ->
                CapitalFlow(
                    label = f.label,
                    netInflow = signedCompact(f.netInflow),
                    pct = signedPctText(f.pct),
                    tone = when {
                        f.netInflow > 0 -> TagTone.POSITIVE
                        f.netInflow < 0 -> TagTone.NEGATIVE
                        else -> TagTone.NEUTRAL
                    },
                )
            },
        )
        val main = flow.flows.firstOrNull { it.label == "主力" }
        main?.let { m ->
            val direction = when {
                m.netInflow > 0 -> "净流入"
                m.netInflow < 0 -> "净流出"
                else -> "基本持平"
            }
            blocks += AnswerBlock.SummaryCallout(
                "主力资金当日${direction} **${NumberFormat.compact(abs(m.netInflow))}**（占比 ${signedPctText(m.pct)}）。" +
                    if (m.netInflow > 0) "主力净流入通常被视为短期积极信号，但需结合量能与价格位置综合判断。"
                    else if (m.netInflow < 0) "主力净流出提示短期抛压存在，关注后续是否持续流出。"
                    else "主力资金观望情绪较浓，等待方向选择。",
            )
        }
        blocks += AnswerBlock.Risk("风险提醒", "资金流向为主动性买卖统计口径，不同数据源存在差异，不构成投资建议。")
        blocks += AnswerBlock.FollowUps(listOf("${ins.name}后市如何", "${ins.name}有哪些风险", "连板梯队"))
        return AiAnswer(Intent.CAPITAL_FLOW, blocks, listOfNotNull(snapshot))
    }

    /** 网关未启动 / 数据不可用时的统一降级回答 */
    private fun derivedUnavailable(intent: Intent, feature: String, followUps: List<String>): AiAnswer {
        val md = "**${feature}数据暂不可用。**\n\n" +
            "该功能依赖本地 AKShare 数据网关，请确认已启动：\n\n" +
            "```\npython scripts/ak_gateway.py\n```\n\n" +
            "启动后监听 `127.0.0.1:8790`，客户端会自动获取真实数据。"
        return AiAnswer(intent, listOf(AnswerBlock.Markdown(md), AnswerBlock.FollowUps(followUps)), emptyList())
    }

    /** 带符号的紧凑金额（+1.33亿 / -5600万），金额为元 */
    private fun signedCompact(value: Double): String =
        (if (value > 0) "+" else "") + NumberFormat.compact(value)

    /** 带符号百分比文本（+9.97%） */
    private fun signedPctText(value: Double): String =
        (if (value > 0) "+" else "") + NumberFormat.fixed(value, 2) + "%"

    // endregion

    // region 知识 / 问候 / 兜底

    private fun knowledge(parsed: ParsedIntent): AiAnswer {
        val entry = KnowledgeBase.match(parsed.rawText)
        if (entry != null) {
            return AiAnswer(Intent.KNOWLEDGE, listOf(AnswerBlock.Markdown(entry.markdown), AnswerBlock.FollowUps(entry.followUps)))
        }
        return fallback(parsed)
    }

    private fun greeting(): AiAnswer {
        val md = """
            |可以直接问标的、对比或风险。当前覆盖港股、A 股与美股主流品种，基于公开行情做解读：
            |
            |- **行情分析**：如“腾讯控股后市如何？”
            |- **趋势判断**：如“恒生指数短期走势判断”
            |- **对比行情**：如“比亚迪 vs 特斯拉 对比”
            |- **风险提醒**：如“阿里巴巴有哪些风险”
            |- **知识问答**：如“什么是市盈率”
            |
            |目前支持港股、A 股与美股的主流标的，直接输入股票名称或代码即可开始。
        """.trimMargin()
        return AiAnswer(Intent.GREETING, listOf(AnswerBlock.Markdown(md), AnswerBlock.FollowUps(KnowledgeBase.defaultFollowUps)))
    }

    private fun fallback(parsed: ParsedIntent): AiAnswer {
        val md = """
            |我暂时没能准确理解「${parsed.rawText.take(30)}」。你可以试试这样问我：
            |
            |- 输入 **股票名称或代码** 查看行情分析，例如“腾讯控股后市如何？”“00700”
            |- 用 **vs / 对比** 连接两只股票进行比较，例如“美团 vs 阿里巴巴”
            |- 询问 **大盘 / 板块**，例如“恒生指数短期走势判断”
            |- 询问 **金融名词**，例如“什么是 RSI”
            |
            |目前内置标的：${supportedNames()}。
        """.trimMargin()
        return AiAnswer(Intent.UNKNOWN, listOf(AnswerBlock.Markdown(md), AnswerBlock.FollowUps(KnowledgeBase.defaultFollowUps)))
    }

    private fun supportedNames(): String = StockCatalog.all.joinToString("、") { it.name }

    // endregion

    /** 详情页 AI 深度解读文案 */
    fun detailInsight(snapshot: MarketSnapshot, analysis: TechnicalAnalysis): String {
        val q = snapshot.quote
        val ins = q.instrument
        val move = if (q.isUp) "上涨" else if (q.isDown) "下跌" else "平盘"
        val driver = when {
            ins.isIndex -> "受权重股表现与市场流动性预期影响"
            ins.sector.contains("互联网") -> "受益于核心业务回暖与市场对新业务增长的预期"
            ins.sector.contains("新能源") || ins.sector.contains("电动车") -> "受行业销量数据与价格竞争预期影响"
            ins.sector.contains("消费") || ins.sector.contains("白酒") -> "受消费复苏预期与渠道动销数据影响"
            ins.sector.contains("金融") -> "受利率环境与资产质量预期影响"
            else -> "受行业景气度与资金偏好变化影响"
        }
        return "${ins.name}今日${move} ${NumberFormat.signedPct(q.changePct)}，$driver。" +
            "技术面${maComment(q.price, analysis).trimEnd('。')}，" +
            "关注上方 ${NumberFormat.price(analysis.resistance)} 压力位的突破情况。" +
            (analysis.rsi14?.let { " RSI ${NumberFormat.fixed(it, 0)}，${rsiComment(it).trimEnd('。')}。" } ?: "") +
            when (analysis.score) {
                in 65..100 -> "建议关注后续财报与业绩指引，可在回踩均线附近分批布局。"
                in 0..38 -> "建议等待止跌信号明确后再行评估，控制仓位。"
                else -> "建议以区间思维操作，逢低关注、逢高兑现。"
            }
    }

    fun insightTags(analysis: TechnicalAnalysis): List<InsightTag> = analysis.tags
}
