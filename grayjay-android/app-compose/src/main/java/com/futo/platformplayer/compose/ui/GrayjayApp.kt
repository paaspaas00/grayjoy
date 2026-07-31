package com.futo.platformplayer.compose.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.screens.HomeScreen
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.screens.ChromecastSheet
import com.futo.platformplayer.compose.ui.screens.LibraryScreen
import com.futo.platformplayer.compose.ui.screens.MiniPlayer
import com.futo.platformplayer.compose.ui.screens.MiniPlayerChrome
import com.futo.platformplayer.compose.ui.screens.PlaylistDetailScreen
import com.futo.platformplayer.compose.ui.screens.RemotePlaylistDetailScreen
import com.futo.platformplayer.compose.ui.screens.SearchScreen
import com.futo.platformplayer.compose.ui.screens.SettingsScreen
import com.futo.platformplayer.compose.ui.screens.SubscriptionsScreen
import com.futo.platformplayer.compose.ui.screens.SourcesScreen
import com.futo.platformplayer.compose.ui.screens.VideoDetailScreen
import com.futo.platformplayer.compose.ui.screens.VideoActionsSheet
import com.futo.platformplayer.compose.ui.screens.visibleSourcesForQuery
import com.futo.platformplayer.compose.ui.screens.DatabaseImportDialogs
import com.futo.platformplayer.compose.ui.screens.SourceTrustDialog
import com.futo.platformplayer.compose.ui.screens.PlaylistPickerDialog
import com.futo.platformplayer.compose.ui.screens.FullscreenPlayerScreen
import com.futo.platformplayer.compose.ui.screens.PlayerSurface
import com.futo.platformplayer.compose.ui.screens.ProfileSwitcherDialogs
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun shouldCoverAppChromeDuringOrientationHandoff(windowOrientation: Int): Boolean =
    windowOrientation == Configuration.ORIENTATION_LANDSCAPE

// Private GitHub repositories do not expose release metadata to unauthenticated app clients.
// Keep the complete banner/check path dormant until the repository is made public.
internal const val RELEASE_UPDATE_CHECK_ENABLED = true

internal fun hasNowPlayingDownload(download: DownloadUiModel?): Boolean {
    if (download == null) return false
    if (download.completedMediaTypes.isEmpty()) {
        return download.status == DownloadStatus.Completed
    }
    val completedMediaTypes = if (download.status == DownloadStatus.Removing) {
        download.completedMediaTypes - download.mediaType
    } else {
        download.completedMediaTypes
    }
    return completedMediaTypes.isNotEmpty()
}

internal enum class GrayjayDestination(
    @param:StringRes val navigationLabelRes: Int,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val showInCompactNavigation: Boolean = true,
) {
    Home(R.string.nav_home, R.string.nav_home, Icons.Outlined.Home),
    Subscriptions(R.string.nav_following, R.string.nav_subscriptions, Icons.Outlined.Subscriptions),
    Search(R.string.nav_search, R.string.nav_search, Icons.Outlined.Search),
    Library(R.string.nav_library, R.string.nav_library, Icons.Outlined.VideoLibrary),
    Settings(R.string.nav_settings, R.string.nav_settings, Icons.Outlined.Settings),
    Sources(R.string.nav_sources, R.string.nav_sources, Icons.Outlined.Extension, showInCompactNavigation = false),
}

internal fun topLevelBackDestination(
    current: GrayjayDestination,
): GrayjayDestination? = GrayjayDestination.Home.takeIf {
    current != GrayjayDestination.Home && current.showInCompactNavigation
}

private data class PlaybackPresentation(
    val video: VideoUiModel?,
    val queue: List<VideoUiModel>,
    val nowPlaying: NowPlayingUiState,
    val channelDetail: ChannelDetailUiState,
    val remotePlaylistDetail: RemotePlaylistDetailUiState,
    val availableUpdate: ReleaseUpdateUiModel?,
    val channels: List<ChannelUiModel>,
    val player: Player,
    val state: PlaybackUiState,
    val followedCreatorIds: Set<String>,
    val downloads: Map<String, DownloadUiModel>,
    val activePlaylistDownloads: Set<PlaylistDownloadBatchUiModel>,
    val isPlaying: Boolean,
    val queueSize: Int,
    val transitionProgress: Float,
    val navigationBackProgress: Float,
    val onExpand: () -> Unit,
    val onCollapse: () -> Unit,
    val onTransitionDragStart: () -> Unit,
    val onTransitionProgressChange: (Float) -> Unit,
    val onTransitionRelease: (Float) -> Unit,
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onSpeedChange: (Float) -> Unit,
    val onUseChannelSpeed: () -> Unit,
    val onChannelSpeedChange: (String, Float?) -> Unit,
    val onQualityChange: (Int?) -> Unit,
    val onAudioLanguageChange: (String?) -> Unit,
    val onCaptionsEnabledChange: (Boolean) -> Unit,
    val onSubtitleLanguageChange: (String?) -> Unit,
    val onEnterFullscreen: () -> Unit,
    val onExitFullscreen: () -> Unit,
    val onRetry: () -> Unit,
    val onToggleFollowing: () -> Unit,
    val onCreatorFollowedChange: (String, Boolean) -> Unit,
    val onLoadChannel: (ChannelUiModel) -> Unit,
    val onChannelTabSelected: (ChannelContentTab) -> Unit,
    val onLoadMoreChannel: () -> Unit,
    val onLoadRemotePlaylist: (PlaylistUiModel) -> Unit,
    val onLoadMoreRemotePlaylist: () -> Unit,
    val onPlayRemotePlaylist: () -> Unit,
    val onPlayRemotePlaylistFrom: (String) -> Unit,
    val onDownloadRemotePlaylist: (DownloadMediaType) -> Unit,
    val onCancelDownloadRemotePlaylist: (DownloadMediaType) -> Unit,
    val onCreateLocalPlaylistFromRemote: (String) -> Unit,
    val onLoadMoreRecommendations: () -> Unit,
    val onLoadMoreComments: () -> Unit,
    val onClose: () -> Unit,
    val onPlayQueue: (List<String>) -> Unit,
    val onQueueVideos: (List<String>) -> Unit,
    val onPlayPlaylist: (String) -> Unit,
    val onPlayPlaylistFrom: (String, String) -> Unit,
    val onToggleWatchLater: (String) -> Unit,
    val onToggleDownloaded: (String) -> Unit,
    val onToggleAudioDownloaded: (String) -> Unit,
    val onDownloadVideo: (String, Int?) -> Unit,
    val onDownloadAudio: (String, Int?) -> Unit,
    val onDownloadVideos: (List<String>, DownloadMediaType) -> Unit,
    val onDownloadPlaylist: (String, DownloadMediaType) -> Unit,
    val onCancelDownloadPlaylist: (String, DownloadMediaType) -> Unit,
    val onVideoLongClick: (VideoUiModel) -> Unit,
    val onAddSelectionToPlaylist: (List<String>) -> Unit,
    val onRemoveSelectionFromHistory: (List<String>) -> Unit,
    val onRemoveDownloads: (List<String>) -> Unit,
    val onRemovePlaylists: (List<String>) -> Unit,
    val onExportDownloads: (List<String>, DownloadMediaType, Uri) -> Unit,
    val onRenamePlaylist: (String, String) -> Unit,
    val onRemoveVideosFromPlaylist: (String, List<String>) -> Unit,
    val onReorderPlaylist: (String, List<String>) -> Unit,
    val onSeek: (Float) -> Unit,
    val onResumeFromHistory: () -> Unit,
    val libraryVideos: List<VideoUiModel>,
    val onOpenProfiles: () -> Unit,
    val defaultPlaybackSpeed: Float,
    val perChannelPlaybackSpeedEnabled: Boolean,
    val channelPlaybackSpeeds: Map<String, Float>,
    val videoPlaybackSpeeds: Map<String, Float>,
    val preferredVideoQuality: Int,
    val preferredAudioBitrate: Int,
    val preferredAudioLanguage: String,
    val preferOriginalAudio: Boolean,
    val stickyCaptionsEnabled: Boolean,
    val showRecommendations: Boolean,
    val searchHistoryEnabled: Boolean,
    val keepScreenAwake: Boolean,
    val pictureInPictureEnabled: Boolean,
    val otherAudioDuckingEnabled: Boolean,
    val otherAudioDuckVolumePercent: Int,
    val themeMode: ThemeMode,
    val showPrivateThemeToggle: Boolean,
    val isDarkTheme: Boolean,
    val onDarkThemeChange: (Boolean) -> Unit,
    val onDefaultPlaybackSpeedChange: (Float) -> Unit,
    val onPerChannelPlaybackSpeedChange: (Boolean) -> Unit,
    val onPreferredVideoQualityChange: (Int) -> Unit,
    val onPreferredAudioBitrateChange: (Int) -> Unit,
    val onPreferredAudioLanguageChange: (String) -> Unit,
    val onPreferOriginalAudioChange: (Boolean) -> Unit,
    val onStickyCaptionsChange: (Boolean) -> Unit,
    val onShowRecommendationsChange: (Boolean) -> Unit,
    val onSearchHistoryChange: (Boolean) -> Unit,
    val onKeepScreenAwakeChange: (Boolean) -> Unit,
    val onPictureInPictureChange: (Boolean) -> Unit,
    val onOtherAudioDuckingChange: (Boolean) -> Unit,
    val onOtherAudioDuckVolumeChange: (Int) -> Unit,
    val pcLink: PcLinkUiState,
    val onScanPcPairingQr: () -> Unit,
    val onRemovePairedComputer: (String) -> Unit,
    val onPlayFromComputer: (String) -> Unit,
    val onToggleComputerPlayback: (String) -> Unit,
    val onPreviousComputerPlayback: (String) -> Unit,
    val onNextComputerPlayback: (String) -> Unit,
    val onSeekComputerPlayback: (String, Long) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val chromecast: ChromecastUiState,
    val onOpenChromecast: () -> Unit,
)

