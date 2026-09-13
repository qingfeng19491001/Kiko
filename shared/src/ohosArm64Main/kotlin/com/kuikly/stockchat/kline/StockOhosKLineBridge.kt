package com.kuikly.stockchat.kline

import com.tencent.kuiklybase.kline.host.OhosKLineChartBridge

/**
 * 把 K 线桥导出到 libshared C API，供鸿蒙 NAPI / KRKLineChart 调用。
 */
class StockOhosKLineBridge {
    private val host = OhosKLineChartBridge()

    fun setProp(key: String, value: String): Boolean = host.setProp(key, value)

    fun call(method: String, params: String): String = host.call(method, params)

    fun resize(width: Int, height: Int) = host.resize(width, height)

    fun pointer(kind: String, x: Double, y: Double, scale: Double, count: Int) =
        host.pointer(kind, x, y, scale, count)

    fun renderCommands(): String = host.renderCommands()

    fun pollEvents(): String = host.pollEvents()

    fun dispose() = host.dispose()
}
