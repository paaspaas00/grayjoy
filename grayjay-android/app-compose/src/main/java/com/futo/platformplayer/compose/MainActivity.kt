package com.futo.platformplayer.compose

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : FragmentActivity() {
    private val grayjayViewModel by viewModels<GrayjayViewModel>()
    private var pendingSourceUrl by mutableStateOf<String?>(null)
    private var pendingDatabaseImportUri by mutableStateOf<Uri?>(null)
    private var deviceIsLandscape by mutableStateOf(false)
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

            LaunchedEffect(pendingSourceUrl) {
                pendingSourceUrl?.let(viewModel::installSource)
                pendingSourceUrl = null
            }
            LaunchedEffect(pendingDatabaseImportUri) {
                pendingDatabaseImportUri?.let(viewModel::prepareDatabaseImport)
                pendingDatabaseImportUri = null
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
            LaunchedEffect(uiState.nowPlaying.video?.id, hasActiveDownloads) {
                if (
                    (uiState.nowPlaying.video != null || hasActiveDownloads) &&
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
                    onPlayPlaylist = viewModel::playPlaylist,
                    onPlayPlaylistFrom = viewModel::playPlaylistFrom,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSkipToNext = viewModel::skipToNext,
                    onSkipToPrevious = viewModel::skipToPrevious,
                    onSeekPlaybackBy = viewModel::seekPlaybackBy,
                    onPlaybackSpeedChange = viewModel::setPlaybackSpeed,
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
                    onLoadRemotePlaylist = viewModel::loadRemotePlaylist,
                    onLoadMoreRemotePlaylist = viewModel::loadMoreRemotePlaylist,
                    onPlayRemotePlaylist = viewModel::playRemotePlaylist,
                    onDownloadRemotePlaylist = viewModel::downloadRemotePlaylist,
                    onCreateLocalPlaylistFromRemote = viewModel::createLocalPlaylistFromRemote,
                    onToggleLiked = viewModel::toggleLiked,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onRenamePlaylist = viewModel::renamePlaylist,
                    onAddVideosToPlaylist = viewModel::addVideosToPlaylist,
                    onRemoveVideosFromPlaylist = viewModel::removeVideosFromPlaylist,
                    onReorderPlaylist = viewModel::reorderPlaylist,
                    onRemoveVideosFromHistory = viewModel::removeVideosFromHistory,
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
                    onPreferredVideoQualityChange = viewModel::setPreferredVideoQuality,
                    onPreferredAudioBitrateChange = viewModel::setPreferredAudioBitrate,
                    onStickyCaptionsChange = viewModel::setStickyCaptionsEnabled,
                    onShowRecommendationsChange = viewModel::setShowRecommendations,
                    onSearchHistoryChange = viewModel::setSearchHistoryEnabled,
                    onKeepScreenAwakeChange = viewModel::setKeepScreenAwake,
                    onOtherAudioDuckingChange = viewModel::setOtherAudioDuckingEnabled,
                    onOtherAudioDuckVolumeChange = viewModel::setOtherAudioDuckVolumePercent,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyPlayerStatusBarVisibility()
        grayjayViewModel.setAppForeground(true)
        if (!isSystemAutoRotateEnabled()) deviceIsLandscape = false
        if (deviceOrientationListener.canDetectOrientation()) {
            deviceOrientationListener.enable()
        }
    }

    override fun onPause() {
        grayjayViewModel.setAppForeground(false)
        deviceOrientationListener.disable()
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingSourceUrl = intent.pluginSourceUrlOrNull()
        pendingDatabaseImportUri = intent.databaseImportUriOrNull()
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

@Suppress("DEPRECATION")
private fun Intent.databaseImportUriOrNull(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data?.takeIf { uri ->
        uri.scheme.equals("content", ignoreCase = true) || uri.scheme.equals("file", ignoreCase = true)
    }
    Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    else -> null
}
