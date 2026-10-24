package com.futo.platformplayer.compose.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiagnosticPrivacyTest {
    @Test fun acknowledgedOrRoutineExitsNeverReopenTheCrashPrompt() {
        val crash = android.app.ApplicationExitInfo.REASON_CRASH
        assertTrue(shouldPromptForHistoricalCrash(crash, 110, 100, 0))
        assertFalse(shouldPromptForHistoricalCrash(crash, 110, 100, 120))
        assertFalse(shouldPromptForHistoricalCrash(crash, 90, 100, 0))
        assertFalse(shouldPromptForHistoricalCrash(crash, 110, 0, 0))
        assertFalse(shouldPromptForHistoricalCrash(android.app.ApplicationExitInfo.REASON_USER_REQUESTED, 110, 100, 0))
        assertFalse(shouldPromptForHistoricalCrash(android.app.ApplicationExitInfo.REASON_SIGNALED, 110, 100, 0))
    }
    @Test fun messagesAndUrlsAreNeverRetainedButFramesAndCausesRemain() {
        val error = IllegalStateException("email@example.test https://private.test/?token=SECRET title Secret Song",
            IllegalArgumentException("Cookie: SECRET"))
        error.stackTrace = arrayOf(StackTraceElement("Example", "play", "Player.kt", 42))
        val safe = technicalStackTrace(error)
        assertTrue(safe.contains("Player.kt:42"))
        assertTrue(safe.contains("IllegalArgumentException"))
        for (value in listOf("SECRET", "email@", "https://", "Secret Song", "Cookie")) assertFalse(safe.contains(value))
        val legacy = "Device: unique device\nThread: email@example.test\nVersion: 4.1.0 (4100)\n" +
            "java.lang.IllegalStateException: SECRET\n at Example.play(Player.kt:42)\nURL: https://private.test"
        val cleaned = sanitizeLegacyDiagnostic(legacy)
        assertTrue(cleaned.contains("Player.kt:42")); assertTrue(cleaned.contains("IllegalStateException"))
        assertFalse(cleaned.contains("SECRET")); assertFalse(cleaned.contains("unique")); assertFalse(cleaned.contains("@"))
    }

    @Test fun cyclicAndHugeTracesHaveABoundedCost() {
        val a = IllegalStateException("secret"); val b = IllegalArgumentException("secret")
        a.initCause(b); b.initCause(a)
        a.stackTrace = Array(20_000) { StackTraceElement("Example", "play", "Example.kt", it) }
        assertTrue(technicalStackTrace(a, 800).length <= 800)
        assertEquals("", technicalStackTrace(a, 0))
    }

    @Test fun diskRingRetainsExactlyTheLastThousandAndSurvivesReopening() {
        val root = Files.createTempDirectory("grayjoy-diagnostic-test").toFile()
        try {
            val ring = DiagnosticEventRing(root)
            repeat(1505) { ring.append("event-$it") }
            val saved = DiagnosticEventRing(root).snapshot()
            assertEquals(1000, saved.size)
            assertEquals("event-505", saved.first()); assertEquals("event-1504", saved.last())
            DiagnosticEventRing(root).append("event-1505")
            assertEquals("event-1505", DiagnosticEventRing(root).snapshot().last())
            assertTrue(root.listFiles()!!.size <= 11)
        } finally { root.deleteRecursively() }
    }

    @Test fun advancedUnlockNeedsTenRecentTapsAndResetsAfterAnIdleGap() {
        val taps = AdvancedUnlockTaps()
        repeat(9) { assertEquals(9 - it, taps.tap(it * 100L)) }
        assertEquals(0, taps.tap(900))
        assertEquals(9, taps.tap(10_000))
    }
}
