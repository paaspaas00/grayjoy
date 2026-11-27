package com.futo.platformplayer.compose.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YoutubeAutoplayTest {
    @Test fun zeroDelayStartsImmediatelyWithoutShowingACountdown() = runBlocking {
        assertTrue(youtubeAutoplayCountdown({ true }, { fail("No countdown at zero") },
            { fail("No delay at zero") }, delaySeconds = 0))
        assertFalse(youtubeAutoplayCountdown({ false }, {}, {}, delaySeconds = 0))
    }
    @Test fun customTimerUsesTheConfiguredDuration() = runBlocking {
        var waited = 0L
        val ticks = mutableListOf<Int>()
        assertTrue(youtubeAutoplayCountdown({ true }, { ticks += it }, { waited += it }, delaySeconds = 3))
        assertEquals(3000L, waited)
        assertEquals(listOf(3, 2, 1), ticks)
    }
    @Test fun disabledByDefault() { assertFalse(GrayjayUiState().youtubeAutoplayEnabled) }
    @Test fun keepsYoutubeOrderAndExcludesSeenUnavailableAndNonVideos() {
        val first = VideoUiModel("one", "One", "", "", "", sourceId = "youtube")
        val items = listOf(first.copy(id = "seen"), first, first, first.copy(id = "other", sourceId = "other"),
            first.copy(id = "short", isShort = true), first.copy(id = "live", isLive = true),
            first.copy(id = "missing", isAvailable = false), first.copy(id = "future", scheduledStartAtMs = 100L), first.copy(id = "two"))
        assertEquals(listOf("one", "two"), youtubeAutoplayCandidates(items, setOf("seen"), 50L).map { it.id })
    }
    @Test fun countdownCompletesOnlyAfterTenSeconds() = runBlocking {
        var elapsed = 0L
        val ticks = mutableListOf<Int>()
        val completed = youtubeAutoplayCountdown({ true }, { ticks.add(it) }, { elapsed += it })
        assertTrue(completed)
        assertEquals(10_000L, elapsed)
        assertEquals((10 downTo 1).toList(), ticks)
    }
    @Test fun cancellationOrNewPlaybackStopsBeforeExecution() = runBlocking {
        var valid = true
        val ticks = mutableListOf<Int>()
        assertFalse(youtubeAutoplayCountdown({ valid }, { ticks.add(it) }, { valid = false }))
        assertEquals(listOf(10), ticks)
    }
    @Test fun cancellationAtLastSecondStillPreventsPlayback() = runBlocking {
        var elapsed = 0L
        assertFalse(youtubeAutoplayCountdown({ elapsed < 10_000L }, {}, { elapsed += it }))
    }
}
