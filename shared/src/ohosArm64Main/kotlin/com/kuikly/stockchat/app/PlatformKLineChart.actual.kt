package com.kuikly.stockchat.app

import com.tencent.kuikly.core.base.ViewContainer

/**
 * OHOS 占位实现：KLine 模块未提供 ohos_arm64 klib，因此用占位图替代。
 * 占位图后续可替换为 ArkUI 原生 Canvas + WebSocket 自绘 K 线。
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
    // 鸿蒙暂无 K 线原生 View：直接用占位组件，并在控制台打印 OHOS 端不支持的提示。
    ChartPlaceholder(
        text = "K 线图（OHOS 端暂以占位呈现）\n$symbolCode · $symbolName",
        loading = false,
    )
    onError("OhosUnsupported", "KLineChart native view is not available on OHOS, using placeholder")
}
