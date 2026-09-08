package com.kuikly.stockchat.data.ai

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/** Android 原生能力所需的宿主引用，由 Kuikly Activity 在生命周期内注入。 */
object AndroidPlatformContext {
    private var activityRef = WeakReference<Activity>(null)
    lateinit var applicationContext: Context
        private set

    fun bind(activity: Activity) {
        applicationContext = activity.applicationContext
        activityRef = WeakReference(activity)
    }

    fun activity(): Activity? = activityRef.get()
}
