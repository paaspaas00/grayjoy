package com.futo.platformplayer.compose.downloads

import org.junit.Assert.*
import org.junit.Test

class MediaExportProgressTest {
    @Test fun unknownAndInvalidEncoderReportsCannotRegressOrPoisonProgress() {
        val tracker = MediaExportItemProgressTracker()
        var previous = 0f
        for (value in listOf(null, -4f, 0f, 0.8f, null, Float.NaN, Float.POSITIVE_INFINITY, 0.2f, 1f, 4f)) {
            val result = tracker.update(MediaExportStage.Converting, value)
            val fraction = MediaExportProgress(0, 1, "test", MediaExportStage.Converting, result).fraction
            assertTrue(fraction.isFinite())
            assertTrue(fraction >= previous)
            previous = fraction
        }
        assertEquals(0.9f, previous, 0.00001f)
        assertEquals(0f, tracker.update(MediaExportStage.Copying, 0f)!!, 0f)
    }

    @Test fun invalidProgressValuesAlwaysProduceABoundedFiniteFraction() {
        for (completed in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            for (total in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
                for (value in listOf(null, -1f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 2f)) {
                    for (stage in MediaExportStage.entries) {
                        val fraction = MediaExportProgress(completed, total, "test", stage, value).fraction
                        assertTrue(fraction.isFinite())
                        assertTrue(fraction in 0f..1f)
                    }
                }
            }
        }
    }

    @Test fun conversionAndCopyStayMonotonicAcrossTheWholeBatch() {
        var previous = 0f
        repeat(4) { item ->
            for (stage in MediaExportStage.entries) {
                for (step in 0..100) {
                    val fraction = MediaExportProgress(item, 4, "test", stage, step / 100f).fraction
                    assertTrue(fraction >= previous)
                    previous = fraction
                }
            }
        }
        assertEquals(1f, previous, 0.00001f)
    }
}
