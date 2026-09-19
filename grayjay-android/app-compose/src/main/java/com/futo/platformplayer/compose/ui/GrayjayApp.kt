package com.futo.platformplayer.compose.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.BuildConfig
import com.futo.platformplayer.compose.data.LibraryExportFormat
import com.futo.platformplayer.compose.playlistQueueFrom
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockCategory
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockRule
import com.futo.platformplayer.compose.ui.screens.HomeScreen
import com.futo.platformplayer.compose.ui.screens.ChannelDetailScreen
import com.futo.platformplayer.compose.ui.screens.ChromecastSheet
import com.futo.platformplayer.compose.ui.screens.LibraryScreen
import com.futo.platformplayer.compose.ui.screens.LibraryFilter
import com.futo.platformplayer.compose.ui.screens.LocalVideoCreatorClick
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
import com.futo.platformplayer.compose.ui.screens.ActiveJobsButton
import com.futo.platformplayer.compose.ui.screens.ActiveJobsPanel
import com.futo.platformplayer.compose.ui.screens.rememberActiveJobItems
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun shouldCoverAppChromeDuringOrientationHandoff(
    windowOrientation: Int,
    compactViewport: Boolean = true,
): Boolean = compactViewport && windowOrientation == Configuration.ORIENTATION_LANDSCAPE

// Private GitHub repositories do not expose release metadata to unauthenticated app clients.
// Keep the complete banner/check path dormant until the repository is made public.
internal const val RELEASE_UPDATE_CHECK_ENABLED = true

/**
 * Owned above fullscreen and PiP branches so page-local state survives while app chrome is not
 * composed. Search type/source choices and list positions otherwise reset when returning.
 */
private val LocalPageStateHolder = staticCompositionLocalOf<SaveableStateHolder> {
    error("Page state holder was not provided")
}

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

internal fun playerBoundsInsideScaffold(
    boundsInRoot: Rect,
    scaffoldLeftInRoot: Float,
    scaffoldTopInRoot: Float,
): Rect = Rect(
    left = boundsInRoot.left - scaffoldLeftInRoot,
    top = boundsInRoot.top - scaffoldTopInRoot,
    right = boundsInRoot.right - scaffoldLeftInRoot,
    bottom = boundsInRoot.bottom - scaffoldTopInRoot,
)

internal fun scaffoldBottomBarNeedsNavigationBarPadding(
    bottomNavigationProvidesInset: Boolean,
): Boolean = !bottomNavigationProvidesInset

internal fun searchMiniplayerBottomInsetPx(
    imeBottomPx: Int,
    navigationBottomPx: Int,
    appBottomBarHeightPx: Int,
): Int = maxOf(imeBottomPx, navigationBottomPx, appBottomBarHeightPx)

internal fun automaticFullscreenAllowedForViewport(widthDp: Int, heightDp: Int): Boolean =
    minOf(widthDp, heightDp) < 600

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
    current != GrayjayDestination.Home
}

internal fun isLocalPlaylistSelection(
    selectedPlaylistId: String?,
    playlists: List<PlaylistUiModel>,
): Boolean = selectedPlaylistId != null && playlists.any { playlist ->
    playlist.id == selectedPlaylistId && playlist.sourceId.isBlank()
}

private data class PlaybackPresentation(
    val activeProfileId: String,
    val uiLanguageTag: String,
    val onUiLanguageChange: (String) -> Unit,
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
    val followingVideos: List<VideoUiModel>,
    val followingFeedLoaded: Boolean,
    val followingFeedLoading: Boolean,
    val followingFeedCompleted: Int,
    val followingFeedTotal: Int,
    val followingFeedError: String?,
    val downloads: Map<String, DownloadUiModel>,
    val downloadStorage: DownloadStorageUiState,
    val youtubeImport: YoutubeImportUiState,
    val backgroundYoutubeImport: BackgroundYoutubeImportUiState,
    val databaseImport: DatabaseImportUiState,
    val libraryTransfer: LibraryTransferUiState,
    val sourceOperationInProgress: Boolean,
    val sourceOperationMessage: String?,
    val onCancelYoutubeImportJobs: () -> Unit,
    val onCancelActiveDownloads: () -> Unit,
    val onOpenActiveJob: (String) -> Unit,
    val pageSlideDirection: Int,
    val pageSlideRequest: Long,
    val onPageSlideConsumed: () -> Unit,
    val downloadFocusVideoId: String?,
    val onDownloadFocusConsumed: () -> Unit,
    val activePlaylistDownloads: Set<PlaylistDownloadBatchUiModel>,
    val automaticPlaylistDownloads: Set<PlaylistDownloadBatchUiModel>,
    val automaticPlaylistDownloadsEnabled: Boolean,
    val isPlaying: Boolean,
    val queueSize: Int,
    val transition: PlayerTransitionState,
    val navigationBackProgress: MutableFloatState,
    val navigationBackFromRight: Boolean,
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
    val onSpeedHoldStart: () -> Unit,
    val onSpeedHoldEnd: () -> Unit,
    val onUseChannelSpeed: () -> Unit,
    val onChannelSpeedChange: (String, Float?) -> Unit,
    val onQualityChange: (Int?) -> Unit,
    val onAudioLanguageChange: (String?) -> Unit,
    val onCaptionsEnabledChange: (Boolean) -> Unit,
    val onSubtitleLanguageChange: (String?) -> Unit,
    val onEnterFullscreen: () -> Unit,
    val onExitFullscreen: () -> Unit,
    val onRetry: () -> Unit,
    val onStoryboardLoadFailure: (String) -> Unit,
    val onToggleFollowing: () -> Unit,
    val onCreatorFollowedChange: (String, Boolean) -> Unit,
    val onLoadChannel: (ChannelUiModel) -> Unit,
    val onChannelTabSelected: (ChannelContentTab) -> Unit,
    val onLoadMoreChannel: () -> Unit,
    val onChannelSearchQueryChange: (String) -> Unit,
    val onLoadMoreChannelSearch: () -> Unit,
    val onLoadFollowingComplete: () -> Unit,
    val onLoadRemotePlaylist: (PlaylistUiModel) -> Unit,
    val onLoadMoreRemotePlaylist: () -> Unit,
    val onPlayRemotePlaylist: () -> Unit,
    val onPlayRemotePlaylistFrom: (String) -> Unit,
    val onDownloadRemotePlaylist: (DownloadMediaType) -> Unit,
    val onCancelDownloadRemotePlaylist: (DownloadMediaType) -> Unit,
    val onCreateLocalPlaylistFromRemote: (String) -> Unit,
    val onLoadMoreRecommendations: () -> Unit,
    val onLoadMoreComments: () -> Unit,
    val onOpenCommentReplies: (String) -> Unit,
    val onDismissCommentReplies: () -> Unit,
    val onLoadMoreCommentReplies: () -> Unit,
    val onClose: () -> Unit,
    val onPlayQueue: (List<String>) -> Unit,
    val onQueueVideos: (List<String>) -> Unit,
    val onPlayNext: (String) -> Unit,
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
    val onPlaylistAutomaticDownloadChange: (String, DownloadMediaType, Boolean) -> Unit,
    val onVideoLongClick: (VideoUiModel) -> Unit,
    val onQueueVideoLongClick: (VideoUiModel) -> Unit,
    val onAddSelectionToPlaylist: (List<String>) -> Unit,
    val onRemoveSelectionFromHistory: (List<String>) -> Unit,
    val onRemoveDownloads: (List<String>) -> Unit,
    val onRemovePlaylists: (List<String>) -> Unit,
    val onExportDownloads: (List<String>, DownloadMediaType, Uri) -> Unit,
    val onExportLibrary: (LibraryExportFormat, Uri) -> Unit,
    val onRenamePlaylist: (String, String) -> Unit,
    val libraryFilter: LibraryFilter,
    val onLibraryFilterChange: (LibraryFilter) -> Unit,
    val libraryPlaylistListState: LazyListState,
    val onRemoveVideosFromPlaylist: (String, List<String>) -> Unit,
    val onReorderPlaylist: (String, List<String>) -> Unit,
    val onSeek: (Float) -> Unit,
    val onResumeFromHistory: () -> Unit,
    val libraryVideos: List<VideoUiModel>,
    val onOpenProfiles: () -> Unit,
    val defaultPlaybackSpeed: Float,
    val perChannelPlaybackSpeedEnabled: Boolean,
    val holdToSpeedEnabled: Boolean,
    val channelPlaybackSpeeds: Map<String, Float>,
    val videoPlaybackSpeeds: Map<String, Float>,
    val sponsorBlockEnabled: Boolean,
    val sponsorBlockSkipNoticesEnabled: Boolean,
    val sponsorBlockCategories: Set<SponsorBlockCategory>,
    val channelSponsorBlockOverrides: Map<String, SponsorBlockRule>,
    val videoSponsorBlockOverrides: Map<String, SponsorBlockRule>,
    val preferredVideoQuality: Int,
    val preferredAudioBitrate: Int,
    val preferredAudioLanguage: String,
    val preferOriginalAudio: Boolean,
    val preferNewPipeForYoutubePlayback: Boolean,
    val subscriptionFetchMode: SubscriptionFetchMode,
    val videoTitleLanguageMode: VideoTitleLanguageMode,
    val stickyCaptionsEnabled: Boolean,
    val showRecommendations: Boolean,
    val searchHistoryEnabled: Boolean,
    val crashLoggingEnabled: Boolean,
    val keepScreenAwake: Boolean,
    val pictureInPictureEnabled: Boolean,
    val onAutomaticPlaylistDownloadsChange: (Boolean) -> Unit,
    val otherAudioDuckingEnabled: Boolean,
    val otherAudioDuckVolumePercent: Int,
    val themeMode: ThemeMode,
    val showPrivateThemeToggle: Boolean,
    val isDarkTheme: Boolean,
    val onDarkThemeChange: (Boolean) -> Unit,
    val onDefaultPlaybackSpeedChange: (Float) -> Unit,
    val onPerChannelPlaybackSpeedChange: (Boolean) -> Unit,
    val onHoldToSpeedChange: (Boolean) -> Unit,
    val onSponsorBlockEnabledChange: (Boolean) -> Unit,
    val onSponsorBlockSkipNoticesEnabledChange: (Boolean) -> Unit,
    val onSponsorBlockCategoriesChange: (Set<SponsorBlockCategory>) -> Unit,
    val onChannelSponsorBlockOverrideChange: (String, SponsorBlockRule?) -> Unit,
    val onVideoSponsorBlockOverrideChange: (String, SponsorBlockRule?) -> Unit,
    val onPreferredVideoQualityChange: (Int) -> Unit,
    val onPreferredAudioBitrateChange: (Int) -> Unit,
    val onPreferredAudioLanguageChange: (String) -> Unit,
    val onPreferOriginalAudioChange: (Boolean) -> Unit,
    val onPreferNewPipeForYoutubePlaybackChange: (Boolean) -> Unit,
    val onSubscriptionFetchModeChange: (SubscriptionFetchMode) -> Unit,
    val onVideoTitleLanguageModeChange: (VideoTitleLanguageMode) -> Unit,
    val onStickyCaptionsChange: (Boolean) -> Unit,
    val onShowRecommendationsChange: (Boolean) -> Unit,
    val onSearchHistoryChange: (Boolean) -> Unit,
    val onCrashLoggingChange: (Boolean) -> Unit,
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
    val onInstallUpdate: (ReleaseUpdateUiModel) -> Unit,
    val updateDownload: UpdateDownloadUiModel?,
    val onCancelUpdateDownload: () -> Unit,
    val onHydrateVideoMetadata: (String) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val chromecast: ChromecastUiState,
    val onOpenChromecast: () -> Unit,
)

