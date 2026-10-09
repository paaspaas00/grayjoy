package com.futo.platformplayer.compose.diagnostics

import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DiagnosticTextTest {
    @Test fun largeCyclicExceptionsStayBoundedWithoutDiscardingTheRootCause() {
        val root = IllegalStateException("Original failure")
        val cause = IllegalArgumentException("Underlying failure")
        root.initCause(cause)
        cause.initCause(root)
        cause.stackTrace = Array(20_000) { StackTraceElement("Example", "run", "Example.kt", it) }
        val text = boundedStackTrace(root, 4096)
        assertTrue(text.startsWith("java.lang.IllegalStateException: Original failure"))
        assertEquals(4096, text.length)
        assertEquals("", boundedStackTrace(root, 0))
    }

    @Test fun systemTraceReaderConsumesOnlyItsBudget() {
        val reader = StringReader("a".repeat(100_000))
        assertEquals(4096, reader.readDiagnosticText(4096).length)
        assertEquals('a'.code, reader.read())
    }

    @Test fun blockedCrashCleanupDoesNotPreventAndroidHandlerFromRunning() {
        val gate = CountDownLatch(1)
        val completed = CountDownLatch(1)
        try {
            assertFalse(runCrashHookWithDeadline({ gate.await(); completed.countDown() }, 10L))
        } finally { gate.countDown() }
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertTrue(runCrashHookWithDeadline({ error("Cleanup also failed") }, 1000L))
    }
}
