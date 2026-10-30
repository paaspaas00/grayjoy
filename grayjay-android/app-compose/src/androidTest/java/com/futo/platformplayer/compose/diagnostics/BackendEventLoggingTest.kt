package com.futo.platformplayer.compose.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.errors.BackendDiagnostics
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class BackendEventLoggingTest {
    @Test fun fallbackTimelineIsSavedEvenWithoutAnyUiNotice(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(CrashLogStore.isEnabled(context))
        CrashLogStore.install(context)
        val failure = IllegalStateException("TEST_ONLY LOGIN_REQUIRED: Sign in to confirm that you are not a bot. Cookie: TEST_CREDENTIAL")
        BackendDiagnostics.fallback("playback", "NewPipe", "Grayjay", failure, "attempt")
        BackendDiagnostics.fallback("playback", "NewPipe", "Grayjay", failure, "recovered")
        val archive = CrashLogStore.createShareArchive(context)
        try {
            val privateRecords = DiagnosticEventRing(File(context.filesDir, "diagnostics/events")).snapshot().mapNotNull {
                runCatching { JSONObject(it) }.getOrNull()
            }.filter { it.optString("details").contains("TEST_ONLY") }
            assertTrue(privateRecords.size >= 2)
            assertTrue(privateRecords.any { it.optString("phase") == "attempt" })
            assertTrue(privateRecords.any { it.optString("phase") == "recovered" })
            privateRecords.forEach { record ->
                assertTrue(record.optString("time").contains("T"))
                assertTrue(record.optString("details").contains("LOGIN_REQUIRED"))
                assertFalse(record.toString().contains("TEST_CREDENTIAL"))
                assertEquals("YoutubeCheck", record.optString("reason"))
            }
            val shared = ZipFile(archive).use { zip -> zip.getInputStream(zip.getEntry("events.jsonl")).bufferedReader().use { it.readText() } }
            assertTrue(shared.contains("YoutubeCheck")); assertTrue(shared.contains("NewPipe")); assertTrue(shared.contains("recovered"))
            assertFalse(shared.contains("TEST_ONLY")); assertFalse(shared.contains("TEST_CREDENTIAL"))
        } finally { archive.delete() }
    }
}
