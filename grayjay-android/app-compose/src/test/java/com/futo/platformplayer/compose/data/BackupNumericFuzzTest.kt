package com.futo.platformplayer.compose.data

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import kotlin.random.Random

class BackupNumericFuzzTest {
    @Test fun durationExportRejectsMalformedOrOverflowingValues() {
        assertEquals(0L, exportDurationSeconds("2:oops:31"))
        assertEquals(0L, exportDurationSeconds("-1:59"))
        assertEquals(0L, exportDurationSeconds("1:99"))
        assertEquals(3723L, exportDurationSeconds("1:02:03"))
        val random = Random(190926)
        repeat(20_000) {
            val hours = random.nextLong(0, Long.MAX_VALUE)
            val minutes = random.nextInt(60)
            val seconds = random.nextInt(60)
            val expected = BigInteger.valueOf(hours) * BigInteger.valueOf(3600) +
                BigInteger.valueOf((minutes * 60L) + seconds)
            val limit = BigInteger.valueOf(Long.MAX_VALUE / 1000)
            assertEquals(if (expected > limit) 0L else expected.toLong(),
                exportDurationSeconds("$hours:$minutes:$seconds"))
            val value = Float.fromBits(random.nextInt())
            val progress = safeExportProgress(value)
            assertTrue(progress.isFinite() && progress in 0.0..1.0)
            val persistedProgress = normalizedLibraryProgress(value)
            assertTrue(persistedProgress.isFinite() && persistedProgress in 0f..1f)
        }
    }
}
