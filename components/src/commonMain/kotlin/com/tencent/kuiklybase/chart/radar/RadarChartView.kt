package com.tencent.kuiklybase.chart.radar

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.RadarChartAttr
import com.tencent.kuiklybase.chart.config.RadarChartEvent
import com.tencent.kuiklybase.chart.core.ChartCanvasRenderer
import com.tencent.kuiklybase.chart.core.cartesian.resolveTooltipPosition
import com.tencent.kuiklybase.chart.core.polar.PolarChartChromeView
import com.tencent.kuiklybase.chart.core.polar.PolarScale
import com.tencent.kuiklybase.chart.core.toChartColor
import com.tencent.kuiklybase.chart.model.ChartSelection
import com.tencent.kuiklybase.chart.model.RadarSeries

class RadarChartView(
    private val seriesProvider: () -> ObservableList<RadarSeries>,
) : PolarChartChromeView<RadarChartAttr, RadarChartEvent>() {

    var selection by observable<ChartSelection?>(null)
    private var tooltipText by observable("")
    private var tooltipX by observable(0f)
    private var tooltipY by observable(0f)
    private var showTooltip by observable(false)
    private var hiddenSeriesNames by observable(emptySet<String>())

    private fun visibleSeries(): List<RadarSeries> =
        seriesProvider().filterNot { hiddenSeriesNames.contains(it.name) }

    override fun createAttr() = RadarChartAttr()

    override fun createEvent() = RadarChartEvent()

    override fun drawPolar(context: ContextApi, width: Float, height: Float) {
        val series = visibleSeries()
        val dimensions = attr.dimensions
        val radius = minOf(width, height) * 0.35f
        val cx = width / 2f
        val cy = height / 2f
        ChartCanvasRenderer.drawRadar(
            context, cx, cy, radius,
            dimensions, series, attr.theme.resolved(), selection,
        )
    }

    override fun onCanvasClick(x: Float, y: Float) {
        val series = visibleSeries()
        val dimensions = attr.dimensions
        if (series.isEmpty() || dimensions.isEmpty() || canvasWidth <= 0f) return
        val radius = minOf(canvasWidth, canvasHeight) * 0.35f
        val cx = canvasWidth / 2f
        val cy = canvasHeight / 2f
        val (sIdx, dimIdx) = PolarScale.hitRadarPoint(
            cx, cy, radius, dimensions, series, x, y,
        ) ?: return
        val label = dimensions.getOrNull(dimIdx)?.label
        val value = series.getOrNull(sIdx)?.values?.getOrNull(dimIdx)
        val seriesName = series.getOrNull(sIdx)?.name.orEmpty()
        selection = ChartSelection.Radar(sIdx, dimIdx, label)
        event.onSelectionChange?.invoke(selection)
        event.onRadarClick?.invoke(sIdx, dimIdx, label)
        tooltipText = "$seriesName · ${label.orEmpty()}: ${value ?: "-"}"
        val tip = resolveTooltipPosition(canvasOffsetX, canvasOffsetY, x, y)
        tooltipX = tip.first
        tooltipY = tip.second
        showTooltip = true
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
                    vfor({ ctx.seriesProvider() }) { s ->
                        View {
                            attr {
                                flexDirection(FlexDirection.ROW)
                                alignItems(FlexAlign.CENTER)
                                marginRight(12f)
                                marginBottom(4f)
                                if (ctx.attr.legend.interactive) {
                                    val hidden = ctx.hiddenSeriesNames.contains(s.name)
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
                                    backgroundColor(s.color.toChartColor())
                                    marginRight(4f)
                                }
                            }
                            Text {
                                attr {
                                    text(s.name)
                                    fontSize(ctx.attr.theme.fontSize)
                                    color(ctx.attr.theme.textColor.toChartColor())
                                }
                            }
                            if (ctx.attr.legend.interactive) {
                                event {
                                    click { ctx.toggleSeries(s.name) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggleSeries(name: String) {
        hiddenSeriesNames = if (hiddenSeriesNames.contains(name)) {
            hiddenSeriesNames - name
        } else {
            hiddenSeriesNames + name
        }
        selection = null
        showTooltip = false
        event.onSelectionChange?.invoke(null)
    }

    override fun renderOverlay(parent: ViewContainer<*, *>) {
        val ctx = this
        parent.apply {
            vif({ ctx.showTooltip }) {
                View {
                    attr {
                        positionAbsolute()
                        left(ctx.tooltipX)
                        top(ctx.tooltipY)
                        backgroundColor(Color(0xD9000000))
                        borderRadius(4f)
                        padding(6f, 8f, 6f, 8f)
                    }
                    Text {
                        attr {
                            text(ctx.tooltipText)
                            fontSize(11f)
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }
}

fun ViewContainer<*, *>.RadarChart(
    seriesProvider: () -> ObservableList<RadarSeries>,
    init: RadarChartView.() -> Unit,
) {
    addChild(RadarChartView(seriesProvider), init)
}
