package com.kuikly.stockchat.kline

import com.tencent.kuiklybase.kline.host.IOSKLineChartBridge
import com.tencent.kuiklybase.kline.host.canvas.KLineCanvasAdapter
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("KLCCanvasAdapter", exact = true)
interface KLCCanvasAdapter : KLineCanvasAdapter

@Suppress("unused")
fun iosKLineBridgeExport(
    onInvalidate: () -> Unit,
    onEvent: (String, Map<String, Any?>) -> Unit,
): IOSKLineChartBridge = IOSKLineChartBridge(onInvalidate, onEvent)
