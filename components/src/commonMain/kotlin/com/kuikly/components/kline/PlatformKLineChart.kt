package com.kuikly.components.kline

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuiklybase.kline.view.KLineChart

/**
 * 对 KuiklyKLineChart 的一层收口。周期栏、指标菜单、行情头仍由业务页实现。
 * 使用明确高度，避免 iOS 双层 flex 撑不满。
 */
fun ViewContainer<*, *>.PlatformKLineChart(
    height: Float,
    symbolCode: String,
    symbolName: String,
    periodValue: Int,
    periodUnit: String,
    modeName: String,
    themeName: String,
    barsJson: String,
    configJson: String,
    priceStyleName: String,
    touchEnabled: Boolean,
    onError: (code: String, message: String) -> Unit = { _, _ -> },
    onPaneLayoutChange: (priceTop: Float, firstTop: Float, secondTop: Float) -> Unit = { _, _, _ -> },
    onPaneHeaderClick: (paneId: String) -> Unit = {},
    onBarClick: (timestamp: Long, index: Int) -> Unit = { _, _ -> },
    onCrosshairChange: (timestamp: Long?, price: Double?) -> Unit = { _, _ -> },
) {
    KLineChart {
        attr {
            height(height)
            symbol(symbolCode, symbolName)
            period(periodValue, periodUnit)
            mode(modeName)
            theme(themeName)
            priceStyle(priceStyleName)
            bars(barsJson)
            config(configJson)
            touchEnable(touchEnabled)
        }
        event {
            onError(onError)
            onPaneLayoutChange(onPaneLayoutChange)
            onPaneHeaderClick(onPaneHeaderClick)
            onBarClick(onBarClick)
            onCrosshairChange(onCrosshairChange)
        }
    }
}
