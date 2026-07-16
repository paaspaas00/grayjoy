package com.futo.platformplayer.compose

import android.view.OrientationEventListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityOrientationTest {
    @Test
    fun `landscape sensor ranges select fullscreen posture`() {
        listOf(60, 90, 120, 240, 270, 300).forEach { degrees ->
            assertEquals(true, physicalLandscapeAt(degrees))
        }
    }

    @Test
    fun `upright and reverse portrait ranges select portrait posture`() {
        listOf(0, 30, 150, 180, 210, 330, 359).forEach { degrees ->
            assertEquals(false, physicalLandscapeAt(degrees))
        }
    }

    @Test
    fun `diagonal and unknown readings preserve the previous posture`() {
        listOf(31, 45, 59, 121, 225, 301, 329).forEach { degrees ->
            assertNull(physicalLandscapeAt(degrees))
        }
        assertNull(physicalLandscapeAt(OrientationEventListener.ORIENTATION_UNKNOWN))
    }

    @Test
    fun `fullscreen posture ignores the sensor when Android auto rotate is disabled`() {
        assertEquals(false, automaticFullscreenPosture(autoRotateEnabled = false, orientation = 90))
        assertEquals(true, automaticFullscreenPosture(autoRotateEnabled = true, orientation = 90))
    }
}
