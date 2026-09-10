package com.kuikly.stockchat.ui.components

import com.kuikly.stockchat.domain.analysis.TagTone
import com.kuikly.stockchat.domain.chat.AnswerBlock
import com.kuikly.stockchat.domain.chat.BarColor
import com.kuikly.stockchat.domain.chat.BarEntry
import com.kuikly.stockchat.domain.chat.CapitalFlow
import com.kuikly.stockchat.domain.chat.KeyLevel
import com.kuikly.stockchat.domain.chat.LadderLevel
import com.kuikly.stockchat.domain.chat.LadderStock
import com.kuikly.stockchat.domain.model.InstrumentCache
import com.kuikly.stockchat.domain.model.StockCatalog
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.charts.BarChart
import com.kuikly.stockchat.ui.components.charts.CandleChart
import com.kuikly.stockchat.ui.components.charts.GaugeChart
import com.kuikly.stockchat.ui.components.charts.SparklineChart
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * AI 回复中的结构化块渲染。所有卡片都是纯函数式 UI：输入 block，输出视图。
 *
 * @param contentWidth 卡片可用宽度（气泡内宽度），Canvas 需要明确尺寸
 * @param onOpenInstrument 点击标的跳转详情
 * @param onFollowUp 点击追问
 */
fun ViewContainer<*, *>.AnswerBlockView(
    block: AnswerBlock,
    contentWidth: Float,
    onOpenInstrument: (key: String) -> Unit,
    onFollowUp: (text: String) -> Unit,
) {
    when (block) {
        is AnswerBlock.Markdown -> Unit // 由流式 Markdown 组件单独渲染
        is AnswerBlock.StockCard -> StockCardView(block, contentWidth, onOpenInstrument)
        is AnswerBlock.CompareCard -> CompareCardView(block, onOpenInstrument)
        is AnswerBlock.ChartCard -> ChartCardView(block, contentWidth, onOpenInstrument)
        is AnswerBlock.MetricGrid -> MetricGridView(block, contentWidth)
        is AnswerBlock.Tags -> TagsView(block)
        is AnswerBlock.Risk -> RiskView(block)
        is AnswerBlock.FollowUps -> FollowUpsView(block, onFollowUp)
        is AnswerBlock.SectionHeader -> SectionHeaderView(block)
        is AnswerBlock.SummaryCallout -> SummaryCalloutView(block)
        is AnswerBlock.BarChartCard -> BarChartCardView(block, contentWidth)
        is AnswerBlock.KeyLevelsCard -> KeyLevelsCardView(block)
        is AnswerBlock.GaugeCard -> GaugeCardView(block, contentWidth)
        is AnswerBlock.MarketBreadthCard -> MarketBreadthCardView(block)
        is AnswerBlock.LimitUpLadderCard -> LimitUpLadderCardView(block)
        is AnswerBlock.CapitalFlowCard -> CapitalFlowCardView(block)
    }
}

// region 行情卡片

