package com.kuikly.stockchat.android.adapter

import android.util.Log
import com.kuikly.stockchat.android.BuildConfig
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter

object KRUncaughtExceptionHandlerAdapter : IKRUncaughtExceptionHandlerAdapter {

    private const val TAG = "KRExceptionHandler"

    override fun uncaughtException(throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            throw throwable
        } else {
            Log.e(TAG, "Kuikly error: ${throwable.stackTraceToString()}")
        }
    }
}
