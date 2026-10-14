package com.futo.platformplayer.compose.engine

import org.junit.Assert.*
import org.junit.Test

class VoiceVolumeLeasesTest {
    @Test fun fiftyPercentIsExactAndDisconnectRestores() {
        val volumes = VoiceVolumeLeases()
        assertNull(volumes.volume)
        volumes.set("session", 50)
        assertEquals(.5f, volumes.volume!!, .00001f)
        volumes.set("session", null)
        assertNull(volumes.volume)
    }
    @Test fun oldConnectionCannotRestoreANewConnection() {
        val volumes = VoiceVolumeLeases()
        volumes.set("old", 50); volumes.set("new", 70)
        volumes.set("old", null)
        assertEquals(.7f, volumes.volume!!, .00001f)
        volumes.clear(); assertNull(volumes.volume)
    }
    @Test fun invalidLevelsCannotModifyAnExistingLease() {
        val volumes = VoiceVolumeLeases(); volumes.set("session", 50)
        listOf(-1, 101, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            assertTrue(runCatching { volumes.set("session", it) }.isFailure)
            assertEquals(.5f, volumes.volume!!, .00001f)
        }
    }
    @Test fun duplicateRestorationIsANoOpForTheOtherAudioObserver() {
        val volumes = VoiceVolumeLeases()
        assertFalse(volumes.set("missing", null))
        assertTrue(volumes.set("session", 50))
        assertFalse(volumes.set("session", 50))
        assertTrue(volumes.set("session", null))
        assertFalse(volumes.set("session", null))
    }
}
