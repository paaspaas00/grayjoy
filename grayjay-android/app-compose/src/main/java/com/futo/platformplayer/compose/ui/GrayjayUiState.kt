package com.futo.platformplayer.compose.ui

import androidx.annotation.StringRes
import com.futo.platformplayer.compose.R

data class GrayjayUiState(
    val videos: List<VideoUiModel> = emptyList(),
    val libraryVideos: List<VideoUiModel> = emptyList(),
    val channels: List<ChannelUiModel> = emptyList(),
    val playlists: List<PlaylistUiModel> = emptyList(),
    val sources: List<SourceUiModel> = emptyList(),
    val dynamicColorsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val privateSessionEnabled: Boolean = false,
    val defaultPlaybackSpeed: Float = 1f,
    val perChannelPlaybackSpeedEnabled: Boolean = true,
    val holdToSpeedEnabled: Boolean = false,
    val channelPlaybackSpeeds: Map<String, Float> = emptyMap(),
    val videoPlaybackSpeeds: Map<String, Float> = emptyMap(),
    val preferredVideoQuality: Int = 0,
    val preferredAudioBitrate: Int = Int.MAX_VALUE,
    val preferredAudioLanguage: String = "en",
    val preferOriginalAudio: Boolean = true,
    val stickyCaptionsEnabled: Boolean = true,
    val showRecommendations: Boolean = true,
    val searchHistoryEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val pictureInPictureEnabled: Boolean = true,
    val otherAudioDuckingEnabled: Boolean = true,
    val otherAudioDuckVolumePercent: Int = 35,
    val profiles: List<ProfileUiModel> = emptyList(),
    val activeProfileId: String = "main",
    val followedCreatorIds: Set<String> = emptySet(),
    val sourceOperationInProgress: Boolean = false,
    val sourceOperationMessage: String? = null,
    val sourceTrustRequest: SourceTrustRequestUiModel? = null,
    val youtubeImport: YoutubeImportUiState = YoutubeImportUiState(),
    val databaseImport: DatabaseImportUiState = DatabaseImportUiState(),
    val downloads: Map<String, DownloadUiModel> = emptyMap(),
    val activePlaylistDownloads: Set<PlaylistDownloadBatchUiModel> = emptySet(),
    val subscriptionVideos: List<VideoUiModel> = emptyList(),
    val home: HomeUiState = HomeUiState(),
    val playback: PlaybackUiState = PlaybackUiState(),
    val search: SearchUiState = SearchUiState(),
    val channelDetail: ChannelDetailUiState = ChannelDetailUiState(),
    val remotePlaylistDetail: RemotePlaylistDetailUiState = RemotePlaylistDetailUiState(),
    val nowPlaying: NowPlayingUiState = NowPlayingUiState(),
    val chromecast: ChromecastUiState = ChromecastUiState(),
    val pcLink: PcLinkUiState = PcLinkUiState(),
    val externalNavigation: ExternalNavigationUiModel? = null,
    val availableUpdate: ReleaseUpdateUiModel? = null,
)

data class PcLinkUiState(
    val pairedComputers: List<PairedComputerUiModel> = emptyList(),
    val activePlayback: PcPlaybackUiModel? = null,
    val serverAddresses: List<String> = emptyList(),
)

data class PairedComputerUiModel(
    val id: String,
    val name: String,
    val lastSeenAtMs: Long,
    val isConnected: Boolean,
)

data class PcPlaybackUiModel(
    val computerId: String,
    val computerName: String,
    val isPlaylist: Boolean,
    val title: String,
    val videoTitle: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val receivedAtMs: Long,
)

enum class YoutubeImportStageUi {
    Connecting,
    Subscriptions,
    History,
    Playlists,
}

data class YoutubeImportSelection(
    val subscriptions: Boolean = true,
    val history: Boolean = true,
    val playlists: Boolean = true,
    val likedVideos: Boolean = true,
)

data class YoutubeImportUiState(
    val isRunning: Boolean = false,
    val stage: YoutubeImportStageUi? = null,
    val completed: Int = 0,
    val total: Int? = null,
    val currentItemCompleted: Int? = null,
    val resultMessage: String? = null,
    val warningMessage: String? = null,
    val errorMessage: String? = null,
)