private data class SourcePresentation(
    val sources: List<SourceUiModel>,
    val home: HomeUiState,
    val onHomeFeedSelected: (HomeFeedType) -> Unit,
    val onRefreshHome: () -> Unit,
    val onLoadMoreHome: () -> Unit,
    val onEnabledChange: (String, Boolean) -> Unit,
    val isOperationInProgress: Boolean,
    val operationMessage: String?,
    val onInstall: (String) -> Unit,
    val onScanQr: () -> Unit,
    val onRefresh: (String) -> Unit,
    val onClearCache: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onLogin: (SourceUiModel) -> Unit,
    val onLogout: (String) -> Unit,
    val youtubeImport: YoutubeImportUiState,
    val onImportYoutube: (String, YoutubeImportSelection) -> Unit,
    val onDismissYoutubeImport: () -> Unit,
    val search: SearchUiState,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchSubmit: (String, SearchContentType, Set<String>) -> Unit,
    val onLoadMoreSearch: () -> Unit,
)

@Composable
fun GrayjayApp(
    uiState: GrayjayUiState,
    player: Player,
    isDarkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onDynamicColorsChange: (Boolean) -> Unit,
    onPrivateSessionChange: (Boolean) -> Unit,
    onOpenVideo: (String) -> Unit,
    onLoadChannel: (ChannelUiModel) -> Unit,
    onChannelTabSelected: (ChannelContentTab) -> Unit = {},
    onHomeFeedSelected: (HomeFeedType) -> Unit,
    onRefreshHome: () -> Unit,
    onLoadMoreHome: () -> Unit = {},
    onPlayQueue: (List<String>) -> Unit,
    onQueueVideos: (List<String>) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onPlayPlaylistFrom: (String, String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSeekPlaybackBy: (Long) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onUseChannelPlaybackSpeed: () -> Unit,
    onChannelPlaybackSpeedChange: (String, Float?) -> Unit,
    onVideoQualityChange: (Int?) -> Unit,
    onAudioLanguageChange: (String?) -> Unit,
    onCaptionsEnabledChange: (Boolean) -> Unit,
    onSubtitleLanguageChange: (String?) -> Unit,
    onRetryPlayback: () -> Unit,
    onClosePlayback: () -> Unit,
    onToggleWatchLater: (String) -> Unit,
    onToggleDownloaded: (String) -> Unit,
    onToggleAudioDownloaded: (String) -> Unit,
    onDownloadVideo: (String, Int?) -> Unit,
    onDownloadAudio: (String, Int?) -> Unit,
    onDownloadVideos: (List<String>, DownloadMediaType) -> Unit,
    onDownloadPlaylist: (String, DownloadMediaType) -> Unit,
    onCancelDownloadPlaylist: (String, DownloadMediaType) -> Unit = { _, _ -> },
    onCreatePlaylist: (String, List<String>) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onAddVideosToPlaylist: (String, List<String>) -> Unit,
    onRemoveVideosFromPlaylist: (String, List<String>) -> Unit,
    onReorderPlaylist: (String, List<String>) -> Unit,
    onRemoveVideosFromHistory: (List<String>) -> Unit,
    onRemoveDownloads: (List<String>) -> Unit = {},
    onRemovePlaylists: (List<String>) -> Unit = {},
    onExportDownloads: (List<String>, DownloadMediaType, Uri) -> Unit = { _, _, _ -> },
    onSeekPlayback: (Float) -> Unit,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onInstallSource: (String) -> Unit,
    onScanSourceQr: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onClearSourceCache: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onLoginSource: (SourceUiModel) -> Unit,
    onLogoutSource: (String) -> Unit,
    onImportYoutube: (String, YoutubeImportSelection) -> Unit,
    onDismissYoutubeImport: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String, SearchContentType, Set<String>) -> Unit,
    onLoadMoreSearch: () -> Unit = {},
    onLoadMoreChannel: () -> Unit = {},
    onLoadRemotePlaylist: (PlaylistUiModel) -> Unit = {},
    onLoadMoreRemotePlaylist: () -> Unit = {},
    onPlayRemotePlaylist: () -> Unit = {},
    onPlayRemotePlaylistFrom: (String) -> Unit = {},
    onDownloadRemotePlaylist: (DownloadMediaType) -> Unit = {},
    onCancelDownloadRemotePlaylist: (DownloadMediaType) -> Unit = {},
    onCreateLocalPlaylistFromRemote: (String) -> Unit = {},
    onLoadMoreRecommendations: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onToggleFollowing: () -> Unit,
    onResumeFromHistory: () -> Unit = {},
    onCreatorFollowedChange: (String, Boolean) -> Unit,
    onChooseDatabaseImport: () -> Unit,
    onChooseNewPipeImport: () -> Unit = {},
    onRetryDatabaseImport: (String) -> Unit,
    onConfirmDatabaseImport: (DatabaseImportSelection) -> Unit,
    onDismissDatabaseImport: () -> Unit,
    onTrustUnverifiedSource: () -> Unit,
    onRejectUnverifiedSource: () -> Unit,
    onSwitchProfile: (String) -> Unit,
    onCreateProfile: (String, String) -> Unit,
    onVerifyProfilePin: (String, String) -> Boolean,
    onDefaultPlaybackSpeedChange: (Float) -> Unit,
    onPerChannelPlaybackSpeedChange: (Boolean) -> Unit,
    onPreferredVideoQualityChange: (Int) -> Unit,
    onPreferredAudioBitrateChange: (Int) -> Unit,
    onPreferredAudioLanguageChange: (String) -> Unit,
    onPreferOriginalAudioChange: (Boolean) -> Unit,
    onStickyCaptionsChange: (Boolean) -> Unit,
    onShowRecommendationsChange: (Boolean) -> Unit,
    onSearchHistoryChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onPictureInPictureChange: (Boolean) -> Unit = {},
    onStartChromecastDiscovery: () -> Unit = {},
    onConnectChromecast: (String) -> Unit = {},
    onDisconnectChromecast: () -> Unit = {},
    onOtherAudioDuckingChange: (Boolean) -> Unit = {},
    onOtherAudioDuckVolumeChange: (Int) -> Unit = {},
    onScanPcPairingQr: () -> Unit = {},
    onRemovePairedComputer: (String) -> Unit = {},
    onPlayFromComputer: (String) -> Unit = {},
    onToggleComputerPlayback: (String) -> Unit = {},
    onPreviousComputerPlayback: (String) -> Unit = {},
    onNextComputerPlayback: (String) -> Unit = {},
    onSeekComputerPlayback: (String, Long) -> Unit = { _, _ -> },
    onExternalNavigationHandled: (Long) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    deviceIsLandscape: Boolean = false,
    pictureInPictureMode: Boolean = false,
    onFullscreenPresentationChanged: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val shareVideoLabel = stringResource(R.string.share_video)
    var destinationName by rememberSaveable { mutableStateOf(GrayjayDestination.Home.name) }
    var selectedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var nestedBackDestinationName by rememberSaveable { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var fullscreenEnteredByRotation by rememberSaveable { mutableStateOf(false) }
    var actionVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionIsRemotePlaylistVideo by rememberSaveable { mutableStateOf(false) }
    var playlistPickerVideoIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var profileDialogVisible by rememberSaveable { mutableStateOf(false) }
    var chromecastSheetVisible by rememberSaveable { mutableStateOf(false) }
    val selected = GrayjayDestination.valueOf(destinationName)
    // Player-transition progress changes every frame. Building a combined list and linearly
    // searching it for every item in a large imported playlist made collapsing an 800-item queue
    // perform millions of comparisons per frame. Memoize one first-match index instead.
    val availableVideosById = remember(
        uiState.videos,
        uiState.subscriptionVideos,
        uiState.libraryVideos,
        uiState.search.videos,
        uiState.home.videos,
        uiState.channelDetail.videos,
        uiState.channelDetail.shorts,
        uiState.remotePlaylistDetail.videos,
        uiState.nowPlaying.video,
        uiState.nowPlaying.recommendations,
    ) {
        buildMap {
            sequenceOf(
                uiState.videos,
                uiState.subscriptionVideos,
                uiState.libraryVideos,
                uiState.search.videos,
                uiState.home.videos,
                uiState.channelDetail.videos,
                uiState.channelDetail.shorts,
                uiState.remotePlaylistDetail.videos,
                listOfNotNull(uiState.nowPlaying.video),
                uiState.nowPlaying.recommendations,
            ).forEach { videos ->
                videos.forEach { video -> putIfAbsent(video.id, video) }
            }
        }
    }
    val selectedVideo = uiState.nowPlaying.video?.takeIf { it.id == selectedVideoId }
        ?: selectedVideoId?.let(availableVideosById::get)
    val selectedChannel = uiState.channels.firstOrNull { it.id == selectedChannelId }
    val selectedPlaylist = uiState.playlists.firstOrNull { it.id == selectedPlaylistId }
        ?: uiState.remotePlaylistDetail.playlist?.takeIf { it.id == selectedPlaylistId }
        ?: (uiState.search.playlists + uiState.channelDetail.playlists)
            .firstOrNull { it.id == selectedPlaylistId }
    // The same content can exist in feeds as its online video model and in Now Playing as a
    // resolved offline/audio-only model. The resolved active model must win; otherwise the final
    // frame of the collapse swaps playbackAudioOnly back to false and covers the audio mini-player
    // artwork with an empty video surface.
    val playbackVideo = uiState.nowPlaying.video?.takeIf {
        it.id == uiState.playback.currentVideoId
    } ?: availableVideosById[uiState.playback.currentVideoId]
    var playerTransitionProgress by rememberSaveable { mutableFloatStateOf(1f) }
    var navigationBackProgress by remember { mutableFloatStateOf(0f) }
    val playerTransitionScope = rememberCoroutineScope()
    var playerTransitionJob by remember { mutableStateOf<Job?>(null) }
    val settlePlayer: (Float, String?) -> Unit = { target, videoId ->
        playerTransitionJob?.cancel()
        if (target == 0f) {
            selectedVideoId = videoId ?: playbackVideo?.id
        }
        playerTransitionJob = playerTransitionScope.launch {
            animate(
                initialValue = playerTransitionProgress,
                targetValue = target,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) { value, _ -> playerTransitionProgress = value }
            playerTransitionProgress = target
            if (target == 1f) {
                selectedVideoId = null
                isFullscreen = false
                fullscreenEnteredByRotation = false
            } else {
                selectedVideoId = videoId ?: playbackVideo?.id
            }
        }
    }
    val queueVideos = remember(uiState.playback.queueVideoIds, availableVideosById) {
        uiState.playback.queueVideoIds.mapNotNull(availableVideosById::get)
    }
    val onSelect: (GrayjayDestination) -> Unit = {
        destinationName = it.name
        selectedVideoId = null
        playerTransitionProgress = 1f
        selectedChannelId = null
        selectedPlaylistId = null
        nestedBackDestinationName = null
    }
    val onVideoClick: (VideoUiModel) -> Unit = {
        onOpenVideo(it.id)
        settlePlayer(0f, it.id)
    }
    val onVideoLongClick: (VideoUiModel) -> Unit = {
        actionIsRemotePlaylistVideo = false
        actionVideoId = it.id
    }
    val onChannelClick: (ChannelUiModel) -> Unit = {
        onLoadChannel(it)
        selectedChannelId = it.id
        selectedPlaylistId = null
        selectedVideoId = null
        playerTransitionProgress = 1f
    }
    val onPlaylistClick: (PlaylistUiModel) -> Unit = {
        if (it.sourceId.isNotBlank()) onLoadRemotePlaylist(it)
        selectedPlaylistId = it.id
        selectedChannelId = null
        selectedVideoId = null
        playerTransitionProgress = 1f
    }
    LaunchedEffect(uiState.externalNavigation?.requestId) {
        val request = uiState.externalNavigation ?: return@LaunchedEffect
        when (request.kind) {
            ExternalNavigationKind.Video -> {
                selectedVideoId = request.contentId
                selectedChannelId = null
                selectedPlaylistId = null
                settlePlayer(0f, request.contentId)
            }
            ExternalNavigationKind.Channel -> {
                selectedChannelId = request.contentId
                selectedVideoId = null
                selectedPlaylistId = null
                playerTransitionProgress = 1f
            }
            ExternalNavigationKind.Playlist -> {
                selectedPlaylistId = request.contentId
                selectedVideoId = null
                selectedChannelId = null
                playerTransitionProgress = 1f
            }
        }
        onExternalNavigationHandled(request.requestId)
    }
    LaunchedEffect(selected) {
        if (RELEASE_UPDATE_CHECK_ENABLED && selected == GrayjayDestination.Settings) {
            onCheckForUpdates()
        }
    }
    val onNavigateBack: () -> Unit = {
        if (selectedVideoId != null) {
            settlePlayer(1f, selectedVideoId)
        } else if (selectedChannelId != null) {
            selectedChannelId = null
        } else if (selectedPlaylistId != null) {
            selectedPlaylistId = null
        } else {
            val nestedDestination = nestedBackDestinationName
            if (nestedDestination != null) {
                destinationName = nestedDestination
            } else {
                topLevelBackDestination(selected)?.let { destinationName = it.name }
            }
            nestedBackDestinationName = null
        }
    }
    val onManageSources: () -> Unit = {
        nestedBackDestinationName = selected.name
        destinationName = GrayjayDestination.Sources.name
        selectedVideoId = null
        playerTransitionProgress = 1f
        selectedChannelId = null
        selectedPlaylistId = null
    }
    val playback = PlaybackPresentation(
        video = playbackVideo,
        queue = queueVideos,
        nowPlaying = uiState.nowPlaying,
        channelDetail = uiState.channelDetail,
        remotePlaylistDetail = uiState.remotePlaylistDetail,
        availableUpdate = uiState.availableUpdate,
        channels = uiState.channels,
        player = player,
        state = uiState.playback,
        followedCreatorIds = uiState.followedCreatorIds,
        downloads = uiState.downloads,
        activePlaylistDownloads = uiState.activePlaylistDownloads,
        isPlaying = uiState.playback.isPlaying,
        queueSize = uiState.playback.queueVideoIds.size,
        transitionProgress = playerTransitionProgress,
        navigationBackProgress = navigationBackProgress,
        onExpand = { settlePlayer(0f, playbackVideo?.id) },
        onCollapse = { settlePlayer(1f, selectedVideoId ?: playbackVideo?.id) },
        onTransitionDragStart = {
            playerTransitionJob?.cancel()
            selectedVideoId = selectedVideoId ?: playbackVideo?.id
        },
        onTransitionProgressChange = {
            playerTransitionJob?.cancel()
            playerTransitionProgress = it.coerceIn(0f, 1f)
        },
        onTransitionRelease = { target ->
            settlePlayer(target, selectedVideoId ?: playbackVideo?.id)
        },
        onToggle = onTogglePlayback,
        onNext = onSkipToNext,
        onPrevious = onSkipToPrevious,
        onSeekBy = onSeekPlaybackBy,
        onSpeedChange = onPlaybackSpeedChange,
        onUseChannelSpeed = onUseChannelPlaybackSpeed,
        onChannelSpeedChange = onChannelPlaybackSpeedChange,
        onQualityChange = onVideoQualityChange,
        onAudioLanguageChange = onAudioLanguageChange,
        onCaptionsEnabledChange = onCaptionsEnabledChange,
        onSubtitleLanguageChange = onSubtitleLanguageChange,
        onEnterFullscreen = {
            fullscreenEnteredByRotation = deviceIsLandscape
            isFullscreen = true
        },
        onExitFullscreen = {
            fullscreenEnteredByRotation = false
            isFullscreen = false
        },
        onRetry = onRetryPlayback,
        onToggleFollowing = onToggleFollowing,
        onCreatorFollowedChange = onCreatorFollowedChange,
        onLoadChannel = onLoadChannel,
        onChannelTabSelected = onChannelTabSelected,
        onLoadMoreChannel = onLoadMoreChannel,
        onLoadRemotePlaylist = onLoadRemotePlaylist,
        onLoadMoreRemotePlaylist = onLoadMoreRemotePlaylist,
        onPlayRemotePlaylist = {
            onPlayRemotePlaylist()
            uiState.remotePlaylistDetail.videos.firstOrNull()?.let { first ->
                settlePlayer(0f, first.id)
            }
        },
        onPlayRemotePlaylistFrom = { videoId ->
            onPlayRemotePlaylistFrom(videoId)
            settlePlayer(0f, videoId)
        },
        onDownloadRemotePlaylist = onDownloadRemotePlaylist,
        onCancelDownloadRemotePlaylist = onCancelDownloadRemotePlaylist,
        onCreateLocalPlaylistFromRemote = onCreateLocalPlaylistFromRemote,
        onLoadMoreRecommendations = onLoadMoreRecommendations,
        onLoadMoreComments = onLoadMoreComments,
        onClose = {
            playerTransitionJob?.cancel()
            playerTransitionProgress = 1f
            selectedVideoId = null
            onClosePlayback()
        },
        onPlayQueue = { queueIds ->
            if (queueIds.isNotEmpty()) {
                onPlayQueue(queueIds)
                settlePlayer(0f, queueIds.first())
            }
        },
        onQueueVideos = onQueueVideos,
        onPlayPlaylist = { playlistId ->
            val playlist = uiState.playlists.firstOrNull { it.id == playlistId }
            if (playlist != null && playlist.videoIds.isNotEmpty()) {
                onPlayPlaylist(playlistId)
                settlePlayer(0f, playlist.videoIds.first())
            }
        },
        onPlayPlaylistFrom = { playlistId, videoId ->
            val playlist = uiState.playlists.firstOrNull { it.id == playlistId }
            if (playlist != null && videoId in playlist.videoIds) {
                onPlayPlaylistFrom(playlistId, videoId)
                settlePlayer(0f, videoId)
            }
        },
        onToggleWatchLater = onToggleWatchLater,
        onToggleDownloaded = onToggleDownloaded,
        onToggleAudioDownloaded = onToggleAudioDownloaded,
        onDownloadVideo = onDownloadVideo,
        onDownloadAudio = onDownloadAudio,
        onDownloadVideos = onDownloadVideos,
        onDownloadPlaylist = onDownloadPlaylist,
        onCancelDownloadPlaylist = onCancelDownloadPlaylist,
        onVideoLongClick = onVideoLongClick,
        onAddSelectionToPlaylist = { playlistPickerVideoIds = it },
        onRemoveSelectionFromHistory = onRemoveVideosFromHistory,
        onRemoveDownloads = onRemoveDownloads,
        onRemovePlaylists = onRemovePlaylists,
        onExportDownloads = onExportDownloads,
        onRenamePlaylist = onRenamePlaylist,
        onRemoveVideosFromPlaylist = onRemoveVideosFromPlaylist,
        onReorderPlaylist = onReorderPlaylist,
        onSeek = onSeekPlayback,
        onResumeFromHistory = onResumeFromHistory,
        libraryVideos = uiState.libraryVideos,
        onOpenProfiles = { profileDialogVisible = true },
        defaultPlaybackSpeed = uiState.defaultPlaybackSpeed,
        perChannelPlaybackSpeedEnabled = uiState.perChannelPlaybackSpeedEnabled,
        channelPlaybackSpeeds = uiState.channelPlaybackSpeeds,
        videoPlaybackSpeeds = uiState.videoPlaybackSpeeds,
        preferredVideoQuality = uiState.preferredVideoQuality,
        preferredAudioBitrate = uiState.preferredAudioBitrate,
        preferredAudioLanguage = uiState.preferredAudioLanguage,
        preferOriginalAudio = uiState.preferOriginalAudio,
        stickyCaptionsEnabled = uiState.stickyCaptionsEnabled,
        showRecommendations = uiState.showRecommendations,
        searchHistoryEnabled = uiState.searchHistoryEnabled,
        keepScreenAwake = uiState.keepScreenAwake,
        pictureInPictureEnabled = uiState.pictureInPictureEnabled,
        otherAudioDuckingEnabled = uiState.otherAudioDuckingEnabled,
        otherAudioDuckVolumePercent = uiState.otherAudioDuckVolumePercent,
        themeMode = uiState.themeMode,
        showPrivateThemeToggle = uiState.activeProfileId == "private",
        isDarkTheme = isDarkTheme,
        onDarkThemeChange = onDarkThemeChange,
        onDefaultPlaybackSpeedChange = onDefaultPlaybackSpeedChange,
        onPerChannelPlaybackSpeedChange = onPerChannelPlaybackSpeedChange,
        onPreferredVideoQualityChange = onPreferredVideoQualityChange,
        onPreferredAudioBitrateChange = onPreferredAudioBitrateChange,
        onPreferredAudioLanguageChange = onPreferredAudioLanguageChange,
        onPreferOriginalAudioChange = onPreferOriginalAudioChange,
        onStickyCaptionsChange = onStickyCaptionsChange,
        onShowRecommendationsChange = onShowRecommendationsChange,
        onSearchHistoryChange = onSearchHistoryChange,
        onKeepScreenAwakeChange = onKeepScreenAwakeChange,
        onPictureInPictureChange = onPictureInPictureChange,
        onOtherAudioDuckingChange = onOtherAudioDuckingChange,
        onOtherAudioDuckVolumeChange = onOtherAudioDuckVolumeChange,
        pcLink = uiState.pcLink,
        onScanPcPairingQr = onScanPcPairingQr,
        onRemovePairedComputer = onRemovePairedComputer,
        onPlayFromComputer = onPlayFromComputer,
        onToggleComputerPlayback = onToggleComputerPlayback,
        onPreviousComputerPlayback = onPreviousComputerPlayback,
        onNextComputerPlayback = onNextComputerPlayback,
        onSeekComputerPlayback = onSeekComputerPlayback,
        onThemeModeChange = onThemeModeChange,
        chromecast = uiState.chromecast,
        onOpenChromecast = {
            chromecastSheetVisible = true
            onStartChromecastDiscovery()
        },
    )

    LaunchedEffect(uiState.playback.currentVideoId, uiState.nowPlaying.video?.id) {
        if (uiState.playback.currentVideoId == null && uiState.nowPlaying.video == null) {
            playerTransitionJob?.cancel()
            playerTransitionProgress = 1f
            selectedVideoId = null
        }
        if (selectedVideoId != null) {
            uiState.playback.currentVideoId?.let { selectedVideoId = it }
        }
    }

    LaunchedEffect(pictureInPictureMode, playbackVideo?.id) {
        if (pictureInPictureMode && playbackVideo != null) {
            // Match legacy Grayjay: returning from PiP always expands the same player back into
            // Now Playing instead of revealing both the internal mini-player and the detail view.
            playerTransitionJob?.cancel()
            playerTransitionProgress = 0f
            selectedVideoId = playbackVideo.id
            fullscreenEnteredByRotation = false
            isFullscreen = false
            onFullscreenPresentationChanged(false, false)
        }
    }
    if (pictureInPictureMode && playbackVideo != null) {
        val queueIndex = uiState.playback.queueVideoIds.indexOf(uiState.playback.currentVideoId)
        PlayerSurface(
            video = playbackVideo,
            player = player,
            playback = uiState.playback,
            isLoading = uiState.nowPlaying.isLoadingPlayback || uiState.playback.isBuffering,
            isFullscreen = false,
            canGoPrevious = queueIndex > 0 || uiState.playback.positionMs > 5_000L,
            canGoNext = queueIndex >= 0 && queueIndex < uiState.playback.queueVideoIds.lastIndex,
            onTogglePlayback = onTogglePlayback,
            onSkipPrevious = onSkipToPrevious,
            onSkipNext = onSkipToNext,
            onSeekBy = onSeekPlaybackBy,
            onSeek = onSeekPlayback,
            onSpeedChange = onPlaybackSpeedChange,
            onQualityChange = onVideoQualityChange,
            onCaptionsEnabledChange = onCaptionsEnabledChange,
            onSubtitleLanguageChange = onSubtitleLanguageChange,
            onRetryPlayback = onRetryPlayback,
            onFullscreen = {},
            controlsAlpha = 0f,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val sources = SourcePresentation(
        sources = visibleSourcesForQuery(uiState.sources, ""),
        home = uiState.home,
        onHomeFeedSelected = onHomeFeedSelected,
        onRefreshHome = onRefreshHome,
        onLoadMoreHome = onLoadMoreHome,
        onEnabledChange = onSourceEnabledChange,
        isOperationInProgress = uiState.sourceOperationInProgress,
        operationMessage = uiState.sourceOperationMessage,
        onInstall = onInstallSource,
        onScanQr = onScanSourceQr,
        onRefresh = onRefreshSource,
        onClearCache = onClearSourceCache,
        onRemove = onRemoveSource,
        onLogin = onLoginSource,
        onLogout = onLogoutSource,
        youtubeImport = uiState.youtubeImport,
        onImportYoutube = onImportYoutube,
        onDismissYoutubeImport = onDismissYoutubeImport,
        search = uiState.search,
        onSearchQueryChange = onSearchQueryChange,
        onSearchSubmit = onSearchSubmit,
        onLoadMoreSearch = onLoadMoreSearch,
    )

    LaunchedEffect(selectedVideo?.id) {
        if (selectedVideo == null) {
            fullscreenEnteredByRotation = false
            isFullscreen = false
        }
    }

    val fullscreenVideo = selectedVideo ?: playbackVideo
    val portraitFullscreen = usePortraitPlayerFullscreen(fullscreenVideo, uiState.playback)
    val windowOrientation = LocalConfiguration.current.orientation
    LaunchedEffect(isFullscreen, portraitFullscreen) {
        onFullscreenPresentationChanged(isFullscreen, portraitFullscreen)
    }
    LaunchedEffect(deviceIsLandscape, selectedVideo?.id, playerTransitionProgress) {
        val expandedNowPlaying = selectedVideo != null && playerTransitionProgress < 0.01f
        when {
            deviceIsLandscape && expandedNowPlaying && !isFullscreen && !portraitFullscreen -> {
                fullscreenEnteredByRotation = true
                isFullscreen = true
            }
            !deviceIsLandscape && isFullscreen && fullscreenEnteredByRotation -> {
                fullscreenEnteredByRotation = false
                isFullscreen = false
            }
        }
    }

    PredictiveBackHandler(
        enabled = isFullscreen || (
            selectedVideo != null || playerTransitionProgress < 0.999f ||
                selectedChannel != null ||
                selectedPlaylist != null || nestedBackDestinationName != null
                    || topLevelBackDestination(selected) != null
            ),
    ) { backEvents ->
        val minimizesPlayer = !isFullscreen &&
            (selectedVideo != null || playerTransitionProgress < 0.999f)
        if (minimizesPlayer) {
            playback.onTransitionDragStart()
            try {
                backEvents.collect { event ->
                    playback.onTransitionProgressChange(event.progress)
                }
                playback.onCollapse()
            } catch (_: CancellationException) {
                playback.onTransitionRelease(0f)
            }
        } else {
            try {
                backEvents.collect { event -> navigationBackProgress = event.progress }
                if (isFullscreen) {
                    fullscreenEnteredByRotation = false
                    isFullscreen = false
                } else {
                    onNavigateBack()
                }
                navigationBackProgress = 0f
            } catch (_: CancellationException) {
                animate(
                    initialValue = navigationBackProgress,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 160),
                ) { value, _ -> navigationBackProgress = value }
                navigationBackProgress = 0f
            }
        }
    }

    if (isFullscreen && fullscreenVideo != null) {
        FullscreenPlayerScreen(
            video = fullscreenVideo,
            player = playback.player,
            playback = playback.state,
            isLoadingPlayback = playback.nowPlaying.isLoadingPlayback,
            onTogglePlayback = playback.onToggle,
            onSkipPrevious = playback.onPrevious,
            onSkipNext = playback.onNext,
            onSeekBy = playback.onSeekBy,
            onSeek = playback.onSeek,
            onSpeedChange = playback.onSpeedChange,
            perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
            videoPlaybackSpeedOverride = playback.videoPlaybackSpeeds[fullscreenVideo.id],
            channelPlaybackSpeed = playback.channelPlaybackSpeeds[fullscreenVideo.playbackChannelKey()],
            defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
            preferredAudioLanguage = playback.preferredAudioLanguage,
            preferOriginalAudio = playback.preferOriginalAudio,
            onUseChannelSpeed = playback.onUseChannelSpeed,
            onChannelSpeedChange = { speed ->
                playback.onChannelSpeedChange(fullscreenVideo.playbackChannelKey(), speed)
            },
            onQualityChange = playback.onQualityChange,
            onAudioLanguageChange = playback.onAudioLanguageChange,
            onCaptionsEnabledChange = playback.onCaptionsEnabledChange,
            onSubtitleLanguageChange = playback.onSubtitleLanguageChange,
            onRetryPlayback = playback.onRetry,
            resumePositionFraction = playback.nowPlaying.resumePositionFraction,
            onResumeFromHistory = playback.onResumeFromHistory,
            onExitFullscreen = playback.onExitFullscreen,
            portraitFullscreen = portraitFullscreen,
            modifier = Modifier.graphicsLayer {
                val progress = navigationBackProgress.coerceIn(0f, 1f)
                scaleX = 1f - 0.04f * progress
                scaleY = 1f - 0.04f * progress
                alpha = 1f - 0.22f * progress
            },
        )
    } else if (shouldCoverAppChromeDuringOrientationHandoff(windowOrientation)) {
        // Fullscreen state changes before Android finishes returning this window to portrait.
        // Keep that hand-off opaque so ordinary app chrome is never drawn or visibly rotated
        // inside the temporary landscape viewport.
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        when (navigationLayoutFor(maxWidth.value.toInt())) {
            NavigationLayout.BottomBar -> BottomNavigationLayout(
                selected = selected,
                navigationSelected = nestedBackDestinationName
                    ?.let(GrayjayDestination::valueOf)
                    ?: selected,
                selectedVideo = selectedVideo,
                selectedChannel = selectedChannel,
                selectedPlaylist = selectedPlaylist,
                playback = playback,
                sourcePresentation = sources,
                onSelect = onSelect,
                onVideoClick = onVideoClick,
                onChannelClick = onChannelClick,
                onPlaylistClick = onPlaylistClick,
                onRemotePlaylistVideoLongClick = { video ->
                    actionIsRemotePlaylistVideo = true
                    actionVideoId = video.id
                },
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = onPrivateSessionChange,
                onImportDatabase = onChooseDatabaseImport,
                onImportNewPipeDatabase = onChooseNewPipeImport,
                videos = (uiState.videos + uiState.subscriptionVideos).distinctBy(VideoUiModel::id),
                channels = uiState.channels,
                playlists = uiState.playlists,
            )
            NavigationLayout.Rail -> RailNavigationLayout(
                selected = selected,
                selectedVideo = selectedVideo,
                selectedChannel = selectedChannel,
                selectedPlaylist = selectedPlaylist,
                playback = playback,
                sourcePresentation = sources,
                onSelect = onSelect,
                onVideoClick = onVideoClick,
                onChannelClick = onChannelClick,
                onPlaylistClick = onPlaylistClick,
                onRemotePlaylistVideoLongClick = { video ->
                    actionIsRemotePlaylistVideo = true
                    actionVideoId = video.id
                },
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = onPrivateSessionChange,
                onImportDatabase = onChooseDatabaseImport,
                onImportNewPipeDatabase = onChooseNewPipeImport,
                videos = (uiState.videos + uiState.subscriptionVideos).distinctBy(VideoUiModel::id),
                channels = uiState.channels,
                playlists = uiState.playlists,
            )
            NavigationLayout.PermanentDrawer -> DrawerNavigationLayout(
                selected = selected,
                selectedVideo = selectedVideo,
                selectedChannel = selectedChannel,
                selectedPlaylist = selectedPlaylist,
                playback = playback,
                sourcePresentation = sources,
                onSelect = onSelect,
                onVideoClick = onVideoClick,
                onChannelClick = onChannelClick,
                onPlaylistClick = onPlaylistClick,
                onRemotePlaylistVideoLongClick = { video ->
                    actionIsRemotePlaylistVideo = true
                    actionVideoId = video.id
                },
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = onPrivateSessionChange,
                onImportDatabase = onChooseDatabaseImport,
                onImportNewPipeDatabase = onChooseNewPipeImport,
                videos = (uiState.videos + uiState.subscriptionVideos).distinctBy(VideoUiModel::id),
                channels = uiState.channels,
                playlists = uiState.playlists,
            )
        }
    }

    actionVideoId?.let(availableVideosById::get)?.let { video ->
        VideoActionsSheet(
            video = video,
            download = uiState.downloads[video.id],
            onDismiss = {
                actionVideoId = null
                actionIsRemotePlaylistVideo = false
            },
            onToggleDownload = { onToggleDownloaded(video.id) },
            onDownloadAudio = { onToggleAudioDownloaded(video.id) },
            onShare = {
                val shareUrl = video.shareUrl.ifBlank { video.contentUrl.ifBlank { video.id } }
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, video.title)
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        },
                        shareVideoLabel,
                    ),
                )
            },
            onAddToPlaylist = { playlistPickerVideoIds = listOf(video.id) },
            onPlayFromHere = if (actionIsRemotePlaylistVideo) {
                { playback.onPlayRemotePlaylistFrom(video.id) }
            } else null,
        )
    }
    if (playlistPickerVideoIds.isNotEmpty()) {
        PlaylistPickerDialog(
            playlists = uiState.playlists,
            videoIds = playlistPickerVideoIds,
            onDismiss = { playlistPickerVideoIds = emptyList() },
            onAdd = onAddVideosToPlaylist,
            onCreate = onCreatePlaylist,
        )
    }
    DatabaseImportDialogs(
        state = uiState.databaseImport,
        onDismiss = onDismissDatabaseImport,
        onPasswordSubmit = onRetryDatabaseImport,
        onConfirm = onConfirmDatabaseImport,
    )
    uiState.sourceTrustRequest?.let { request ->
        SourceTrustDialog(
            request = request,
            onTrust = onTrustUnverifiedSource,
            onReject = onRejectUnverifiedSource,
        )
    }
    ProfileSwitcherDialogs(
        profiles = uiState.profiles,
        activeProfileId = uiState.activeProfileId,
        visible = profileDialogVisible,
        onDismiss = { profileDialogVisible = false },
        onSwitch = onSwitchProfile,
        onCreate = onCreateProfile,
        onVerifyPin = onVerifyProfilePin,
    )
    if (chromecastSheetVisible) {
        ChromecastSheet(
            state = uiState.chromecast,
            onConnect = onConnectChromecast,
            onDisconnect = onDisconnectChromecast,
            onDismiss = { chromecastSheetVisible = false },
        )
    }
}

