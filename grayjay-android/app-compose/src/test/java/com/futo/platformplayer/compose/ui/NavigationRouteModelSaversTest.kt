package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class NavigationRouteModelSaversTest {
    @Test fun channelRoutingIdentitySurvivesWithoutHeavyContent() {
        val channel = ChannelUiModel("https://custom.example/creator", "Creator", "custom-source-id",
            "Custom source", 42, "12 million", "large description".repeat(5_000),
            thumbnailUrl = "https://custom.example/icon", bannerUrl = "https://custom.example/banner")
        val encoded = encodeVisitedChannels(mapOf(channel.id to channel))
        val restored = decodeVisitedChannels(encoded).getValue(channel.id)
        assertEquals(channel.id, restored.id)
        assertEquals(channel.name, restored.name)
        assertEquals(channel.sourceId, restored.sourceId)
        assertEquals(channel.source, restored.source)
        assertTrue(restored.description.isEmpty())
        assertTrue(restored.thumbnailUrl.isEmpty())
        assertTrue(restored.bannerUrl.isEmpty())
        assertTrue(encoded.sumOf(String::length) < 500)
    }

    @Test fun remotePlaylistRetainsIdentityButNeverSavesItsVideoIds() {
        val playlist = PlaylistUiModel("https://custom.example/list", "List", "description",
            (1..50_000).map { "video-$it" }, sourceId = "custom")
        val local = playlist.copy(id = "local", sourceId = "")
        val encoded = encodeVisitedPlaylists(mapOf(playlist.id to playlist, local.id to local))
        val restored = decodeVisitedPlaylists(encoded)
        assertEquals(setOf(playlist.id), restored.keys)
        assertEquals(playlist.sourceId, restored.getValue(playlist.id).sourceId)
        assertTrue(restored.getValue(playlist.id).videoIds.isEmpty())
        assertEquals(3, encoded.size)
    }

    @Test fun manyLongRoutesRemainWithinTheSavedStateBudgetWithoutChangingIds() {
        val channels = (1..500).associate { index ->
            val id = "https://custom.example/" + "$index-" + "x".repeat(1_000)
            id to ChannelUiModel(id, "Name".repeat(100), "custom", "Source", 0, "", "")
        }
        val encoded = encodeVisitedChannels(channels)
        assertTrue(encoded.sumOf(String::length) <= 16_384)
        assertTrue(encoded.size <= 42 * 4)
        assertTrue(decodeVisitedChannels(encoded).keys.all(channels::containsKey))
        val tooLong = ChannelUiModel("x".repeat(4_097), "Name", "custom", "Source", 0, "", "")
        assertTrue(encodeVisitedChannels(mapOf(tooLong.id to tooLong)).isEmpty())
    }
}