fun ViewContainer<*, *>.StockCardView(
    block: AnswerBlock.StockCard,
    contentWidth: Float,
    onOpen: (key: String) -> Unit,
) {
    val q = block.quote
    val ins = q.instrument
    InstrumentCache.put(ins)
    val changeColor = AppTheme.changeColor(q.change)
    val sparkW = 96f
    Card(padding = 14f, onClick = { onOpen(ins.key) }) {
        attr { marginTop(8f); marginBottom(4f) }
        // 头部：头像 + 名称 + 代码 + 状态
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            InstrumentAvatar(ins, 38f)
            View {
                attr { flex(1f); marginLeft(10f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text(ins.name)
                            fontSize(16f)
                            fontWeight600()
                            color(AppTheme.textPrimary)
                        }
                    }
                    View {
                        attr {
                            marginLeft(6f)
                            backgroundColor(AppTheme.surfaceMuted)
                            borderRadius(4f)
                            paddingLeft(5f); paddingRight(5f); paddingTop(1f); paddingBottom(1f)
                        }
                        Text {
                            attr {
                                text(ins.displayCode)
                                fontSize(10f)
                                color(AppTheme.textSecondary)
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text(q.tradingStatus + " · " + q.updateTime)
                        fontSize(11f)
                        color(AppTheme.textTertiary)
                        marginTop(3f)
                    }
                }
            }
            Icon(IconKind.CHEVRON_RIGHT, 18f, AppTheme.textTertiary)
        }
        // 价格行 + 迷你走势
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginTop(12f) }
            View {
                attr { flex(1f) }
                View {
                    attr { flexDirectionRow(); alignItemsFlexEnd() }
                    Text {
                        attr {
                            text(NumberFormat.price(q.price))
                            fontSize(28f)
                            fontWeight700()
                            color(changeColor)
                        }
                    }
                    Text {
                        attr {
                            text(ins.market.currency)
                            fontSize(11f)
                            color(AppTheme.textTertiary)
                            marginLeft(4f)
                            marginBottom(5f)
                        }
                    }
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); marginTop(4f) }
                    Text {
                        attr {
                            text(NumberFormat.signed(q.change))
                            fontSize(13f)
                            fontWeight500()
                            color(changeColor)
                            marginRight(6f)
                        }
                    }
                    ChangeBadge(q.change, NumberFormat.signedPct(q.changePct), filled = true)
                }
            }
            if (block.sparkline.size >= 2) {
                SparklineChart(
                    values = block.sparkline,
                    width = sparkW,
                    height = 44f,
                    color = changeColor,
                    fillAlphaColor = if (q.isDown) Color(0x33E5484DL) else Color(0x3316A34AL),
                )
            }
        }
        // 指标行
        Divider(vertical = 12f)
        View {
            attr { flexDirectionRow(); justifyContentSpaceBetween() }
            if (ins.isIndex) {
                LabelValue("今开", NumberFormat.price(q.open))
                LabelValue("最高", NumberFormat.price(q.high), AppTheme.up)
                LabelValue("最低", NumberFormat.price(q.low), AppTheme.down)
                LabelValue("成交额", NumberFormat.compact(q.turnover), alignRight = true)
            } else {
                LabelValue("成交额", NumberFormat.compact(q.turnover))
                LabelValue("市盈率", NumberFormat.ratio(q.pe))
                LabelValue("市值", NumberFormat.capFromYi(q.totalMarketCap ?: q.marketCap))
                LabelValue("振幅", NumberFormat.pct(q.amplitude), alignRight = true)
            }
        }
        // 技术标签
        block.analysis?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            View {
                attr { flexDirectionRow(); flexWrapWrap(); marginTop(12f) }
                tags.forEach { TagChip(it) }
            }
        }
        if (q.isMock) {
            Text {
                attr {
                    text("离线演示数据，仅供体验")
                    fontSize(10f)
                    color(AppTheme.textTertiary)
                    marginTop(4f)
                }
            }
        }
    }
}

// endregion

// region 对比卡片

fun ViewContainer<*, *>.CompareCardView(block: AnswerBlock.CompareCard, onOpen: (key: String) -> Unit) {
    Card(padding = 0f) {
        attr { marginTop(8f); marginBottom(4f); overflow(true) }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(14f); paddingRight(14f); paddingTop(12f); paddingBottom(12f)
            }
            Icon(IconKind.COMPARE, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                    flex(1f)
                }
            }
        }
        // 表头
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                backgroundColor(AppTheme.surfaceMuted)
                paddingLeft(14f); paddingRight(14f); paddingTop(8f); paddingBottom(8f)
            }
            Text { attr { text("指标"); fontSize(12f); color(AppTheme.textTertiary); width(72f) } }
            View {
                attr { flex(1f); alignItemsCenter() }
                event { click { onOpen(block.leftKey) } }
                Text { attr { text(block.leftName); fontSize(12f); fontWeight600(); color(AppTheme.ink); textAlignCenter() } }
            }
            View {
                attr { flex(1f); alignItemsCenter() }
                event { click { onOpen(block.rightKey) } }
                Text { attr { text(block.rightName); fontSize(12f); fontWeight600(); color(AppTheme.ink); textAlignCenter() } }
            }
            Text { attr { text("差值"); fontSize(12f); color(AppTheme.textTertiary); width(70f); textAlignRight() } }
        }
        block.rows.forEachIndexed { index, row ->
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    paddingLeft(14f); paddingRight(14f); paddingTop(10f); paddingBottom(10f)
                    if (index % 2 == 1) backgroundColor(Color(0xFFFAFBFCL))
                }
                Text { attr { text(row.label); fontSize(12f); color(AppTheme.textSecondary); width(72f) } }
                Text { attr { text(row.left); fontSize(13f); fontWeight500(); color(AppTheme.textPrimary); flex(1f); textAlignCenter() } }
                Text { attr { text(row.right); fontSize(13f); fontWeight500(); color(AppTheme.textPrimary); flex(1f); textAlignCenter() } }
                Text {
                    attr {
                        text(row.diff)
                        fontSize(12f)
                        fontWeight600()
                        color(if (row.diffTone == TagTone.NEUTRAL) AppTheme.textSecondary else AppTheme.toneColor(row.diffTone))
                        width(70f)
                        textAlignRight()
                    }
                }
            }
        }
        Spacer(4f)
    }
}

