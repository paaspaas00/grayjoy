package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.testTag
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
import com.futo.platformplayer.compose.ui.screens.LibraryScreen
import com.futo.platformplayer.compose.ui.screens.MiniPlayer
import com.futo.platformplayer.compose.ui.screens.MiniPlayerChrome
import com.futo.platformplayer.compose.ui.screens.PlaylistDetailScreen
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
    val channels: List<ChannelUiModel>,
    val player: Player,
    val state: PlaybackUiState,
    val followedCreatorIds: Set<String>,
    val downloads: Map<String, DownloadUiModel>,
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
    val onQualityChange: (Int?) -> Unit,
    val onCaptionsEnabledChange: (Boolean) -> Unit,
    val onSubtitleLanguageChange: (String?) -> Unit,
    val onEnterFullscreen: () -> Unit,
    val onExitFullscreen: () -> Unit,
    val onRetry: () -> Unit,
    val onToggleFollowing: () -> Unit,
    val onCreatorFollowedChange: (String, Boolean) -> Unit,
    val onLoadChannel: (ChannelUiModel) -> Unit,
    val onLoadMoreChannel: () -> Unit,
    val onLoadMoreRecommendations: () -> Unit,
    val onLoadMoreComments: () -> Unit,
    val onClose: () -> Unit,
    val onPlayQueue: (List<String>) -> Unit,
    val onPlayPlaylist: (String) -> Unit,
    val onPlayPlaylistFrom: (String, String) -> Unit,
    val onToggleWatchLater: (String) -> Unit,
    val onToggleDownloaded: (String) -> Unit,
    val onToggleAudioDownloaded: (String) -> Unit,
    val onDownloadVideo: (String, Int?) -> Unit,
    val onDownloadAudio: (String, Int?) -> Unit,
    val onDownloadVideos: (List<String>, DownloadMediaType) -> Unit,
    val onDownloadPlaylist: (String, DownloadMediaType) -> Unit,
    val onVideoLongClick: (VideoUiModel) -> Unit,
    val onAddSelectionToPlaylist: (List<String>) -> Unit,
    val onRemoveSelectionFromHistory: (List<String>) -> Unit,
    val onRenamePlaylist: (String, String) -> Unit,
    val onRemoveVideosFromPlaylist: (String, List<String>) -> Unit,
    val onReorderPlaylist: (String, List<String>) -> Unit,
    val onSeek: (Float) -> Unit,
    val libraryVideos: List<VideoUiModel>,
    val onOpenProfiles: () -> Unit,
    val defaultPlaybackSpeed: Float,
    val preferredVideoQuality: Int,
    val preferredAudioBitrate: Int,
    val stickyCaptionsEnabled: Boolean,
    val showRecommendations: Boolean,
    val searchHistoryEnabled: Boolean,
    val keepScreenAwake: Boolean,
    val showPrivateThemeToggle: Boolean,
    val isDarkTheme: Boolean,
    val onDarkThemeChange: (Boolean) -> Unit,
    val onDefaultPlaybackSpeedChange: (Float) -> Unit,
    val onPreferredVideoQualityChange: (Int) -> Unit,
    val onPreferredAudioBitrateChange: (Int) -> Unit,
    val onStickyCaptionsChange: (Boolean) -> Unit,
    val onShowRecommendationsChange: (Boolean) -> Unit,
    val onSearchHistoryChange: (Boolean) -> Unit,
    val onKeepScreenAwakeChange: (Boolean) -> Unit,
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
    onDynamicColorsChange: (Boolean) -> Unit,
    onPrivateSessionChange: (Boolean) -> Unit,
    onOpenVideo: (String) -> Unit,
    onLoadChannel: (ChannelUiModel) -> Unit,
    onHomeFeedSelected: (HomeFeedType) -> Unit,
    onRefreshHome: () -> Unit,
    onLoadMoreHome: () -> Unit = {},
    onPlayQueue: (List<String>) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onPlayPlaylistFrom: (String, String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSeekPlaybackBy: (Long) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVideoQualityChange: (Int?) -> Unit,
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
    onToggleLiked: (String) -> Unit,
    onCreatePlaylist: (String, List<String>) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onAddVideosToPlaylist: (String, List<String>) -> Unit,
    onRemoveVideosFromPlaylist: (String, List<String>) -> Unit,
    onReorderPlaylist: (String, List<String>) -> Unit,
    onRemoveVideosFromHistory: (List<String>) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onInstallSource: (String) -> Unit,
    onScanSourceQr: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onClearSourceCache: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onLoginSource: (SourceUiModel) -> Unit,
    onLogoutSource: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String, SearchContentType, Set<String>) -> Unit,
    onLoadMoreSearch: () -> Unit = {},
    onLoadMoreChannel: () -> Unit = {},
    onLoadMoreRecommendations: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onToggleFollowing: () -> Unit,
    onCreatorFollowedChange: (String, Boolean) -> Unit,
    onChooseDatabaseImport: () -> Unit,
    onRetryDatabaseImport: (String) -> Unit,
    onConfirmDatabaseImport: (DatabaseImportSelection) -> Unit,
    onDismissDatabaseImport: () -> Unit,
    onTrustUnverifiedSource: () -> Unit,
    onRejectUnverifiedSource: () -> Unit,
    onSwitchProfile: (String) -> Unit,
    onCreateProfile: (String, String) -> Unit,
    onVerifyProfilePin: (String, String) -> Boolean,
    onDefaultPlaybackSpeedChange: (Float) -> Unit,
    onPreferredVideoQualityChange: (Int) -> Unit,
    onPreferredAudioBitrateChange: (Int) -> Unit,
    onStickyCaptionsChange: (Boolean) -> Unit,
    onShowRecommendationsChange: (Boolean) -> Unit,
    onSearchHistoryChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    deviceIsLandscape: Boolean = false,
    onFullscreenChanged: (Boolean) -> Unit = {},
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
    var playlistPickerVideoIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var profileDialogVisible by rememberSaveable { mutableStateOf(false) }
    val selected = GrayjayDestination.valueOf(destinationName)
    val availableVideos = uiState.videos + uiState.subscriptionVideos +
        uiState.libraryVideos + uiState.search.videos +
        listOfNotNull(uiState.nowPlaying.video) + uiState.nowPlaying.recommendations
    val selectedVideo = uiState.nowPlaying.video?.takeIf { it.id == selectedVideoId }
        ?: availableVideos.firstOrNull { it.id == selectedVideoId }
    val selectedChannel = uiState.channels.firstOrNull { it.id == selectedChannelId }
    val selectedPlaylist = uiState.playlists.firstOrNull { it.id == selectedPlaylistId }
    val playbackVideo = availableVideos.firstOrNull { it.id == uiState.playback.currentVideoId }
        ?: uiState.nowPlaying.video
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
    val queueVideos = uiState.playback.queueVideoIds.mapNotNull { id ->
        availableVideos.firstOrNull { it.id == id }
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
    val onVideoLongClick: (VideoUiModel) -> Unit = { actionVideoId = it.id }
    val onChannelClick: (ChannelUiModel) -> Unit = {
        onLoadChannel(it)
        selectedChannelId = it.id
        selectedPlaylistId = null
        selectedVideoId = null
        playerTransitionProgress = 1f
    }
    val onPlaylistClick: (PlaylistUiModel) -> Unit = {
        selectedPlaylistId = it.id
        selectedChannelId = null
        selectedVideoId = null
        playerTransitionProgress = 1f
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
        channels = uiState.channels,
        player = player,
        state = uiState.playback,
        followedCreatorIds = uiState.followedCreatorIds,
        downloads = uiState.downloads,
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
        onQualityChange = onVideoQualityChange,
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
        onLoadMoreChannel = onLoadMoreChannel,
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
        onVideoLongClick = onVideoLongClick,
        onAddSelectionToPlaylist = { playlistPickerVideoIds = it },
        onRemoveSelectionFromHistory = onRemoveVideosFromHistory,
        onRenamePlaylist = onRenamePlaylist,
        onRemoveVideosFromPlaylist = onRemoveVideosFromPlaylist,
        onReorderPlaylist = onReorderPlaylist,
        onSeek = onSeekPlayback,
        libraryVideos = uiState.libraryVideos,
        onOpenProfiles = { profileDialogVisible = true },
        defaultPlaybackSpeed = uiState.defaultPlaybackSpeed,
        preferredVideoQuality = uiState.preferredVideoQuality,
        preferredAudioBitrate = uiState.preferredAudioBitrate,
        stickyCaptionsEnabled = uiState.stickyCaptionsEnabled,
        showRecommendations = uiState.showRecommendations,
        searchHistoryEnabled = uiState.searchHistoryEnabled,
        keepScreenAwake = uiState.keepScreenAwake,
        showPrivateThemeToggle = uiState.activeProfileId == "private",
        isDarkTheme = isDarkTheme,
        onDarkThemeChange = onDarkThemeChange,
        onDefaultPlaybackSpeedChange = onDefaultPlaybackSpeedChange,
        onPreferredVideoQualityChange = onPreferredVideoQualityChange,
        onPreferredAudioBitrateChange = onPreferredAudioBitrateChange,
        onStickyCaptionsChange = onStickyCaptionsChange,
        onShowRecommendationsChange = onShowRecommendationsChange,
        onSearchHistoryChange = onSearchHistoryChange,
        onKeepScreenAwakeChange = onKeepScreenAwakeChange,
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

    LaunchedEffect(isFullscreen) {
        onFullscreenChanged(isFullscreen)
    }

    LaunchedEffect(deviceIsLandscape, selectedVideo?.id, playerTransitionProgress) {
        val expandedNowPlaying = selectedVideo != null && playerTransitionProgress < 0.01f
        when {
            deviceIsLandscape && expandedNowPlaying && !isFullscreen -> {
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

    val fullscreenVideo = selectedVideo ?: playbackVideo
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
            onQualityChange = playback.onQualityChange,
            onCaptionsEnabledChange = playback.onCaptionsEnabledChange,
            onSubtitleLanguageChange = playback.onSubtitleLanguageChange,
            onRetryPlayback = playback.onRetry,
            onExitFullscreen = playback.onExitFullscreen,
            modifier = Modifier.graphicsLayer {
                val progress = navigationBackProgress.coerceIn(0f, 1f)
                scaleX = 1f - 0.04f * progress
                scaleY = 1f - 0.04f * progress
                alpha = 1f - 0.22f * progress
            },
        )
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
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = onPrivateSessionChange,
                onImportDatabase = onChooseDatabaseImport,
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
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = onPrivateSessionChange,
                onImportDatabase = onChooseDatabaseImport,
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
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = onPrivateSessionChange,
                onImportDatabase = onChooseDatabaseImport,
                videos = (uiState.videos + uiState.subscriptionVideos).distinctBy(VideoUiModel::id),
                channels = uiState.channels,
                playlists = uiState.playlists,
            )
        }
    }

    availableVideos.firstOrNull { it.id == actionVideoId }?.let { video ->
        VideoActionsSheet(
            video = video,
            download = uiState.downloads[video.id],
            onDismiss = { actionVideoId = null },
            onToggleLike = { onToggleLiked(video.id) },
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
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
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
        onVideoBack = onVideoBack,
        nestedBackEnabled = nestedBackEnabled,
        onManageSources = onManageSources,
        dynamicColorsEnabled = dynamicColorsEnabled,
        onDynamicColorsChange = onDynamicColorsChange,
        privateSessionEnabled = privateSessionEnabled,
        onPrivateSessionChange = onPrivateSessionChange,
        onImportDatabase = onImportDatabase,
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
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
    playlists: List<PlaylistUiModel>,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            header = {
                GrayjayMark(Modifier.padding(vertical = 16.dp))
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
            onVideoBack = onVideoBack,
            nestedBackEnabled = nestedBackEnabled,
            onManageSources = onManageSources,
            dynamicColorsEnabled = dynamicColorsEnabled,
            onDynamicColorsChange = onDynamicColorsChange,
            privateSessionEnabled = privateSessionEnabled,
            onPrivateSessionChange = onPrivateSessionChange,
            onImportDatabase = onImportDatabase,
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
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
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
                    GrayjayMark()
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
            onVideoBack = onVideoBack,
            nestedBackEnabled = nestedBackEnabled,
            onManageSources = onManageSources,
            dynamicColorsEnabled = dynamicColorsEnabled,
            onDynamicColorsChange = onDynamicColorsChange,
            privateSessionEnabled = privateSessionEnabled,
            onPrivateSessionChange = onPrivateSessionChange,
            onImportDatabase = onImportDatabase,
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
    onVideoBack: () -> Unit,
    nestedBackEnabled: Boolean,
    onManageSources: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    privateSessionEnabled: Boolean,
    onPrivateSessionChange: (Boolean) -> Unit,
    onImportDatabase: () -> Unit,
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
                                    GrayjayMark(Modifier.size(28.dp))
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
                if (selectedChannel != null) {
                ChannelDetailScreen(
                    channel = selectedChannel,
                    videos = if (playback.channelDetail.channelId == selectedChannel.id) {
                        playback.channelDetail.videos
                    } else {
                        videos
                    },
                    videosAreChannelScoped = playback.channelDetail.channelId == selectedChannel.id,
                    isLoading = playback.channelDetail.channelId == selectedChannel.id &&
                        playback.channelDetail.isLoading,
                    isLoadingMore = playback.channelDetail.channelId == selectedChannel.id &&
                        playback.channelDetail.isLoadingMore,
                    hasMore = playback.channelDetail.channelId == selectedChannel.id &&
                        playback.channelDetail.hasMore,
                    onLoadMore = playback.onLoadMoreChannel,
                    errorMessage = playback.channelDetail
                        .takeIf { it.channelId == selectedChannel.id }
                        ?.errorMessage,
                    isFollowing = selectedChannel.id in playback.followedCreatorIds,
                    onFollowingChange = {
                        playback.onCreatorFollowedChange(selectedChannel.id, it)
                    },
                    onVideoClick = onVideoClick,
                    onVideoLongClick = playback.onVideoLongClick,
                )
                } else if (selectedPlaylist != null) {
                PlaylistDetailScreen(
                    playlist = selectedPlaylist,
                    videos = (videos + playback.libraryVideos).distinctBy(VideoUiModel::id),
                    downloads = playback.downloads,
                    onVideoClick = onVideoClick,
                    onVideoLongClick = playback.onVideoLongClick,
                    onPlayAll = { playback.onPlayPlaylist(selectedPlaylist.id) },
                    onPlayFromHere = { videoId ->
                        playback.onPlayPlaylistFrom(selectedPlaylist.id, videoId)
                    },
                    onDownloadAllAsAudio = { ids ->
                        playback.onDownloadPlaylist(selectedPlaylist.id, DownloadMediaType.Audio)
                    },
                    onDownloadAllAsVideo = { ids ->
                        playback.onDownloadPlaylist(selectedPlaylist.id, DownloadMediaType.Video)
                    },
                    onRename = { title ->
                        playback.onRenamePlaylist(selectedPlaylist.id, title)
                    },
                    onAddSelectionToPlaylist = playback.onAddSelectionToPlaylist,
                    onRemoveVideos = { ids ->
                        playback.onRemoveVideosFromPlaylist(selectedPlaylist.id, ids)
                    },
                    onReorder = { ids ->
                        playback.onReorderPlaylist(selectedPlaylist.id, ids)
                    },
                )
                } else {
                    when (selected) {
                    GrayjayDestination.Home -> HomeScreen(
                        home = sourcePresentation.home,
                        onFeedSelected = sourcePresentation.onHomeFeedSelected,
                        onRefresh = sourcePresentation.onRefreshHome,
                        onLoadMore = sourcePresentation.onLoadMoreHome,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = playback.onVideoLongClick,
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
                        onRemoveSelectionFromHistory = playback.onRemoveSelectionFromHistory,
                        onToggleDownload = playback.onToggleDownloaded,
                        onRenamePlaylist = playback.onRenamePlaylist,
                    )
                    GrayjayDestination.Settings -> SettingsScreen(
                        dynamicColorsEnabled = dynamicColorsEnabled,
                        onDynamicColorsChange = onDynamicColorsChange,
                        privateSessionEnabled = privateSessionEnabled,
                        onPrivateSessionChange = onPrivateSessionChange,
                        onManageSources = onManageSources,
                        onImportDatabase = onImportDatabase,
                        activeSourceCount = sourcePresentation.sources.count {
                            it.isEnabled && it.availability != SourceAvailability.MissingPlugin
                        },
                        defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
                        onDefaultPlaybackSpeedChange = playback.onDefaultPlaybackSpeedChange,
                        preferredVideoQuality = playback.preferredVideoQuality,
                        onPreferredVideoQualityChange = playback.onPreferredVideoQualityChange,
                        preferredAudioBitrate = playback.preferredAudioBitrate,
                        onPreferredAudioBitrateChange = playback.onPreferredAudioBitrateChange,
                        stickyCaptionsEnabled = playback.stickyCaptionsEnabled,
                        onStickyCaptionsChange = playback.onStickyCaptionsChange,
                        showRecommendations = playback.showRecommendations,
                        onShowRecommendationsChange = playback.onShowRecommendationsChange,
                        searchHistoryEnabled = playback.searchHistoryEnabled,
                        onSearchHistoryChange = playback.onSearchHistoryChange,
                        keepScreenAwake = playback.keepScreenAwake,
                        onKeepScreenAwakeChange = playback.onKeepScreenAwakeChange,
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
                    )
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
                                        if (transitionVideo.playbackFromDownload) {
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
                                    IconButton(onClick = { openCastSelector(context) }) {
                                        Icon(
                                            Icons.Outlined.Cast,
                                            contentDescription = stringResource(R.string.cast_to_device),
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
                                onQualityChange = playback.onQualityChange,
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
            PlayerSurface(
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
                onQualityChange = playback.onQualityChange,
                onCaptionsEnabledChange = playback.onCaptionsEnabledChange,
                onSubtitleLanguageChange = playback.onSubtitleLanguageChange,
                onRetryPlayback = playback.onRetry,
                onFullscreen = playback.onEnterFullscreen,
                controlsAlpha = if (!isTransitionDragging && progress <= 0.001f) 1f else 0f,
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(
                        width = with(density) { width.toDp() },
                        height = with(density) { height.toDp() },
                    )
                    .then(transitionDragModifier)
                    .zIndex(2f),
            )
        }
    }
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private fun openCastSelector(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        MediaRouter2.getInstance(context).showSystemOutputSwitcher()
    ) {
        return
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS)) }
        .getOrElse { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
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
private fun GrayjayMark(modifier: Modifier = Modifier) {
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
        Text(
            text = "G",
            color = Color.White,
            fontSize = 15.sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
