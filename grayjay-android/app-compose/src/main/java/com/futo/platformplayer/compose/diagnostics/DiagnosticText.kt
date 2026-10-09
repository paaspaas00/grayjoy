package com.futo.platformplayer.compose.diagnostics

import java.io.PrintWriter
import java.io.Reader
import java.io.Writer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A failed job may own the same lock cleanup needs. Never hold Android's crash handler forever. */
internal fun runCrashHookWithDeadline(hook: () -> Unit, timeoutMs: Long = 250L): Boolean {
    val finished = CountDownLatch(1)
    Thread({
        try { hook() } catch (_: Throwable) { } finally { finished.countDown() }
    }, "grayjoy-crash-cleanup").apply { isDaemon = true }.start()
    return finished.await(timeoutMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
}

/** Stop formatting when the budget is exhausted; truncating a finished stack still allocates it. */
internal fun boundedStackTrace(error: Throwable, maxCharacters: Int): String {
    val limit = maxCharacters.coerceAtLeast(0)
    val text = StringBuilder(minOf(limit, 4096))
    val writer = object : Writer() {
        override fun write(buffer: CharArray, offset: Int, length: Int) {
            val remaining = limit - text.length
            if (length > 0 && remaining <= 0) throw DiagnosticLimitReached
            text.append(buffer, offset, minOf(remaining, length))
            if (length > remaining) throw DiagnosticLimitReached
        }
        override fun flush() = Unit
        override fun close() = Unit
    }
    try { error.printStackTrace(PrintWriter(writer)) } catch (_: DiagnosticLimitReachedException) { }
    return text.toString()
}

internal fun Reader.readDiagnosticText(maxCharacters: Int): String {
    val limit = maxCharacters.coerceAtLeast(0)
    val text = StringBuilder(minOf(limit, 4096))
    val buffer = CharArray(minOf(limit, 4096).coerceAtLeast(1))
    while (text.length < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - text.length))
        if (count < 0) break
        if (count == 0) {
            val character = read()
            if (character < 0) break
            text.append(character.toChar())
        } else text.append(buffer, 0, count)
    }
    return text.toString()
}

private open class DiagnosticLimitReachedException : RuntimeException(null, null, false, false)
private object DiagnosticLimitReached : DiagnosticLimitReachedException()
