package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PcPlaybackPositionTest {
    @Test
    fun playingPositionAdvancesFromTheLastPcUpdate() {
        assertEquals(
            32_500L,
            estimatedPcPlaybackPosition(
                positionMs = 30_000L,
                durationMs = 120_000L,
                isPlaying = true,
                receivedAtMs = 10_000L,
                nowMs = 12_500L,
            ),
        )
    }

    @Test
    fun pausedPositionDoesNotAdvance() {
        assertEquals(
            30_000L,
            estimatedPcPlaybackPosition(
                positionMs = 30_000L,
                durationMs = 120_000L,
                isPlaying = false,
                receivedAtMs = 10_000L,
                nowMs = 25_000L,
            ),
        )
    }

    @Test
    fun estimatedPositionNeverPassesTheDuration() {
        assertEquals(
            120_000L,
            estimatedPcPlaybackPosition(
                positionMs = 119_000L,
                durationMs = 120_000L,
                isPlaying = true,
                receivedAtMs = 10_000L,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun stalePcStateDoesNotConfirmASeek() {
        assertEquals(
            false,
            pcSeekConfirmed(
                requestedPositionMs = 31_000L,
                requestedAtMs = 20_000L,
                pcPositionMs = 30_000L,
                durationMs = 120_000L,
                isPlaying = false,
                pcReceivedAtMs = 19_000L,
                nowMs = 20_100L,
            ),
        )
    }

    @Test
    fun freshPcStateConfirmsASeekWithinTolerance() {
        assertEquals(
            true,
            pcSeekConfirmed(
                requestedPositionMs = 90_000L,
                requestedAtMs = 20_000L,
                pcPositionMs = 90_250L,
                durationMs = 120_000L,
                isPlaying = false,
                pcReceivedAtMs = 20_500L,
                nowMs = 20_500L,
            ),
        )
    }
}