// endregion

// region K 线卡片

fun ViewContainer<*, *>.ChartCardView(block: AnswerBlock.ChartCard, contentWidth: Float, onOpen: (key: String) -> Unit) {
    val chartW = contentWidth - 28f
    Card(padding = 14f, onClick = { onOpen(block.instrumentKey) }) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.CHART, 16f, AppTheme.ink)
            View {
                attr { flex(1f); marginLeft(6f) }
                Text { attr { text(block.title); fontSize(14f); fontWeight600(); color(AppTheme.textPrimary) } }
                Text { attr { text(block.subtitle); fontSize(11f); color(AppTheme.textTertiary); marginTop(2f) } }
            }
            Text { attr { text("详情"); fontSize(12f); color(AppTheme.textSecondary) } }
            Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.textTertiary)
        }
        View {
            attr { marginTop(10f) }
            CandleChart(block.bars, chartW, 190f)
        }
        block.bars.lastOrNull()?.let { last ->
            View {
                attr { flexDirectionRow(); justifyContentSpaceBetween(); marginTop(8f) }
                LabelValue("开", NumberFormat.price(last.open), valueSize = 12f)
                LabelValue("高", NumberFormat.price(last.high), AppTheme.up, valueSize = 12f)
                LabelValue("低", NumberFormat.price(last.low), AppTheme.down, valueSize = 12f)
                LabelValue("收", NumberFormat.price(last.close), AppTheme.changeColor(last.close - last.open), valueSize = 12f)
                LabelValue("量", NumberFormat.compact(last.volume), alignRight = true, valueSize = 12f)
            }
        }
    }
}

// endregion

// region 指标网格 / 标签 / 风险 / 追问

fun ViewContainer<*, *>.MetricGridView(block: AnswerBlock.MetricGrid, contentWidth: Float) {
    val cellWidth = (contentWidth - 6f) / 2
    View {
        attr { flexDirectionRow(); flexWrapWrap(); justifyContentSpaceBetween(); marginTop(8f) }
        block.items.forEach { metric ->
            View {
                attr {
                    width(cellWidth)
                    marginBottom(6f)
                }
                View {
                    attr {
                        backgroundColor(AppTheme.surfaceMuted)
                        borderRadius(8f)
                        padding(10f)
                    }
                    Text { attr { text(metric.label); fontSize(11f); color(AppTheme.textTertiary) } }
                    Text {
                        attr {
                            text(metric.value)
                            fontSize(15f)
                            fontWeight600()
                            color(if (metric.tone == TagTone.NEUTRAL) AppTheme.textPrimary else AppTheme.toneColor(metric.tone))
                            marginTop(3f)
                        }
                    }
                }
            }
        }
    }
}

fun ViewContainer<*, *>.TagsView(block: AnswerBlock.Tags) {
    View {
        attr { flexDirectionRow(); flexWrapWrap(); marginTop(8f) }
        block.tags.forEach { TagChip(it, 12f) }
    }
}

fun ViewContainer<*, *>.RiskView(block: AnswerBlock.Risk) {
    View {
        attr {
            flexDirectionRow()
            backgroundColor(AppTheme.warningSoft)
            borderRadius(10f)
            padding(10f)
            marginTop(10f)
        }
        Icon(IconKind.ALERT, 16f, AppTheme.warning)
        View {
            attr { flex(1f); marginLeft(8f) }
            Text { attr { text(block.title); fontSize(12f); fontWeight600(); color(AppTheme.warning) } }
            Text {
                attr {
                    text(block.body)
                    fontSize(12f)
                    lineHeight(18f)
                    color(Color(0xFF92400EL))
                    marginTop(3f)
                }
            }
        }
    }
}