enum class ExternalNavigationKind { Video, Channel, Playlist }

data class ExternalNavigationUiModel(
    val requestId: Long,
    val kind: ExternalNavigationKind,
    val contentId: String,
)

data class ReleaseUpdateUiModel(
    val versionName: String,
    val releaseUrl: String,
)

data class ChromecastDeviceUiModel(
    val id: String,
    val name: String,
    val protocol: CastProtocolUi,
)

enum class CastProtocolUi { Chromecast, FCast }

data class ChromecastUiState(
    val devices: List<ChromecastDeviceUiModel> = emptyList(),
    val isDiscovering: Boolean = false,
    val activeDeviceId: String? = null,
    val activeDeviceName: String? = null,
    val activeProtocol: CastProtocolUi? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
)

enum class DownloadStatus {
    Preparing,
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
    Removing,
}

enum class DownloadMediaType { Video, Audio }

data class PlaylistDownloadBatchUiModel(
    val playlistId: String,
    val mediaType: DownloadMediaType,
)

data class DownloadUiModel(
    val profileId: String,
    val videoId: String,
    val mediaType: DownloadMediaType = DownloadMediaType.Video,
    val status: DownloadStatus,
    val progress: Float? = null,
    val bytesDownloaded: Long = 0L,
    val contentLength: Long? = null,
    val completedParts: Int = 0,
    val totalParts: Int = 1,
    val errorMessage: String? = null,
    val requiresPluginTransport: Boolean = false,
    val preparedAtMs: Long? = null,
    val targetVideoHeight: Int? = null,
    val targetAudioBitrate: Int? = null,
    val completedMediaTypes: Set<DownloadMediaType> = emptySet(),
    val activeMediaTypes: Set<DownloadMediaType> = emptySet(),
    val failedMediaTypes: Set<DownloadMediaType> = emptySet(),
) {
    val isComplete: Boolean get() =
        status == DownloadStatus.Completed || completedMediaTypes.isNotEmpty()
    val isActive: Boolean get() = status in setOf(
        DownloadStatus.Preparing,
        DownloadStatus.Queued,
        DownloadStatus.Downloading,
        DownloadStatus.Paused,
        DownloadStatus.Removing,
    )

    fun isComplete(mediaType: DownloadMediaType): Boolean =
        mediaType in completedMediaTypes ||
            (completedMediaTypes.isEmpty() && status == DownloadStatus.Completed && this.mediaType == mediaType)

    fun isActive(mediaType: DownloadMediaType): Boolean =
        mediaType in activeMediaTypes ||
            (activeMediaTypes.isEmpty() && isActive && this.mediaType == mediaType)

    fun hasAttempt(mediaType: DownloadMediaType): Boolean =
        isComplete(mediaType) || isActive(mediaType) || mediaType in failedMediaTypes
}

enum class ThemeMode { System, Light, Dark }

enum class HomeFeedType(@param:StringRes val labelRes: Int) {
    Subscriptions(R.string.feed_subscriptions),
    ForYou(R.string.feed_for_you),
    Trending(R.string.feed_trending),
    Live(R.string.feed_live),
}

data class HomeUiState(
    val selectedFeed: HomeFeedType = HomeFeedType.Subscriptions,
    val videos: List<VideoUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val subscriptionsLoaded: Int = 0,
    val subscriptionsTotal: Int = 0,
)

enum class ChannelContentTab(@param:StringRes val labelRes: Int) {
    Videos(R.string.videos),
    Shorts(R.string.shorts),
    Playlists(R.string.playlists),
}

