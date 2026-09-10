package com.kuikly.stockchat.app

import com.tencent.kuikly.core.base.ViewContainer

/**
 * 跨端 K 线图渲染。
 *
 * - Android / iOS：调用 `com.tencent.kuiklybase.kline.view.KLineChart` 原生 View
 * - OHOS（鸿蒙）：KLine 模块未提供 ohos_arm64 klib，渲染一张占位图
 *
 * 参数与 KuiklyKLineChart 完整 Demo 对齐：priceStyleName 控制蜡烛/折线，
 * onPaneLayoutChange / onPaneHeaderClick 支撑 pane 头部的指标切换菜单。
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
    priceStyleName: String,
    touchEnabled: Boolean,
    onError: (code: String, message: String) -> Unit,
    onPaneLayoutChange: (priceTop: Float, firstTop: Float, secondTop: Float) -> Unit,
    onPaneHeaderClick: (paneId: String) -> Unit,
)
