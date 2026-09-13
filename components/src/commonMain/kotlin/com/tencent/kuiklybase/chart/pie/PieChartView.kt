package com.tencent.kuiklybase.chart.pie

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.PieChartAttr
import com.tencent.kuiklybase.chart.config.PieChartEvent
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.polar.PolarChartChromeView
import com.tencent.kuiklybase.chart.core.polar.PolarHitTester
import com.tencent.kuiklybase.chart.core.polar.PolarLayoutEngine
import com.tencent.kuiklybase.chart.core.toChartColor
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.ChartSlice

class PieChartView(
    private val sliceProvider: () -> ObservableList<ChartSlice>,
) : PolarChartChromeView<PieChartAttr, PieChartEvent>() {

    var selection by observable<ChartSelection?>(null)
    private var hiddenSliceLabels by observable(emptySet<String>())

    private fun visibleSlices(): List<ChartSlice> =
        filterVisibleSlices(sliceProvider().toList(), hiddenSliceLabels)

    override fun createAttr() = PieChartAttr()

    override fun createEvent() = PieChartEvent()

    private fun resolveLayout(width: Float, height: Float) = PolarLayoutEngine.compute(
        width,
        height,
        innerRadiusRatio = attr.innerRadiusRatio,
        ringWidth = attr.ringWidth,
        isDonut = attr.ringWidth > 0f || attr.innerRadiusRatio > 0f,
    )

    override fun drawPolar(context: ContextApi, width: Float, height: Float) {
        val slices = visibleSlices()
        val layout = resolveLayout(width, height)
        val theme = attr.theme.resolved()
        ChartCanvasRenderer.drawPieSlices(
            context,
            layout.centerX,
            layout.centerY,
            layout.outerRadius,
            layout.innerRadius,
            slices,
            attr.startAngle,
            selection,
            theme,
            attr.showPercentLabel,
        )
        if (attr.centerText.isNotEmpty()) {
            context.font(14f)
            context.fillStyle(theme.textColor.toChartColor())
            context.textAlign(TextAlign.CENTER)
            context.fillText(
                attr.centerText,
                layout.centerX,
                layout.centerY,
            )
        }
    }

    override fun onCanvasClick(x: Float, y: Float) {
        val slices = visibleSlices()
        if (slices.isEmpty() || canvasWidth <= 0f) return
        val layout = resolveLayout(canvasWidth, canvasHeight)
        val idx = PolarHitTester.hitSlice(layout, slices, attr.startAngle, x, y) ?: return
        val slice = slices[idx]
        selection = ChartSelection.Slice(idx, slice.label)
        event.onSelectionChange?.invoke(selection)
        val sourceIndex = sliceProvider().indexOfFirst { it.label == slice.label }
        event.onSliceClick?.invoke(slice, sourceIndex.takeIf { it >= 0 } ?: idx)
    }

    override fun renderLegend(parent: ViewContainer<*, *>) {
        val ctx = this
        parent.apply {
            vif({ ctx.attr.legend.show }) {
                View {
                    attr {
                        flexDirection(FlexDirection.ROW)
                        justifyContent(FlexJustifyContent.CENTER)
                        alignItems(FlexAlign.CENTER)
                        padding(8f)
                        flexWrapWrap()
                    }
                    vfor({ ctx.sliceProvider() }) { slice ->
                        View {
                            attr {
                                flexDirection(FlexDirection.ROW)
                                alignItems(FlexAlign.CENTER)
                                marginRight(12f)
                                marginBottom(4f)
                                if (ctx.attr.legend.interactive) {
                                    val hidden = ctx.hiddenSliceLabels.contains(slice.label)
                                    padding(4f, 8f, 4f, 4f)
                                    borderRadius(4f)
                                    backgroundColor(Color(if (hidden) 0xFFE5E7EB else 0xFFF5F6FA))
                                    opacity(if (hidden) 0.45f else 1f)
                                }
                            }
                            View {
                                attr {
                                    size(10f, 10f)
                                    borderRadius(5f)
                                    backgroundColor(slice.color.toChartColor())
                                    marginRight(4f)
                                }
                            }
                            Text {
                                attr {
                                    text(slice.label)
                                    fontSize(ctx.attr.theme.fontSize)
                                    color(ctx.attr.theme.textColor.toChartColor())
                                }
                            }
                            if (ctx.attr.legend.interactive) {
                                event {
                                    click { ctx.toggleSlice(slice.label) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggleSlice(label: String) {
        hiddenSliceLabels = toggleHiddenPolarItem(hiddenSliceLabels, label)
        selection = null
        event.onSelectionChange?.invoke(null)
    }
}

internal fun filterVisibleSlices(
    slices: List<ChartSlice>,
    hiddenLabels: Set<String>,
): List<ChartSlice> = slices.filterNot { hiddenLabels.contains(it.label) }

internal fun toggleHiddenPolarItem(hiddenLabels: Set<String>, label: String): Set<String> =
    if (hiddenLabels.contains(label)) hiddenLabels - label else hiddenLabels + label

fun ViewContainer<*, *>.PieChart(
    sliceProvider: () -> ObservableList<ChartSlice>,
    init: PieChartView.() -> Unit,
) {
    addChild(PieChartView(sliceProvider), init)
}

/** 环形图：复用 [PieChart]，默认开启环宽。 */
fun ViewContainer<*, *>.DonutChart(
    sliceProvider: () -> ObservableList<ChartSlice>,
    init: PieChartView.() -> Unit,
) {
    PieChart(sliceProvider) {
        attr {
            ringWidth = 40f
        }
        init()
    }
}