internal fun usePortraitPlayerFullscreen(
    video: VideoUiModel?,
    playback: PlaybackUiState,
): Boolean {
    if (video == null) return false
    val isShortsUrl = sequenceOf(video.id, video.contentUrl, video.shareUrl)
        .filter(String::isNotBlank)
        .any { url -> url.contains("/shorts/", ignoreCase = true) }
    val dimensionsMatchVideo = playback.currentVideoId == video.id
    val width = playback.currentVideoWidth?.takeIf { dimensionsMatchVideo } ?: 0
    val height = playback.currentVideoHeight?.takeIf { dimensionsMatchVideo } ?: 0
    return isShortsUrl || (width > 0 && height > width)
}

@Composable
private fun BottomNavigationLayout(
    selected: GrayjayDestination,
    navigationSelected: GrayjayDestination,
    selectedVideo: VideoUiModel?,
    selectedChannel: ChannelUiModel?,
    selectedPlaylist: PlaylistUiModel?,
    playback: PlaybackPresentation,
    sourcePresentation: SourcePresentation,
    onSelect: (GrayjayDestination) -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onChannelClick: (ChannelUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onRemotePlaylistVideoLongClick: (VideoUiModel) -> Unit,
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
    onImportNewPipeDatabase: () -> Unit,
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
    playlists: List<PlaylistUiModel>,
) {
    GrayjayScaffold(
        selected = selected,
        selectedVideo = selectedVideo,
        selectedChannel = selectedChannel,
        selectedPlaylist = selectedPlaylist,
        playback = playback,
        sourcePresentation = sourcePresentation,
        onVideoClick = onVideoClick,
        onChannelClick = onChannelClick,
        onPlaylistClick = onPlaylistClick,
        onRemotePlaylistVideoLongClick = onRemotePlaylistVideoLongClick,
        onVideoBack = onVideoBack,
        nestedBackEnabled = nestedBackEnabled,
        onManageSources = onManageSources,
        dynamicColorsEnabled = dynamicColorsEnabled,
        onDynamicColorsChange = onDynamicColorsChange,
        privateSessionEnabled = privateSessionEnabled,
        onPrivateSessionChange = onPrivateSessionChange,
        onImportDatabase = onImportDatabase,
        onImportNewPipeDatabase = onImportNewPipeDatabase,
        videos = videos,
        channels = channels,
        playlists = playlists,
        bottomBar = {
            NavigationBar {
                GrayjayDestination.entries.filter { it.showInCompactNavigation }.forEach { destination ->
                    NavigationBarItem(
                        selected = navigationSelected == destination,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.testTag("nav-${destination.name.lowercase()}"),
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = stringResource(destination.titleRes),
                            )
                        },
                        label = { Text(stringResource(destination.navigationLabelRes)) },
                    )
                }
            }
        },
    )
}