data class ChannelDetailUiState(
    val channelId: String? = null,
    val channel: ChannelUiModel? = null,
    val selectedTab: ChannelContentTab = ChannelContentTab.Videos,
    val videos: List<VideoUiModel> = emptyList(),
    val shorts: List<VideoUiModel> = emptyList(),
    val playlists: List<PlaylistUiModel> = emptyList(),
    val loadedTabs: Set<ChannelContentTab> = emptySet(),
    val continuationIds: Map<ChannelContentTab, String> = emptyMap(),
    val tabsWithMore: Set<ChannelContentTab> = emptySet(),
    val supportsShorts: Boolean = false,
    val supportsPlaylists: Boolean = false,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val isLoadingMore: Boolean = false,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

data class RemotePlaylistDetailUiState(
    val playlist: PlaylistUiModel? = null,
    val videos: List<VideoUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingAll: Boolean = false,
    val activeDownloadMediaTypes: Set<DownloadMediaType> = emptySet(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

data class ProfileUiModel(
    val id: String,
    val name: String,
    val protection: ProfileProtection,
    val isBuiltIn: Boolean = false,
)

enum class ProfileProtection { None, DeviceCredential, Pin }

data class SourceTrustRequestUiModel(
    val token: String,
    val pluginName: String,
    val publisher: String,
    val publisherUrl: String,
    val configUrl: String,
    val publicKeyFingerprint: String,
)

enum class DatabaseImportFormat { Grayjay, NewPipe }

data class DatabaseImportPreviewUiModel(
    val fileName: String,
    val sourceCount: Int,
    val pluginSettingsCount: Int,
    val subscriptionCount: Int,
    val watchLaterCount: Int,
    val playlistCount: Int,
    val historyCount: Int,
    val hasLegacySettings: Boolean,
    val format: DatabaseImportFormat = DatabaseImportFormat.Grayjay,
)

data class DatabaseImportUiState(
    val isBusy: Boolean = false,
    val preview: DatabaseImportPreviewUiModel? = null,
    val passwordRequired: Boolean = false,
    val fileName: String = "",
    val errorMessage: String? = null,
    val resultMessage: String? = null,
    val format: DatabaseImportFormat = DatabaseImportFormat.Grayjay,
)

data class DatabaseImportSelection(
    val importSources: Boolean = true,
    val importPluginSettings: Boolean = true,
    val importSubscriptions: Boolean = true,
    val importWatchLater: Boolean = true,
    val importPlaylists: Boolean = true,
    val importHistory: Boolean = true,
)

data class PlaybackUiState(
    val currentVideoId: String? = null,
    val queueVideoIds: List<String> = emptyList(),
    val isCasting: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1f,
    val captionsEnabled: Boolean = false,
    val availableVideoQualities: List<Int> = emptyList(),
    val selectedVideoQuality: Int? = null,
    val currentVideoWidth: Int? = null,
    val currentVideoHeight: Int? = null,
    val selectedSubtitleLanguage: String? = null,
    val selectedSubtitleTrackIndex: Int? = null,
    val availableAudioLanguages: List<String> = emptyList(),
    val selectedAudioLanguage: String? = null,
    val audioLanguageAutomatic: Boolean = true,
    val errorMessage: String? = null,
    val audioSpectrum: List<Float> = emptyList(),
)

data class VideoCommentUiModel(
    val author: String,
    val authorThumbnailUrl: String = "",
    val message: String,
    val age: String,
    val likeCount: Long? = null,
    val replyCount: Int? = null,
)

data class NowPlayingUiState(
    val video: VideoUiModel? = null,
    val isLoadingPlayback: Boolean = false,
    val isLoadingExtras: Boolean = false,
    val recommendations: List<VideoUiModel> = emptyList(),
    val comments: List<VideoCommentUiModel> = emptyList(),
    val recommendationsAvailable: Boolean = false,
    val commentsAvailable: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val recommendationContinuationId: String? = null,
    val commentsContinuationId: String? = null,
    val hasMoreRecommendations: Boolean = false,
    val hasMoreComments: Boolean = false,
    val isFollowing: Boolean = false,
    val resumePositionFraction: Float? = null,
    val errorMessage: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
    val hasSearched: Boolean = false,
    val videos: List<VideoUiModel> = emptyList(),
    val channels: List<ChannelUiModel> = emptyList(),
    val playlists: List<PlaylistUiModel> = emptyList(),
    val errorMessage: String? = null,
)

enum class SearchContentType(@param:StringRes val labelRes: Int) {
    Videos(R.string.videos),
    Creators(R.string.creators),
    Playlists(R.string.playlists),
}
