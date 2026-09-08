package com.kuikly.stockchat.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawerPhysicsTest {
    @Test fun closingFlingWinsEvenAboveHalfway() {
        assertEquals(0f, DrawerPhysics.target(0.8f, -900f))
        assertEquals(1f, DrawerPhysics.target(0.2f, 900f))
        assertEquals(0f, DrawerPhysics.target(0.5f, 0f))
        assertEquals(1f, DrawerPhysics.target(0.51f, 0f))
    }

    @Test fun dragContinuesFromInterruptedPositionAndCanReverse() {
        assertEquals(0.5f, DrawerPhysics.progress(0.7f, -60f, 300f), 0.0001f)
        assertEquals(0.8f, DrawerPhysics.progress(0.7f, 30f, 300f), 0.0001f)
        assertEquals(0f, DrawerPhysics.progress(0.7f, -1000f, 300f))
        assertEquals(1f, DrawerPhysics.progress(0.7f, 1000f, 300f))
    }

    @Test fun bezierUsesTimeCoordinateAndNeverOvershoots() {
        assertEquals(0f, DrawerPhysics.ease(0f))
        assertEquals(1f, DrawerPhysics.ease(1f))
        // At parameter t=.5, x=.245 and y=.77.
        assertEquals(0.77f, DrawerPhysics.ease(0.245f), 0.00001f)
        var previous = 0f
        for (i in 0..1000) {
            val current = DrawerPhysics.ease(i / 1000f)
            assertTrue(current in previous..1f)
            previous = current
        }
    }
}