fun ViewContainer<*, *>.FollowUpsView(block: AnswerBlock.FollowUps, onFollowUp: (String) -> Unit) {
    View {
        attr { marginTop(12f) }
        Text { attr { text("继续提问"); fontSize(11f); fontWeight600(); color(AppTheme.textTertiary); marginBottom(8f) } }
        block.items.forEach { item ->
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    border(Border(1f, BorderStyle.SOLID, AppTheme.border))
                    borderRadius(8f)
                    paddingLeft(12f); paddingRight(10f); paddingTop(9f); paddingBottom(9f)
                    marginBottom(6f)
                }
                event { click { onFollowUp(item) } }
                Text {
                    attr {
                        text(item)
                        fontSize(13f)
                        color(AppTheme.textPrimary)
                        flex(1f)
                    }
                }
                Icon(IconKind.CHEVRON_RIGHT, 14f, AppTheme.textTertiary)
            }
        }
    }
}

// endregion

// region 章节标题 / 核心结论

fun ViewContainer<*, *>.SectionHeaderView(block: AnswerBlock.SectionHeader) {
    View {
        attr { flexDirectionRow(); alignItemsCenter(); marginTop(16f); marginBottom(2f) }
        View {
            attr {
                size(20f, 20f)
                borderRadius(5f)
                backgroundColor(AppTheme.ink)
                allCenter()
            }
            Text {
                attr {
                    text(block.index.toString().padStart(2, '0'))
                    fontSize(11f)
                    fontWeight700()
                    color(Color.WHITE)
                }
            }
        }
        Text {
            attr {
                text(block.title)
                fontSize(15f)
                fontWeight700()
                color(AppTheme.textPrimary)
                marginLeft(8f)
            }
        }
        if (block.subtitle.isNotEmpty()) {
            Text {
                attr {
                    text(block.subtitle)
                    fontSize(11f)
                    color(AppTheme.textTertiary)
                    marginLeft(8f)
                }
            }
        }
    }
}

fun ViewContainer<*, *>.SummaryCalloutView(block: AnswerBlock.SummaryCallout) {
    View {
        attr {
            flexDirectionRow()
            backgroundColor(AppTheme.accentSoft)
            borderRadius(10f)
            marginTop(10f)
            overflow(true)
        }
        View { attr { width(3f); backgroundColor(AppTheme.accent) } }
        View {
            attr {
                flex(1f)
                paddingLeft(12f); paddingRight(12f); paddingTop(11f); paddingBottom(11f)
            }
            Text {
                attr {
                    text(block.text)
                    fontSize(13f)
                    lineHeight(20f)
                    color(AppTheme.textPrimary)
                }
            }
        }
    }
}

// endregion

// region 柱状图卡片

fun ViewContainer<*, *>.BarChartCardView(block: AnswerBlock.BarChartCard, contentWidth: Float) {
    val chartW = contentWidth - 28f
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.CHART, 16f, AppTheme.ink)
            View {
                attr { flex(1f); marginLeft(6f) }
                Text { attr { text(block.title); fontSize(14f); fontWeight600(); color(AppTheme.textPrimary) } }
                if (block.subtitle.isNotEmpty()) {
                    Text { attr { text(block.subtitle); fontSize(11f); color(AppTheme.textTertiary); marginTop(2f) } }
                }
            }
            if (block.unit.isNotEmpty()) {
                Text { attr { text("单位：${block.unit}"); fontSize(10f); color(AppTheme.textTertiary) } }
            }
        }
        View {
            attr { marginTop(8f) }
            BarChart(block.bars, chartW, 150f, block.unit)
        }
    }
}

// endregion

// region 关键价位表

