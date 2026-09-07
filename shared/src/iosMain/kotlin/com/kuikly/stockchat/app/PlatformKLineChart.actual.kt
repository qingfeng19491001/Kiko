package com.kuikly.stockchat.app

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuiklybase.kline.view.KLineChart

/**
 * iOS 实现：直接调用 KuiklyKLineChart KMP 模块提供的扩展函数。
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
    onError: (code: String, message: String) -> Unit,
) {
    KLineChart {
        attr {
            flex(flexValue)
            symbol(symbolCode, symbolName)
            period(periodValue, periodUnit)
            mode(modeName)
            theme(themeName)
            bars(barsJson)
            config(configJson)
        }
        event {
            onError(onError)
        }
    }
}
