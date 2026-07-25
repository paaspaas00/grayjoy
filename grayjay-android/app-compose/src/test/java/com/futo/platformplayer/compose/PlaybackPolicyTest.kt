package com.futo.platformplayer.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun perVideoSpeedWinsOverChannelAndAppDefaults() {
        assertEquals(
            1.5f,
            resolvedPlaybackSpeed(
                videoId = "video",
                channelId = "channel",
                defaultSpeed = 1f,
                perChannelEnabled = true,
                videoSpeeds = mapOf("video" to 1.5f),
                channelSpeeds = mapOf("channel" to 1.25f),
            ),
        )
    }

    @Test
    fun channelSpeedWinsWhenVideoHasNoOverride() {
        assertEquals(
            1.25f,
            resolvedPlaybackSpeed(
                videoId = "video",
                channelId = "channel",
                defaultSpeed = 1f,
                perChannelEnabled = true,
                videoSpeeds = emptyMap(),
                channelSpeeds = mapOf("channel" to 1.25f),
            ),
        )
    }

    @Test
    fun disablingChannelSpeedsFallsBackToAppDefault() {
        assertEquals(
            1f,
            resolvedPlaybackSpeed(
                videoId = "video",
                channelId = "channel",
                defaultSpeed = 1f,
                perChannelEnabled = false,
                videoSpeeds = emptyMap(),
                channelSpeeds = mapOf("channel" to 2f),
            ),
        )
    }

    @Test
    fun queueSelectionDropsDuplicatesAndAlreadyQueuedVideos() {
        assertEquals(
            listOf("third", "fourth"),
            unqueuedVideoIds(
                requestedVideoIds = listOf("first", "third", "third", "fourth"),
                activeQueueVideoIds = listOf("first"),
                knownQueueVideoIds = listOf("second"),
            ),
        )
    }
}
