package com.futo.platformplayer.compose

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.diagnostics.CrashLogStore
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ResilientPreferencesTest {
    private fun isolated(run: (Context, SharedPreferences) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "preferences-fixture-${UUID.randomUUID()}"
        val stored = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences = stored
        }
        try { run(context, stored) } finally { app.deleteSharedPreferences(name) }
    }

    @Test fun wrongPrimitiveTypesUseDefaultsWithoutModifyingTheirStoredValues() = isolated { _, stored ->
        assertTrue(stored.edit().putString("boolean", "broken").putString("integer", "broken")
            .putString("long", "broken").putString("float", "broken").putInt("string", 7)
            .putBoolean("set", true).commit())
        val before = stored.all
        val safe = ResilientPreferences(stored)
        assertTrue(safe.getBoolean("boolean", true))
        assertEquals(12, safe.getInt("integer", 12))
        assertEquals(13L, safe.getLong("long", 13L))
        assertEquals(1.5f, safe.getFloat("float", 1.5f), 0f)
        assertEquals("default", safe.getString("string", "default"))
        assertEquals(setOf("default"), safe.getStringSet("set", mutableSetOf("default")))
        assertEquals(before, stored.all)
    }

    @Test fun startupSettingsFallbackAndPrivacyFailClosedPreserveRestoredData() = isolated { context, stored ->
        assertTrue(stored.edit().putString("dynamic_colors_enabled", "bad")
            .putInt("preferred_audio_language", 7).putBoolean("preferred_video_quality", true)
            .putString("default_playback_speed", "fast").putString("private_session_enabled", "unknown")
            .putInt("followed_creator_ids", 99).putString("following_initialized", "unknown").commit())
        val before = stored.all
        val settings = GrayjayPreferences(context)
        assertTrue(settings.dynamicColorsEnabled)
        assertEquals("en", settings.preferredAudioLanguage)
        assertEquals(0, settings.preferredVideoQuality)
        assertEquals(1f, settings.defaultPlaybackSpeed, 0f)
        assertTrue("Malformed privacy flag must not enable recording history", settings.privateSessionEnabled)
        assertTrue(settings.initializeFollowedCreators(setOf("sample")).isEmpty())
        assertEquals(before, stored.all)
        assertTrue(stored.edit().remove("private_session_enabled").commit())
        assertFalse(settings.privateSessionEnabled)
        assertTrue(stored.edit().putBoolean("private_session_enabled", false).commit())
        assertFalse(settings.privateSessionEnabled)
    }

    @Test fun malformedDiagnosticsFlagCannotCrashTheStartupGuardOrCrashHandler() = isolated { context, stored ->
        for (write in listOf<(SharedPreferences.Editor) -> Unit>(
            { it.putString("crash_logging_enabled", "bad") },
            { it.putInt("crash_logging_enabled", 1) },
            { it.putStringSet("crash_logging_enabled", setOf("true")) },
        )) {
            val editor = stored.edit()
            write(editor)
            assertTrue(editor.commit())
            val before = stored.all
            assertFalse(CrashLogStore.isEnabled(context))
            assertEquals(before, stored.all)
        }
        assertTrue(stored.edit().putBoolean("crash_logging_enabled", true).commit())
        assertTrue(CrashLogStore.isEnabled(context))
    }

    @Test fun deletedProfileCannotBeInitializedEvenWithPreviouslyInitializedSubscriptions() = isolated { context, stored ->
        assertTrue(stored.edit().putStringSet("deleted_profile_ids", setOf("deleted-fixture"))
            .putBoolean("following_initialized", true).putStringSet("followed_creator_ids", setOf("original")).commit())
        val before = stored.all
        val error = runCatching { GrayjayPreferences(context, "deleted-fixture").initializeFollowedCreators(setOf("new")) }.exceptionOrNull()
        assertTrue(error is com.futo.platformplayer.compose.data.DeletedProfileException)
        assertEquals(before, stored.all)
    }
}
