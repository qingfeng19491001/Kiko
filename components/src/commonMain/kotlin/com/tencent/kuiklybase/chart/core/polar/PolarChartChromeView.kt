package com.tencent.kuiklybase.chart.core.polar

import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.layoutFrameDidChange
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.ContextApi
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.PolarChartAttr
import com.tencent.kuiklybase.chart.core.toChartColor

/**
 * 极坐标图壳：标题 / Canvas 点击 / 图例 / 可选叠加层。
 * 饼图与雷达图共用，子类只负责绘制、命中与图例内容。
 */
abstract class PolarChartChromeView<A : PolarChartAttr, E : ComposeEvent> :
    ComposeView<A, E>() {

    protected var canvasWidth by observable(0f)
    protected var canvasHeight by observable(0f)
    protected var canvasOffsetX by observable(0f)
    protected var canvasOffsetY by observable(0f)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    flexDirection(FlexDirection.COLUMN)
                    backgroundColor(ctx.attr.theme.backgroundColor.toChartColor())
                }
                vif({ ctx.attr.title.isNotEmpty() }) {
                    Text {
                        attr {
                            text(ctx.attr.title)
                            fontSize(16f)
                            fontWeightSemiBold()
                            color(ctx.attr.theme.textColor.toChartColor())
                            marginBottom(8f)
                            marginLeft(12f)
                        }
                    }
                }
                View {
                    attr { flex(1f) }
                    event {
                        layoutFrameDidChange { frame ->
                            ctx.canvasOffsetX = frame.x
                            ctx.canvasOffsetY = frame.y
                            if (frame.width > 0f) ctx.canvasWidth = frame.width
                            if (frame.height > 0f) ctx.canvasHeight = frame.height
                        }
                        click { params ->
                            if (!ctx.attr.interaction.enableTap) return@click
                            ctx.onCanvasClick(params.x, params.y)
                        }
                    }
                    Canvas({ attr { flex(1f) } }) { context, width, height ->
                        ctx.canvasWidth = width
                        ctx.canvasHeight = height
                        ctx.drawPolar(context, width, height)
                    }
                }
                ctx.renderLegend(this)
                ctx.renderOverlay(this)
            }
        }
    }

    protected abstract fun drawPolar(context: ContextApi, width: Float, height: Float)

    protected abstract fun onCanvasClick(x: Float, y: Float)

    protected open fun renderLegend(parent: ViewContainer<*, *>) {}

    protected open fun renderOverlay(parent: ViewContainer<*, *>) {}
}
