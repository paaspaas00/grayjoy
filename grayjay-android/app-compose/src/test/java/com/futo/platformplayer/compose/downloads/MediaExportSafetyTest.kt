package com.futo.platformplayer.compose.downloads

import java.io.IOException
import java.util.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class MediaExportSafetyTest {
    @Test fun spaceFailureStopsAndAwaitsWriterCleanup() = runBlocking {
        var checks = 0
        var cleanedUp = false
        try {
            withExportStorageGuard(
                checkSpace = { if (++checks > 1) throw IOException("disk full") },
                intervalMs = 1,
            ) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        delay(5)
                        cleanedUp = true
                    }
                }
            }
            fail("Expected the storage failure")
        } catch (error: IOException) {
            assertEquals("disk full", error.message)
            assertTrue(cleanedUp)
        }
    }

    @Test fun cancellationStopsMonitoring() = runBlocking {
        var checks = 0
        val started = CompletableDeferred<Unit>()
        val job = async {
            withExportStorageGuard(checkSpace = { checks++ }, intervalMs = 1) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancelAndJoin()
        val afterCancel = checks
        delay(10)
        assertEquals(afterCancel, checks)
    }

    @Test fun initialSpaceFailureNeverStartsWriter() = runBlocking {
        var started = false
        try {
            withExportStorageGuard(checkSpace = { throw IOException("disk full") }) {
                started = true
            }
            fail("Expected the storage failure")
        } catch (_: IOException) {
            assertFalse(started)
        }
    }

    @Test fun generatedUnicodeNamesRemainValidAndWithinProviderLimits() {
        val random = Random(0xE77047)
        repeat(5_000) {
            val original = buildString {
                repeat(random.nextInt(300)) { append(random.nextInt(0x10000).toChar()) }
            }
            val name = mediaExportFileBaseName(original)
            assertTrue(name.isNotBlank())
            assertTrue(name.length <= 120)
            assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
            assertEquals(name, String(name.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
            assertFalse(name.any { it in "\\/:*?\"<>|" || Character.isISOControl(it) })
            assertFalse(name.endsWith('.') || name.endsWith(' '))
        }
        assertEquals("Grayjoy download", mediaExportFileBaseName(" . "))
        assertEquals("__", mediaExportFileBaseName("\uD800\uD800"))
    }
}
