package com.kuikly.stockchat.app

import com.tencent.kuikly.core.base.ViewContainer

/**
 * 跨端 K 线图渲染。
 *
 * - Android / iOS：调用 `com.tencent.kuiklybase.kline.view.KLineChart` 原生 View
 * - OHOS（鸿蒙）：KLine 模块未提供 ohos_arm64 klib，渲染一张占位图
 */
expect fun ViewContainer<*, *>.PlatformKLineChart(
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
)
