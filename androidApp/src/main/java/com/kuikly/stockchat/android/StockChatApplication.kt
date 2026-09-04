package com.kuikly.stockchat.android

import android.app.Application

class StockChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Application
            private set
    }
}
