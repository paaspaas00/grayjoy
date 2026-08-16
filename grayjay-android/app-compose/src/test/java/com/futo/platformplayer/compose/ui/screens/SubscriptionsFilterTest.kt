package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.ChannelUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionsFilterTest {
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
    fun newBadgeCountsOnlyUnwatchedVideosForThatCreator() {
        val channel = ChannelUiModel(
            id = "creator-url",
            name = "Creator",
            sourceId = "youtube",
            source = "YouTube",
            unreadCount = 0,
            followerCount = "",
            description = "",
        )
        val videos = listOf(
            video("new-1", authorUrl = channel.id),
            video("new-2", authorUrl = channel.id),
            video("watched", authorUrl = channel.id).copy(lastWatchedAt = 1L),
            video("other", authorUrl = "someone-else"),
        )

        assertEquals(2, newVideoCountsByCreator(videos, listOf(channel))[channel.id])
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
}
