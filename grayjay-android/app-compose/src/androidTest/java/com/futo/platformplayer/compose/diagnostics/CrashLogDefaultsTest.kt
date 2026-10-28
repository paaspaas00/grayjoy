package com.futo.platformplayer.compose.diagnostics

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CrashLogDefaultsTest {
    @Test fun missingPreferenceEnablesLocalLogsButExplicitOptOutIsRespected() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "diagnostic-default-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        val preferences = context.getSharedPreferences("", Context.MODE_PRIVATE)
        try {
            assertTrue(CrashLogStore.isEnabled(context))
            assertFalse(CrashLogStore.isDownloadsCopyEnabled(context))
            CrashLogStore.setDownloadsCopyEnabled(context, true)
            assertTrue(CrashLogStore.isDownloadsCopyEnabled(context))
            CrashLogStore.setDownloadsCopyEnabled(context, false)
            assertFalse(CrashLogStore.isDownloadsCopyEnabled(context))
            preferences.edit().putBoolean("crash_logging_enabled", false).commit()
            assertFalse(CrashLogStore.isEnabled(context))
            preferences.edit().putBoolean("crash_logging_enabled", true).commit()
            assertTrue(CrashLogStore.isEnabled(context))
        } finally { base.deleteSharedPreferences(name) }
    }
}
