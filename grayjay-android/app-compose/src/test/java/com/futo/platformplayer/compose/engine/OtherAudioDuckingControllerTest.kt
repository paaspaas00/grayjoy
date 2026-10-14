package com.futo.platformplayer.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherAudioDuckingControllerTest {
    @Test fun assistantDrainDoesNotApplyASecondReduction() {
        val media = android.media.AudioAttributes.USAGE_MEDIA
        val assistant = android.media.AudioAttributes.USAGE_ASSISTANT
        assertFalse(shouldDuckForPlaybackUsages(listOf(media, assistant)))
        assertTrue(shouldDuckForPlaybackUsages(listOf(media, assistant, media)))
        assertEquals(.5f, effectivePlaybackVolume(.5f, true, .35f), .001f)
        assertEquals(.35f, effectivePlaybackVolume(null, true, .35f), .001f)
        assertEquals(1f, effectivePlaybackVolume(null, false, .35f), .001f)
    }
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
