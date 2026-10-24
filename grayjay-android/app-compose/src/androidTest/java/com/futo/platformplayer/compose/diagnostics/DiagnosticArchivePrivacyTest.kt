package com.futo.platformplayer.compose.diagnostics

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class DiagnosticArchivePrivacyTest {
    @Test fun exportedZipContainsOnlySanitizedTechnicalRecords(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "diagnostic-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        try {
            val logs = File(context.filesDir, "diagnostics").apply { mkdirs() }
            File(logs, "fixture.txt").writeText("Version: 4.1.0 (4100)\njava.lang.IllegalStateException: COOKIE_SECRET\n" +
                " at Example.play(Player.kt:42)\nhttps://private.test/token\nuser@example.test\n")
            DiagnosticEventRing(File(logs, "events")).append(org.json.JSONObject()
                .put("time", "2026-10-24T12:00:00Z").put("component", "Playback").put("level", "error")
                .put("trace", "java.lang.IllegalArgumentException: COOKIE_SECRET\n at Example.run(Player.kt:10)")
                .put("message", "user@example.test").toString())
            val archive = CrashLogStore.createShareArchive(context)
            val contents = ZipFile(archive).use { zip -> zip.entries().asSequence().joinToString("\n") { entry ->
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            } }
            assertTrue(contents.contains("Player.kt:42")); assertTrue(contents.contains("Player.kt:10"))
            assertFalse(contents.contains("COOKIE_SECRET")); assertFalse(contents.contains("user@")); assertFalse(contents.contains("https://private"))
        } finally { root.deleteRecursively() }
    }
}
