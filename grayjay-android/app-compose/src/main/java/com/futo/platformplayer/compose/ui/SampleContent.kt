package com.futo.platformplayer.compose.ui

import androidx.media3.datasource.HttpDataSource

data class VideoUiModel(
    val id: String,
    val title: String,
    val creator: String,
    val metadata: String,
    val duration: String,
    val channelId: String = "",
    val sourceId: String = "youtube",
    val isLive: Boolean = false,
    val watchProgress: Float = 0f,
    val isDownloaded: Boolean = false,
    val isWatchLater: Boolean = false,
    val isLiked: Boolean = false,
    val lastWatchedAt: Long = 0L,
    val playlistNames: List<String> = emptyList(),
    val playbackUrl: String = "",
    val playbackMimeType: String = "",
    val playbackManifest: String = "",
    val audioUrl: String = "",
    val playbackRequestHeaders: Map<String, String> = emptyMap(),
    val playbackDataSourceFactory: HttpDataSource.Factory? = null,
    val contentUrl: String = "",
    val thumbnailUrl: String = "",
    val description: String = "",
    val shareUrl: String = "",
    val authorUrl: String = "",
    val authorThumbnailUrl: String = "",
    val authorSubscriberCount: Long? = null,
    val likeCount: Long? = null,
    val dislikeCount: Long? = null,
    val sourceName: String = "",
    val sourceIconUrl: String = "",
    val subtitleTracks: List<SubtitleUiModel> = emptyList(),
    val qualityVariants: List<VideoQualityUiModel> = emptyList(),
)

data class VideoQualityUiModel(
    val height: Int,
    val playbackUrl: String,
    val playbackMimeType: String = "",
    val playbackRequestHeaders: Map<String, String> = emptyMap(),
    val playbackManifest: String = "",
    val playbackDataSourceFactory: HttpDataSource.Factory? = null,
)

data class SubtitleUiModel(
    val name: String,
    val language: String?,
    val uri: String,
    val mimeType: String,
)

data class ChannelUiModel(
    val id: String,
    val name: String,
    val sourceId: String,
    val source: String,
    val unreadCount: Int,
    val followerCount: String,
    val description: String,
    val thumbnailUrl: String = "",
    val bannerUrl: String = "",
    val links: Map<String, String> = emptyMap(),
)

data class PlaylistUiModel(
    val id: String,
    val title: String,
    val description: String,
    val videoIds: List<String>,
)

data class SourceUiModel(
    val id: String,
    val name: String,
    val description: String,
    val accentColor: Long,
    val isEnabled: Boolean,
    val engineId: String = id,
    val pluginConfigPath: String? = null,
    val pluginConfigUrl: String = "",
    val iconUrl: String = "",
    val isCustom: Boolean = false,
    val availability: SourceAvailability = SourceAvailability.PluginAvailable,
    val isAuthenticated: Boolean = false,
)

enum class SourceAvailability {
    PluginAvailable,
    LocalIndex,
    MissingPlugin,
}

// Only a fallback source registry remains here. User-facing video, creator, and
// playlist data is loaded from plugins or the local library, never demo records.
internal val previewSources = listOf(
    SourceUiModel(
        id = "youtube",
        name = "YouTube",
        description = "Videos, channels, playlists, and live streams",
        accentColor = 0xFFE53935,
        isEnabled = true,
    ),
    SourceUiModel(
        id = "peertube",
        name = "PeerTube",
        description = "Federated video across independent instances",
        accentColor = 0xFFF1680D,
        isEnabled = false,
    ),
    SourceUiModel(
        id = "odysee",
        name = "Odysee",
        description = "Decentralized video and creator channels",
        accentColor = 0xFF2F7CF6,
        isEnabled = false,
    ),
    SourceUiModel(
        id = "twitch",
        name = "Twitch",
        description = "Live streams, clips, and followed channels",
        accentColor = 0xFF9146FF,
        isEnabled = false,
    ),
    SourceUiModel(
        id = "soundcloud",
        name = "SoundCloud",
        description = "Music, podcasts, and audio creators",
        accentColor = 0xFFFF5500,
        isEnabled = false,
    ),
    SourceUiModel(
        id = "nebula",
        name = "Nebula",
        description = "Independent creator originals",
        accentColor = 0xFF202A44,
        isEnabled = false,
    ),
)