/**
 * Keeps frame-by-frame player morph state out of [GrayjayApp]'s restart scope. Reading a plain
 * Float here used to rebuild the complete navigation tree, the active lazy list and Now Playing
 * on every animation frame. Consumers that actually draw the morph observe this stable holder in
 * their own small restart scopes instead.
 */
@Stable
private class PlayerTransitionState(initialProgress: Float) {
    var progress by mutableFloatStateOf(initialProgress)
    var target by mutableFloatStateOf(initialProgress)
    var isSettling by mutableStateOf(false)

    fun snapTo(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        progress = normalized
        target = normalized
        isSettling = false
    }
}

private data class SourcePresentation(
    val sources: List<SourceUiModel>,
    val filterSelections: Map<String, Map<String, String>>,
    val onFilterSelectionChange: (String, String, String) -> Unit,
    val home: HomeUiState,
    val onHomeFeedSelected: (HomeFeedType) -> Unit,
    val onHomeBrowseTabSelected: (String, String) -> Unit,
    val onHomeBrowseOptionSelected: (String, String, String) -> Unit,
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
    val backgroundYoutubeImport: BackgroundYoutubeImportUiState,
    val youtubeImportSchedule: YoutubeImportScheduleUiState,
    val onImportYoutube: (String, YoutubeImportSelection) -> Unit,
    val onDismissYoutubeImport: () -> Unit,
    val onCancelYoutubeImportJobs: () -> Unit,
    val onYoutubeImportScheduleChange:
        (String, YoutubeImportInterval, YoutubeImportSelection) -> Unit,
    val search: SearchUiState,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchSubmit: (String, SearchContentType, Set<String>) -> Unit,
    val onLoadMoreSearch: () -> Unit,
    val onSearchVideoLongClick: (VideoUiModel) -> Unit,
    val searchAutoFocusRequested: Boolean,
    val onSearchAutoFocusConsumed: () -> Unit,
)

@Stable
private class GrayjayTransientUiState(
    actionVideoId: String? = null,
    actionIsRemotePlaylistVideo: Boolean = false,
    actionIsQueueVideo: Boolean = false,
    actionCanAddToQueue: Boolean = false,
    playlistPickerVideoIds: List<String> = emptyList(),
    profileDialogVisible: Boolean = false,
    chromecastSheetVisible: Boolean = false,
) {
    var actionVideoId by mutableStateOf(actionVideoId)
    var actionIsRemotePlaylistVideo by mutableStateOf(actionIsRemotePlaylistVideo)
    var actionIsQueueVideo by mutableStateOf(actionIsQueueVideo)
    var actionCanAddToQueue by mutableStateOf(actionCanAddToQueue)
    var playlistPickerVideoIds by mutableStateOf(playlistPickerVideoIds)
    var profileDialogVisible by mutableStateOf(profileDialogVisible)
    var chromecastSheetVisible by mutableStateOf(chromecastSheetVisible)
}

internal fun activeDownloadNavigationTarget(
    downloads: Collection<DownloadUiModel>,
): String? = listOf(
    DownloadStatus.Downloading,
    DownloadStatus.Preparing,
    DownloadStatus.Queued,
    DownloadStatus.Paused,
).firstNotNullOfOrNull { status ->
    downloads.firstOrNull { it.status == status }?.videoId
} ?: downloads.firstOrNull(DownloadUiModel::isActive)?.videoId

private val GrayjayTransientUiStateSaver =
    androidx.compose.runtime.saveable.Saver<GrayjayTransientUiState, List<Any?>>(
        save = { state ->
            listOf(
                state.actionVideoId,
                state.actionIsRemotePlaylistVideo,
                state.actionIsQueueVideo,
                state.actionCanAddToQueue,
                state.playlistPickerVideoIds,
                state.profileDialogVisible,
                state.chromecastSheetVisible,
            )
        },
        restore = { values ->
            @Suppress("UNCHECKED_CAST")
            GrayjayTransientUiState(
                actionVideoId = values[0] as String?,
                actionIsRemotePlaylistVideo = values[1] as Boolean,
                actionIsQueueVideo = values[2] as Boolean,
                actionCanAddToQueue = values[3] as Boolean,
                playlistPickerVideoIds = values[4] as List<String>,
                profileDialogVisible = values[5] as Boolean,
                chromecastSheetVisible = values[6] as Boolean,
            )
        },
    )

