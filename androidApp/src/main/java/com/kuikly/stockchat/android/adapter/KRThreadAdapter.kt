package com.kuikly.stockchat.android.adapter

import com.tencent.kuikly.core.render.android.adapter.IKRThreadAdapter
import java.util.concurrent.Executors

class KRThreadAdapter : IKRThreadAdapter {
    override fun executeOnSubThread(task: () -> Unit) {
        execOnSubThread(task)
    }
}

private val subThreadPool by lazy { Executors.newFixedThreadPool(2) }

fun execOnSubThread(task: () -> Unit) {
    subThreadPool.execute(task)
}
