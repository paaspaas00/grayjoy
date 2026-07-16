package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRepositoryTest {
    @Test
    fun visibleContentContainsOnlyEnabledSourcesAndPrunesPlaylists() {
        val videos = listOf(video("youtube-video", "youtube"), video("odysee-video", "odysee"))
        val channels = listOf(channel("youtube-channel", "youtube"), channel("odysee-channel", "odysee"))
        val playlists = listOf(
            PlaylistUiModel(
                id = "mixed",
                title = "Mixed",
                description = "",
                videoIds = videos.map(VideoUiModel::id),
            ),
        )

        val visible = visibleContentForSources(
            videos = videos,
            channels = channels,
            playlists = playlists,
            enabledSourceIds = setOf("odysee"),
        )

        assertEquals(listOf("odysee-video"), visible.videos.map(VideoUiModel::id))
        assertEquals(listOf("odysee-channel"), visible.channels.map(ChannelUiModel::id))
        assertEquals(listOf("odysee-video"), visible.playlists.single().videoIds)
    }

    private fun video(id: String, sourceId: String) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "Now",
        duration = "1:00",
        sourceId = sourceId,
    )

    private fun channel(id: String, sourceId: String) = ChannelUiModel(
        id = id,
        name = id,
        sourceId = sourceId,
        source = sourceId,
        unreadCount = 0,
        followerCount = "0 followers",
        description = "",
    )
}