@Composable
fun GrayjayApp(
    uiState: GrayjayUiState,
    player: Player,
    actions: GrayjayAppActions,
    uiLanguageTag: String = "",
    isDarkTheme: Boolean = false,
    updateDownload: UpdateDownloadUiModel? = null,
    deviceIsLandscape: Boolean = false,
    pictureInPictureMode: Boolean = false,
) {
    val context = LocalContext.current
    val displayClock = rememberDisplayClock()
    val pageStateHolder = rememberSaveableStateHolder()
    val channelArtworkIndex = remember(uiState.channels, uiState.channelDetail.channel) {
        ChannelArtworkIndex(listOfNotNull(uiState.channelDetail.channel) + uiState.channels)
    }
    val shareVideoLabel = stringResource(R.string.share_video)
    val creatorLabel = stringResource(R.string.creator)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var destinationName by rememberSaveable { mutableStateOf(GrayjayDestination.Home.name) }
    var selectedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var browseHistory by rememberSaveable(stateSaver = BrowseHistorySaver) {
        mutableStateOf(emptyList<BrowseRoute>())
    }
    val visitedPlaylists = remember { androidx.compose.runtime.mutableStateMapOf<String, PlaylistUiModel>() }
    var navigationProfileId by rememberSaveable { mutableStateOf(uiState.activeProfileId) }
    var searchAutoFocusRequested by rememberSaveable { mutableStateOf(false) }
    var libraryFilterName by rememberSaveable { mutableStateOf(LibraryFilter.History.name) }
    var pendingPageSlideDirection by rememberSaveable { mutableStateOf(0) }
    var pageSlideRequest by rememberSaveable { mutableStateOf(0L) }
    var pendingDownloadFocusId by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryPlaylistListState = rememberLazyListState()
    var nestedBackDestinationName by rememberSaveable { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var shortsModeActive by rememberSaveable { mutableStateOf(false) }
    val shortsPhone = com.futo.platformplayer.compose.ui.screens.supportsShortsFeedPlayer(LocalConfiguration.current.screenWidthDp, LocalConfiguration.current.screenHeightDp, LocalConfiguration.current.smallestScreenWidthDp)
    val shortsVideos = remember(uiState.home.selectedFeed, uiState.home.videos) {
        if (uiState.home.selectedFeed == HomeFeedType.Shorts) {
            uiState.home.videos.filter(VideoUiModel::isAvailable)
        } else {
            emptyList()
        }
    }
    val shortsVideoIds = remember(shortsVideos) { shortsVideos.mapTo(hashSetOf(), VideoUiModel::id) }
    var fullscreenEnteredByRotation by rememberSaveable { mutableStateOf(false) }
    val transientUi = rememberSaveable(saver = GrayjayTransientUiStateSaver) {
        GrayjayTransientUiState()
    }
    val selected = GrayjayDestination.valueOf(destinationName)
    // Player-transition progress changes every frame. Building a combined list and linearly
    // searching it for every item in a large imported playlist made collapsing an 800-item queue
    // perform millions of comparisons per frame. Memoize one first-match index instead.
    val availableVideosById = remember(
        uiState.videos,
        uiState.subscriptionVideos,
        uiState.followingVideos,
        uiState.libraryVideos,
        uiState.search.videos,
        uiState.home.videos,
        uiState.channelDetail.videos,
        uiState.channelDetail.shorts,
        uiState.channelDetail.liveStreams,
        uiState.channelDetail.searchVideos,
        uiState.remotePlaylistDetail.videos,
        uiState.nowPlaying.video,
        uiState.nowPlaying.recommendations,
    ) {
        buildMap {
            sequenceOf(
                uiState.videos,
                uiState.subscriptionVideos,
                uiState.followingVideos,
                uiState.libraryVideos,
                uiState.search.videos,
                uiState.home.videos,
                uiState.channelDetail.videos,
                uiState.channelDetail.shorts,
                uiState.channelDetail.liveStreams,
                uiState.channelDetail.searchVideos,
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
        ?: uiState.channelDetail.channel?.takeIf { it.id == selectedChannelId }
    val selectedPlaylist = uiState.playlists.firstOrNull { it.id == selectedPlaylistId }
        ?: uiState.remotePlaylistDetail.playlist?.takeIf { it.id == selectedPlaylistId }
        ?: (uiState.search.playlists + uiState.channelDetail.playlists)
            .firstOrNull { it.id == selectedPlaylistId }
        ?: uiState.playbackPlaylist?.takeIf { it.id == selectedPlaylistId }
        ?: visitedPlaylists[selectedPlaylistId]
    // The same content can exist in feeds as its online video model and in Now Playing as a
    // resolved offline/audio-only model. The resolved active model must win; otherwise the final
    // frame of the collapse swaps playbackAudioOnly back to false and covers the audio mini-player
    // artwork with an empty video surface.
    val playbackVideo = uiState.nowPlaying.video?.takeIf {
        uiState.nowPlaying.isLoadingPlayback || it.id == uiState.playback.currentVideoId
    } ?: availableVideosById[uiState.playback.currentVideoId]
    val playerTransition = remember { PlayerTransitionState(1f) }
    LaunchedEffect(playerTransition) {
        snapshotFlow {
            Triple(
                playerTransition.target,
                playerTransition.progress,
                playerTransition.isSettling,
            )
        }.collect { (target, progress, isSettling) ->
            // A lifecycle interruption can cancel the final animation callback after its last
            // frame was drawn. Never leave the transition overlay alive over navigation chrome.
            if (!isSettling && target >= 0.999f && progress >= 0.999f) {
                selectedVideoId = null
            }
        }
    }
    val navigationBackProgress = remember { mutableFloatStateOf(0f) }
    var navigationBackFromRight by remember { mutableStateOf(false) }
    val playerTransitionScope = rememberCoroutineScope()
    var playerTransitionJob by remember { mutableStateOf<Job?>(null) }
    val snapPlayerTransition: (Float) -> Unit = { value ->
        // A navigation reset must also stop the old completion callback from reopening the player.
        playerTransitionJob?.cancel()
        playerTransition.snapTo(value)
    }
    val restorePlaybackPlaylistDestination: () -> Unit = {
        uiState.playbackPlaylist?.let { playingPlaylist ->
            val playlist = uiState.playlists.firstOrNull { it.id == playingPlaylist.id }
                ?: playingPlaylist.takeIf { it.sourceId.isNotBlank() }
            if (playlist != null) {
                visitedPlaylists[playlist.id] = playlist
                selectedChannelId = null
                selectedPlaylistId = playlist.id
                if (playlist.sourceId.isBlank()) {
                    destinationName = GrayjayDestination.Library.name
                    libraryFilterName = LibraryFilter.Playlists.name
                    browseHistory = emptyList()
                    nestedBackDestinationName = null
                } else if (uiState.remotePlaylistDetail.playlist?.id != playlist.id) {
                    actions.onLoadRemotePlaylist(playlist)
                }
            }
        }
    }
    val settlePlayer: (Float, String?) -> Unit = { target, videoId ->
        playerTransitionJob?.cancel()
        if (target == 1f) restorePlaybackPlaylistDestination()
        if (target == 0f) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            selectedVideoId = videoId ?: playbackVideo?.id
        }
        playerTransition.target = target
        playerTransition.isSettling = true
        playerTransitionJob = playerTransitionScope.launch {
            animate(
                initialValue = playerTransition.progress,
                targetValue = target,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            ) { value, _ -> playerTransition.progress = value }
            playerTransition.progress = target
            playerTransition.isSettling = false
            if (target == 1f) {
                selectedVideoId = null
                isFullscreen = false
                fullscreenEnteredByRotation = false
            } else {
                selectedVideoId = videoId ?: playbackVideo?.id
            }
        }
    }
    val queueVideos = remember(
        uiState.playback.queueVideoIds,
        uiState.playback.fullQueueVideoIds,
        availableVideosById,
    ) {
        uiState.playback.fullQueueVideoIds
            .ifEmpty { uiState.playback.queueVideoIds }
            .mapNotNull(availableVideosById::get)
    }
    val onSelect: (GrayjayDestination) -> Unit = {
        shortsModeActive = false
        // Clear focus before switching pages so disposal cannot steal the incoming field's focus.
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        searchAutoFocusRequested = it == GrayjayDestination.Search &&
            selected != GrayjayDestination.Search
        destinationName = it.name
        selectedVideoId = null
        snapPlayerTransition(1f)
        selectedChannelId = null
        selectedPlaylistId = null
        nestedBackDestinationName = null
        browseHistory = emptyList()
    }
    fun currentBrowseRoute() = BrowseRoute(
        destinationName, selectedChannelId, selectedPlaylistId, libraryFilterName, selectedVideoId,
    )
    fun rememberBrowseOrigin() {
        browseHistory = appendBrowseRoute(browseHistory, currentBrowseRoute())
    }
    LaunchedEffect(uiState.activeProfileId) {
        if (navigationProfileId != uiState.activeProfileId) {
            navigationProfileId = uiState.activeProfileId
            onSelect(GrayjayDestination.Home)
            visitedPlaylists.clear()
            libraryFilterName = LibraryFilter.History.name
            transientUi.actionVideoId = null
            transientUi.playlistPickerVideoIds = emptyList()
        }
    }
    val onVideoClick: (VideoUiModel) -> Unit = {
        searchAutoFocusRequested = false
        shortsModeActive = uiState.brainrotShortsEnabled && shortsPhone && it.id in shortsVideoIds &&
            selected == GrayjayDestination.Home && uiState.home.selectedFeed == HomeFeedType.Shorts &&
            selectedChannelId == null && selectedPlaylistId == null && !uiState.chromecast.isConnected
        if (shortsModeActive) { isFullscreen = true; fullscreenEnteredByRotation = false }
        actions.onOpenVideo(it.id)
        if (it.isAvailable && it.scheduledStartAtMs <= System.currentTimeMillis()) {
            settlePlayer(0f, it.id)
        }
    }
    val onVideoLongClick: (VideoUiModel) -> Unit = {
        transientUi.actionIsRemotePlaylistVideo = false
        transientUi.actionIsQueueVideo = false
        transientUi.actionCanAddToQueue = false
        transientUi.actionVideoId = it.id
    }
    val onChannelClick: (ChannelUiModel) -> Unit = {
        if (selectedChannelId != it.id || selectedVideoId != null) rememberBrowseOrigin()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        searchAutoFocusRequested = false
        actions.onLoadChannel(it)
        selectedChannelId = it.id
        selectedPlaylistId = null
        selectedVideoId = null
        snapPlayerTransition(1f)
    }
    val latestChannels by rememberUpdatedState(uiState.channels)
    val latestSources by rememberUpdatedState(uiState.sources)
    val latestOnChannelClick by rememberUpdatedState(onChannelClick)
    val onVideoCreatorClick: (VideoUiModel) -> Unit = remember(context, creatorLabel) {
        { video ->
            val candidateIds = setOf(
                video.authorUrl,
                video.channelId,
                "${video.sourceId}:${video.creator}",
            ).filter(String::isNotBlank).toSet()
            val channel = latestChannels.firstOrNull { it.id in candidateIds }
                ?: ChannelUiModel(
                    id = video.authorUrl.ifBlank {
                        video.channelId.ifBlank { "${video.sourceId}:${video.creator}" }
                    },
                    name = video.creator,
                    sourceId = video.sourceId,
                    source = latestSources.firstOrNull { it.id == video.sourceId }?.name
                        ?: video.sourceName.ifBlank { video.sourceId },
                    unreadCount = 0,
                    followerCount = creatorLabel,
                    description = "",
                    thumbnailUrl = video.authorThumbnailUrl,
                )
            latestOnChannelClick(channel)
        }
    }
    val onPlaylistClick: (PlaylistUiModel) -> Unit = {
        visitedPlaylists[it.id] = it
        if (it.sourceId.isNotBlank() && selectedPlaylistId != it.id) rememberBrowseOrigin()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        searchAutoFocusRequested = false
        if (it.sourceId.isNotBlank()) actions.onLoadRemotePlaylist(it)
        if (it.sourceId.isBlank()) {
            destinationName = GrayjayDestination.Library.name
            libraryFilterName = LibraryFilter.Playlists.name
            nestedBackDestinationName = null
            browseHistory = emptyList()
        }
        selectedPlaylistId = it.id
        selectedChannelId = null
        selectedVideoId = null
        snapPlayerTransition(1f)
    }
    LaunchedEffect(uiState.externalNavigation?.requestId) {
        val request = uiState.externalNavigation ?: return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        searchAutoFocusRequested = false
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
                snapPlayerTransition(1f)
            }
            ExternalNavigationKind.Playlist -> {
                if (uiState.playlists.any { it.id == request.contentId && it.sourceId.isBlank() }) {
                    destinationName = GrayjayDestination.Library.name
                    libraryFilterName = LibraryFilter.Playlists.name
                    browseHistory = emptyList()
                    nestedBackDestinationName = null
                }
                selectedPlaylistId = request.contentId
                selectedVideoId = null
                selectedChannelId = null
                snapPlayerTransition(1f)
            }
        }
        actions.onExternalNavigationHandled(request.requestId)
    }
    LaunchedEffect(uiState.videoOpenDialog?.videoId) {
        val dialog = uiState.videoOpenDialog ?: return@LaunchedEffect
        if (selectedVideoId == dialog.videoId) {
            settlePlayer(1f, dialog.videoId)
        }
    }
    LaunchedEffect(selected) {
        if (RELEASE_UPDATE_CHECK_ENABLED && selected == GrayjayDestination.Home) {
            actions.onCheckForUpdates()
        }
    }
    val onNavigateBack: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (selectedVideoId != null) {
            settlePlayer(1f, selectedVideoId)
        } else if (browseHistory.isNotEmpty()) {
            val route = browseHistory.last()
            browseHistory = browseHistory.dropLast(1)
            destinationName = route.destination
            libraryFilterName = route.libraryFilter
            selectedChannelId = route.channelId
            selectedPlaylistId = route.playlistId
            selectedVideoId = route.videoId?.takeIf { it == playbackVideo?.id }
            snapPlayerTransition(if (selectedVideoId == null) 1f else 0f)
            route.channelId?.let { id ->
                uiState.channels.firstOrNull { it.id == id }?.let(actions.onLoadChannel)
            }
            route.playlistId?.let { id -> visitedPlaylists[id] }
                ?.takeIf { it.sourceId.isNotBlank() }
                ?.let(actions.onLoadRemotePlaylist)
            nestedBackDestinationName = null
        } else if (selectedChannelId != null) {
            selectedChannelId = null
        } else if (selectedPlaylistId != null) {
            val returningFromLocalPlaylist = isLocalPlaylistSelection(
                selectedPlaylistId,
                uiState.playlists,
            )
            selectedPlaylistId = null
            if (returningFromLocalPlaylist) {
                destinationName = GrayjayDestination.Library.name
                libraryFilterName = LibraryFilter.Playlists.name
                nestedBackDestinationName = null
            }
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
        rememberBrowseOrigin()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        nestedBackDestinationName = selected.name
        destinationName = GrayjayDestination.Sources.name
        selectedVideoId = null
        snapPlayerTransition(1f)
        selectedChannelId = null
        selectedPlaylistId = null
    }
    val playback = PlaybackPresentation(
        activeProfileId = uiState.activeProfileId,
        uiLanguageTag = uiLanguageTag,
        onUiLanguageChange = actions.onUiLanguageChange,
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
        followingVideos = uiState.followingVideos,
        followingFeedLoaded = uiState.followingFeedLoaded,
        followingFeedLoading = uiState.followingFeedLoading,
        followingFeedCompleted = uiState.followingFeedCompleted,
        followingFeedTotal = uiState.followingFeedTotal,
        followingFeedError = uiState.followingFeedError,
        downloads = uiState.downloads,
        downloadStorage = uiState.downloadStorage,
        youtubeImport = uiState.youtubeImport,
        backgroundYoutubeImport = uiState.backgroundYoutubeImport,
        databaseImport = uiState.databaseImport,
        libraryTransfer = uiState.libraryTransfer,
        sourceOperationInProgress = uiState.sourceOperationInProgress,
        sourceOperationMessage = uiState.sourceOperationMessage,
        onCancelYoutubeImportJobs = actions.onCancelYoutubeImportJobs,
        onCancelActiveDownloads = actions.onCancelActiveDownloads,
        onOpenActiveJob = { jobId ->
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            if (jobId == "downloads") {
                pendingPageSlideDirection = -1
                pageSlideRequest++
                pendingDownloadFocusId = activeDownloadNavigationTarget(
                    uiState.downloads.values,
                )
            }
            destinationName = when (jobId) {
                "downloads" -> GrayjayDestination.Library.name
                "youtube-import", "youtube-import-background", "source-operation" ->
                    GrayjayDestination.Sources.name
                else -> GrayjayDestination.Settings.name
            }
            if (jobId == "downloads") libraryFilterName = LibraryFilter.Downloads.name
            nestedBackDestinationName = null
            selectedChannelId = null
            selectedPlaylistId = null
            selectedVideoId = null
            snapPlayerTransition(1f)
        },
        pageSlideDirection = pendingPageSlideDirection,
        pageSlideRequest = pageSlideRequest,
        onPageSlideConsumed = { pendingPageSlideDirection = 0 },
        downloadFocusVideoId = pendingDownloadFocusId,
        onDownloadFocusConsumed = { pendingDownloadFocusId = null },
        activePlaylistDownloads = uiState.activePlaylistDownloads,
        automaticPlaylistDownloads = uiState.automaticPlaylistDownloads,
        automaticPlaylistDownloadsEnabled = uiState.automaticPlaylistDownloadsEnabled,
        isPlaying = uiState.playback.isPlaying,
        queueSize = uiState.playback.fullQueueVideoIds
            .ifEmpty { uiState.playback.queueVideoIds }
            .size,
        transition = playerTransition,
        navigationBackProgress = navigationBackProgress,
        navigationBackFromRight = navigationBackFromRight,
        onExpand = { settlePlayer(0f, playbackVideo?.id) },
        onCollapse = { settlePlayer(1f, selectedVideoId ?: playbackVideo?.id) },
        onTransitionDragStart = {
            playerTransitionJob?.cancel()
            playerTransition.isSettling = false
            selectedVideoId = selectedVideoId ?: playbackVideo?.id
        },
        onTransitionProgressChange = {
            playerTransitionJob?.cancel()
            playerTransition.isSettling = false
            playerTransition.progress = it.coerceIn(0f, 1f)
        },
        onTransitionRelease = { target ->
            settlePlayer(target, selectedVideoId ?: playbackVideo?.id)
        },
        onToggle = actions.onTogglePlayback,
        onNext = actions.onSkipToNext,
        onPrevious = actions.onSkipToPrevious,
        onSeekBy = actions.onSeekPlaybackBy,
        onSpeedChange = actions.onPlaybackSpeedChange,
        onSpeedHoldStart = actions.onSpeedHoldStart,
        onSpeedHoldEnd = actions.onSpeedHoldEnd,
        onUseChannelSpeed = actions.onUseChannelPlaybackSpeed,
        onChannelSpeedChange = actions.onChannelPlaybackSpeedChange,
        onQualityChange = actions.onVideoQualityChange,
        onAudioLanguageChange = actions.onAudioLanguageChange,
        onCaptionsEnabledChange = actions.onCaptionsEnabledChange,
        onSubtitleLanguageChange = actions.onSubtitleLanguageChange,
        onEnterFullscreen = {
            fullscreenEnteredByRotation = false
            isFullscreen = true
        },
        onExitFullscreen = {
            shortsModeActive = false
            fullscreenEnteredByRotation = false
            isFullscreen = false
        },
        onRetry = actions.onRetryPlayback,
        onStoryboardLoadFailure = actions.onStoryboardLoadFailure,
        onToggleFollowing = actions.onToggleFollowing,
        onCreatorFollowedChange = actions.onCreatorFollowedChange,
        onLoadChannel = actions.onLoadChannel,
        onChannelTabSelected = actions.onChannelTabSelected,
        onLoadMoreChannel = actions.onLoadMoreChannel,
        onChannelSearchQueryChange = actions.onChannelSearchQueryChange,
        onLoadMoreChannelSearch = actions.onLoadMoreChannelSearch,
        onLoadFollowingComplete = actions.onLoadFollowingComplete,
        onLoadRemotePlaylist = actions.onLoadRemotePlaylist,
        onLoadMoreRemotePlaylist = actions.onLoadMoreRemotePlaylist,
        onPlayRemotePlaylist = {
            actions.onPlayRemotePlaylist()
            uiState.remotePlaylistDetail.videos.firstOrNull()?.let { first ->
                settlePlayer(0f, first.id)
            }
        },
        onPlayRemotePlaylistFrom = { videoId ->
            actions.onPlayRemotePlaylistFrom(videoId)
            settlePlayer(0f, videoId)
        },
        onDownloadRemotePlaylist = actions.onDownloadRemotePlaylist,
        onCancelDownloadRemotePlaylist = actions.onCancelDownloadRemotePlaylist,
        onCreateLocalPlaylistFromRemote = actions.onCreateLocalPlaylistFromRemote,
        onLoadMoreRecommendations = actions.onLoadMoreRecommendations,
        onLoadMoreComments = actions.onLoadMoreComments,
        onOpenCommentReplies = actions.onOpenCommentReplies,
        onDismissCommentReplies = actions.onDismissCommentReplies,
        onLoadMoreCommentReplies = actions.onLoadMoreCommentReplies,
        onClose = {
            restorePlaybackPlaylistDestination()
            snapPlayerTransition(1f)
            selectedVideoId = null
            actions.onClosePlayback()
        },
        onPlayQueue = { queueIds ->
            if (queueIds.isNotEmpty()) {
                actions.onPlayQueue(queueIds)
                settlePlayer(0f, queueIds.first())
            }
        },
        onQueueVideos = actions.onQueueVideos,
        onPlayNext = actions.onPlayNext,
        onPlayPlaylist = { playlistId ->
            val playlist = uiState.playlists.firstOrNull { it.id == playlistId }
            if (playlist != null && playlist.videoIds.isNotEmpty()) {
                actions.onPlayPlaylist(playlistId)
                settlePlayer(0f, playlist.videoIds.first())
            }
        },
        onPlayPlaylistFrom = { playlistId, videoId ->
            val playlist = uiState.playlists.firstOrNull { it.id == playlistId }
            if (playlist != null && videoId in playlist.videoIds) {
                actions.onPlayPlaylistFrom(playlistId, videoId)
                settlePlayer(0f, videoId)
            }
        },
        onToggleWatchLater = actions.onToggleWatchLater,
        onToggleDownloaded = actions.onToggleDownloaded,
        onToggleAudioDownloaded = actions.onToggleAudioDownloaded,
        onDownloadVideo = actions.onDownloadVideo,
        onDownloadAudio = actions.onDownloadAudio,
        onDownloadVideos = actions.onDownloadVideos,
        onDownloadPlaylist = actions.onDownloadPlaylist,
        onCancelDownloadPlaylist = actions.onCancelDownloadPlaylist,
        onPlaylistAutomaticDownloadChange = actions.onPlaylistAutomaticDownloadChange,
        onVideoLongClick = onVideoLongClick,
        onQueueVideoLongClick = { queuedVideo ->
            transientUi.actionIsRemotePlaylistVideo = false
            transientUi.actionIsQueueVideo = true
            transientUi.actionVideoId = queuedVideo.id
        },
        onAddSelectionToPlaylist = { transientUi.playlistPickerVideoIds = it },
        onRemoveSelectionFromHistory = actions.onRemoveVideosFromHistory,
        onRemoveDownloads = actions.onRemoveDownloads,
        onRemovePlaylists = actions.onRemovePlaylists,
        onExportDownloads = actions.onExportDownloads,
        onExportLibrary = actions.onExportLibrary,
        onRenamePlaylist = actions.onRenamePlaylist,
        libraryFilter = LibraryFilter.valueOf(libraryFilterName),
        onLibraryFilterChange = { libraryFilterName = it.name },
        libraryPlaylistListState = libraryPlaylistListState,
        onRemoveVideosFromPlaylist = actions.onRemoveVideosFromPlaylist,
        onReorderPlaylist = actions.onReorderPlaylist,
        onSeek = actions.onSeekPlayback,
        onResumeFromHistory = actions.onResumeFromHistory,
        libraryVideos = uiState.libraryVideos,
        onOpenProfiles = { transientUi.profileDialogVisible = true },
        defaultPlaybackSpeed = uiState.defaultPlaybackSpeed,
        perChannelPlaybackSpeedEnabled = uiState.perChannelPlaybackSpeedEnabled,
        holdToSpeedEnabled = uiState.holdToSpeedEnabled,
        channelPlaybackSpeeds = uiState.channelPlaybackSpeeds,
        videoPlaybackSpeeds = uiState.videoPlaybackSpeeds,
        sponsorBlockEnabled = uiState.sponsorBlockEnabled,
        sponsorBlockSkipNoticesEnabled = uiState.sponsorBlockSkipNoticesEnabled,
        sponsorBlockCategories = uiState.sponsorBlockCategories,
        channelSponsorBlockOverrides = uiState.channelSponsorBlockOverrides,
        videoSponsorBlockOverrides = uiState.videoSponsorBlockOverrides,
        preferredVideoQuality = uiState.preferredVideoQuality,
        preferredAudioBitrate = uiState.preferredAudioBitrate,
        preferredAudioLanguage = uiState.preferredAudioLanguage,
        preferOriginalAudio = uiState.preferOriginalAudio,
        preferNewPipeForYoutubePlayback = uiState.preferNewPipeForYoutubePlayback,
        subscriptionFetchMode = uiState.subscriptionFetchMode,
        videoTitleLanguageMode = uiState.videoTitleLanguageMode,
        stickyCaptionsEnabled = uiState.stickyCaptionsEnabled,
        showRecommendations = uiState.showRecommendations,
        searchHistoryEnabled = uiState.searchHistoryEnabled,
        crashLoggingEnabled = uiState.crashLoggingEnabled,
        keepScreenAwake = uiState.keepScreenAwake,
        pictureInPictureEnabled = uiState.pictureInPictureEnabled,
        onAutomaticPlaylistDownloadsChange = actions.onAutomaticPlaylistDownloadsChange,
        otherAudioDuckingEnabled = uiState.otherAudioDuckingEnabled,
        otherAudioDuckVolumePercent = uiState.otherAudioDuckVolumePercent,
        themeMode = uiState.themeMode,
        showPrivateThemeToggle = uiState.activeProfileId == "private",
        isDarkTheme = isDarkTheme,
        onDarkThemeChange = actions.onDarkThemeChange,
        onDefaultPlaybackSpeedChange = actions.onDefaultPlaybackSpeedChange,
        onPerChannelPlaybackSpeedChange = actions.onPerChannelPlaybackSpeedChange,
        onHoldToSpeedChange = actions.onHoldToSpeedChange,
        onSponsorBlockEnabledChange = actions.onSponsorBlockEnabledChange,
        onSponsorBlockSkipNoticesEnabledChange = actions.onSponsorBlockSkipNoticesEnabledChange,
        onSponsorBlockCategoriesChange = actions.onSponsorBlockCategoriesChange,
        onChannelSponsorBlockOverrideChange = actions.onChannelSponsorBlockOverrideChange,
        onVideoSponsorBlockOverrideChange = actions.onVideoSponsorBlockOverrideChange,
        onPreferredVideoQualityChange = actions.onPreferredVideoQualityChange,
        onPreferredAudioBitrateChange = actions.onPreferredAudioBitrateChange,
        onPreferredAudioLanguageChange = actions.onPreferredAudioLanguageChange,
        onPreferOriginalAudioChange = actions.onPreferOriginalAudioChange,
        onPreferNewPipeForYoutubePlaybackChange = actions.onPreferNewPipeForYoutubePlaybackChange,
        onSubscriptionFetchModeChange = actions.onSubscriptionFetchModeChange,
        onVideoTitleLanguageModeChange = actions.onVideoTitleLanguageModeChange,
        onStickyCaptionsChange = actions.onStickyCaptionsChange,
        onShowRecommendationsChange = actions.onShowRecommendationsChange,
        onSearchHistoryChange = actions.onSearchHistoryChange,
        onCrashLoggingChange = actions.onCrashLoggingChange,
        onKeepScreenAwakeChange = actions.onKeepScreenAwakeChange,
        onPictureInPictureChange = actions.onPictureInPictureChange,
        onOtherAudioDuckingChange = actions.onOtherAudioDuckingChange,
        onOtherAudioDuckVolumeChange = actions.onOtherAudioDuckVolumeChange,
        pcLink = uiState.pcLink,
        onScanPcPairingQr = actions.onScanPcPairingQr,
        onRemovePairedComputer = actions.onRemovePairedComputer,
        onPlayFromComputer = actions.onPlayFromComputer,
        onToggleComputerPlayback = actions.onToggleComputerPlayback,
        onPreviousComputerPlayback = actions.onPreviousComputerPlayback,
        onNextComputerPlayback = actions.onNextComputerPlayback,
        onSeekComputerPlayback = actions.onSeekComputerPlayback,
        onInstallUpdate = actions.onInstallUpdate,
        updateDownload = updateDownload,
        onCancelUpdateDownload = actions.onCancelUpdateDownload,
        onHydrateVideoMetadata = actions.onHydrateVideoMetadata,
        onThemeModeChange = actions.onThemeModeChange,
        chromecast = uiState.chromecast,
        onOpenChromecast = {
            transientUi.chromecastSheetVisible = true
            actions.onStartChromecastDiscovery()
        },
    )

    LaunchedEffect(uiState.playback.currentVideoId, uiState.nowPlaying.video?.id) {
        if (uiState.playback.currentVideoId == null && uiState.nowPlaying.video == null) {
            snapPlayerTransition(1f)
            selectedVideoId = null
        }
        if (selectedVideoId != null && !uiState.nowPlaying.isLoadingPlayback) {
            uiState.playback.currentVideoId?.let { selectedVideoId = it }
        }
    }

    LaunchedEffect(pictureInPictureMode, playbackVideo?.id) {
        if (pictureInPictureMode && playbackVideo != null) {
            // Match legacy Grayjay: returning from PiP always expands the same player back into
            // Now Playing instead of revealing both the internal mini-player and the detail view.
            snapPlayerTransition(0f)
            selectedVideoId = playbackVideo.id
            fullscreenEnteredByRotation = false
            isFullscreen = false
            actions.onFullscreenPresentationChanged(false, false)
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
            canGoPrevious = queueIndex > 0 || player.currentPosition > 5_000L,
            canGoNext = queueIndex >= 0 && queueIndex < uiState.playback.queueVideoIds.lastIndex,
            onTogglePlayback = actions.onTogglePlayback,
            onSkipPrevious = actions.onSkipToPrevious,
            onSkipNext = actions.onSkipToNext,
            onSeekBy = actions.onSeekPlaybackBy,
            onSeek = actions.onSeekPlayback,
            onSpeedChange = actions.onPlaybackSpeedChange,
            onQualityChange = actions.onVideoQualityChange,
            onCaptionsEnabledChange = actions.onCaptionsEnabledChange,
            onSubtitleLanguageChange = actions.onSubtitleLanguageChange,
            onRetryPlayback = actions.onRetryPlayback,
            onFullscreen = {},
            controlsAlpha = 0f,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val sources = SourcePresentation(
        sources = remember(uiState.sources) { visibleSourcesForQuery(uiState.sources, "") },
        filterSelections = uiState.sourceFilterSelections,
        onFilterSelectionChange = actions.onSourceFilterSelectionChange,
        home = uiState.home,
        onHomeFeedSelected = actions.onHomeFeedSelected,
        onHomeBrowseTabSelected = actions.onHomeBrowseTabSelected,
        onHomeBrowseOptionSelected = actions.onHomeBrowseOptionSelected,
        onRefreshHome = actions.onRefreshHome,
        onLoadMoreHome = actions.onLoadMoreHome,
        onEnabledChange = actions.onSourceEnabledChange,
        isOperationInProgress = uiState.sourceOperationInProgress,
        operationMessage = uiState.sourceOperationMessage,
        onInstall = actions.onInstallSource,
        onScanQr = actions.onScanSourceQr,
        onRefresh = actions.onRefreshSource,
        onClearCache = actions.onClearSourceCache,
        onRemove = actions.onRemoveSource,
        onLogin = actions.onLoginSource,
        onLogout = actions.onLogoutSource,
        youtubeImport = uiState.youtubeImport,
        backgroundYoutubeImport = uiState.backgroundYoutubeImport,
        youtubeImportSchedule = uiState.youtubeImportSchedule,
        onImportYoutube = actions.onImportYoutube,
        onDismissYoutubeImport = actions.onDismissYoutubeImport,
        onCancelYoutubeImportJobs = actions.onCancelYoutubeImportJobs,
        onYoutubeImportScheduleChange = actions.onYoutubeImportScheduleChange,
        search = uiState.search,
        onSearchQueryChange = actions.onSearchQueryChange,
        onSearchSubmit = actions.onSearchSubmit,
        onLoadMoreSearch = actions.onLoadMoreSearch,
        onSearchVideoLongClick = { video ->
            transientUi.actionIsRemotePlaylistVideo = false
            transientUi.actionIsQueueVideo = false
            transientUi.actionCanAddToQueue = true
            transientUi.actionVideoId = video.id
        },
        searchAutoFocusRequested = searchAutoFocusRequested,
        onSearchAutoFocusConsumed = { searchAutoFocusRequested = false },
    )

    LaunchedEffect(selectedVideo?.id) {
        if (selectedVideo == null) {
            fullscreenEnteredByRotation = false
            isFullscreen = false
        }
    }

    val fullscreenVideo = selectedVideo ?: playbackVideo
    val portraitFullscreen = (shortsModeActive && shortsPhone) || usePortraitPlayerFullscreen(fullscreenVideo, uiState.playback)
    val currentConfiguration = LocalConfiguration.current
    val windowOrientation = currentConfiguration.orientation
    val automaticFullscreenAllowed = automaticFullscreenAllowedForViewport(
        widthDp = currentConfiguration.screenWidthDp,
        heightDp = currentConfiguration.screenHeightDp,
    )
    LaunchedEffect(isFullscreen, portraitFullscreen) {
        actions.onFullscreenPresentationChanged(isFullscreen, portraitFullscreen)
    }
    LaunchedEffect(
        deviceIsLandscape,
        automaticFullscreenAllowed,
        selectedVideo?.id,
        playerTransition.isSettling,
        playerTransition.target,
    ) {
        val expandedNowPlaying = selectedVideo != null &&
            !playerTransition.isSettling && playerTransition.target < 0.01f
        when {
            automaticFullscreenAllowed && deviceIsLandscape && expandedNowPlaying &&
                !isFullscreen && !portraitFullscreen -> {
                fullscreenEnteredByRotation = true
                isFullscreen = true
            }
            (!deviceIsLandscape || !automaticFullscreenAllowed) &&
                isFullscreen && fullscreenEnteredByRotation -> {
                fullscreenEnteredByRotation = false
                isFullscreen = false
            }
        }
    }

    PredictiveBackHandler(
        enabled = isFullscreen || (
            selectedVideo != null ||
                selectedChannel != null ||
                selectedPlaylist != null || nestedBackDestinationName != null || browseHistory.isNotEmpty()
                    || topLevelBackDestination(selected) != null
            ),
    ) { backEvents ->
        val minimizesPlayer = !isFullscreen &&
            selectedVideo != null
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
                backEvents.collect { event ->
                    navigationBackFromRight = event.swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT
                    navigationBackProgress.floatValue = event.progress
                }
                if (isFullscreen) {
                    shortsModeActive = false
                    fullscreenEnteredByRotation = false
                    isFullscreen = false
                } else {
                    onNavigateBack()
                }
                navigationBackProgress.floatValue = 0f
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    try {
                        animate(
                            initialValue = navigationBackProgress.floatValue,
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 160),
                        ) { value, _ -> navigationBackProgress.floatValue = value }
                    } finally {
                        navigationBackProgress.floatValue = 0f
                    }
                }
            }
        }
    }

    if (isFullscreen && fullscreenVideo != null) {
        val fullscreenContent: @Composable () -> Unit = {
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
            holdToSpeedEnabled = playback.holdToSpeedEnabled,
            onSpeedHoldStart = playback.onSpeedHoldStart,
            onSpeedHoldEnd = playback.onSpeedHoldEnd,
            perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
            videoPlaybackSpeedOverride = playback.videoPlaybackSpeeds[fullscreenVideo.id],
            channelPlaybackSpeed = playback.channelPlaybackSpeeds[fullscreenVideo.playbackChannelKey()],
            sponsorBlockSegments = playback.nowPlaying.sponsorBlockSegments,
            sponsorBlockSkipNotice = playback.nowPlaying.sponsorBlockSkipNotice,
            sponsorBlockInheritedRule = playback.channelSponsorBlockOverrides[fullscreenVideo.playbackChannelKey()]
                ?: SponsorBlockRule(playback.sponsorBlockEnabled, playback.sponsorBlockCategories),
            sponsorBlockVideoOverride = playback.videoSponsorBlockOverrides[fullscreenVideo.id],
            onSponsorBlockVideoOverrideChange = { rule ->
                playback.onVideoSponsorBlockOverrideChange(fullscreenVideo.id, rule)
            },
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
            onStoryboardLoadFailure = { playback.onStoryboardLoadFailure(fullscreenVideo.id) },
            resumePositionFraction = playback.nowPlaying.resumePositionFraction,
            onResumeFromHistory = playback.onResumeFromHistory,
            onExitFullscreen = playback.onExitFullscreen,
            portraitFullscreen = portraitFullscreen,
            modifier = Modifier.graphicsLayer {
                val progress = navigationBackProgress.floatValue.coerceIn(0f, 1f)
                scaleX = 1f - 0.04f * progress
                scaleY = 1f - 0.04f * progress
                alpha = 1f - 0.22f * progress
            },
        )
        }
        if (shortsModeActive && shortsPhone && uiState.brainrotShortsEnabled && fullscreenVideo.id in shortsVideoIds) {
            com.futo.platformplayer.compose.ui.screens.ShortsFeedPlayer(
                videos = shortsVideos, activeVideoId = fullscreenVideo.id, player = playback.player,
                onVideoSelected = onVideoClick,
                hasMore = uiState.home.hasMore && !uiState.home.isLoadingMore && !uiState.home.isLoading,
                onLoadMore = actions.onLoadMoreHome,
                content = fullscreenContent,
            )
        } else fullscreenContent()
    } else if (
        shouldCoverAppChromeDuringOrientationHandoff(
            windowOrientation = windowOrientation,
            compactViewport = automaticFullscreenAllowed,
        )
    ) {
        // Fullscreen state changes before Android finishes returning this window to portrait.
        // Keep that hand-off opaque so ordinary app chrome is never drawn or visibly rotated
        // inside the temporary landscape viewport.
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else CompositionLocalProvider(
        LocalPageStateHolder provides pageStateHolder,
        LocalVideoCreatorClick provides onVideoCreatorClick,
        LocalChannelArtworkIndex provides channelArtworkIndex,
        LocalChannelArtworkRequest provides actions.onHydrateChannelArtwork,
        LocalDisplayClock provides displayClock,
        com.futo.platformplayer.compose.ui.screens.LocalExtraPreferences provides com.futo.platformplayer.compose.ui.screens.ExtraPreferences(
            uiState.brainrotShortsEnabled, actions.onBrainrotShortsChange, uiState.rebuildingCaches, actions.onRebuildContentCaches,
        ),
    ) {
        val navigationVideos = remember(uiState.videos, uiState.subscriptionVideos) {
            (uiState.videos + uiState.subscriptionVideos).distinctBy(VideoUiModel::id)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
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
                    transientUi.actionIsRemotePlaylistVideo = true
                    transientUi.actionIsQueueVideo = false
                    transientUi.actionVideoId = video.id
                },
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = actions.onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = actions.onPrivateSessionChange,
                onImportDatabase = actions.onChooseDatabaseImport,
                onImportNewPipeDatabase = actions.onChooseNewPipeImport,
                videos = navigationVideos,
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
                    transientUi.actionIsRemotePlaylistVideo = true
                    transientUi.actionIsQueueVideo = false
                    transientUi.actionVideoId = video.id
                },
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = actions.onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = actions.onPrivateSessionChange,
                onImportDatabase = actions.onChooseDatabaseImport,
                onImportNewPipeDatabase = actions.onChooseNewPipeImport,
                videos = navigationVideos,
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
                    transientUi.actionIsRemotePlaylistVideo = true
                    transientUi.actionIsQueueVideo = false
                    transientUi.actionVideoId = video.id
                },
                onVideoBack = onNavigateBack,
                nestedBackEnabled = nestedBackDestinationName != null,
                onManageSources = onManageSources,
                dynamicColorsEnabled = uiState.dynamicColorsEnabled,
                onDynamicColorsChange = actions.onDynamicColorsChange,
                privateSessionEnabled = uiState.privateSessionEnabled,
                onPrivateSessionChange = actions.onPrivateSessionChange,
                onImportDatabase = actions.onChooseDatabaseImport,
                onImportNewPipeDatabase = actions.onChooseNewPipeImport,
                videos = navigationVideos,
                channels = uiState.channels,
                playlists = uiState.playlists,
            )
        }
    }
    LaunchedEffect(
        playerTransition.progress,
        playerTransition.target,
        playerTransition.isSettling,
        selectedVideoId,
    ) {
        if (
            selectedVideoId != null &&
            !playerTransition.isSettling &&
            playerTransition.target >= 0.999f &&
            playerTransition.progress >= 0.999f
        ) {
            selectedVideoId = null
        }
    }
    }

    transientUi.actionVideoId?.let(availableVideosById::get)?.let { video ->
        VideoActionsSheet(
            video = video,
            download = uiState.downloads[video.id],
            onDismiss = {
                transientUi.actionVideoId = null
                transientUi.actionIsRemotePlaylistVideo = false
                transientUi.actionIsQueueVideo = false
                transientUi.actionCanAddToQueue = false
            },
            onToggleDownload = { actions.onToggleDownloaded(video.id) },
            onDownloadAudio = { actions.onToggleAudioDownloaded(video.id) },
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
            onAddToPlaylist = { transientUi.playlistPickerVideoIds = listOf(video.id) },
            onAddToQueue = if (transientUi.actionCanAddToQueue) {
                { playback.onQueueVideos(listOf(video.id)) }
            } else null,
            onPlayNext = if (transientUi.actionIsQueueVideo) {
                { playback.onPlayNext(video.id) }
            } else null,
            onPlayFromHere = if (transientUi.actionIsRemotePlaylistVideo) {
                { playback.onPlayRemotePlaylistFrom(video.id) }
            } else null,
        )
    }
    if (transientUi.playlistPickerVideoIds.isNotEmpty()) {
        PlaylistPickerDialog(
            playlists = uiState.playlists,
            videoIds = transientUi.playlistPickerVideoIds,
            onDismiss = { transientUi.playlistPickerVideoIds = emptyList() },
            onAdd = actions.onAddVideosToPlaylist,
            onCreate = actions.onCreatePlaylist,
        )
    }
    DatabaseImportDialogs(
        state = uiState.databaseImport,
        onDismiss = actions.onDismissDatabaseImport,
        onPasswordSubmit = actions.onRetryDatabaseImport,
        onConfirm = actions.onConfirmDatabaseImport,
    )
    uiState.sourceTrustRequest?.let { request ->
        SourceTrustDialog(
            request = request,
            onTrust = actions.onTrustUnverifiedSource,
            onReject = actions.onRejectUnverifiedSource,
        )
    }
    ProfileSwitcherDialogs(
        profiles = uiState.profiles,
        activeProfileId = uiState.activeProfileId,
        visible = transientUi.profileDialogVisible,
        onDismiss = { transientUi.profileDialogVisible = false },
        onSwitch = actions.onSwitchProfile,
        onCreate = actions.onCreateProfile,
        onVerifyPin = actions.onVerifyProfilePin,
        onRename = actions.onRenameProfile,
        onSetDeviceCredentialProtection = actions.onSetProfileDeviceCredentialProtection,
        onDelete = actions.onDeleteProfile,
        bypassProtection = BuildConfig.DEBUG && context.packageName.endsWith(".graytest"),
    )
    if (transientUi.chromecastSheetVisible) {
        ChromecastSheet(
            state = uiState.chromecast,
            onConnect = actions.onConnectChromecast,
            onDisconnect = actions.onDisconnectChromecast,
            onDismiss = { transientUi.chromecastSheetVisible = false },
        )
    }
    uiState.videoOpenDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = actions.onDismissVideoOpenDialog,
            title = {
                Text(
                    stringResource(
                        when {
                            dialog.scheduledStartAtMs > 0L -> R.string.video_scheduled_title
                            dialog.permanentlyUnavailable -> R.string.video_no_longer_available_title
                            else -> R.string.could_not_open_video
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dialog.title, style = MaterialTheme.typography.titleMedium)
                    Text(dialog.message)
                }
            },
            confirmButton = {
                TextButton(onClick = actions.onDismissVideoOpenDialog) {
                    Text(stringResource(R.string.ok))
                }
            },
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
    val compactLayout = com.futo.platformplayer.compose.ui.screens.compactUi()
    val navInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
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
        bottomNavigationProvidesInset = true,
        bottomBar = {
            NavigationBar(modifier = if (compactLayout) Modifier.height(64.dp + navInset) else Modifier) {
                GrayjayDestination.entries.filter { it.showInCompactNavigation }.forEach { destination ->
                    NavigationBarItem(
                        alwaysShowLabel = !compactLayout,
                        selected = navigationSelected == destination,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.testTag("nav-${destination.name.lowercase()}"),
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = stringResource(destination.titleRes),
                            )
                        },
                        label = { Text(stringResource(destination.navigationLabelRes), maxLines = 1,
                            style = if (compactLayout) MaterialTheme.typography.labelSmall else androidx.compose.material3.LocalTextStyle.current,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
                    modifier = Modifier.testTag("nav-${destination.name.lowercase()}"),
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(destination.icon, contentDescription = stringResource(destination.titleRes))
                    },
                    label = { Text(stringResource(destination.navigationLabelRes), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
    var drawerVisible by rememberSaveable { mutableStateOf(true) }
    val toggleDrawer = { drawerVisible = !drawerVisible }
    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = drawerVisible,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(tween(180)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(tween(130)),
        ) {
            PermanentDrawerSheet(Modifier.width(280.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = toggleDrawer) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuOpen,
                            contentDescription = stringResource(R.string.close_navigation_menu),
                        )
                    }
                    GrayjoyMark()
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                }
                GrayjayDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        selected = selected == destination,
                        onClick = { onSelect(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.titleRes)) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("nav-${destination.name.lowercase()}"),
                    )
                }
            }
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
            drawerVisible = drawerVisible,
            onToggleDrawer = toggleDrawer,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    bottomNavigationProvidesInset: Boolean = false,
    drawerVisible: Boolean = false,
    onToggleDrawer: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var expandedPlayerBounds by remember { mutableStateOf<Rect?>(null) }
    var miniPlayerBounds by remember { mutableStateOf<Rect?>(null) }
    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var rootLeftPx by remember { mutableFloatStateOf(0f) }
    var rootTopPx by remember { mutableFloatStateOf(0f) }
    var appBottomBarHeightPx by remember { mutableFloatStateOf(0f) }
    var isTransitionDragging by remember { mutableStateOf(false) }
    val pageStateHolder = LocalPageStateHolder.current
    val compactChrome = com.futo.platformplayer.compose.ui.screens.compactUi()
    val transitionVideo = selectedVideo ?: playback.video
    val transitionActive = transitionVideo != null && selectedVideo != null
    val isRootSearch = selected == GrayjayDestination.Search &&
        selectedChannel == null && selectedPlaylist == null && !nestedBackEnabled
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    val bottomNavigationFallbackPx = with(density) { 80.dp.roundToPx() }
    val searchMiniplayerBottomPx = searchMiniplayerBottomInsetPx(
        imeBottomPx = imeBottomPx,
        navigationBottomPx = navigationBottomPx,
        appBottomBarHeightPx = if (bottomNavigationProvidesInset) {
            appBottomBarHeightPx.roundToInt().coerceAtLeast(bottomNavigationFallbackPx)
        } else {
            0
        },
    )
    val predictiveBackTransform = Modifier.graphicsLayer {
        val progress = playback.navigationBackProgress.floatValue.coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(if (playback.navigationBackFromRight) 1f else 0f, 0.5f)
        translationX = size.width * 0.16f * progress * if (playback.navigationBackFromRight) -1f else 1f
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
        playback.onTransitionProgressChange(playback.transition.progress + delta / travel)
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
                playback.transition.progress >= 0.5f -> 1f
                else -> 0f
            }
            playback.onTransitionRelease(target)
        },
    )
    var activeJobsExpanded by rememberSaveable { mutableStateOf(false) }
    val activeJobs = rememberActiveJobItems(
        youtubeImport = playback.youtubeImport,
        backgroundYoutubeImport = playback.backgroundYoutubeImport,
        databaseImport = playback.databaseImport,
        downloads = playback.downloads,
        videos = playback.libraryVideos + videos,
        sourceOperationInProgress = playback.sourceOperationInProgress,
        sourceOperationMessage = playback.sourceOperationMessage,
        updateDownload = playback.updateDownload,
        libraryTransfer = playback.libraryTransfer,
        downloadStorage = playback.downloadStorage,
    )

    Box(
        modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            rootLeftPx = position.x
            rootTopPx = position.y
            rootHeightPx = coordinates.size.height.toFloat()
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isRootSearch) {
                Column {
                CenterAlignedTopAppBar(
                    expandedHeight = if (compactChrome) 56.dp else 64.dp,
                    modifier = predictiveBackTransform.then(
                        if (transitionActive) Modifier.clearAndSetSemantics { }
                        else Modifier,
                    ),
                    title = {
                        if (selectedChannel != null) {
                            Text(selectedChannel.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = if (compactChrome) MaterialTheme.typography.titleMedium else androidx.compose.material3.LocalTextStyle.current)
                        } else if (selectedPlaylist != null) {
                            Text(selectedPlaylist.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = if (compactChrome) MaterialTheme.typography.titleMedium else androidx.compose.material3.LocalTextStyle.current)
                        } else if (selected == GrayjayDestination.Home) {
                            if (drawerVisible) Text(stringResource(R.string.nav_home)) else Row(
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
                                Text(stringResource(R.string.app_name), style = if (compactChrome) MaterialTheme.typography.titleMedium else androidx.compose.material3.LocalTextStyle.current)
                            }
                        } else {
                            Text(stringResource(selected.titleRes), style = if (compactChrome) MaterialTheme.typography.titleMedium else androidx.compose.material3.LocalTextStyle.current)
                        }
                    },
                    navigationIcon = {
                        val hasBack = selectedChannel != null ||
                            selectedPlaylist != null || nestedBackEnabled
                        Row {
                            if (onToggleDrawer != null && !drawerVisible) {
                                IconButton(onClick = onToggleDrawer) {
                                    Icon(
                                        Icons.Outlined.Menu,
                                        contentDescription = stringResource(
                                            R.string.open_navigation_menu,
                                        ),
                                    )
                                }
                            }
                            if (hasBack) {
                                IconButton(onClick = onVideoBack) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            } else if (
                                onToggleDrawer == null &&
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
                        ActiveJobsButton(
                            jobs = activeJobs,
                            expanded = activeJobsExpanded,
                            storageWarning = playback.downloadStorage,
                            onClick = { activeJobsExpanded = !activeJobsExpanded },
                        )
                        IconButton(onClick = playback.onOpenProfiles) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = stringResource(R.string.open_profile),
                            )
                        }
                    },
                )
                ActiveJobsPanel(
                    visible = activeJobsExpanded,
                    jobs = activeJobs,
                    onCancelYoutubeImports = playback.onCancelYoutubeImportJobs,
                    onCancelDownloads = playback.onCancelActiveDownloads,
                    onOpenJob = { jobId ->
                        activeJobsExpanded = false
                        playback.onOpenActiveJob(jobId)
                    },
                    storageWarning = playback.downloadStorage,
                )
                }
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .then(
                            if (
                                scaffoldBottomBarNeedsNavigationBarPadding(
                                    bottomNavigationProvidesInset =
                                        bottomNavigationProvidesInset,
                                )
                            ) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (!isRootSearch) playback.video?.let { video ->
                        MiniPlayer(
                            video = video,
                            isPlaying = playback.isPlaying,
                            progress = if (playback.state.durationMs > 0) {
                                playback.state.positionMs.toFloat() / playback.state.durationMs
                            } else {
                                0f
                            },
                            player = playback.player.takeUnless { playback.state.isCasting },
                            canSkip = playback.queueSize > 1,
                            chromeAlpha = if (transitionActive) 0f else 1f,
                            onExpand = playback.onExpand,
                            onTogglePlayback = playback.onToggle,
                            onSkipToNext = playback.onNext,
                            onClose = playback.onClose,
                            onVideoBoundsChanged = { measuredBounds ->
                                miniPlayerBounds = playerBoundsInsideScaffold(
                                    boundsInRoot = measuredBounds,
                                    scaffoldLeftInRoot = rootLeftPx,
                                    scaffoldTopInRoot = rootTopPx,
                                )
                            },
                            modifier = transitionDragModifier,
                        )
                    }
                    Box(
                        Modifier.onGloballyPositioned { coordinates ->
                            appBottomBarHeightPx = coordinates.size.height.toFloat()
                        },
                    ) {
                        bottomBar()
                    }
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
                LaunchedEffect(contentPageKey, playback.pageSlideDirection) {
                    if (playback.pageSlideDirection != 0) {
                        // QuickPageTransition captures the request for this page. Clear it on the
                        // next frame so unrelated navigation returns to the regular short fade.
                        androidx.compose.runtime.withFrameNanos { }
                        playback.onPageSlideConsumed()
                    }
                }
                QuickPageTransition(
                    targetKey = contentPageKey,
                    horizontalSlideDirection = playback.pageSlideDirection,
                    animationRequest = playback.pageSlideRequest,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize(),
                ) { animatedPageKey ->
                pageStateHolder.SaveableStateProvider("${playback.activeProfileId}:$animatedPageKey") {
                CompositionLocalProvider(
                    LocalPageBackEnabled provides (!transitionActive && animatedPageKey == contentPageKey),
                ) {
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
                    onSearchQueryChange = playback.onChannelSearchQueryChange,
                    onLoadMoreSearch = playback.onLoadMoreChannelSearch,
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
                    sponsorBlockGlobalRule = SponsorBlockRule(
                        playback.sponsorBlockEnabled,
                        playback.sponsorBlockCategories,
                    ),
                    sponsorBlockOverride = playback.channelSponsorBlockOverrides[animatedChannel.id],
                    onSponsorBlockOverrideChange = { rule ->
                        playback.onChannelSponsorBlockOverrideChange(animatedChannel.id, rule)
                    },
                )
                } else if (animatedPlaylist != null) {
                if (animatedPlaylist.sourceId.isBlank()) PlaylistDetailScreen(
                    playlist = animatedPlaylist,
                    currentVideoId = playback.state.currentVideoId,
                    isPlaying = playback.isPlaying,
                    videos = remember(videos, playback.libraryVideos) {
                        (videos + playback.libraryVideos).distinctBy(VideoUiModel::id)
                    },
                    downloads = playback.downloads,
                    activeDownloadMediaTypes = playback.activePlaylistDownloads
                        .filter { it.playlistId == animatedPlaylist.id }
                        .mapTo(mutableSetOf()) { it.mediaType },
                    automaticDownloadMediaTypes = playback.automaticPlaylistDownloads
                        .filter { it.playlistId == animatedPlaylist.id }
                        .mapTo(mutableSetOf()) { it.mediaType },
                    automaticDownloadsEnabled = playback.automaticPlaylistDownloadsEnabled,
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
                    onAutomaticDownloadChange = { mediaType, enabled ->
                        playback.onPlaylistAutomaticDownloadChange(
                            animatedPlaylist.id,
                            mediaType,
                            enabled,
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
                    currentVideoId = playback.state.currentVideoId,
                    isPlaying = playback.isPlaying,
                    downloads = playback.downloads,
                    localPlaylists = playlists,
                    onVideoClick = onVideoClick,
                    onPlayFromHere = playback.onPlayRemotePlaylistFrom,
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
                        sources = sourcePresentation.sources,
                        availableUpdate = playback.availableUpdate.takeIf {
                            RELEASE_UPDATE_CHECK_ENABLED
                        },
                        onInstallUpdate = playback.onInstallUpdate,
                        updateDownload = playback.updateDownload,
                        onCancelUpdateDownload = playback.onCancelUpdateDownload,
                        onHydrateVideoMetadata = playback.onHydrateVideoMetadata,
                        onFeedSelected = sourcePresentation.onHomeFeedSelected,
                        onBrowseTabSelected = sourcePresentation.onHomeBrowseTabSelected,
                        onBrowseOptionSelected = sourcePresentation.onHomeBrowseOptionSelected,
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
                        videos = remember(
                            playback.followingVideos, playback.nowPlaying.recommendations,
                            playback.nowPlaying.video,
                        ) { (
                            playback.followingVideos + playback.nowPlaying.recommendations +
                                listOfNotNull(playback.nowPlaying.video)
                            ).distinctBy(VideoUiModel::id) },
                        completeFeedLoaded = playback.followingFeedLoaded,
                        completeFeedLoading = playback.followingFeedLoading,
                        completeFeedCompleted = playback.followingFeedCompleted,
                        completeFeedTotal = playback.followingFeedTotal,
                        completeFeedError = playback.followingFeedError,
                        onRequestCompleteFeed = playback.onLoadFollowingComplete,
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
                        filterSelections = sourcePresentation.filterSelections,
                        onFilterSelectionChange = sourcePresentation.onFilterSelectionChange,
                        onQueryChange = sourcePresentation.onSearchQueryChange,
                        onSubmit = sourcePresentation.onSearchSubmit,
                        onLoadMore = sourcePresentation.onLoadMoreSearch,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = sourcePresentation.onSearchVideoLongClick,
                        onChannelClick = onChannelClick,
                        onPlaylistClick = onPlaylistClick,
                        autoFocus = sourcePresentation.searchAutoFocusRequested,
                        onAutoFocusConsumed = sourcePresentation.onSearchAutoFocusConsumed,
                        showNavigationMenuButton = onToggleDrawer != null && !drawerVisible,
                        onNavigationMenuClick = onToggleDrawer ?: {},
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
                        onPlayDownloads = playback.onPlayQueue,
                        onPlayDownloadsFrom = { ids, videoId ->
                            playback.onPlayQueue(playlistQueueFrom(ids, videoId))
                        },
                        onRemoveSelectionFromHistory = playback.onRemoveSelectionFromHistory,
                        onRemoveDownloads = playback.onRemoveDownloads,
                        onRemovePlaylists = playback.onRemovePlaylists,
                        onExportDownloads = playback.onExportDownloads,
                        onRenamePlaylist = playback.onRenamePlaylist,
                        selectedFilter = playback.libraryFilter,
                        onSelectedFilterChange = playback.onLibraryFilterChange,
                        playlistListState = playback.libraryPlaylistListState,
                        downloadFocusVideoId = playback.downloadFocusVideoId,
                        onDownloadFocusConsumed = playback.onDownloadFocusConsumed,
                    )
                    GrayjayDestination.Settings -> SettingsScreen(
                        sources = sourcePresentation.sources,
                        uiLanguageTag = playback.uiLanguageTag,
                        onUiLanguageChange = playback.onUiLanguageChange,
                        dynamicColorsEnabled = dynamicColorsEnabled,
                        onDynamicColorsChange = onDynamicColorsChange,
                        themeMode = playback.themeMode,
                        onThemeModeChange = playback.onThemeModeChange,
                        privateSessionEnabled = privateSessionEnabled,
                        onPrivateSessionChange = onPrivateSessionChange,
                        onManageSources = onManageSources,
                        onImportDatabase = onImportDatabase,
                        onImportNewPipeDatabase = onImportNewPipeDatabase,
                        onExportLibrary = playback.onExportLibrary,
                        libraryVideos = playback.libraryVideos,
                        downloads = playback.downloads,
                        onRemoveDownloads = playback.onRemoveDownloads,
                        onExportDownloads = playback.onExportDownloads,
                        activeSourceCount = sourcePresentation.sources.count {
                            it.isEnabled && it.availability != SourceAvailability.MissingPlugin
                        },
                        defaultPlaybackSpeed = playback.defaultPlaybackSpeed,
                        onDefaultPlaybackSpeedChange = playback.onDefaultPlaybackSpeedChange,
                        perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                        onPerChannelPlaybackSpeedChange = playback.onPerChannelPlaybackSpeedChange,
                        holdToSpeedEnabled = playback.holdToSpeedEnabled,
                        onHoldToSpeedChange = playback.onHoldToSpeedChange,
                        sponsorBlockEnabled = playback.sponsorBlockEnabled,
                        sponsorBlockSkipNoticesEnabled = playback.sponsorBlockSkipNoticesEnabled,
                        sponsorBlockCategories = playback.sponsorBlockCategories,
                        onSponsorBlockEnabledChange = playback.onSponsorBlockEnabledChange,
                        onSponsorBlockSkipNoticesEnabledChange =
                            playback.onSponsorBlockSkipNoticesEnabledChange,
                        onSponsorBlockCategoriesChange = playback.onSponsorBlockCategoriesChange,
                        preferredVideoQuality = playback.preferredVideoQuality,
                        onPreferredVideoQualityChange = playback.onPreferredVideoQualityChange,
                        preferredAudioBitrate = playback.preferredAudioBitrate,
                        onPreferredAudioBitrateChange = playback.onPreferredAudioBitrateChange,
                        preferredAudioLanguage = playback.preferredAudioLanguage,
                        onPreferredAudioLanguageChange = playback.onPreferredAudioLanguageChange,
                        preferOriginalAudio = playback.preferOriginalAudio,
                        onPreferOriginalAudioChange = playback.onPreferOriginalAudioChange,
                        preferNewPipeForYoutubePlayback =
                            playback.preferNewPipeForYoutubePlayback,
                        onPreferNewPipeForYoutubePlaybackChange =
                            playback.onPreferNewPipeForYoutubePlaybackChange,
                        subscriptionFetchMode = playback.subscriptionFetchMode,
                        onSubscriptionFetchModeChange = playback.onSubscriptionFetchModeChange,
                        videoTitleLanguageMode = playback.videoTitleLanguageMode,
                        onVideoTitleLanguageModeChange = playback.onVideoTitleLanguageModeChange,
                        stickyCaptionsEnabled = playback.stickyCaptionsEnabled,
                        onStickyCaptionsChange = playback.onStickyCaptionsChange,
                        showRecommendations = playback.showRecommendations,
                        onShowRecommendationsChange = playback.onShowRecommendationsChange,
                        searchHistoryEnabled = playback.searchHistoryEnabled,
                        onSearchHistoryChange = playback.onSearchHistoryChange,
                        crashLoggingEnabled = playback.crashLoggingEnabled,
                        onCrashLoggingChange = playback.onCrashLoggingChange,
                        keepScreenAwake = playback.keepScreenAwake,
                        onKeepScreenAwakeChange = playback.onKeepScreenAwakeChange,
                        pictureInPictureEnabled = playback.pictureInPictureEnabled,
                        onPictureInPictureChange = playback.onPictureInPictureChange,
                        automaticPlaylistDownloadsEnabled =
                            playback.automaticPlaylistDownloadsEnabled,
                        onAutomaticPlaylistDownloadsChange =
                            playback.onAutomaticPlaylistDownloadsChange,
                        otherAudioDuckingEnabled = playback.otherAudioDuckingEnabled,
                        onOtherAudioDuckingChange = playback.onOtherAudioDuckingChange,
                        otherAudioDuckVolumePercent = playback.otherAudioDuckVolumePercent,
                        onOtherAudioDuckVolumeChange = playback.onOtherAudioDuckVolumeChange,
                        pcLink = playback.pcLink,
                        onScanPcPairingQr = playback.onScanPcPairingQr,
                        onRemovePairedComputer = playback.onRemovePairedComputer,
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
                        backgroundYoutubeImport = sourcePresentation.backgroundYoutubeImport,
                        youtubeImportSchedule = sourcePresentation.youtubeImportSchedule,
                        onImportYoutube = sourcePresentation.onImportYoutube,
                        onDismissYoutubeImport = sourcePresentation.onDismissYoutubeImport,
                        onCancelYoutubeImportJobs =
                            sourcePresentation.onCancelYoutubeImportJobs,
                        onYoutubeImportScheduleChange =
                            sourcePresentation.onYoutubeImportScheduleChange,
                    )
                    }
                }
                }
                }
                }
            }
        }

        if (isRootSearch) playback.video?.let { video ->
            MiniPlayer(
                video = video,
                isPlaying = playback.isPlaying,
                progress = if (playback.state.durationMs > 0) {
                    playback.state.positionMs.toFloat() / playback.state.durationMs
                } else {
                    0f
                },
                player = playback.player.takeUnless { playback.state.isCasting },
                canSkip = playback.queueSize > 1,
                chromeAlpha = if (transitionActive) 0f else 1f,
                onExpand = playback.onExpand,
                onTogglePlayback = playback.onToggle,
                onSkipToNext = playback.onNext,
                onClose = playback.onClose,
                onVideoBoundsChanged = { measuredBounds ->
                    miniPlayerBounds = playerBoundsInsideScaffold(
                        boundsInRoot = measuredBounds,
                        scaffoldLeftInRoot = rootLeftPx,
                        scaffoldTopInRoot = rootTopPx,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // This inset belongs outside every pointer-input modifier: the
                    // navigation bar/IME beneath it must not become part of the drag target.
                    .padding(
                        bottom = with(density) { searchMiniplayerBottomPx.toDp() },
                    )
                    .then(transitionDragModifier)
                    .zIndex(2f),
            )
        }

        if (transitionVideo != null && (transitionActive || expandedPlayerBounds != null)) {
            val expanded = expandedPlayerBounds
            val minimized = miniPlayerBounds
            val expandedTop = expanded?.top ?: 0f
            val minimizedTop = minimized?.top ?: 0f
            val minimizedHeight = minimized?.height
            val expandedSurfaceColor = MaterialTheme.colorScheme.surface
            val minimizedSurfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .playerMorphViewport(
                        expandedHeightPx = rootHeightPx,
                        minimizedHeightPx = minimizedHeight,
                        minimizedTopPx = minimizedTop,
                        isActive = transitionActive,
                        progress = { playback.transition.progress },
                    )
                    .then(if (transitionActive) Modifier else Modifier.clearAndSetSemantics { })
                    .drawWithContent {
                        val transitionProgress = playback.transition.progress.coerceIn(0f, 1f)
                        clipRect(bottom = size.height) {
                            drawRect(
                                androidx.compose.ui.graphics.lerp(
                                    expandedSurfaceColor,
                                    minimizedSurfaceColor,
                                    transitionProgress,
                                ),
                            )
                            this@drawWithContent.drawContent()
                        }
                    }
                    .zIndex(1f),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = -expandedTop * playback.transition.progress
                            },
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        topBar = {
                            CenterAlignedTopAppBar(
                                modifier = Modifier.graphicsLayer {
                                    alpha = ((0.7f - playback.transition.progress) / 0.7f)
                                        .coerceIn(0f, 1f)
                                },
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
                                    Row {
                                        if (onToggleDrawer != null && !drawerVisible) {
                                            IconButton(onClick = onToggleDrawer) {
                                                Icon(
                                                    Icons.Outlined.Menu,
                                                    contentDescription = stringResource(
                                                        R.string.open_navigation_menu,
                                                    ),
                                                )
                                            }
                                        }
                                        IconButton(onClick = playback.onCollapse) {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.ArrowBack,
                                                contentDescription = stringResource(R.string.back),
                                            )
                                        }
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
                                    IconButton(
                                        onClick = playback.onClose,
                                        modifier = Modifier.testTag("now-playing-close"),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.close_playback),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .graphicsLayer {
                                    alpha = ((0.7f - playback.transition.progress) / 0.7f)
                                        .coerceIn(0f, 1f)
                                },
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
                                holdToSpeedEnabled = playback.holdToSpeedEnabled,
                                onSpeedHoldStart = playback.onSpeedHoldStart,
                                onSpeedHoldEnd = playback.onSpeedHoldEnd,
                                perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                                videoPlaybackSpeedOverride =
                                    playback.videoPlaybackSpeeds[transitionVideo.id],
                                channelPlaybackSpeed = playback.channelPlaybackSpeeds[
                                    transitionVideo.playbackChannelKey()
                                ],
                                sponsorBlockInheritedRule = playback.channelSponsorBlockOverrides[
                                    transitionVideo.playbackChannelKey()
                                ] ?: SponsorBlockRule(
                                    playback.sponsorBlockEnabled,
                                    playback.sponsorBlockCategories,
                                ),
                                sponsorBlockVideoOverride =
                                    playback.videoSponsorBlockOverrides[transitionVideo.id],
                                onSponsorBlockVideoOverrideChange = { rule ->
                                    playback.onVideoSponsorBlockOverrideChange(transitionVideo.id, rule)
                                },
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
                                onStoryboardLoadFailure = { playback.onStoryboardLoadFailure(transitionVideo.id) },
                                onVideoClick = onVideoClick,
                                onVideoLongClick = playback.onVideoLongClick,
                                onQueueVideoLongClick = playback.onQueueVideoLongClick,
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
                                onOpenCommentReplies = playback.onOpenCommentReplies,
                                onDismissCommentReplies = playback.onDismissCommentReplies,
                                onLoadMoreCommentReplies = playback.onLoadMoreCommentReplies,
                                onResumeFromHistory = playback.onResumeFromHistory,
                                renderPlayer = false,
                                isActive = transitionActive,
                                allowSideBySideLayout = onToggleDrawer != null && !drawerVisible,
                                onPlayerBoundsChanged = { measuredBounds ->
                                    val transitionProgress = playback.transition.progress
                                        .coerceIn(0f, 1f)
                                    val overlayTransformY =
                                        minimizedTop * transitionProgress -
                                            expandedTop * transitionProgress
                                    val localBounds = playerBoundsInsideScaffold(
                                        boundsInRoot = measuredBounds,
                                        scaffoldLeftInRoot = rootLeftPx,
                                        scaffoldTopInRoot = rootTopPx,
                                    )
                                    val normalizedBounds = Rect(
                                        left = localBounds.left,
                                        top = localBounds.top - overlayTransformY,
                                        right = localBounds.right,
                                        bottom = localBounds.bottom - overlayTransformY,
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
                    if (transitionActive && minimized != null) {
                        TransitionMiniPlayerChrome(
                            playback = playback,
                            video = transitionVideo,
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
            val queueIndex = playback.state.queueVideoIds.indexOf(playback.state.currentVideoId)
            if (!transitionVideo.playbackAudioOnly || transitionActive) PlayerSurface(
                video = transitionVideo,
                player = playback.player,
                playback = playback.state,
                isLoading = playback.nowPlaying.isLoadingPlayback || playback.state.isBuffering,
                isFullscreen = false,
                canGoPrevious = queueIndex > 0 || playback.player.currentPosition > 5_000L,
                canGoNext = queueIndex >= 0 && queueIndex < playback.state.queueVideoIds.lastIndex,
                onTogglePlayback = playback.onToggle,
                onSkipPrevious = playback.onPrevious,
                onSkipNext = playback.onNext,
                onSeekBy = playback.onSeekBy,
                onSeek = playback.onSeek,
                onSpeedChange = playback.onSpeedChange,
                holdToSpeedEnabled = playback.holdToSpeedEnabled,
                onSpeedHoldStart = playback.onSpeedHoldStart,
                onSpeedHoldEnd = playback.onSpeedHoldEnd,
                perChannelPlaybackSpeedEnabled = playback.perChannelPlaybackSpeedEnabled,
                videoPlaybackSpeedOverride = playback.videoPlaybackSpeeds[transitionVideo.id],
                channelPlaybackSpeed =
                    playback.channelPlaybackSpeeds[transitionVideo.playbackChannelKey()],
                sponsorBlockSegments = playback.nowPlaying.sponsorBlockSegments,
                sponsorBlockSkipNotice = playback.nowPlaying.sponsorBlockSkipNotice,
                sponsorBlockInheritedRule = playback.channelSponsorBlockOverrides[
                    transitionVideo.playbackChannelKey()
                ] ?: SponsorBlockRule(playback.sponsorBlockEnabled, playback.sponsorBlockCategories),
                sponsorBlockVideoOverride = playback.videoSponsorBlockOverrides[transitionVideo.id],
                onSponsorBlockVideoOverrideChange = { rule ->
                    playback.onVideoSponsorBlockOverrideChange(transitionVideo.id, rule)
                },
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
                controlsAlpha = if (
                    transitionActive && !isTransitionDragging &&
                    !playback.transition.isSettling && playback.transition.target <= 0.001f
                ) 1f else 0f,
                topControlsAtStart = onToggleDrawer != null && !drawerVisible,
                resumePositionFraction = playback.nowPlaying.resumePositionFraction,
                onResumeFromHistory = playback.onResumeFromHistory,
                modifier = (if (transitionActive && expanded != null) {
                    Modifier
                        .offset {
                            val progress = playback.transition.progress.coerceIn(0f, 1f)
                            IntOffset(
                                lerp(start.left, end.left, progress).roundToInt(),
                                lerp(start.top, end.top, progress).roundToInt(),
                            )
                        }
                        .size(
                            width = with(density) { start.width.toDp() },
                            height = with(density) { start.height.toDp() },
                        )
                        .graphicsLayer {
                            val progress = playback.transition.progress.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = lerp(start.width, end.width, progress) / start.width
                            scaleY = lerp(start.height, end.height, progress) / start.height
                            alpha = if (transitionVideo.playbackAudioOnly) {
                                1f - progress
                            } else {
                                1f
                            }
                        }
                } else {
                    Modifier
                        .offset {
                            IntOffset(end.left.roundToInt(), end.top.roundToInt())
                        }
                        .size(
                            width = with(density) { end.width.toDp() },
                            height = with(density) { end.height.toDp() },
                        )
                })
                    .then(transitionDragModifier)
                    .zIndex(2f),
            )
        }
    }
}

@Composable
private fun TransitionMiniPlayerChrome(
    playback: PlaybackPresentation,
    video: VideoUiModel,
) {
    val visible by remember(playback.transition) {
        derivedStateOf { playback.transition.progress > 0.8f }
    }
    if (!visible) return
    MiniPlayerChrome(
        video = video,
        isPlaying = playback.isPlaying,
        progress = if (playback.state.durationMs > 0) {
            playback.state.positionMs.toFloat() / playback.state.durationMs
        } else {
            0f
        },
        player = playback.player.takeUnless { playback.state.isCasting },
        canSkip = playback.queueSize > 1,
        onTogglePlayback = playback.onToggle,
        onSkipToNext = playback.onNext,
        onClose = playback.onClose,
        applyTestTags = false,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = ((playback.transition.progress - 0.8f) / 0.2f)
                    .coerceIn(0f, 1f)
            }
            .clearAndSetSemantics { },
    )
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

internal fun transitionOverlayHeightPx(
    rootHeightPx: Float,
    minimizedHeightPx: Float?,
    progress: Float,
): Float {
    val root = rootHeightPx.coerceAtLeast(1f)
    val minimized = minimizedHeightPx?.coerceAtLeast(1f) ?: root
    return lerp(root, minimized, progress.coerceIn(0f, 1f)).coerceAtLeast(1f)
}

private fun VideoUiModel.playbackChannelKey(): String = authorUrl.ifBlank {
    channelId.ifBlank { "$sourceId:$creator" }
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