@Composable
private fun RailNavigationLayout(
    selected: GrayjayDestination,
    selectedVideo: VideoUiModel?,
    selectedChannel: ChannelUiModel?,
    selectedPlaylist: PlaylistUiModel?,
    playback: PlaybackPresentation,
    sourcePresentation: SourcePresentation,
    onSelect: (GrayjayDestination) -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onChannelClick: (ChannelUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onRemotePlaylistVideoLongClick: (VideoUiModel) -> Unit,
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
    onImportNewPipeDatabase: () -> Unit,
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
    playlists: List<PlaylistUiModel>,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            header = {
                GrayjoyMark(Modifier.padding(vertical = 16.dp))
            },
        ) {
            Spacer(Modifier.weight(1f))
            GrayjayDestination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(destination.icon, contentDescription = stringResource(destination.titleRes))
                    },
                    label = { Text(stringResource(destination.navigationLabelRes)) },
                )
            }
            Spacer(Modifier.weight(1f))
        }
        GrayjayScaffold(
            selected = selected,
            selectedVideo = selectedVideo,
            selectedChannel = selectedChannel,
            selectedPlaylist = selectedPlaylist,
            playback = playback,
            sourcePresentation = sourcePresentation,
            onVideoClick = onVideoClick,
            onChannelClick = onChannelClick,
            onPlaylistClick = onPlaylistClick,
            onRemotePlaylistVideoLongClick = onRemotePlaylistVideoLongClick,
            onVideoBack = onVideoBack,
            nestedBackEnabled = nestedBackEnabled,
            onManageSources = onManageSources,
            dynamicColorsEnabled = dynamicColorsEnabled,
            onDynamicColorsChange = onDynamicColorsChange,
            privateSessionEnabled = privateSessionEnabled,
            onPrivateSessionChange = onPrivateSessionChange,
            onImportDatabase = onImportDatabase,
            onImportNewPipeDatabase = onImportNewPipeDatabase,
            videos = videos,
            channels = channels,
            playlists = playlists,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DrawerNavigationLayout(
    selected: GrayjayDestination,
    selectedVideo: VideoUiModel?,
    selectedChannel: ChannelUiModel?,
    selectedPlaylist: PlaylistUiModel?,
    playback: PlaybackPresentation,
    sourcePresentation: SourcePresentation,
    onSelect: (GrayjayDestination) -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onChannelClick: (ChannelUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onRemotePlaylistVideoLongClick: (VideoUiModel) -> Unit,
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
    onImportNewPipeDatabase: () -> Unit,
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
    playlists: List<PlaylistUiModel>,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(Modifier.width(280.dp)) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GrayjoyMark()
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                }
                GrayjayDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        selected = selected == destination,
                        onClick = { onSelect(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.titleRes)) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        GrayjayScaffold(
            selected = selected,
            selectedVideo = selectedVideo,
            selectedChannel = selectedChannel,
            selectedPlaylist = selectedPlaylist,
            playback = playback,
            sourcePresentation = sourcePresentation,
            onVideoClick = onVideoClick,
            onChannelClick = onChannelClick,
            onPlaylistClick = onPlaylistClick,
            onRemotePlaylistVideoLongClick = onRemotePlaylistVideoLongClick,
            onVideoBack = onVideoBack,
            nestedBackEnabled = nestedBackEnabled,
            onManageSources = onManageSources,
            dynamicColorsEnabled = dynamicColorsEnabled,
            onDynamicColorsChange = onDynamicColorsChange,
            privateSessionEnabled = privateSessionEnabled,
            onPrivateSessionChange = onPrivateSessionChange,
            onImportDatabase = onImportDatabase,
            onImportNewPipeDatabase = onImportNewPipeDatabase,
            videos = videos,
            channels = channels,
            playlists = playlists,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrayjayScaffold(
    selected: GrayjayDestination,
    selectedVideo: VideoUiModel?,
    selectedChannel: ChannelUiModel?,
    selectedPlaylist: PlaylistUiModel?,
    playback: PlaybackPresentation,
    sourcePresentation: SourcePresentation,
    onVideoClick: (VideoUiModel) -> Unit,
    onChannelClick: (ChannelUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onRemotePlaylistVideoLongClick: (VideoUiModel) -> Unit,
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
    onImportNewPipeDatabase: () -> Unit,
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
    playlists: List<PlaylistUiModel>,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var expandedPlayerBounds by remember { mutableStateOf<Rect?>(null) }
    var miniPlayerBounds by remember { mutableStateOf<Rect?>(null) }
    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var isTransitionDragging by remember { mutableStateOf(false) }
    val transitionVideo = selectedVideo ?: playback.video
    val transitionActive = transitionVideo != null &&
        (selectedVideo != null || playback.transitionProgress < 0.999f)
    val transitionMiniChromeAlpha =
        ((playback.transitionProgress - 0.8f) / 0.2f).coerceIn(0f, 1f)
    val predictiveBackTransform = Modifier.graphicsLayer {
        val progress = playback.navigationBackProgress.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0f, 0.5f)
        translationX = size.width * 0.16f * progress
        scaleX = 1f - 0.025f * progress
        scaleY = 1f - 0.025f * progress
        alpha = 1f - 0.12f * progress
    }
    val dragState = rememberDraggableState { delta ->
        val expanded = expandedPlayerBounds
        val minimized = miniPlayerBounds
        val travel = if (expanded != null && minimized != null) {
            abs(minimized.top - expanded.top).coerceAtLeast(1f)
        } else {
            with(density) { 560.dp.toPx() }
        }
        playback.onTransitionProgressChange(playback.transitionProgress + delta / travel)
    }
    val transitionDragModifier = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = {
            isTransitionDragging = true
            playback.onTransitionDragStart()
        },
        onDragStopped = { velocity ->
            isTransitionDragging = false
            val target = when {
                velocity > 1_200f -> 1f
                velocity < -1_200f -> 0f
                playback.transitionProgress >= 0.5f -> 1f
                else -> 0f
            }
            playback.onTransitionRelease(target)
        },
    )

    Box(
        modifier.onGloballyPositioned { rootHeightPx = it.size.height.toFloat() },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val isRootSearch = selected == GrayjayDestination.Search &&
                    selectedChannel == null && selectedPlaylist == null && !nestedBackEnabled
                if (!isRootSearch) CenterAlignedTopAppBar(
                    modifier = predictiveBackTransform.then(
                        if (transitionActive) Modifier.clearAndSetSemantics { }
                        else Modifier,
                    ),
                    title = {
                        if (selectedChannel != null) {
                            Text(selectedChannel.name)
                        } else if (selectedPlaylist != null) {
                            Text(selectedPlaylist.title)
                        } else if (selected == GrayjayDestination.Home) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (playback.showPrivateThemeToggle) {
                                    Icon(
                                        Icons.Outlined.Masks,
                                        contentDescription = stringResource(R.string.profile_private),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("private-profile-mark"),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    GrayjoyMark(Modifier.size(28.dp))
                                }
                                Text(stringResource(R.string.app_name))
                            }
                        } else {
                            Text(stringResource(selected.titleRes))
                        }
                    },
                    navigationIcon = {
                        if (selectedChannel != null || selectedPlaylist != null || nestedBackEnabled) {
                            IconButton(onClick = onVideoBack) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        } else if (
                            selected == GrayjayDestination.Home &&
                            playback.showPrivateThemeToggle
                        ) {
                            IconButton(
                                onClick = {
                                    playback.onDarkThemeChange(!playback.isDarkTheme)
                                },
                            ) {
                                Icon(
                                    if (playback.isDarkTheme) {
                                        Icons.Outlined.LightMode
                                    } else {
                                        Icons.Outlined.DarkMode
                                    },
                                    contentDescription = if (playback.isDarkTheme) {
                                        stringResource(R.string.use_light_theme)
                                    } else {
                                        stringResource(R.string.use_dark_theme)
                                    },
                                )
                            }
                        }
                    },
                    actions = {
                        if (privateSessionEnabled) {
                            IconButton(onClick = { onPrivateSessionChange(false) }) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = stringResource(R.string.private_session_enabled),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(onClick = playback.onOpenProfiles) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = stringResource(R.string.open_profile),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    playback.video?.let { video ->
                        MiniPlayer(
                            video = video,
                            isPlaying = playback.isPlaying,
                            progress = if (playback.state.durationMs > 0) {
                                playback.state.positionMs.toFloat() / playback.state.durationMs
                            } else {
                                0f
                            },
                            canSkip = playback.queueSize > 1,
                            chromeAlpha = if (transitionActive) 0f else 1f,
                            onExpand = playback.onExpand,
                            onTogglePlayback = playback.onToggle,
                            onSkipToNext = playback.onNext,
                            onClose = playback.onClose,
                            onVideoBoundsChanged = { miniPlayerBounds = it },
                            modifier = transitionDragModifier,
                        )
                    }
                    bottomBar()
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .then(predictiveBackTransform)
                    .then(
                        if (transitionActive) Modifier.clearAndSetSemantics { }
                        else Modifier,
                    ),
            ) {
                val contentPageKey = when {
                    selectedChannel != null -> "channel:${selectedChannel.id}"
                    selectedPlaylist != null -> "playlist:${selectedPlaylist.id}"
                    else -> "destination:${selected.name}"
                }
                AnimatedContent(
                    targetState = contentPageKey,
                    transitionSpec = {
                        (fadeIn(tween(210)) + scaleIn(tween(240), initialScale = 0.985f))
                            .togetherWith(
                                fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 1.01f),
                            )
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "app-page-transition",
                ) { animatedPageKey ->
                val animatedChannel = animatedPageKey
                    .takeIf { it.startsWith("channel:") }
                    ?.substringAfter("channel:")
                    ?.let { id -> channels.firstOrNull { it.id == id } }
                val animatedPlaylist = animatedPageKey
                    .takeIf { it.startsWith("playlist:") }
                    ?.substringAfter("playlist:")
                    ?.let { id ->
                        selectedPlaylist?.takeIf { it.id == id }
                            ?: playlists.firstOrNull { it.id == id }
                    }
                val animatedDestination = animatedPageKey
                    .takeIf { it.startsWith("destination:") }
                    ?.substringAfter("destination:")
                    ?.let { name -> runCatching { GrayjayDestination.valueOf(name) }.getOrNull() }
                    ?: selected
                if (animatedChannel != null) {
                ChannelDetailScreen(
                    channel = animatedChannel,
                    detail = playback.channelDetail,
                    onLoadMore = playback.onLoadMoreChannel,
                    onTabSelected = playback.onChannelTabSelected,
                    onPlaylistClick = onPlaylistClick,
                    isFollowing = animatedChannel.id in playback.followedCreatorIds,
                    onFollowingChange = {
                        playback.onCreatorFollowedChange(animatedChannel.id, it)
                    },
                    onVideoClick = onVideoClick,
                    onVideoLongClick = playback.onVideoLongClick,
                    perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                    channelPlaybackSpeed = playback.channelPlaybackSpeeds[animatedChannel.id],
                    defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
                    onPlaybackSpeedChange = { speed ->
                        playback.onChannelSpeedChange(animatedChannel.id, speed)
                    },
                )
                } else if (animatedPlaylist != null) {
                if (animatedPlaylist.sourceId.isBlank()) PlaylistDetailScreen(
                    playlist = animatedPlaylist,
                    videos = (videos + playback.libraryVideos).distinctBy(VideoUiModel::id),
                    downloads = playback.downloads,
                    activeDownloadMediaTypes = playback.activePlaylistDownloads
                        .filter { it.playlistId == animatedPlaylist.id }
                        .mapTo(mutableSetOf()) { it.mediaType },
                    onVideoClick = onVideoClick,
                    onVideoLongClick = playback.onVideoLongClick,
                    onPlayAll = { playback.onPlayPlaylist(animatedPlaylist.id) },
                    onPlayFromHere = { videoId ->
                        playback.onPlayPlaylistFrom(animatedPlaylist.id, videoId)
                    },
                    onDownloadAllAsAudio = { ids ->
                        playback.onDownloadPlaylist(animatedPlaylist.id, DownloadMediaType.Audio)
                    },
                    onDownloadAllAsVideo = { ids ->
                        playback.onDownloadPlaylist(animatedPlaylist.id, DownloadMediaType.Video)
                    },
                    onCancelDownloadAllAsAudio = {
                        playback.onCancelDownloadPlaylist(
                            animatedPlaylist.id,
                            DownloadMediaType.Audio,
                        )
                    },
                    onCancelDownloadAllAsVideo = {
                        playback.onCancelDownloadPlaylist(
                            animatedPlaylist.id,
                            DownloadMediaType.Video,
                        )
                    },
                    onRename = { title ->
                        playback.onRenamePlaylist(animatedPlaylist.id, title)
                    },
                    onAddSelectionToPlaylist = playback.onAddSelectionToPlaylist,
                    onRemoveVideos = { ids ->
                        playback.onRemoveVideosFromPlaylist(animatedPlaylist.id, ids)
                    },
                    onReorder = { ids ->
                        playback.onReorderPlaylist(animatedPlaylist.id, ids)
                    },
                )
                else RemotePlaylistDetailScreen(
                    detail = playback.remotePlaylistDetail,
                    downloads = playback.downloads,
                    localPlaylists = playlists,
                    onVideoClick = onVideoClick,
                    onVideoLongClick = onRemotePlaylistVideoLongClick,
                    onPlayAll = playback.onPlayRemotePlaylist,
                    onDownloadAll = playback.onDownloadRemotePlaylist,
                    onCancelDownloadAll = playback.onCancelDownloadRemotePlaylist,
                    onCreateLocalPlaylist = playback.onCreateLocalPlaylistFromRemote,
                    onLoadMore = playback.onLoadMoreRemotePlaylist,
                )
                } else {
                    when (animatedDestination) {
                    GrayjayDestination.Home -> HomeScreen(
                        home = sourcePresentation.home,
                        onFeedSelected = sourcePresentation.onHomeFeedSelected,
                        onRefresh = sourcePresentation.onRefreshHome,
                        onLoadMore = sourcePresentation.onLoadMoreHome,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = playback.onVideoLongClick,
                        pcPlayback = playback.pcLink.activePlayback,
                        onPlayFromComputer = playback.onPlayFromComputer,
                        onToggleComputerPlayback = playback.onToggleComputerPlayback,
                        onPreviousComputerPlayback = playback.onPreviousComputerPlayback,
                        onNextComputerPlayback = playback.onNextComputerPlayback,
                        onSeekComputerPlayback = playback.onSeekComputerPlayback,
                    )
                    GrayjayDestination.Subscriptions -> SubscriptionsScreen(
                        channels = channels,
                        videos = (
                            videos + playback.nowPlaying.recommendations +
                                listOfNotNull(playback.nowPlaying.video)
                            ).distinctBy(VideoUiModel::id),
                        followedCreatorIds = playback.followedCreatorIds,
                        onFollowedChange = playback.onCreatorFollowedChange,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = playback.onVideoLongClick,
                        onQueueSelection = playback.onQueueVideos,
                        onChannelClick = onChannelClick,
                    )
                    GrayjayDestination.Search -> SearchScreen(
                        search = sourcePresentation.search,
                        sources = sourcePresentation.sources,
                        onQueryChange = sourcePresentation.onSearchQueryChange,
                        onSubmit = sourcePresentation.onSearchSubmit,
                        onLoadMore = sourcePresentation.onLoadMoreSearch,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = playback.onVideoLongClick,
                        onChannelClick = onChannelClick,
                        onPlaylistClick = onPlaylistClick,
                    )
                    GrayjayDestination.Library -> LibraryScreen(
                        videos = playback.libraryVideos,
                        playlists = playlists,
                        downloads = playback.downloads,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = playback.onVideoLongClick,
                        onPlaylistClick = onPlaylistClick,
                        onAddSelectionToPlaylist = playback.onAddSelectionToPlaylist,
                        onQueueSelection = playback.onQueueVideos,
                        onRemoveSelectionFromHistory = playback.onRemoveSelectionFromHistory,
                        onRemoveDownloads = playback.onRemoveDownloads,
                        onRemovePlaylists = playback.onRemovePlaylists,
                        onExportDownloads = playback.onExportDownloads,
                        onRenamePlaylist = playback.onRenamePlaylist,
                    )
                    GrayjayDestination.Settings -> SettingsScreen(
                        dynamicColorsEnabled = dynamicColorsEnabled,
                        onDynamicColorsChange = onDynamicColorsChange,
                        themeMode = playback.themeMode,
                        onThemeModeChange = playback.onThemeModeChange,
                        privateSessionEnabled = privateSessionEnabled,
                        onPrivateSessionChange = onPrivateSessionChange,
                        onManageSources = onManageSources,
                        onImportDatabase = onImportDatabase,
                        onImportNewPipeDatabase = onImportNewPipeDatabase,
                        activeSourceCount = sourcePresentation.sources.count {
                            it.isEnabled && it.availability != SourceAvailability.MissingPlugin
                        },
                        defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
                        onDefaultPlaybackSpeedChange = playback.onDefaultPlaybackSpeedChange,
                        perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                        onPerChannelPlaybackSpeedChange = playback.onPerChannelPlaybackSpeedChange,
                        preferredVideoQuality = playback.preferredVideoQuality,
                        onPreferredVideoQualityChange = playback.onPreferredVideoQualityChange,
                        preferredAudioBitrate = playback.preferredAudioBitrate,
                        onPreferredAudioBitrateChange = playback.onPreferredAudioBitrateChange,
                        preferredAudioLanguage = playback.preferredAudioLanguage,
                        onPreferredAudioLanguageChange = playback.onPreferredAudioLanguageChange,
                        preferOriginalAudio = playback.preferOriginalAudio,
                        onPreferOriginalAudioChange = playback.onPreferOriginalAudioChange,
                        stickyCaptionsEnabled = playback.stickyCaptionsEnabled,
                        onStickyCaptionsChange = playback.onStickyCaptionsChange,
                        showRecommendations = playback.showRecommendations,
                        onShowRecommendationsChange = playback.onShowRecommendationsChange,
                        searchHistoryEnabled = playback.searchHistoryEnabled,
                        onSearchHistoryChange = playback.onSearchHistoryChange,
                        keepScreenAwake = playback.keepScreenAwake,
                        onKeepScreenAwakeChange = playback.onKeepScreenAwakeChange,
                        pictureInPictureEnabled = playback.pictureInPictureEnabled,
                        onPictureInPictureChange = playback.onPictureInPictureChange,
                        otherAudioDuckingEnabled = playback.otherAudioDuckingEnabled,
                        onOtherAudioDuckingChange = playback.onOtherAudioDuckingChange,
                        otherAudioDuckVolumePercent = playback.otherAudioDuckVolumePercent,
                        onOtherAudioDuckVolumeChange = playback.onOtherAudioDuckVolumeChange,
                        pcLink = playback.pcLink,
                        onScanPcPairingQr = playback.onScanPcPairingQr,
                        onRemovePairedComputer = playback.onRemovePairedComputer,
                        availableUpdate = playback.availableUpdate.takeIf {
                            RELEASE_UPDATE_CHECK_ENABLED
                        },
                    )
                    GrayjayDestination.Sources -> SourcesScreen(
                        sources = sourcePresentation.sources,
                        isOperationInProgress = sourcePresentation.isOperationInProgress,
                        operationMessage = sourcePresentation.operationMessage,
                        onSourceEnabledChange = sourcePresentation.onEnabledChange,
                        onInstallSource = sourcePresentation.onInstall,
                        onScanSourceQr = sourcePresentation.onScanQr,
                        onRefreshSource = sourcePresentation.onRefresh,
                        onClearSourceCache = sourcePresentation.onClearCache,
                        onRemoveSource = sourcePresentation.onRemove,
                        onLoginSource = sourcePresentation.onLogin,
                        onLogoutSource = sourcePresentation.onLogout,
                        youtubeImport = sourcePresentation.youtubeImport,
                        onImportYoutube = sourcePresentation.onImportYoutube,
                        onDismissYoutubeImport = sourcePresentation.onDismissYoutubeImport,
                    )
                    }
                }
                }
            }
        }

        if (transitionActive) {
            val transitionProgress = playback.transitionProgress.coerceIn(0f, 1f)
            val contentAlpha = ((0.7f - transitionProgress) / 0.7f).coerceIn(0f, 1f)
            val expanded = expandedPlayerBounds
            val minimized = miniPlayerBounds
            val overlayTop = (minimized?.top ?: 0f) * transitionProgress
            val overlayHeight = if (rootHeightPx > 0f && minimized != null) {
                lerp(rootHeightPx, minimized.height, transitionProgress)
            } else {
                rootHeightPx.coerceAtLeast(1f)
            }
            val overlayContentOffsetY = -(expanded?.top ?: 0f) * transitionProgress
            val overlayTransformY = overlayTop + overlayContentOffsetY
            val transitionSurfaceColor = androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainerHigh,
                transitionProgress,
            )
            TransitionViewport(
                viewportHeightPx = overlayHeight,
                contentHeightPx = rootHeightPx.coerceAtLeast(1f),
                modifier = Modifier
                    .offset { IntOffset(0, overlayTop.roundToInt()) }
                    .fillMaxWidth()
                    .background(transitionSurfaceColor)
                    .clipToBounds()
                    .zIndex(1f),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier
                            .offset { IntOffset(0, overlayContentOffsetY.roundToInt()) }
                            .fillMaxSize(),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        topBar = {
                            CenterAlignedTopAppBar(
                                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(stringResource(R.string.now_playing))
                                        if (hasNowPlayingDownload(playback.downloads[transitionVideo.id])) {
                                            Icon(
                                                Icons.Outlined.DownloadDone,
                                                contentDescription = stringResource(R.string.playing_downloaded),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .testTag("now-playing-downloaded"),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = playback.onCollapse) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = stringResource(R.string.back),
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = playback.onOpenChromecast,
                                        modifier = Modifier.testTag("chromecast-button"),
                                    ) {
                                        Icon(
                                            if (playback.chromecast.isConnected) Icons.Outlined.CastConnected
                                            else Icons.Outlined.Cast,
                                            contentDescription = stringResource(R.string.cast_to_device),
                                            tint = if (playback.chromecast.isConnected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    ) { contentPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding)
                                .graphicsLayer { alpha = contentAlpha },
                        ) {
                            VideoDetailScreen(
                                video = transitionVideo,
                                download = playback.downloads[transitionVideo.id],
                                player = playback.player,
                                playback = playback.state,
                                nowPlaying = playback.nowPlaying,
                                queueVideos = playback.queue,
                                onTogglePlayback = playback.onToggle,
                                onSkipPrevious = playback.onPrevious,
                                onSkipNext = playback.onNext,
                                onSeekBy = playback.onSeekBy,
                                onToggleWatchLater = {
                                    playback.onToggleWatchLater(transitionVideo.id)
                                },
                                onToggleDownload = {
                                    playback.onToggleDownloaded(transitionVideo.id)
                                },
                                onDownloadVideo = { height ->
                                    playback.onDownloadVideo(transitionVideo.id, height)
                                },
                                onToggleAudioDownload = {
                                    playback.onToggleAudioDownloaded(transitionVideo.id)
                                },
                                onDownloadAudio = { bitrate ->
                                    playback.onDownloadAudio(transitionVideo.id, bitrate)
                                },
                                onAddToPlaylist = {
                                    playback.onAddSelectionToPlaylist(listOf(transitionVideo.id))
                                },
                                preferredVideoQuality = playback.preferredVideoQuality,
                                preferredAudioBitrate = playback.preferredAudioBitrate,
                                onToggleFollowing = playback.onToggleFollowing,
                                onSeek = playback.onSeek,
                                onSpeedChange = playback.onSpeedChange,
                                perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                                videoPlaybackSpeedOverride =
                                    playback.videoPlaybackSpeeds[transitionVideo.id],
                                channelPlaybackSpeed = playback.channelPlaybackSpeeds[
                                    transitionVideo.playbackChannelKey()
                                ],
                                defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
                                onUseChannelSpeed = playback.onUseChannelSpeed,
                                onChannelSpeedChange = { speed ->
                                    playback.onChannelSpeedChange(
                                        transitionVideo.playbackChannelKey(),
                                        speed,
                                    )
                                },
                                onQualityChange = playback.onQualityChange,
                                preferredAudioLanguage = playback.preferredAudioLanguage,
                                preferOriginalAudio = playback.preferOriginalAudio,
                                onAudioLanguageChange = playback.onAudioLanguageChange,
                                onCaptionsEnabledChange = playback.onCaptionsEnabledChange,
                                onSubtitleLanguageChange = playback.onSubtitleLanguageChange,
                                onRetryPlayback = playback.onRetry,
                                onVideoClick = onVideoClick,
                                onVideoLongClick = playback.onVideoLongClick,
                                creatorChannel = playback.channels.firstOrNull { channel ->
                                    channel.id == transitionVideo.authorUrl.ifBlank {
                                        transitionVideo.channelId.ifBlank {
                                            "${transitionVideo.sourceId}:${transitionVideo.creator}"
                                        }
                                    }
                                },
                                onCreatorPreview = playback.onLoadChannel,
                                onCreatorClick = onChannelClick,
                                onFullscreen = playback.onEnterFullscreen,
                                onLoadMoreRecommendations = playback.onLoadMoreRecommendations,
                                onLoadMoreComments = playback.onLoadMoreComments,
                                onResumeFromHistory = playback.onResumeFromHistory,
                                renderPlayer = false,
                                onPlayerBoundsChanged = { measuredBounds ->
                                    val normalizedBounds = Rect(
                                        left = measuredBounds.left,
                                        top = measuredBounds.top - overlayTransformY,
                                        right = measuredBounds.right,
                                        bottom = measuredBounds.bottom - overlayTransformY,
                                    )
                                    if (
                                        normalizedBounds.width > 1f &&
                                        normalizedBounds.height > 1f &&
                                        (expandedPlayerBounds == null || transitionProgress <= 0.001f)
                                    ) {
                                        expandedPlayerBounds = normalizedBounds
                                    }
                                },
                            )
                        }
                    }
                    if (minimized != null && transitionMiniChromeAlpha > 0.001f) {
                        MiniPlayerChrome(
                            video = transitionVideo,
                            isPlaying = playback.isPlaying,
                            progress = if (playback.state.durationMs > 0) {
                                playback.state.positionMs.toFloat() / playback.state.durationMs
                            } else {
                                0f
                            },
                            canSkip = playback.queueSize > 1,
                            onTogglePlayback = playback.onToggle,
                            onSkipToNext = playback.onNext,
                            onClose = playback.onClose,
                            applyTestTags = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = transitionMiniChromeAlpha }
                                .clearAndSetSemantics { },
                        )
                    }
                }
            }
        }

        val expanded = expandedPlayerBounds
        val minimized = miniPlayerBounds
        if (transitionVideo != null && (expanded != null || minimized != null)) {
            val start = expanded ?: minimized!!
            val end = minimized ?: expanded!!
            val progress = playback.transitionProgress.coerceIn(0f, 1f)
            val left = lerp(start.left, end.left, progress)
            val top = lerp(start.top, end.top, progress)
            val width = lerp(start.width, end.width, progress).coerceAtLeast(1f)
            val height = lerp(start.height, end.height, progress).coerceAtLeast(1f)
            val queueIndex = playback.state.queueVideoIds.indexOf(playback.state.currentVideoId)
            if (!(transitionVideo.playbackAudioOnly && progress >= 0.999f)) PlayerSurface(
                video = transitionVideo,
                player = playback.player,
                playback = playback.state,
                isLoading = playback.nowPlaying.isLoadingPlayback || playback.state.isBuffering,
                isFullscreen = false,
                canGoPrevious = queueIndex > 0 || playback.state.positionMs > 5_000L,
                canGoNext = queueIndex >= 0 && queueIndex < playback.state.queueVideoIds.lastIndex,
                onTogglePlayback = playback.onToggle,
                onSkipPrevious = playback.onPrevious,
                onSkipNext = playback.onNext,
                onSeekBy = playback.onSeekBy,
                onSeek = playback.onSeek,
                onSpeedChange = playback.onSpeedChange,
                perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                videoPlaybackSpeedOverride = playback.videoPlaybackSpeeds[transitionVideo.id],
                channelPlaybackSpeed =
                    playback.channelPlaybackSpeeds[transitionVideo.playbackChannelKey()],
                defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
                onUseChannelSpeed = playback.onUseChannelSpeed,
                onChannelSpeedChange = { speed ->
                    playback.onChannelSpeedChange(transitionVideo.playbackChannelKey(), speed)
                },
                onQualityChange = playback.onQualityChange,
                onCaptionsEnabledChange = playback.onCaptionsEnabledChange,
                onSubtitleLanguageChange = playback.onSubtitleLanguageChange,
                onRetryPlayback = playback.onRetry,
                onFullscreen = playback.onEnterFullscreen,
                controlsAlpha = if (!isTransitionDragging && progress <= 0.001f) 1f else 0f,
                resumePositionFraction = playback.nowPlaying.resumePositionFraction,
                onResumeFromHistory = playback.onResumeFromHistory,
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(
                        width = with(density) { width.toDp() },
                        height = with(density) { height.toDp() },
                    )
                    .then(transitionDragModifier)
                    .graphicsLayer {
                        alpha = if (transitionVideo.playbackAudioOnly) 1f - progress else 1f
                    }
                    .zIndex(2f),
            )
        }
    }
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private fun VideoUiModel.playbackChannelKey(): String = authorUrl.ifBlank {
    channelId.ifBlank { "$sourceId:$creator" }
}

@Composable
private fun TransitionViewport(
    viewportHeightPx: Float,
    contentHeightPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val contentHeight = contentHeightPx.roundToInt().coerceAtLeast(1)
        val viewportHeight = viewportHeightPx.roundToInt()
            .coerceIn(1, constraints.maxHeight.coerceAtLeast(1))
        val placeables = measurables.map { measurable ->
            measurable.measure(Constraints.fixed(width, contentHeight))
        }
        layout(width, viewportHeight) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun GrayjoyMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF405DB1), Color(0xFF4F91DC), Color(0xFF1BACC6)),
                ),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.grayjoy_logo_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
        )
    }
}