fun ViewContainer<*, *>.KeyLevelsCardView(block: AnswerBlock.KeyLevelsCard) {
    Card(padding = 0f) {
        attr { marginTop(8f); marginBottom(4f); overflow(true) }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(14f); paddingRight(14f); paddingTop(12f); paddingBottom(12f)
            }
            Icon(IconKind.TREND_UP, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                }
            }
        }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                backgroundColor(AppTheme.surfaceMuted)
                paddingLeft(14f); paddingRight(14f); paddingTop(8f); paddingBottom(8f)
            }
            Text { attr { text("价位"); fontSize(12f); color(AppTheme.textTertiary); width(64f) } }
            Text { attr { text("数值"); fontSize(12f); color(AppTheme.textTertiary); width(76f) } }
            Text { attr { text("含义"); fontSize(12f); color(AppTheme.textTertiary); flex(1f) } }
        }
        block.levels.forEachIndexed { index, level ->
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    paddingLeft(14f); paddingRight(14f); paddingTop(9f); paddingBottom(9f)
                    if (index % 2 == 1) backgroundColor(Color(0xFFFAFBFCL))
                }
                Text { attr { text(level.label); fontSize(12f); fontWeight500(); color(AppTheme.textSecondary); width(64f) } }
                Text {
                    attr {
                        text(level.value)
                        fontSize(13f)
                        fontWeight600()
                        color(if (level.tone == TagTone.NEUTRAL) AppTheme.textPrimary else AppTheme.toneColor(level.tone))
                        width(76f)
                    }
                }
                Text { attr { text(level.note); fontSize(11f); lineHeight(16f); color(AppTheme.textTertiary); flex(1f) } }
            }
        }
        Spacer(4f)
    }
}

// endregion

// region 仪表盘卡片

fun ViewContainer<*, *>.GaugeCardView(block: AnswerBlock.GaugeCard, contentWidth: Float) {
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.SPARKLE, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                }
            }
        }
        View {
            attr { alignItemsCenter(); marginTop(4f) }
            GaugeChart(block.value, block.max, contentWidth - 28f, 120f, block.label)
            if (block.description.isNotEmpty()) {
                Text {
                    attr {
                        text(block.description)
                        fontSize(11f)
                        lineHeight(16f)
                        color(AppTheme.textTertiary)
                        textAlignCenter()
                        marginTop(6f)
                    }
                }
            }
        }
    }
}

// endregion

// region 市场广度卡片

fun ViewContainer<*, *>.MarketBreadthCardView(block: AnswerBlock.MarketBreadthCard) {
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.COMPARE, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                    flex(1f)
                }
            }
            View {
                attr {
                    backgroundColor(AppTheme.upSoft)
                    borderRadius(4f)
                    paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                }
                Text {
                    attr {
                        text("涨停率 ${block.limitUpRate}")
                        fontSize(11f)
                        fontWeight600()
                        color(AppTheme.up)
                    }
                }
            }
        }
        // 涨跌对比条
        val total = block.advancing + block.declining
        View {
            attr {
                flexDirectionRow()
                height(8f)
                borderRadius(4f)
                overflow(true)
                backgroundColor(AppTheme.surfaceMuted)
                marginTop(12f)
            }
            if (total > 0) {
                View { attr { flex(block.advancing.toFloat()); backgroundColor(AppTheme.up) } }
                View { attr { flex(block.declining.toFloat()); backgroundColor(AppTheme.down) } }
            }
        }
        View {
            attr { flexDirectionRow(); justifyContentSpaceBetween(); marginTop(12f) }
            LabelValue("上涨", "${block.advancing}", AppTheme.up, valueSize = 15f)
            LabelValue("下跌", "${block.declining}", AppTheme.down, valueSize = 15f)
            LabelValue("涨停", "${block.limitUp}", AppTheme.up, valueSize = 15f)
            LabelValue("跌停", "${block.limitDown}", AppTheme.down, valueSize = 15f)
            LabelValue("停牌", "${block.halted}", AppTheme.textSecondary, alignRight = true, valueSize = 15f)
        }
    }
}

// endregion

// region 连板梯队卡片

