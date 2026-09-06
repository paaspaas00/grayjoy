package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.ChannelUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionsFilterTest {
    @Test
    fun subscriptionMembershipDoesNotScanEveryFollowedChannelForEveryVideo() {
        val ids = (0 until 2_000).mapTo(hashSetOf()) { "channel-$it" }
        var probes = 0
        val countedIds = object : Set<String> by ids {
            override fun contains(element: String): Boolean {
                probes++
                return ids.contains(element)
            }
            override fun iterator(): Iterator<String> =
                error("Subscription filtering must use set membership, not a nested scan")
        }
        val videos = List(10_000) { video("video-$it", authorUrl = "channel-${it % 2_000}") }
        assertEquals(10_000, videosForFollowedCreators(videos, countedIds).size)
        assertEquals(10_000, probes)
    }

    @Test
    fun matchesSubscriptionsAcrossPluginCreatorIdentifiers() {
        val videos = listOf(
            video("author", authorUrl = "creator-url"),
            video("channel", channelId = "channel-id"),
            video("fallback", creator = "Creator"),
            video("other", creator = "Other"),
        )

        val result = videosForFollowedCreators(
            videos,
            setOf("creator-url", "channel-id", "youtube:Creator"),
        )

        assertEquals(listOf("author", "channel", "fallback"), result.map(VideoUiModel::id))
    }

    @Test
    fun uploadFilterKeepsOnlyTheSelectedChannel() {
        val videos = listOf(
            video("new-1", authorUrl = "creator-url"),
            video("new-2", authorUrl = "creator-url"),
            video("other", authorUrl = "someone-else"),
        )

        assertEquals(videos, uploadsFromChannel(videos, null))
        assertEquals(
            listOf("new-1", "new-2"),
            uploadsFromChannel(videos, "creator-url").map(VideoUiModel::id),
        )
    }

    @Test
    fun managementSearchSourceAndSortComposePredictably() {
        val channels = listOf(
            channel("z", "Zulu", "youtube", "YouTube"),
            channel("a", "Alpha", "nebula", "Nebula"),
            channel("b", "Beta", "youtube", "YouTube"),
        )

        assertEquals(
            listOf("a", "b", "z"),
            managedFollowedChannels(channels, "", null, ascending = true)
                .map(ChannelUiModel::id),
        )
        assertEquals(
            listOf("z", "b"),
            managedFollowedChannels(channels, "", "youtube", ascending = false)
                .map(ChannelUiModel::id),
        )
        assertEquals(
            listOf("a"),
            managedFollowedChannels(channels, "neb", null, ascending = true)
                .map(ChannelUiModel::id),
        )
    }

    private fun video(
        id: String,
        authorUrl: String = "",
        channelId: String = "",
        creator: String = id,
    ) = VideoUiModel(
        id = id,
        title = id,
        creator = creator,
        metadata = "",
        duration = "1:00",
        sourceId = "youtube",
        authorUrl = authorUrl,
        channelId = channelId,
    )

    private fun channel(
        id: String,
        name: String,
        sourceId: String,
        source: String,
    ) = ChannelUiModel(
        id = id,
        name = name,
        sourceId = sourceId,
        source = source,
        unreadCount = 0,
        followerCount = "",
        description = "",
    )
}
