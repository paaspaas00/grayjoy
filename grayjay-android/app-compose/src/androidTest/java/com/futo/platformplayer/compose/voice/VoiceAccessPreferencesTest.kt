package com.futo.platformplayer.compose.voice

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VoiceAccessPreferencesTest {
    @Test fun malformedAuthorizationOrRevisionFailsClosedWithoutRewritingTheSource() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "voice-access-fixture-${UUID.randomUUID()}"
        val stored = app.getSharedPreferences(name, 0)
        try {
            assertTrue(stored.edit().putBoolean("enabled", true).putString("revision", "bad").commit())
            val before = stored.all
            assertFalse(readVoiceAccessState(stored).enabled)
            assertEquals(before, stored.all)
            assertTrue(stored.edit().putLong("revision", 7L).commit())
            assertEquals(VoiceAccessState(true, 7L), readVoiceAccessState(stored))
            assertTrue(stored.edit().putInt("enabled", 1).commit())
            assertFalse(readVoiceAccessState(stored).enabled)
            assertEquals(1, stored.getInt("enabled", 0))
        } finally { app.deleteSharedPreferences(name) }
    }
}
