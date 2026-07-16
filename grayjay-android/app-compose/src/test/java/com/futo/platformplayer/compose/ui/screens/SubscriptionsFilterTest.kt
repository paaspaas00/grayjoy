package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.VideoUiModel
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
