package com.futo.platformplayer.compose

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AppLanguagePreferenceTest {
    @Test fun wrongTypedRestoredLanguageDoesNotCrashApplicationContextCreation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "language-fixture-${UUID.randomUUID()}"
        val preferences = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences = preferences
        }
        try {
            val writers = listOf<(SharedPreferences.Editor) -> Unit>(
                { it.putBoolean("ui_language_tag", true) },
                { it.putInt("ui_language_tag", 1) },
                { it.putLong("ui_language_tag", 2L) },
                { it.putFloat("ui_language_tag", 3f) },
                { it.putStringSet("ui_language_tag", setOf("it")) },
            )
            for (write in writers) {
                val editor = preferences.edit()
                write(editor)
                assertTrue(editor.commit())
                val original = preferences.all["ui_language_tag"]
                assertEquals(AppLanguageManager.SYSTEM_LANGUAGE_TAG, AppLanguageManager.selectedLanguageTag(context))
                assertSame(context, AppLanguageManager.localizedContext(context))
                assertEquals("Fallback must not rewrite restored preferences", original, preferences.all["ui_language_tag"])
            }
            assertTrue(preferences.edit().putString("ui_language_tag", "it-IT").commit())
            assertEquals("it", AppLanguageManager.selectedLanguageTag(context))
            assertEquals("it", AppLanguageManager.localizedContext(context).resources.configuration.locales[0].language)
        } finally { app.deleteSharedPreferences(name) }
    }
}
