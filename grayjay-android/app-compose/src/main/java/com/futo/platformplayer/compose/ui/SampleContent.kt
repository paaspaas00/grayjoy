package com.futo.platformplayer.compose.ui

import androidx.media3.datasource.HttpDataSource
import androidx.media3.common.StreamKey
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor

data class VideoUiModel(
    val id: String,
    val title: String,
    val creator: String,
    val metadata: String,
    val duration: String,
    val viewCount: Long = 0L,
    val publishedAtMs: Long = 0L,
    val channelId: String = "",
    val sourceId: String = "youtube",
    val isLive: Boolean = false,
    val isAvailable: Boolean = true,
    val scheduledStartAtMs: Long = 0L,
    val isDrmProtected: Boolean = false,
    val drmLicenseUri: String = "",
    val drmLicenseRequestExecutor: JSRequestExecutor? = null,
    val playbackTracker: IPlaybackTracker? = null,
    val playbackHasMuxedAudio: Boolean = false,
    val watchProgress: Float = 0f,
    val isDownloaded: Boolean = false,
    val playbackFromDownload: Boolean = false,
    val playbackCacheNamespace: String = "",
    val audioCacheNamespace: String = "",
    val playbackStreamKeys: List<StreamKey> = emptyList(),
    val audioStreamKeys: List<StreamKey> = emptyList(),
    val isWatchLater: Boolean = false,
    val isLiked: Boolean = false,
    val lastWatchedAt: Long = 0L,
    val playlistNames: List<String> = emptyList(),
    val playbackUrl: String = "",
    val playbackMimeType: String = "",
    val playbackManifest: String = "",
    val audioUrl: String = "",
    val audioRequestHeaders: Map<String, String> = emptyMap(),
    val audioDataSourceFactory: HttpDataSource.Factory? = null,
    val audioDownloadUrl: String = "",
    val audioDownloadMimeType: String = "",
    val audioDownloadManifest: String = "",
    val audioDownloadRequestHeaders: Map<String, String> = emptyMap(),
    val audioDownloadDataSourceFactory: HttpDataSource.Factory? = null,
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
    val audioQualityVariants: List<AudioQualityUiModel> = emptyList(),
    val audioLanguages: List<AudioLanguageUiModel> = emptyList(),
    val resolvedAudioLanguage: String? = null,
    val resolvedAudioIsOriginal: Boolean = false,
    val storyboard: StoryboardUiModel? = null,
    /** True only when the active offline descriptor contains audio and no video track. */
    val playbackAudioOnly: Boolean = false,
)

/** DRM sources such as Crunchyroll may be streamed but are never offered as offline downloads. */
fun VideoUiModel.supportsOfflineDownload(): Boolean =
    !isLive && !isDrmProtected && !sourceId.equals("crunchyroll", ignoreCase = true)

data class StoryboardUiModel(
    val levels: List<StoryboardLevelUiModel>,
)

data class StoryboardLevelUiModel(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val columns: Int,
    val rows: Int,
    val intervalMs: Long,
    val sheetUrlTemplate: String,
)

data class VideoQualityUiModel(
    val height: Int,
    val playbackUrl: String,
    val playbackMimeType: String = "",
    val playbackRequestHeaders: Map<String, String> = emptyMap(),
    val playbackManifest: String = "",
    val playbackDataSourceFactory: HttpDataSource.Factory? = null,
)

data class AudioQualityUiModel(
    val bitrate: Int,
    val name: String,
    val playbackUrl: String,
    val language: String? = null,
    val isOriginal: Boolean = false,
    val isPriority: Boolean = false,
    val playbackMimeType: String = "",
    val playbackRequestHeaders: Map<String, String> = emptyMap(),
    val playbackManifest: String = "",
    val playbackDataSourceFactory: HttpDataSource.Factory? = null,
)

data class AudioLanguageUiModel(
    val language: String,
    val name: String,
    val isOriginal: Boolean = false,
)

data class SubtitleUiModel(
    val name: String,
    val language: String?,
    val uri: String,
    val mimeType: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val cacheNamespace: String = "",
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
    val sourceId: String = "",
    val thumbnailUrl: String = "",
    val videoCount: Int = videoIds.size,
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