fun ViewContainer<*, *>.LimitUpLadderCardView(block: AnswerBlock.LimitUpLadderCard) {
    Card(padding = 14f) {
        attr { marginTop(8f); marginBottom(4f) }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Icon(IconKind.TREND_UP, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                    flex(1f)
                }
            }
            View {
                attr {
                    backgroundColor(AppTheme.ink)
                    borderRadius(4f)
                    paddingLeft(6f); paddingRight(6f); paddingTop(2f); paddingBottom(2f)
                }
                Text {
                    attr {
                        text("最高 ${block.maxLevel} 连板")
                        fontSize(11f)
                        fontWeight600()
                        color(Color.WHITE)
                    }
                }
            }
        }
        block.levels.forEach { level ->
            View {
                attr { flexDirectionRow(); marginTop(10f) }
                View {
                    attr {
                        width(44f)
                        height(22f)
                        borderRadius(4f)
                        backgroundColor(if (level.level >= block.maxLevel) AppTheme.downSoft else AppTheme.surfaceMuted)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("${level.level}板")
                            fontSize(11f)
                            fontWeight600()
                            color(if (level.level >= block.maxLevel) AppTheme.down else AppTheme.textSecondary)
                        }
                    }
                }
                View {
                    attr { flex(1f); flexDirectionRow(); flexWrapWrap(); marginLeft(8f) }
                    level.stocks.forEach { stock ->
                        View {
                            attr {
                                backgroundColor(AppTheme.surfaceMuted)
                                borderRadius(6f)
                                paddingLeft(8f); paddingRight(8f); paddingTop(5f); paddingBottom(5f)
                                marginRight(6f); marginBottom(6f)
                                flexDirectionRow(); alignItemsCenter()
                            }
                            Text { attr { text(stock.name); fontSize(12f); fontWeight600(); color(AppTheme.textPrimary) } }
                            Text {
                                attr {
                                    text(stock.changePct)
                                    fontSize(11f)
                                    fontWeight500()
                                    color(AppTheme.up)
                                    marginLeft(5f)
                                }
                            }
                            Text {
                                attr {
                                    text(stock.marketCap)
                                    fontSize(10f)
                                    color(AppTheme.textTertiary)
                                    marginLeft(5f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region 资金流向卡片

fun ViewContainer<*, *>.CapitalFlowCardView(block: AnswerBlock.CapitalFlowCard) {
    Card(padding = 0f) {
        attr { marginTop(8f); marginBottom(4f); overflow(true) }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                paddingLeft(14f); paddingRight(14f); paddingTop(12f); paddingBottom(12f)
            }
            Icon(IconKind.CHART, 16f, AppTheme.ink)
            Text {
                attr {
                    text(block.title)
                    fontSize(14f)
                    fontWeight600()
                    color(AppTheme.textPrimary)
                    marginLeft(6f)
                }
            }
        }
        View {
            attr {
                flexDirectionRow(); alignItemsCenter()
                backgroundColor(AppTheme.surfaceMuted)
                paddingLeft(14f); paddingRight(14f); paddingTop(8f); paddingBottom(8f)
            }
            Text { attr { text("类型"); fontSize(12f); color(AppTheme.textTertiary); flex(1f) } }
            Text { attr { text("净流入"); fontSize(12f); color(AppTheme.textTertiary); width(88f); textAlignRight() } }
            Text { attr { text("占比"); fontSize(12f); color(AppTheme.textTertiary); width(64f); textAlignRight() } }
        }
        block.flows.forEachIndexed { index, flow ->
            val color = if (flow.tone == TagTone.NEUTRAL) AppTheme.textSecondary else AppTheme.toneColor(flow.tone)
            View {
                attr {
                    flexDirectionRow(); alignItemsCenter()
                    paddingLeft(14f); paddingRight(14f); paddingTop(10f); paddingBottom(10f)
                    if (index % 2 == 1) backgroundColor(Color(0xFFFAFBFCL))
                }
                Text { attr { text(flow.label); fontSize(13f); fontWeight500(); color(AppTheme.textPrimary); flex(1f) } }
                Text { attr { text(flow.netInflow); fontSize(13f); fontWeight600(); color(color); width(88f); textAlignRight() } }
                Text { attr { text(flow.pct); fontSize(12f); color(color); width(64f); textAlignRight() } }
            }
        }
        Spacer(4f)
    }
}

// endregion

internal fun instrumentName(key: String): String =
        InstrumentCache.get(key)?.name ?: StockCatalog.findByKey(key)?.name ?: key
