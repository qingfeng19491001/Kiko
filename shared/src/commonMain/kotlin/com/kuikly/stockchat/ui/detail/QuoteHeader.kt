package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.PeerUniverse
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.InstrumentAvatar
import com.kuikly.stockchat.ui.components.TagChip
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// region 头部行情


/** KuiklyKLineChart 完整 Demo 同款行情头部：左侧 120f 大价格，右侧 3×3 横向指标。 */
fun ViewContainer<*, *>.DemoQuoteOverview(quote: Quote) {
    val changeColor = AppTheme.changeColor(quote.change)
    View {
        attr {
            height(104f)
            paddingLeft(12f); paddingRight(12f); paddingTop(10f); paddingBottom(8f)
            backgroundColor(AppTheme.surface)
            flexDirectionRow(); alignItemsCenter()
        }
        View {
            attr { width(120f); height(86f); justifyContentCenter() }
            Text {
                val priceText = NumberFormat.price(quote.price)
                attr {
                    text(priceText)
                    // 茅台这类千元标的价格 7 个字符，34f 会超出 120f 宽度，缩到 28f 防换行
                    fontSize(if (priceText.length > 6) 28f else 34f)
                    fontWeight600()
                    color(changeColor)
                }
            }
            View {
                attr { height(22f); flexDirectionRow(); alignItemsCenter(); marginTop(2f) }
                Text { attr { text(NumberFormat.signed(quote.change)); fontSize(13f); fontWeight600(); color(changeColor) } }
                Text { attr { text(NumberFormat.signedPct(quote.changePct)); fontSize(13f); fontWeight600(); marginLeft(8f); color(changeColor) } }
            }
        }
        View {
            attr { flex(1f); height(86f); marginLeft(4f); justifyContentCenter() }
            demoQuoteMetrics(quote).chunked(3).forEach { row ->
                View {
                    attr { height(27f); flexDirectionRow(); alignItemsCenter() }
                    row.forEach { (label, value) -> DemoQuoteMetric(label, value) }
                }
            }
        }
    }
}

private fun demoQuoteMetrics(quote: Quote): List<Pair<String, String>> = listOf(
    "高" to NumberFormat.price(quote.high),
    "总值" to NumberFormat.capFromYi(quote.totalMarketCap ?: quote.marketCap),
    "量比" to "--",
    "低" to NumberFormat.price(quote.low),
    "流通" to NumberFormat.capFromYi(quote.marketCap),
    "换手" to NumberFormat.pct(quote.turnoverRate),
    "开" to NumberFormat.price(quote.open),
    "量" to NumberFormat.compact(quote.volume),
    "额" to NumberFormat.compact(quote.turnover),
)

/** 官方 Demo 的指标 cell：label(9f，固定 23f 宽) + value(10f 半粗) 横向排列 */
private fun ViewContainer<*, *>.DemoQuoteMetric(label: String, value: String) {
    View {
        attr { flex(1f); height(27f); flexDirectionRow(); alignItemsCenter() }
        Text { attr { text(label); fontSize(9f); color(Color(0xFF8A94A6L)); width(23f) } }
        Text { attr { text(value); fontSize(10f); fontWeight600(); color(Color(0xFF374151L)) } }
    }
}

// endregion
