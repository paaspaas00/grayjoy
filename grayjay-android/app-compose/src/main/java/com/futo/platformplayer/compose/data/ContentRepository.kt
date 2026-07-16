package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.previewSources

data class ContentSnapshot(
    val videos: List<VideoUiModel>,
    val channels: List<ChannelUiModel>,
    val playlists: List<PlaylistUiModel>,
    val sources: List<SourceUiModel>,
)

interface ContentRepository {
    fun snapshot(): ContentSnapshot
}

internal class LocalContentRepository : ContentRepository {
    override fun snapshot() = ContentSnapshot(
        videos = emptyList(),
        channels = emptyList(),
        playlists = emptyList(),
        sources = previewSources,
    )
}
