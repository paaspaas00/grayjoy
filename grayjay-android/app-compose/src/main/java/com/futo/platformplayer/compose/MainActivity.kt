package com.futo.platformplayer.compose

import android.Manifest
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.futo.platformplayer.compose.ui.GrayjayApp
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.ThemeMode
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import com.futo.platformplayer.compose.playback.PictureInPictureActionReceiver
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.media3.ui.PlayerView

class MainActivity : FragmentActivity() {
    private val grayjayViewModel by viewModels<GrayjayViewModel>()
    private var pendingSourceUrl by mutableStateOf<String?>(null)
    private var pendingDatabaseImportUri by mutableStateOf<Uri?>(null)
    private var pendingExternalContentUrl by mutableStateOf<String?>(null)
    private var pendingPcPairingUrl by mutableStateOf<String?>(null)
    private var deviceIsLandscape by mutableStateOf(false)
    private var pictureInPictureMode by mutableStateOf(false)
    private var pictureInPictureEntryPending = false
    private var pictureInPictureSourceRect: Rect? = null
    private var playerFullscreen = false
    private var playerLandscapeFullscreen = false
    private val deviceOrientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                automaticFullscreenPosture(
                    autoRotateEnabled = isSystemAutoRotateEnabled(),
                    orientation = orientation,
                )?.let { deviceIsLandscape = it }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        pendingSourceUrl = intent.pluginSourceUrlOrNull()
        pendingDatabaseImportUri = intent.databaseImportUriOrNull()
        pendingExternalContentUrl = intent.externalContentUrlOrNull()
        pendingPcPairingUrl = intent.pcPairingUrlOrNull()
        enableEdgeToEdge()
        setContent {
            val viewModel = grayjayViewModel
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val databaseImportPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let(viewModel::prepareDatabaseImport)
            }
            val newPipeImportPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let(viewModel::prepareNewPipeImport)
            }
            val sourceLoginLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.getStringExtra(SourceLoginActivity.EXTRA_SOURCE_ID)
                        ?.let(viewModel::reloadSourceAuthentication)
                }
            }
            val sourceQrLauncher = rememberLauncherForActivityResult(
                ScanContract(),
            ) { result ->
                result.contents?.let(viewModel::installSourceFromQr)
            }
            val pcQrLauncher = rememberLauncherForActivityResult(
                ScanContract(),
            ) { result ->
                result.contents?.let { payload ->
                    val message = if (viewModel.pairComputerFromQr(payload)) {
                        R.string.computer_paired
                    } else {
                        R.string.invalid_pc_pairing_qr
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                notificationPermissionPreferences.edit()
                    .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
                    .apply()
            }

            LaunchedEffect(uiState.keepScreenAwake) {
                if (uiState.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(
                uiState.pictureInPictureEnabled,
                uiState.nowPlaying.video?.id,
                uiState.playback.isPlaying,
                uiState.playback.isBuffering,
                uiState.nowPlaying.isLoadingPlayback,
                uiState.playback.currentVideoWidth,
                uiState.playback.currentVideoHeight,
                pictureInPictureMode,
            ) {
                updatePictureInPictureParams()
            }

            LaunchedEffect(pendingSourceUrl) {
                pendingSourceUrl?.let(viewModel::installSource)
                pendingSourceUrl = null
            }
            LaunchedEffect(pendingDatabaseImportUri) {
                pendingDatabaseImportUri?.let(viewModel::prepareDatabaseImport)
                pendingDatabaseImportUri = null
            }
            LaunchedEffect(pendingExternalContentUrl) {
                pendingExternalContentUrl?.let(viewModel::openExternalUrl)
                pendingExternalContentUrl = null
            }
            LaunchedEffect(pendingPcPairingUrl) {
                pendingPcPairingUrl?.let { payload ->
                    val message = if (viewModel.pairComputerFromQr(payload)) {
                        R.string.computer_paired
                    } else {
                        R.string.invalid_pc_pairing_qr
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
                pendingPcPairingUrl = null
            }
            val hasActiveDownloads = uiState.downloads.values.any { it.isActive }
            var pendingDownloadCompletionKeys by remember(uiState.activeProfileId) {
                mutableStateOf(emptySet<DownloadCompletionKey>())
            }
            LaunchedEffect(uiState.activeProfileId, uiState.downloads) {
                val transition = updateDownloadCompletionBatch(
                    pending = pendingDownloadCompletionKeys,
                    downloads = uiState.downloads.values,
                )
                pendingDownloadCompletionKeys = transition.pending
                if (transition.showCompletionToast) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.all_downloads_completed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            LaunchedEffect(
                uiState.nowPlaying.video?.id,
                hasActiveDownloads,
                uiState.pcLink.pairedComputers.size,
            ) {
                if (
                    (
                        uiState.nowPlaying.video != null ||
                            hasActiveDownloads ||
                            uiState.pcLink.pairedComputers.isNotEmpty()
                        ) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED &&
                    !notificationPermissionPreferences.getBoolean(
                        KEY_NOTIFICATION_PERMISSION_REQUESTED,
                        false,
                    )
                ) {
                    notificationPermissionPreferences.edit()
                        .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
                        .apply()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.System -> systemInDarkTheme
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            GrayjayTheme(
                darkTheme = darkTheme,
                dynamicColor = uiState.dynamicColorsEnabled,
            ) {
                GrayjayApp(
                    uiState = uiState,
                    player = viewModel.player,
                    isDarkTheme = darkTheme,
                    onDarkThemeChange = viewModel::setDarkThemeEnabled,
                    onThemeModeChange = viewModel::setThemeMode,
                    deviceIsLandscape = deviceIsLandscape,
                    onFullscreenPresentationChanged = ::setFullscreenPresentation,
                    onDynamicColorsChange = viewModel::setDynamicColorsEnabled,
                    onPrivateSessionChange = viewModel::setPrivateSessionEnabled,
                    onOpenVideo = viewModel::openVideo,
                    onLoadChannel = viewModel::loadChannel,
                    onChannelTabSelected = viewModel::selectChannelTab,
                    onHomeFeedSelected = viewModel::selectHomeFeed,
                    onRefreshHome = viewModel::refreshHome,
                    onLoadMoreHome = viewModel::loadMoreHome,
                    onPlayQueue = viewModel::playQueue,
                    onQueueVideos = viewModel::enqueueVideos,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onPlayPlaylistFrom = viewModel::playPlaylistFrom,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSkipToNext = viewModel::skipToNext,
                    onSkipToPrevious = viewModel::skipToPrevious,
                    onSeekPlaybackBy = viewModel::seekPlaybackBy,
                    onPlaybackSpeedChange = viewModel::setPlaybackSpeed,
                    onUseChannelPlaybackSpeed = viewModel::useChannelPlaybackSpeedForCurrentVideo,
                    onChannelPlaybackSpeedChange = viewModel::setChannelPlaybackSpeed,
                    onVideoQualityChange = viewModel::setVideoQuality,
                    onCaptionsEnabledChange = viewModel::setCaptionsEnabled,
                    onSubtitleLanguageChange = viewModel::setSubtitleLanguage,
                    onRetryPlayback = viewModel::retryPlayback,
                    onClosePlayback = viewModel::closePlayback,
                    onToggleWatchLater = viewModel::toggleWatchLater,
                    onToggleDownloaded = viewModel::toggleDownloaded,
                    onToggleAudioDownloaded = viewModel::toggleAudioDownloaded,
                    onDownloadVideo = viewModel::downloadVideo,
                    onDownloadAudio = viewModel::downloadAudio,
                    onDownloadVideos = viewModel::downloadVideos,
                    onDownloadPlaylist = viewModel::downloadPlaylist,
                    onCancelDownloadPlaylist = viewModel::cancelPlaylistDownload,
                    onLoadRemotePlaylist = viewModel::loadRemotePlaylist,
                    onLoadMoreRemotePlaylist = viewModel::loadMoreRemotePlaylist,
                    onPlayRemotePlaylist = viewModel::playRemotePlaylist,
                    onPlayRemotePlaylistFrom = viewModel::playRemotePlaylistFrom,
                    onDownloadRemotePlaylist = viewModel::downloadRemotePlaylist,
                    onCancelDownloadRemotePlaylist = viewModel::cancelRemotePlaylistDownload,
                    onCreateLocalPlaylistFromRemote = viewModel::createLocalPlaylistFromRemote,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onRenamePlaylist = viewModel::renamePlaylist,
                    onAddVideosToPlaylist = viewModel::addVideosToPlaylist,
                    onRemoveVideosFromPlaylist = viewModel::removeVideosFromPlaylist,
                    onReorderPlaylist = viewModel::reorderPlaylist,
                    onRemoveVideosFromHistory = viewModel::removeVideosFromHistory,
                    onRemoveDownloads = viewModel::removeDownloads,
                    onRemovePlaylists = viewModel::removePlaylists,
                    onExportDownloads = viewModel::exportDownloads,
                    onSeekPlayback = viewModel::seekPlayback,
                    onResumeFromHistory = viewModel::resumePlaybackFromHistory,
                    onSourceEnabledChange = viewModel::setSourceEnabled,
                    onInstallSource = viewModel::installSource,
                    onScanSourceQr = {
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(getString(R.string.scan_source_qr_description))
                            setOrientationLocked(true)
                            setCameraId(0)
                            setBeepEnabled(false)
                            setBarcodeImageEnabled(false)
                            setCaptureActivity(QRCaptureActivity::class.java)
                        }.let(sourceQrLauncher::launch)
                    },
                    onRefreshSource = viewModel::refreshSource,
                    onClearSourceCache = viewModel::clearSourceCache,
                    onRemoveSource = viewModel::removeSource,
                    onLoginSource = { source ->
                        sourceLoginLauncher.launch(
                            SourceLoginActivity.intent(
                                context = this,
                                sourceId = source.id,
                                pluginId = source.engineId,
                                configUrl = source.pluginConfigUrl,
                                profileId = uiState.activeProfileId,
                            ),
                        )
                    },
                    onLogoutSource = viewModel::clearSourceAuthentication,
                    onImportYoutube = viewModel::importYoutubeAccount,
                    onDismissYoutubeImport = viewModel::dismissYoutubeImport,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSearchSubmit = viewModel::submitSearch,
                    onLoadMoreSearch = viewModel::loadMoreSearch,
                    onLoadMoreChannel = viewModel::loadMoreChannel,
                    onLoadMoreRecommendations = viewModel::loadMoreRecommendations,
                    onLoadMoreComments = viewModel::loadMoreComments,
                    onToggleFollowing = viewModel::toggleFollowing,
                    onCreatorFollowedChange = viewModel::setCreatorFollowed,
                    onChooseDatabaseImport = { databaseImportPicker.launch(arrayOf("*/*")) },
                    onChooseNewPipeImport = {
                        newPipeImportPicker.launch(
                            arrayOf("application/zip", "application/x-sqlite3", "application/json", "*/*"),
                        )
                    },
                    onRetryDatabaseImport = viewModel::retryDatabaseImport,
                    onConfirmDatabaseImport = viewModel::confirmDatabaseImport,
                    onDismissDatabaseImport = viewModel::dismissDatabaseImport,
                    onTrustUnverifiedSource = viewModel::trustUnverifiedSource,
                    onRejectUnverifiedSource = viewModel::rejectUnverifiedSource,
                    onSwitchProfile = viewModel::switchProfile,
                    onCreateProfile = viewModel::createProfile,
                    onVerifyProfilePin = viewModel::verifyProfilePin,
                    onDefaultPlaybackSpeedChange = viewModel::setDefaultPlaybackSpeed,
                    onPerChannelPlaybackSpeedChange = viewModel::setPerChannelPlaybackSpeedEnabled,
                    onPreferredVideoQualityChange = viewModel::setPreferredVideoQuality,
                    onPreferredAudioBitrateChange = viewModel::setPreferredAudioBitrate,
                    onStickyCaptionsChange = viewModel::setStickyCaptionsEnabled,
                    onShowRecommendationsChange = viewModel::setShowRecommendations,
                    onSearchHistoryChange = viewModel::setSearchHistoryEnabled,
                    onKeepScreenAwakeChange = viewModel::setKeepScreenAwake,
                    onPictureInPictureChange = viewModel::setPictureInPictureEnabled,
                    onStartChromecastDiscovery = viewModel::startChromecastDiscovery,
                    onConnectChromecast = viewModel::connectChromecast,
                    onDisconnectChromecast = viewModel::disconnectChromecast,
                    onOtherAudioDuckingChange = viewModel::setOtherAudioDuckingEnabled,
                    onOtherAudioDuckVolumeChange = viewModel::setOtherAudioDuckVolumePercent,
                    onScanPcPairingQr = {
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(getString(R.string.scan_pc_pairing_qr))
                            setOrientationLocked(true)
                            setCameraId(0)
                            setBeepEnabled(false)
                            setBarcodeImageEnabled(false)
                            setCaptureActivity(QRCaptureActivity::class.java)
                        }.let(pcQrLauncher::launch)
                    },
                    onRemovePairedComputer = viewModel::removePairedComputer,
                    onPlayFromComputer = viewModel::playFromComputer,
                    onToggleComputerPlayback = viewModel::toggleComputerPlayback,
                    onPreviousComputerPlayback = viewModel::skipComputerPlaybackPrevious,
                    onNextComputerPlayback = viewModel::skipComputerPlaybackNext,
                    onSeekComputerPlayback = viewModel::seekComputerPlayback,
                    onExternalNavigationHandled = viewModel::consumeExternalNavigation,
                    onCheckForUpdates = viewModel::checkForUpdates,
                    pictureInPictureMode = pictureInPictureMode,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pictureInPictureEntryPending = false
        if (!isInPictureInPictureMode) pictureInPictureMode = false
        applyPlayerStatusBarVisibility()
        grayjayViewModel.setAppForeground(true)
        if (!isSystemAutoRotateEnabled()) deviceIsLandscape = false
        if (deviceOrientationListener.canDetectOrientation()) {
            deviceOrientationListener.enable()
        }
        window.decorView.post {
            capturePictureInPictureSourceRect()
            updatePictureInPictureParams()
        }
    }

    override fun onPause() {
        grayjayViewModel.setAppForeground(false)
        deviceOrientationListener.disable()
        if (!pictureInPictureEntryPending && !pictureInPictureMode && !isInPictureInPictureMode) {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
        }
        super.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!shouldEnterPictureInPicture(grayjayViewModel.uiState.value)) {
            pictureInPictureEntryPending = false
            return
        }
        pictureInPictureEntryPending = true
        capturePictureInPictureSourceRect()
        val params = buildPictureInPictureParams()
        setPictureInPictureParams(params)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching { enterPictureInPictureMode(params) }
                .onFailure { pictureInPictureEntryPending = false }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPictureEntryPending = false
        pictureInPictureMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            setFullscreenPresentation(fullscreen = false, portraitVideo = false)
        } else {
            // Lifecycle is still CREATED both when Android closes the PiP window and when the
            // user expands it back into this Activity. Treating CREATED as a dismissal therefore
            // closed the real playback while Grayjoy was returning to the foreground. A genuine
            // PiP close finishes the Activity and clears its ViewModel/player naturally; a normal
            // expansion must leave Now Playing untouched.
            window.decorView.post {
                capturePictureInPictureSourceRect()
                updatePictureInPictureParams()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSourceUrl = intent.pluginSourceUrlOrNull()
        pendingDatabaseImportUri = intent.databaseImportUriOrNull()
        pendingExternalContentUrl = intent.externalContentUrlOrNull()
        pendingPcPairingUrl = intent.pcPairingUrlOrNull()
    }

    private val notificationPermissionPreferences by lazy {
        getSharedPreferences(NOTIFICATION_PERMISSION_PREFERENCES, MODE_PRIVATE)
    }

    private fun isSystemAutoRotateEnabled(): Boolean = runCatching {
        Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    }.getOrDefault(false)

    private fun setFullscreenPresentation(fullscreen: Boolean, portraitVideo: Boolean) {
        playerFullscreen = fullscreen
        playerLandscapeFullscreen = fullscreen && !portraitVideo
        val playerOrientation = fullscreenPlayerOrientation(fullscreen, portraitVideo)
        if (requestedOrientation != playerOrientation) {
            // This is scoped to Grayjoy's Activity. SENSOR_LANDSCAPE deliberately bypasses the
            // user's global rotation lock without reading or changing that Android setting.
            requestedOrientation = playerOrientation
        }
        applyPlayerStatusBarVisibility()
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!pictureInPictureMode) capturePictureInPictureSourceRect()
        runCatching { setPictureInPictureParams(buildPictureInPictureParams()) }
    }

    private fun buildPictureInPictureParams(): PictureInPictureParams {
        val state = grayjayViewModel.uiState.value
        val hasVideo = state.nowPlaying.video != null &&
            state.playback.currentVideoId != null &&
            state.nowPlaying.video?.playbackAudioOnly != true
        val autoEnter = shouldEnterPictureInPicture(state)
        val videoSize = normalizedPictureInPictureAspectRatio(
            state.playback.currentVideoWidth ?: grayjayViewModel.player.videoSize.width,
            state.playback.currentVideoHeight ?: grayjayViewModel.player.videoSize.height,
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(videoSize.first, videoSize.second))
            .apply {
                pictureInPictureSourceRect
                    ?.takeUnless { it.isEmpty }
                    ?.let(::setSourceRectHint)
                setActions(
                    if (hasVideo) listOf(pictureInPicturePlayPauseAction(state.playback.isPlaying))
                    else emptyList(),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun pictureInPicturePlayPauseAction(isPlaying: Boolean): RemoteAction {
        val label = getString(if (isPlaying) R.string.pause else R.string.play)
        return RemoteAction(
            Icon.createWithResource(
                this,
                if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
            ),
            label,
            label,
            PictureInPictureActionReceiver.togglePlaybackIntent(this),
        )
    }

    private fun capturePictureInPictureSourceRect() {
        if (pictureInPictureMode) return
        val playerView = window.decorView.findVisiblePlayerView() ?: return
        if (playerView.width <= 1 || playerView.height <= 1) return
        val location = IntArray(2)
        playerView.getLocationInWindow(location)
        pictureInPictureSourceRect = Rect(
            location[0],
            location[1],
            location[0] + playerView.width,
            location[1] + playerView.height,
        )
    }

    private fun applyPlayerStatusBarVisibility() {
        WindowCompat.getInsetsController(window, window.decorView).run {
            if (playerLandscapeFullscreen) hide(WindowInsetsCompat.Type.statusBars())
            else show(WindowInsetsCompat.Type.statusBars())
            if (playerFullscreen) hide(WindowInsetsCompat.Type.navigationBars())
            else show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_PREFERENCES = "notification_permission"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "requested"
    }
}

private fun View.findVisiblePlayerView(): PlayerView? {
    if (this is PlayerView && isShown && alpha > 0f) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findVisiblePlayerView()?.let { return it }
    }
    return null
}

internal fun normalizedPictureInPictureAspectRatio(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 16 to 9
    val ratio = width.toDouble() / height.toDouble()
    return when {
        ratio > 2.38 -> 16 to 9
        ratio < 0.43 -> 9 to 16
        else -> width to height
    }
}

internal fun shouldEnterPictureInPicture(state: com.futo.platformplayer.compose.ui.GrayjayUiState): Boolean =
    shouldEnterPictureInPicture(
        enabled = state.pictureInPictureEnabled,
        hasVideo = state.nowPlaying.video != null && state.playback.currentVideoId != null,
        audioOnly = state.nowPlaying.video?.playbackAudioOnly == true,
        isPlaying = state.playback.isPlaying,
        isBuffering = state.playback.isBuffering,
        isLoading = state.nowPlaying.isLoadingPlayback,
        isCasting = state.chromecast.isConnected || state.chromecast.isConnecting,
    )

internal fun shouldEnterPictureInPicture(
    enabled: Boolean,
    hasVideo: Boolean,
    audioOnly: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoading: Boolean,
    isCasting: Boolean = false,
): Boolean = enabled && hasVideo && !audioOnly && !isCasting && (isPlaying || isBuffering || isLoading)

internal fun physicalLandscapeAt(orientation: Int): Boolean? = when {
    orientation == OrientationEventListener.ORIENTATION_UNKNOWN -> null
    orientation in 60..120 || orientation in 240..300 -> true
    orientation in 0..30 || orientation in 150..210 || orientation in 330..359 -> false
    else -> null
}

internal fun automaticFullscreenPosture(
    autoRotateEnabled: Boolean,
    orientation: Int,
): Boolean? = if (autoRotateEnabled) physicalLandscapeAt(orientation) else false

internal fun fullscreenPlayerOrientation(
    fullscreen: Boolean,
    portraitVideo: Boolean,
): Int = if (fullscreen && !portraitVideo) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
} else {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

internal data class DownloadCompletionKey(
    val profileId: String,
    val videoId: String,
    val mediaType: DownloadMediaType,
)

internal data class DownloadCompletionTransition(
    val pending: Set<DownloadCompletionKey>,
    val showCompletionToast: Boolean,
)

internal fun updateDownloadCompletionBatch(
    pending: Set<DownloadCompletionKey>,
    downloads: Collection<DownloadUiModel>,
): DownloadCompletionTransition {
    val active = downloads.flatMapTo(mutableSetOf()) { download ->
        val mediaTypes = when {
            download.status == DownloadStatus.Removing -> emptySet()
            download.activeMediaTypes.isNotEmpty() -> download.activeMediaTypes
            download.isActive -> setOf(download.mediaType)
            else -> emptySet()
        }
        mediaTypes.map { mediaType ->
            DownloadCompletionKey(download.profileId, download.videoId, mediaType)
        }
    }
    val completed = downloads.flatMapTo(mutableSetOf()) { download ->
        val mediaTypes = when {
            download.completedMediaTypes.isNotEmpty() -> download.completedMediaTypes
            download.status == DownloadStatus.Completed -> setOf(download.mediaType)
            else -> emptySet()
        }
        mediaTypes.map { mediaType ->
            DownloadCompletionKey(download.profileId, download.videoId, mediaType)
        }
    }
    val observed = pending + active
    if (active.isNotEmpty()) {
        return DownloadCompletionTransition(observed, showCompletionToast = false)
    }
    return DownloadCompletionTransition(
        pending = emptySet(),
        showCompletionToast = observed.isNotEmpty() && completed.containsAll(observed),
    )
}

private fun Intent.pluginSourceUrlOrNull(): String? = dataString?.takeIf { value ->
    value.startsWith("grayjay://plugin/", ignoreCase = true) ||
        value.startsWith("vfuto://", ignoreCase = true)
}

private fun Intent.pcPairingUrlOrNull(): String? = dataString?.takeIf { value ->
    value.startsWith("grayjoy://pc-pair", ignoreCase = true)
}

private fun Intent.externalContentUrlOrNull(): String? = externalContentUrl(
    action = action,
    dataString = dataString,
    sharedText = getStringExtra(Intent.EXTRA_TEXT),
)

internal fun externalContentUrl(
    action: String?,
    dataString: String?,
    sharedText: String?,
): String? {
    val candidate = when (action) {
        Intent.ACTION_SEND -> sharedText
        else -> dataString
    }?.trim().orEmpty()
    return WEB_URL_REGEX.find(candidate)?.value
}

@Suppress("DEPRECATION")
private fun Intent.databaseImportUriOrNull(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data?.takeIf { uri ->
        uri.scheme.equals("content", ignoreCase = true) || uri.scheme.equals("file", ignoreCase = true)
    }
    Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    else -> null
}

private val WEB_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
