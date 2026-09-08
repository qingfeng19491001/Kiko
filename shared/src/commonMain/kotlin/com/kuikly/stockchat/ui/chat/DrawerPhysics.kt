package com.kuikly.stockchat.ui.chat

/** Pure motion rules shared by touch tracking and timed settling. */
internal object DrawerPhysics {
    fun progress(start: Float, displacement: Float, width: Float): Float =
        (start + displacement / width.coerceAtLeast(1f)).coerceIn(0f, 1f)

    fun target(progress: Float, velocity: Float): Float = when {
        velocity > 680f -> 1f
        velocity < -680f -> 0f
        progress > 0.5f -> 1f
        else -> 0f
    }

    /** Solve x(t) first: the Bezier parameter is not elapsed time. */
    fun ease(time: Float): Float {
        if (time <= 0f) return 0f
        if (time >= 1f) return 1f
        var low = 0f
        var high = 1f
        repeat(24) {
            val t = (low + high) * 0.5f
            val u = 1f - t
            val x = 3f * u * u * t * 0.32f + t * t * t
            if (x < time) low = t else high = t
        }
        val t = (low + high) * 0.5f
        val u = 1f - t
        return 3f * u * u * t * 0.72f + 3f * u * t * t + t * t * t
    }
}
