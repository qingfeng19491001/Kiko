package com.kuikly.stockchat.app

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuiklybase.kline.view.KLineChart

/**
 * OHOS 实现：使用 KuiklyKLineChart 的 ohosArm64 KMP 层和 ArkTS 原生 Host。
 */
actual fun ViewContainer<*, *>.PlatformKLineChart(
    flexValue: Float,
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
    onError: (code: String, message: String) -> Unit,
    onPaneLayoutChange: (priceTop: Float, firstTop: Float, secondTop: Float) -> Unit,
    onPaneHeaderClick: (paneId: String) -> Unit,
) {
    KLineChart {
        attr {
            flex(flexValue)
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
        }
    }
}
