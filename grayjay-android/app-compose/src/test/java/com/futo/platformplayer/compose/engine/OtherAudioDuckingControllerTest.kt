package com.futo.platformplayer.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherAudioDuckingControllerTest {
    @Test
    fun `one active player is treated as Grayjoy alone`() {
        assertFalse(shouldDuckForActivePlaybackCount(0))
        assertFalse(shouldDuckForActivePlaybackCount(1))
    }

    @Test
    fun `additional active player requests ducking`() {
        assertTrue(shouldDuckForActivePlaybackCount(2))
        assertTrue(shouldDuckForActivePlaybackCount(5))
    }

    @Test
    fun `duck percentage remains in a safe audible range`() {
        assertEquals(10, clampDuckVolumePercent(-1))
        assertEquals(35, clampDuckVolumePercent(35))
        assertEquals(80, clampDuckVolumePercent(100))
    }
}
