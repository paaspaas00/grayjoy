package com.futo.platformplayer.compose

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.provider.OpenableColumns
import java.net.URI
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futo.platformplayer.compose.data.ContentRepository
import com.futo.platformplayer.compose.data.CachedHomePage
import com.futo.platformplayer.compose.data.CachedHomeSnapshot
import com.futo.platformplayer.compose.data.HomeCacheRepository
import com.futo.platformplayer.compose.data.LibraryRepository
import com.futo.platformplayer.compose.data.LegacyBackupPasswordRequiredException
import com.futo.platformplayer.compose.data.LegacyGrayjayBackup
import com.futo.platformplayer.compose.data.LegacyGrayjayBackupParser
import com.futo.platformplayer.compose.data.NewPipeBackup
import com.futo.platformplayer.compose.data.NewPipeBackupParser
import com.futo.platformplayer.compose.data.LocalContentRepository
import com.futo.platformplayer.compose.data.ProfileRepository
import com.futo.platformplayer.compose.data.SharedPreferencesLibraryRepository
import com.futo.platformplayer.compose.data.SharedPreferencesSourceRepository
import com.futo.platformplayer.compose.diagnostics.CrashLogStore
import com.futo.platformplayer.compose.data.SourceRepository
import com.futo.platformplayer.compose.data.visibleContentForSources
import com.futo.platformplayer.compose.data.withLibraryState
import com.futo.platformplayer.compose.data.buildImportLibrary
import com.futo.platformplayer.compose.data.playlistTitleExists
import com.futo.platformplayer.compose.data.uniqueRemotePlaylistTitle
import com.futo.platformplayer.compose.casting.ChromecastManager
import com.futo.platformplayer.compose.engine.AndroidGrayjayEngine
import com.futo.platformplayer.compose.engine.EnginePlaybackState
import com.futo.platformplayer.compose.engine.EngineUserImportSelection
import com.futo.platformplayer.compose.engine.EngineUserImportStage
import com.futo.platformplayer.compose.engine.EngineResolvePriority
import com.futo.platformplayer.compose.engine.EngineUrlKind
import com.futo.platformplayer.compose.engine.GrayjayEngine
import com.futo.platformplayer.compose.engine.SearchCorpus
import com.futo.platformplayer.compose.downloads.GrayjoyDownloadStore
import com.futo.platformplayer.compose.downloads.GrayjoyDownloadExporter
import com.futo.platformplayer.compose.downloads.GrayjoyDownloadQueue
import com.futo.platformplayer.compose.downloads.GrayjoyOfflinePlaylistStore
import com.futo.platformplayer.compose.downloads.OfflinePlaylistDownload
import com.futo.platformplayer.compose.downloads.QueuedDownload
import com.futo.platformplayer.compose.downloads.NetworkMonitor
import com.futo.platformplayer.compose.downloads.isRecoverableConnectivityFailure
import com.futo.platformplayer.compose.pclink.PcLinkManager
import com.futo.platformplayer.compose.pclink.PcLinkProtocol
import com.futo.platformplayer.compose.pclink.PcLinkService
import com.futo.platformplayer.compose.pclink.PcLinkSnapshot
import com.futo.platformplayer.compose.pclink.PcMediaKind
import com.futo.platformplayer.compose.pclink.PcPlaybackState
import com.futo.platformplayer.compose.pclink.PcRemoteCommandType
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.ExternalNavigationKind
import com.futo.platformplayer.compose.ui.ExternalNavigationUiModel
import com.futo.platformplayer.compose.ui.GrayjayUiState
import com.futo.platformplayer.compose.ui.DatabaseImportPreviewUiModel
import com.futo.platformplayer.compose.ui.DatabaseImportSelection
import com.futo.platformplayer.compose.ui.DatabaseImportUiState
import com.futo.platformplayer.compose.ui.DatabaseImportFormat
import com.futo.platformplayer.compose.ui.ChannelDetailUiState
import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.CommentRepliesUiState
import com.futo.platformplayer.compose.ui.ChromecastUiState
import com.futo.platformplayer.compose.ui.AudioQualityUiModel
import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.HomeUiState
import com.futo.platformplayer.compose.ui.NowPlayingUiState
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.PlaylistDownloadBatchUiModel
import com.futo.platformplayer.compose.ui.RemotePlaylistDetailUiState
import com.futo.platformplayer.compose.ui.ReleaseUpdateUiModel
import com.futo.platformplayer.compose.ui.PlaybackUiState
import com.futo.platformplayer.compose.ui.PairedComputerUiModel
import com.futo.platformplayer.compose.ui.PcLinkUiState
import com.futo.platformplayer.compose.ui.PcPlaybackUiModel
import com.futo.platformplayer.compose.ui.SearchUiState
import com.futo.platformplayer.compose.ui.SearchContentType
import com.futo.platformplayer.compose.ui.SourceAvailability
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.SourceTrustRequestUiModel
import com.futo.platformplayer.compose.ui.ThemeMode
import com.futo.platformplayer.compose.ui.YoutubeImportSelection
import com.futo.platformplayer.compose.ui.YoutubeImportStageUi
import com.futo.platformplayer.compose.ui.YoutubeImportUiState
import com.futo.platformplayer.backend.GrayjaySignatureMismatchException
import com.futo.platformplayer.backend.GrayjayScheduledVideoException
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.ui.supportsOfflineDownload
import com.futo.platformplayer.compose.ui.VideoTitleLanguageMode
import com.futo.platformplayer.compose.ui.YoutubeBackendMode
import com.futo.platformplayer.compose.ui.SubscriptionFetchMode
import com.futo.platformplayer.backend.YoutubeSubscriptionFetchMode
import com.futo.platformplayer.compose.update.GitHubReleaseChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a successfully loaded Home feed for the lifetime of the app process. Activity recreation
 * (including OEM resume/relaunch behaviour) must not turn returning to Home into a network refresh.
 * Pull-to-refresh and actual feed/profile changes still go through the engine explicitly.
 */
private object HomeSessionCache {
    private val profiles = mutableMapOf<String, CachedHomeSnapshot>()

    @Synchronized
    fun get(profileId: String): CachedHomeSnapshot? = profiles[profileId]

    @Synchronized
    fun put(profileId: String, snapshot: CachedHomeSnapshot) {
        profiles[profileId] = snapshot
    }

    @Synchronized
    fun removeFeed(profileId: String, feed: HomeFeedType) {
        val current = profiles[profileId] ?: return
        val remaining = current.pages - feed
        if (remaining.isEmpty()) profiles.remove(profileId)
        else profiles[profileId] = current.copy(pages = remaining)
    }
}

private const val QUEUE_LOOKAHEAD = 2
private const val WATCH_PROGRESS_WRITE_DEBOUNCE_MS = 250L

/**
 * Detaches tracked jobs before cancelling them. A cancellation may run a job's `finally` block
 * immediately, and those blocks remove themselves from the same registry. Iterating the live map
 * while that happens throws [ConcurrentModificationException] during profile switches.
 */
internal fun <K> MutableMap<K, Job>.cancelAndClearJobs() {
    val jobs = values.toList()
    clear()
    jobs.forEach(Job::cancel)
}

internal fun playlistQueueFrom(videoIds: List<String>, selectedVideoId: String): List<String> {
    val selectedIndex = videoIds.indexOf(selectedVideoId)
    return if (selectedIndex >= 0) videoIds.drop(selectedIndex) else emptyList()
}

internal fun activePlaylistDownloadBatches(
    descriptors: List<OfflinePlaylistDownload>,
    downloads: Map<String, DownloadUiModel>,
): Set<PlaylistDownloadBatchUiModel> = descriptors.mapNotNullTo(mutableSetOf()) { descriptor ->
    PlaylistDownloadBatchUiModel(descriptor.playlistId, descriptor.mediaType).takeIf {
        (descriptor.managedVideoIds - descriptor.excludedVideoIds).any { videoId ->
            downloads[videoId]?.isComplete(descriptor.mediaType) != true
        }
    }
}

internal fun pendingPlaylistCancellationIds(
    cancelled: OfflinePlaylistDownload,
    remainingDescriptors: List<OfflinePlaylistDownload>,
    downloads: Map<String, DownloadUiModel>,
): Set<String> {
    val stillOwned = remainingDescriptors
        .filter { it.mediaType == cancelled.mediaType }
        .flatMapTo(mutableSetOf()) { it.managedVideoIds - it.excludedVideoIds }
    return (cancelled.managedVideoIds - cancelled.excludedVideoIds)
        .filterTo(mutableSetOf()) { videoId ->
            downloads[videoId]?.isComplete(cancelled.mediaType) != true &&
                videoId !in stillOwned
        }
}

internal fun preparedQueueItemsAhead(
    queueVideoIds: List<String>,
    currentVideoId: String?,
): Int {
    val currentIndex = queueVideoIds.indexOf(currentVideoId)
    return if (currentIndex < 0) 0 else queueVideoIds.lastIndex - currentIndex
}

internal fun unqueuedVideoIds(
    requestedVideoIds: List<String>,
    activeQueueVideoIds: Collection<String>,
    knownQueueVideoIds: Collection<String>,
): List<String> {
    val existing = activeQueueVideoIds.toSet() + knownQueueVideoIds
    return requestedVideoIds.distinct().filterNot(existing::contains)
}

internal fun resolvedPlaybackSpeed(
    videoId: String?,
    channelId: String?,
    defaultSpeed: Float,
    perChannelEnabled: Boolean,
    videoSpeeds: Map<String, Float>,
    channelSpeeds: Map<String, Float>,
): Float = videoId?.let(videoSpeeds::get)
    ?: channelId
        ?.takeIf { perChannelEnabled }
        ?.let(channelSpeeds::get)
    ?: defaultSpeed

private data class PlaybackQueueSession(
    val generation: Long,
    val profileId: String,
    val playlistId: String?,
    val pendingVideos: MutableList<VideoUiModel>,
    val knownVideoIds: MutableSet<String>,
    val orderedVideoIds: MutableList<String>,
)

private data class SpeedHoldSnapshot(
    val speed: Float,
    val wasPlaying: Boolean,
    val wasCasting: Boolean,
)

internal fun selectAudioQualityVariant(
    variants: List<AudioQualityUiModel>,
    preferredBitrate: Int?,
    preferredLanguage: String? = null,
): AudioQualityUiModel? {
    val usable = variants.filter { it.bitrate > 0 }
    val languageVariants = preferredLanguage
        ?.takeIf(String::isNotBlank)
        ?.let { language ->
            usable.filter { variant ->
                variant.language.equalsAudioLanguage(language)
            }
        }
        .orEmpty()
    val candidates = languageVariants.ifEmpty { usable }
    return when {
        candidates.isEmpty() -> null
        preferredBitrate == null || preferredBitrate == Int.MAX_VALUE ->
            candidates.maxByOrNull { it.bitrate }
        else -> candidates
            .filter { it.bitrate <= preferredBitrate }
            .maxByOrNull { it.bitrate }
            ?: candidates.minByOrNull { it.bitrate }
    }
}

private fun String?.equalsAudioLanguage(other: String): Boolean {
    val first = this?.trim()?.replace('_', '-')?.lowercase(Locale.ROOT).orEmpty()
    val second = other.trim().replace('_', '-').lowercase(Locale.ROOT)
    return first == second || first.substringBefore('-') == second.substringBefore('-')
}

internal fun isPermanentlyUnavailableVideo(
    error: Throwable,
    videoTitle: String = "",
    isLive: Boolean = false,
): Boolean {
    val messages = generateSequence(error) { it.cause }
        .map { it.message?.lowercase(Locale.ROOT).orEmpty() }
        .toList()
    return generateSequence(error) { it.cause }
        .any { cause ->
            if (cause is ScriptUnavailableException) return@any true
            val message = cause.message?.lowercase(Locale.ROOT).orEmpty()
            PERMANENT_UNAVAILABLE_MESSAGES.any(message::contains)
        } || (
        messages.any { it.contains("no supported video or audio stream") } &&
            (isLive || videoTitle.contains("(live)", ignoreCase = true))
        )
}

private val PERMANENT_UNAVAILABLE_MESSAGES = listOf(
    "video unavailable",
    "content unavailable",
    "no longer available",
    "has been removed",
    "was removed",
    "deleted video",
    "private video",
)

private fun Throwable.hasMessageInChain(value: String): Boolean =
    generateSequence(this) { it.cause }
        .any { it.message?.contains(value, ignoreCase = true) == true }

private fun Throwable.scheduledVideoException(): GrayjayScheduledVideoException? =
    generateSequence(this) { it.cause }
        .filterIsInstance<GrayjayScheduledVideoException>()
        .firstOrNull()

/**
 * Chooses the same hierarchy as legacy Grayjay: a requested audio rendition first, then the
 * plugin's dedicated download/playback audio, and finally a muxed representation when the source
 * descriptor guarantees that it contains audio. The final fallback is important for older
 * uploads whose plugin result only exposes one progressive audio+video file; it must never be
 * applied to an unmuxed video-only representation.
 */
internal fun VideoUiModel.asAudioDownloadDescriptor(
    preferredBitrate: Int?,
): VideoUiModel {
    val variant = selectAudioQualityVariant(
        variants = audioQualityVariants,
        preferredBitrate = preferredBitrate,
        preferredLanguage = resolvedAudioLanguage,
    )
    val audioPlaybackUrl = variant?.playbackUrl.orEmpty().ifBlank {
        audioDownloadUrl
    }.ifBlank { audioUrl }.ifBlank {
        playbackUrl.takeIf {
            playbackMimeType.startsWith("audio/") || playbackHasMuxedAudio
        }.orEmpty()
    }
    require(audioPlaybackUrl.isNotBlank()) { "This source returned no downloadable audio." }
    val hasVariant = variant != null
    val hasDedicatedDownloadSource = !hasVariant && audioDownloadUrl.isNotBlank()
    val usesMuxedFallback = !hasVariant &&
        !hasDedicatedDownloadSource &&
        audioUrl.isBlank() &&
        playbackHasMuxedAudio &&
        audioPlaybackUrl == playbackUrl
    return copy(
        playbackFromDownload = false,
        playbackAudioOnly = false,
        playbackCacheNamespace = "",
        audioCacheNamespace = "",
        playbackStreamKeys = emptyList(),
        audioStreamKeys = emptyList(),
        playbackUrl = audioPlaybackUrl,
        playbackMimeType = when {
            hasVariant -> variant?.playbackMimeType.orEmpty()
            hasDedicatedDownloadSource -> audioDownloadMimeType
            usesMuxedFallback -> playbackMimeType
            else -> playbackMimeType.takeIf { it.startsWith("audio/") }.orEmpty()
        },
        playbackManifest = when {
            hasVariant -> variant?.playbackManifest.orEmpty()
            hasDedicatedDownloadSource -> audioDownloadManifest
            usesMuxedFallback -> playbackManifest
            else -> ""
        },
        audioUrl = "",
        playbackRequestHeaders = when {
            hasVariant -> variant?.playbackRequestHeaders.orEmpty()
            hasDedicatedDownloadSource -> audioDownloadRequestHeaders
            else -> audioRequestHeaders.ifEmpty { playbackRequestHeaders }
        },
        // Request executors/modifiers are part of the selected source and are required for
        // authenticated/plugin-proxied audio downloads.
        playbackDataSourceFactory = when {
            hasVariant -> variant?.playbackDataSourceFactory
            hasDedicatedDownloadSource -> audioDownloadDataSourceFactory
            else -> audioDataSourceFactory ?: playbackDataSourceFactory
        },
        audioRequestHeaders = emptyMap(),
        audioDataSourceFactory = null,
        audioDownloadUrl = "",
        audioDownloadMimeType = "",
        audioDownloadManifest = "",
        audioDownloadRequestHeaders = emptyMap(),
        audioDownloadDataSourceFactory = null,
        subtitleTracks = emptyList(),
        qualityVariants = emptyList(),
        audioQualityVariants = emptyList(),
    )
}

class GrayjayViewModel(application: Application) : AndroidViewModel(application) {
    private fun text(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun quantityText(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        getApplication<Application>().resources.getQuantityString(id, quantity, *args)

    private val contentRepository: ContentRepository = LocalContentRepository()
    private val content = contentRepository.snapshot()
    private val profileRepository = ProfileRepository(application)
    private var activeProfileId = profileRepository.activeProfileId()
    private var preferences = GrayjayPreferences(application, activeProfileId)
    private var libraryRepository: LibraryRepository =
        SharedPreferencesLibraryRepository(application, activeProfileId)
    private var homeCacheRepository = HomeCacheRepository(application, activeProfileId)
    private var sourceRepository: SourceRepository =
        SharedPreferencesSourceRepository(application, activeProfileId)
    private val engine: GrayjayEngine = AndroidGrayjayEngine(application)
    private val chromecastManager = ChromecastManager(application)
    // Opening Media3's download index and SimpleCache can involve disk/database work. Initialize
    // them lazily from the IO collector instead of doing that before the Activity's first frame.
    private val downloadStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GrayjoyDownloadStore.get(application)
    }
    private val downloadExporter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GrayjoyDownloadExporter(application, downloadStore)
    }
    private val downloadQueue = GrayjoyDownloadQueue(application)
    private val networkMonitor = NetworkMonitor(application)
    private val offlinePlaylistStore = GrayjoyOfflinePlaylistStore(application)
    private val releaseChecker = GitHubReleaseChecker()
    private val pcLinkManager = PcLinkManager.get(application)
    private val baseEngineSources = engine.sources(content.sources)
    private var engineSources = (
        baseEngineSources + sourceRepository.loadCustomSources()
        ).distinctBy { it.engineId }.also(engine::registerSources)
    private var followedCreatorIds = preferences.initializeFollowedCreators(
        content.channels.map(ChannelUiModel::id).toSet(),
    )
    // Large imported histories can be several megabytes. Do not parse them while Activity startup
    // is waiting for the first frame; the Library state is filled from IO immediately afterwards.
    private var allVideos = emptyList<VideoUiModel>()
    private var savedVideosById = emptyMap<String, VideoUiModel>()
    private val remoteVideos = linkedMapOf<String, VideoUiModel>()
    private val remoteChannels = linkedMapOf<String, ChannelUiModel>()
    private var enabledSourceIds: Set<String> = sourceRepository.loadEnabledSourceIds(engineSources)
        .filterTo(mutableSetOf()) { sourceId ->
            engineSources.any {
                it.id == sourceId && it.availability != SourceAvailability.MissingPlugin
            }
        }
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var profileSwitchJob: Job? = null
    private var detailsJob: Job? = null
    private var storyboardJob: Job? = null
    private var audioLanguageJob: Job? = null
    private var channelJob: Job? = null
    private var homeJob: Job? = null
    private var searchPagingJob: Job? = null
    private var homePagingJob: Job? = null
    private var homeCacheWriteJob: Job? = null
    private var channelPagingJob: Job? = null
    private var remotePlaylistJob: Job? = null
    private var remotePlaylistPagingJob: Job? = null
    private var extrasPagingJob: Job? = null
    private var commentRepliesJob: Job? = null
    private var resumePromptJob: Job? = null
    private var downloadQueueRestoreJob: Job? = null
    private var offlinePlaylistSyncJob: Job? = null
    private var queuePreparationJob: Job? = null
    private var queuePreparationBlockedUntilMs = 0L
    private var queueMutationJob: Job? = null
    private var watchProgressWriteJob: Job? = null
    private var externalUrlJob: Job? = null
    private var releaseCheckJob: Job? = null
    private var youtubeImportJob: Job? = null
    private var pcHandoffJob: Job? = null
    private var libraryLoadJob: Job? = null
    private var pluginUpdateJob: Job? = null
    private var lastPluginUpdateCheckMs = 0L
    private val historyWriteJobs = mutableMapOf<String, Job>()
    private var homeLoadGeneration = 0L
    private var playbackGeneration = 0L
    private var videoOpenRequestGeneration = 0L
    private var youtubeImportGeneration = 0L
    private var playbackQueueSession: PlaybackQueueSession? = null
    private var pendingPlaybackVideoId: String? = null
    private var pendingPcHandoffSeek: Pair<String, Long>? = null
    private var activePlaylistId: String? = null
    private var appIsForeground = false
    private var externalNavigationRequestId = 0L
    private var suppressChromecastHandoff = false
    private var resumeLocalAfterFailedCast = false
    private var speedHoldSnapshot: SpeedHoldSnapshot? = null
    private val downloadJobs = mutableMapOf<String, Job>()
    // Old Grayjay prepares the next queued video immediately before transferring it. Keeping
    // this single-file queue prevents signed plugin URLs for later playlist items expiring.
    private val downloadPreparationSemaphore = Semaphore(1)
    private val metadataHydrationSemaphore = Semaphore(2)
    private val metadataHydrationJobs = mutableMapOf<String, Job>()
    private val metadataHydrationAttempts = mutableSetOf<String>()
    private var metadataHydrationSaveJob: Job? = null
    private val downloadPreparationStates = mutableMapOf<String, DownloadUiModel>()
    private val autoRepairedDownloadKeys = mutableSetOf<String>()
    private var appliedDownloadIndexSignature: Pair<Set<String>, Set<String>>? = null
    private val homeFeedCache = mutableMapOf<HomeFeedType, List<VideoUiModel>>()
    private val homeContinuationCache = mutableMapOf<HomeFeedType, String?>()
    private val homeHasMoreCache = mutableMapOf<HomeFeedType, Boolean>()
    private var pendingDatabaseImportUri: Uri? = null
    private var pendingDatabaseImport: LegacyGrayjayBackup? = null
    private var pendingNewPipeImport: NewPipeBackup? = null
    private val initialContent = visibleContentForSources(
        videos = content.videos,
        channels = content.channels,
        playlists = content.playlists,
        enabledSourceIds = enabledSourceIds,
    )
    private val _uiState = MutableStateFlow(
        GrayjayUiState(
            videos = initialContent.videos,
            libraryVideos = allVideos,
            channels = initialContent.channels,
            playlists = emptyList(),
            sources = engineSources.map {
                it.copy(
                    isEnabled = it.id in enabledSourceIds,
                    isAuthenticated = engine.isSourceAuthenticated(it.id),
                )
            },
            dynamicColorsEnabled = preferences.dynamicColorsEnabled,
            themeMode = preferences.themeMode,
            privateSessionEnabled = preferences.privateSessionEnabled,
            defaultPlaybackSpeed = preferences.defaultPlaybackSpeed,
            perChannelPlaybackSpeedEnabled = preferences.perChannelPlaybackSpeedEnabled,
            holdToSpeedEnabled = preferences.holdToSpeedEnabled,
            channelPlaybackSpeeds = preferences.channelPlaybackSpeeds(),
            videoPlaybackSpeeds = preferences.videoPlaybackSpeeds(),
            preferredVideoQuality = preferences.preferredVideoQuality,
            preferredAudioBitrate = preferences.preferredAudioBitrate,
            preferredAudioLanguage = preferences.preferredAudioLanguage,
            preferOriginalAudio = preferences.preferOriginalAudio,
            preferNewPipeForYoutubePlayback = preferences.preferNewPipeForYoutubePlayback,
            youtubeBackendMode = preferences.youtubeBackendMode,
            subscriptionFetchMode = preferences.subscriptionFetchMode,
            videoTitleLanguageMode = preferences.videoTitleLanguageMode,
            stickyCaptionsEnabled = preferences.stickyCaptionsEnabled,
            showRecommendations = preferences.showRecommendations,
            searchHistoryEnabled = preferences.searchHistoryEnabled,
            crashLoggingEnabled = CrashLogStore.isEnabled(application),
            keepScreenAwake = preferences.keepScreenAwake,
            pictureInPictureEnabled = preferences.pictureInPictureEnabled,
            otherAudioDuckingEnabled = preferences.otherAudioDuckingEnabled,
            otherAudioDuckVolumePercent = preferences.otherAudioDuckVolumePercent,
            profiles = profileRepository.profiles(),
            activeProfileId = activeProfileId,
            followedCreatorIds = followedCreatorIds,
        ),
    )
    val uiState = _uiState.asStateFlow()
    val player get() = engine.player

    init {
        engine.setProfile(activeProfileId)
        configureVideoTitleLanguage()
        configureYoutubeBackend()
        val initialLibraryRepository = libraryRepository
        val initialProfileId = activeProfileId
        libraryLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val savedVideos = initialLibraryRepository.loadSavedVideos()
            val playlists = initialLibraryRepository.loadPlaylists()
            withContext(Dispatchers.Main.immediate) {
                if (
                    initialProfileId == activeProfileId &&
                    initialLibraryRepository === libraryRepository
                ) {
                    savedVideos.forEach(::registerRemoteChannel)
                    applyLibrarySnapshot(savedVideos, playlists)
                    _uiState.update { it.copy(channels = visibleKnownChannels()) }
                }
            }
        }
        viewModelScope.launch {
            engine.backendNotices.collect { notice ->
                Toast.makeText(
                    getApplication(),
                    text(R.string.youtube_backend_fallback_notice, notice.operation),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        chromecastManager.onMediaEnded = {
            viewModelScope.launch(Dispatchers.Main.immediate) { skipToNext() }
        }
        preferences.loadImportedChannels().forEach { remoteChannels[it.id] = it }
        allVideos.forEach(::registerRemoteChannel)
        _uiState.update { it.copy(channels = visibleKnownChannels()) }
        viewModelScope.launch {
            engine.playback.collect { playback ->
                val cast = chromecastManager.state.value
                _uiState.update {
                    it.copy(
                        playback = playback.toUiState(
                            fullQueueVideoIds = playbackQueueSession
                                ?.orderedVideoIds
                                ?.toList()
                                .orEmpty(),
                        ).withChromecast(cast),
                    )
                }
                val currentId = playback.currentVideoId
                // engine.pausePlayback() publishes the old Media3 item while a newly selected
                // video/playlist is still resolving. Treating that stale item as a transition
                // cancels the new details job and can append the new queue behind the old media.
                // Wait until Media3 actually exposes the pending selection.
                val awaitingPendingSelection = pendingPlaybackVideoId?.let { it != currentId } == true
                if (
                    !awaitingPendingSelection &&
                    currentId != null &&
                    _uiState.value.nowPlaying.video?.id != currentId
                ) {
                    findVideo(currentId)?.let { currentVideo ->
                        recordHistory(currentVideo)
                        detailsJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                nowPlaying = NowPlayingUiState(
                                    video = currentVideo,
                                    isLoadingExtras = true,
                                    isFollowing = preferences.isCreatorFollowed(currentVideo.creatorKey()),
                                    resumePositionFraction = currentVideo.resumePositionFraction(),
                                ),
                            )
                        }
                        scheduleResumePromptDismiss(currentVideo.id, currentVideo.resumePositionFraction())
                        applyPlaybackSpeed(currentVideo)
                        requestStoryboard(currentVideo, playbackGeneration)
                        detailsJob = viewModelScope.launch { loadExtras(currentVideo) }
                    }
                } else if (
                    !awaitingPendingSelection &&
                    currentId == null &&
                    engine.player.mediaItemCount == 0 &&
                    pendingPlaybackVideoId == null &&
                    _uiState.value.nowPlaying.video != null
                ) {
                    activePlaylistId = null
                    detailsJob?.cancel()
                    extrasPagingJob?.cancel()
                    _uiState.update { it.copy(nowPlaying = NowPlayingUiState()) }
                }
                if (!awaitingPendingSelection) prepareQueueLookAhead(playback)
                pendingPcHandoffSeek
                    ?.takeIf { (videoId, _) -> videoId == playback.currentVideoId }
                    ?.let { (_, positionMs) ->
                        pendingPcHandoffSeek = null
                        engine.player.seekTo(positionMs.coerceAtLeast(0L))
                        engine.player.play()
                    }
            }
        }
        viewModelScope.launch {
            pcLinkManager.snapshot.collect { snapshot ->
                _uiState.update {
                    it.copy(pcLink = snapshot.toUiState())
                }
            }
        }
        // Avoid creating a foreground service and HTTP server during every cold start for users
        // who have never paired a computer. Pairing starts it immediately when it is needed.
        if (pcLinkManager.snapshot.value.pairedComputers.isNotEmpty()) {
            PcLinkService.ensureRunning(application)
        }
        viewModelScope.launch {
            var previous = chromecastManager.state.value
            chromecastManager.state.collect { cast ->
                _uiState.update { state ->
                    state.copy(
                        chromecast = cast,
                        playback = state.playback.withChromecast(cast),
                    )
                }
                if (previous.isConnected && !cast.isConnected) {
                    if (suppressChromecastHandoff) {
                        suppressChromecastHandoff = false
                    } else {
                        handoffChromecastToLocal(previous)
                    }
                } else if (
                    previous.isConnecting &&
                    !cast.isConnecting &&
                    !cast.isConnected &&
                    resumeLocalAfterFailedCast
                ) {
                    if (!engine.playback.value.isPlaying) engine.togglePlayback()
                    resumeLocalAfterFailedCast = false
                }
                if (cast.isConnected) resumeLocalAfterFailedCast = false
                previous = cast
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                // Timeline and mini-player clocks read Media3 locally. Only the much less frequent
                // history checkpoint belongs in the root state, otherwise every wide tablet page
                // and its navigation chrome recomposes once per second during playback.
                if (engine.playback.value.currentVideoId != null) {
                    persistCurrentPlaybackProgress()
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            delay(STARTUP_BACKGROUND_WORK_DELAY_MS)
            downloadStore.downloads.collect {
                libraryLoadJob?.join()
                syncDownloadState()
            }
        }
        restoreDownloadQueue()
        scheduleOfflinePlaylistSync()
        if (!restoreHomeFromSession()) {
            loadHome(HomeFeedType.Subscriptions, forceRefresh = false)
        }
    }

    fun setDynamicColorsEnabled(enabled: Boolean) {
        preferences.dynamicColorsEnabled = enabled
        _uiState.update { it.copy(dynamicColorsEnabled = enabled) }
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        preferences.themeMode = if (enabled) ThemeMode.Dark else ThemeMode.Light
        _uiState.update { it.copy(themeMode = preferences.themeMode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.themeMode = mode
        _uiState.update { it.copy(themeMode = preferences.themeMode) }
    }

    fun setPrivateSessionEnabled(enabled: Boolean) {
        preferences.privateSessionEnabled = enabled
        _uiState.update { it.copy(privateSessionEnabled = enabled) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        preferences.defaultPlaybackSpeed = speed
        _uiState.update { it.copy(defaultPlaybackSpeed = preferences.defaultPlaybackSpeed) }
        applyPlaybackSpeed(_uiState.value.nowPlaying.video)
    }

    fun setPerChannelPlaybackSpeedEnabled(enabled: Boolean) {
        preferences.perChannelPlaybackSpeedEnabled = enabled
        _uiState.update {
            it.copy(perChannelPlaybackSpeedEnabled = preferences.perChannelPlaybackSpeedEnabled)
        }
        applyPlaybackSpeed(_uiState.value.nowPlaying.video)
    }

    fun setHoldToSpeedEnabled(enabled: Boolean) {
        preferences.holdToSpeedEnabled = enabled
        _uiState.update { it.copy(holdToSpeedEnabled = preferences.holdToSpeedEnabled) }
        if (!enabled) endSpeedHold()
    }

    fun setPreferredVideoQuality(height: Int) {
        preferences.preferredVideoQuality = height
        _uiState.update { it.copy(preferredVideoQuality = preferences.preferredVideoQuality) }
        engine.setVideoQuality(height.takeIf { it > 0 })
    }

    fun setPreferredAudioBitrate(bitrate: Int) {
        preferences.preferredAudioBitrate = bitrate
        _uiState.update { it.copy(preferredAudioBitrate = preferences.preferredAudioBitrate) }
    }

    fun setPreferredAudioLanguage(language: String) {
        preferences.preferredAudioLanguage = language
        _uiState.update {
            it.copy(preferredAudioLanguage = preferences.preferredAudioLanguage)
        }
        setAudioLanguage(null)
    }

    fun setPreferOriginalAudio(enabled: Boolean) {
        preferences.preferOriginalAudio = enabled
        _uiState.update { it.copy(preferOriginalAudio = enabled) }
        setAudioLanguage(null)
    }

    fun setPreferNewPipeForYoutubePlayback(enabled: Boolean) {
        setYoutubeBackendMode(
            if (enabled) YoutubeBackendMode.NewPipe else YoutubeBackendMode.Grayjay,
        )
    }

    fun setYoutubeBackendMode(mode: YoutubeBackendMode) {
        preferences.youtubeBackendMode = mode
        configureYoutubeBackend()
        _uiState.update {
            it.copy(
                youtubeBackendMode = mode,
                preferNewPipeForYoutubePlayback = mode == YoutubeBackendMode.NewPipe,
            )
        }
        homeFeedCache.clear()
        homeContinuationCache.clear()
        HomeSessionCache.removeFeed(activeProfileId, HomeFeedType.Subscriptions)
    }

    fun setSubscriptionFetchMode(mode: SubscriptionFetchMode) {
        preferences.subscriptionFetchMode = mode
        configureYoutubeBackend()
        _uiState.update { it.copy(subscriptionFetchMode = mode) }
        homeFeedCache.remove(HomeFeedType.Subscriptions)
        homeContinuationCache.remove(HomeFeedType.Subscriptions)
        HomeSessionCache.removeFeed(activeProfileId, HomeFeedType.Subscriptions)
    }

    fun setVideoTitleLanguageMode(mode: VideoTitleLanguageMode) {
        if (preferences.videoTitleLanguageMode == mode) return
        preferences.videoTitleLanguageMode = mode
        _uiState.update { it.copy(videoTitleLanguageMode = mode) }
        configureVideoTitleLanguage()
        // Existing cached titles reflect the old request locale. Keep the current screen stable,
        // and make the next explicit feed/search/channel load use the selected policy.
        HomeSessionCache.removeFeed(activeProfileId, HomeFeedType.Subscriptions)
        homeFeedCache.remove(HomeFeedType.Subscriptions)
    }

    private fun configureVideoTitleLanguage() {
        engine.configureVideoTitleLanguage(
            preferOriginal = preferences.videoTitleLanguageMode == VideoTitleLanguageMode.Original,
            languageTag = AppLanguageManager.effectiveLanguageTag(getApplication()),
        )
    }

    private fun configureYoutubeBackend() {
        engine.configureYoutubeBackend(
            useNewPipe = preferences.youtubeBackendMode == YoutubeBackendMode.NewPipe,
            subscriptionFetchMode = when (preferences.subscriptionFetchMode) {
                SubscriptionFetchMode.Fast -> YoutubeSubscriptionFetchMode.Fast
                SubscriptionFetchMode.Complete -> YoutubeSubscriptionFetchMode.Complete
            },
        )
    }

    fun refreshVideoTitleLanguageConfiguration() = configureVideoTitleLanguage()

    fun setStickyCaptionsEnabled(enabled: Boolean) {
        preferences.stickyCaptionsEnabled = enabled
        _uiState.update { it.copy(stickyCaptionsEnabled = enabled) }
    }

    fun setShowRecommendations(enabled: Boolean) {
        preferences.showRecommendations = enabled
        _uiState.update { state ->
            state.copy(
                showRecommendations = enabled,
                nowPlaying = if (enabled) state.nowPlaying else state.nowPlaying.copy(
                    recommendations = emptyList(),
                    recommendationsAvailable = false,
                ),
            )
        }
        if (enabled) {
            _uiState.value.nowPlaying.video?.let { video ->
                detailsJob?.cancel()
                detailsJob = viewModelScope.launch { loadExtras(video) }
            }
        }
    }

    fun setSearchHistoryEnabled(enabled: Boolean) {
        preferences.searchHistoryEnabled = enabled
        _uiState.update { it.copy(searchHistoryEnabled = enabled) }
    }

    fun setCrashLoggingEnabled(enabled: Boolean) {
        CrashLogStore.setEnabled(getApplication(), enabled)
        _uiState.update { it.copy(crashLoggingEnabled = enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        preferences.keepScreenAwake = enabled
        _uiState.update { it.copy(keepScreenAwake = enabled) }
    }

    fun setPictureInPictureEnabled(enabled: Boolean) {
        preferences.pictureInPictureEnabled = enabled
        _uiState.update { it.copy(pictureInPictureEnabled = enabled) }
    }

    fun setOtherAudioDuckingEnabled(enabled: Boolean) {
        preferences.otherAudioDuckingEnabled = enabled
        _uiState.update { it.copy(otherAudioDuckingEnabled = enabled) }
        engine.setOtherAudioDucking(enabled, preferences.otherAudioDuckVolumePercent)
    }

    fun setOtherAudioDuckVolumePercent(percent: Int) {
        preferences.otherAudioDuckVolumePercent = percent
        _uiState.update {
            it.copy(otherAudioDuckVolumePercent = preferences.otherAudioDuckVolumePercent)
        }
        engine.setOtherAudioDucking(
            preferences.otherAudioDuckingEnabled,
            preferences.otherAudioDuckVolumePercent,
        )
    }

    fun pairComputerFromQr(payload: String): Boolean {
        val paired = pcLinkManager.pair(payload)
        if (paired) PcLinkService.ensureRunning(getApplication())
        return paired
    }

    fun removePairedComputer(computerId: String) {
        pcLinkManager.remove(computerId)
        if (pcLinkManager.snapshot.value.pairedComputers.isEmpty()) {
            PcLinkService.stop(getApplication())
        }
    }

    fun toggleComputerPlayback(computerId: String) {
        val playback = pcLinkManager.snapshot.value.activePlayback
            ?.takeIf { it.computerId == computerId }
            ?: return
        pcLinkManager.enqueueCommand(
            computerId,
            if (playback.isPlaying) PcRemoteCommandType.Pause else PcRemoteCommandType.Play,
        )
    }

    fun skipComputerPlaybackPrevious(computerId: String) {
        pcLinkManager.enqueueCommand(computerId, PcRemoteCommandType.Previous)
    }

    fun skipComputerPlaybackNext(computerId: String) {
        pcLinkManager.enqueueCommand(computerId, PcRemoteCommandType.Next)
    }

    fun seekComputerPlayback(computerId: String, positionMs: Long) {
        val durationMs = pcLinkManager.snapshot.value.activePlayback
            ?.takeIf { it.computerId == computerId }
            ?.durationMs
            ?: return
        if (durationMs <= 0L) return
        pcLinkManager.enqueueCommand(
            computerId,
            PcRemoteCommandType.Seek,
            positionMs.coerceIn(0L, durationMs),
        )
    }

    fun playFromComputer(computerId: String) {
        val playback = pcLinkManager.snapshot.value.activePlayback
            ?.takeIf { it.computerId == computerId }
            ?: return
        pcHandoffJob?.cancel()
        pcHandoffJob = viewModelScope.launch {
            try {
                val playlistTransferred = if (
                    playback.kind == PcMediaKind.Playlist &&
                    playback.playlistUrl.isNotBlank()
                ) {
                    runCatching { handoffPcPlaylist(playback) }.getOrElse { error ->
                        Log.w("GrayjayViewModel", "PC playlist handoff failed; using current video.", error)
                        false
                    }
                } else {
                    false
                }
                val transferred = playlistTransferred || handoffPcVideo(playback)
                if (transferred) {
                    pcLinkManager.enqueueCommand(playback.computerId, PcRemoteCommandType.Pause)
                } else {
                    Toast.makeText(
                        getApplication(),
                        R.string.pc_transfer_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "PC playback handoff failed.", error)
                Toast.makeText(
                    getApplication(),
                    R.string.pc_transfer_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private suspend fun handoffPcPlaylist(playback: PcPlaybackState): Boolean {
        val route = engine.routeUrl(playback.playlistUrl, enabledSourceIds)
            ?.takeIf { it.kind == EngineUrlKind.Playlist }
            ?: return false
        val playlist = PlaylistUiModel(
            id = route.url,
            title = playback.title.ifBlank { externalContentLabel(route.url) },
            description = "",
            videoIds = emptyList(),
            sourceId = route.sourceId,
        )
        val details = engine.loadPlaylist(playlist)
        val videos = details.videos.map { it.withPersistedLibraryState() }.toMutableList()
        var continuationId = details.continuationId
        var hasMore = details.hasMore
        var pagesWithoutNewVideos = 0
        while (hasMore && continuationId != null && pagesWithoutNewVideos < 2) {
            val page = engine.loadMoreChannel(continuationId)
            val known = videos.mapTo(mutableSetOf(), VideoUiModel::id)
            val additions = page.videos
                .map { it.withPersistedLibraryState() }
                .filterNot { it.id in known }
            if (additions.isEmpty()) pagesWithoutNewVideos += 1 else pagesWithoutNewVideos = 0
            videos += additions
            continuationId = page.continuationId
            hasMore = page.hasMore
        }
        if (videos.isEmpty()) return false
        videos.forEach { video ->
            remoteVideos[video.id] = video
            registerRemoteChannel(video)
        }
        val current = videos.firstOrNull { video ->
            sameYoutubeVideo(video.contentUrl, playback.videoUrl) ||
                sameYoutubeVideo(video.shareUrl, playback.videoUrl) ||
                sameYoutubeVideo(video.id, playback.videoUrl)
        } ?: videos.first()
        val queueIds = playlistQueueFrom(videos.map(VideoUiModel::id), current.id)
        if (queueIds.isEmpty()) return false
        _uiState.update { state ->
            state.copy(
                channels = visibleKnownChannels(),
                remotePlaylistDetail = RemotePlaylistDetailUiState(
                    playlist = details.playlist.copy(videoIds = videos.map(VideoUiModel::id)),
                    videos = videos,
                    continuationId = continuationId,
                    hasMore = hasMore,
                ),
            )
        }
        pendingPcHandoffSeek = current.id to playback.positionMs
        activePlaylistId = null
        startQueue(queueIds)
        publishExternalNavigation(ExternalNavigationKind.Video, current.id)
        return true
    }

    private suspend fun handoffPcVideo(playback: PcPlaybackState): Boolean {
        val normalizedUrl = playback.videoUrl.trim()
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            return false
        }
        val route = engine.routeUrl(normalizedUrl, enabledSourceIds)
            ?.takeIf { it.kind == EngineUrlKind.Video }
            ?: return false
        val source = _uiState.value.sources.firstOrNull { it.id == route.sourceId }
        val sourceName = source?.name ?: route.sourceId.replaceFirstChar(Char::uppercase)
        val video = VideoUiModel(
            id = route.url,
            title = playback.videoTitle.ifBlank {
                playback.title.ifBlank { externalContentLabel(route.url) }
            },
            creator = sourceName,
            metadata = "",
            duration = "",
            sourceId = route.sourceId,
            contentUrl = route.url,
            shareUrl = route.url,
            sourceName = sourceName,
            sourceIconUrl = source?.iconUrl.orEmpty(),
        )
        remoteVideos[video.id] = video
        pendingPcHandoffSeek = video.id to playback.positionMs
        openVideo(video.id)
        publishExternalNavigation(ExternalNavigationKind.Video, video.id)
        return true
    }

    private fun restoreHomeFromSession(): Boolean {
        val sessionSnapshot = HomeSessionCache.get(activeProfileId)
        val snapshot = sessionSnapshot
            ?: homeCacheRepository.load()?.also { HomeSessionCache.put(activeProfileId, it) }
            ?: return false
        val selectedFeed = sessionSnapshot?.selectedFeed?.takeIf(snapshot.pages::containsKey)
            ?: HomeFeedType.Subscriptions.takeIf(snapshot.pages::containsKey)
            ?: snapshot.selectedFeed.takeIf(snapshot.pages::containsKey)
            ?: snapshot.pages.keys.firstOrNull()
            ?: return false
        homeFeedCache.clear()
        homeContinuationCache.clear()
        homeHasMoreCache.clear()
        snapshot.pages.forEach { (feed, page) ->
            val videos = page.videos
                .map { it.withPersistedLibraryState() }
                .withKnownChannelPresentation()
            homeFeedCache[feed] = videos
            homeContinuationCache[feed] = page.continuationId
            homeHasMoreCache[feed] = page.hasMore
            videos.forEach { video ->
                remoteVideos[video.id] = video
                registerRemoteChannel(video)
            }
        }
        val page = snapshot.pages.getValue(selectedFeed)
        val subscriptionCount = if (selectedFeed == HomeFeedType.Subscriptions) {
            followedCreatorIds.size
        } else {
            0
        }
        _uiState.update { state ->
            state.copy(
                channels = visibleKnownChannels(),
                subscriptionVideos = homeFeedCache[HomeFeedType.Subscriptions].orEmpty(),
                home = HomeUiState(
                    selectedFeed = selectedFeed,
                    videos = homeFeedCache[selectedFeed].orEmpty(),
                    continuationId = page.continuationId,
                    hasMore = page.hasMore,
                    subscriptionsLoaded = subscriptionCount,
                    subscriptionsTotal = subscriptionCount,
                ),
            )
        }
        return true
    }

    private fun saveHomeToSession(selectedFeed: HomeFeedType = _uiState.value.home.selectedFeed) {
        if (homeFeedCache.isEmpty()) return
        val snapshot = CachedHomeSnapshot(
                selectedFeed = selectedFeed,
                pages = homeFeedCache.mapValues { (feed, videos) ->
                    CachedHomePage(
                        videos = videos,
                        continuationId = homeContinuationCache[feed],
                        hasMore = homeHasMoreCache[feed] == true,
                    )
                },
        )
        HomeSessionCache.put(activeProfileId, snapshot)
        val repository = homeCacheRepository
        homeCacheWriteJob?.cancel()
        homeCacheWriteJob = viewModelScope.launch(Dispatchers.IO) {
            repository.save(snapshot)
        }
    }

    fun selectHomeFeed(feed: HomeFeedType) {
        if (_uiState.value.home.selectedFeed == feed && !_uiState.value.home.isLoading) return
        val cached = homeFeedCache[feed]
        if (cached != null) {
            val presentedCache = cached.withKnownChannelPresentation()
            homeFeedCache[feed] = presentedCache
            val cachedSubscriptionCount = if (feed == HomeFeedType.Subscriptions) {
                visibleKnownChannels().count { channel ->
                    channel.id in followedCreatorIds && channel.sourceId in enabledSourceIds
                }
            } else {
                0
            }
            _uiState.update {
                it.copy(
                    home = HomeUiState(
                        selectedFeed = feed,
                        videos = presentedCache,
                        continuationId = homeContinuationCache[feed],
                        hasMore = homeHasMoreCache[feed] == true,
                        subscriptionsLoaded = cachedSubscriptionCount,
                        subscriptionsTotal = cachedSubscriptionCount,
                    ),
                )
            }
            saveHomeToSession(feed)
        } else {
            loadHome(feed, forceRefresh = false)
        }
    }

    fun refreshHome() {
        loadHome(_uiState.value.home.selectedFeed, forceRefresh = true)
    }

    private fun loadHome(feed: HomeFeedType, forceRefresh: Boolean) {
        if (forceRefresh) {
            HomeSessionCache.removeFeed(activeProfileId, feed)
            homeCacheRepository.removeFeed(feed)
        }
        homeJob?.cancel()
        homePagingJob?.cancel()
        val loadGeneration = ++homeLoadGeneration
        val cached = homeFeedCache[feed].orEmpty()
        val subscriptionTotal = if (feed == HomeFeedType.Subscriptions) {
            visibleKnownChannels().count { channel ->
                channel.id in followedCreatorIds && channel.sourceId in enabledSourceIds
            }
        } else {
            0
        }
        _uiState.update { state ->
            state.copy(
                home = HomeUiState(
                    selectedFeed = feed,
                    videos = cached,
                    isLoading = cached.isEmpty(),
                    isRefreshing = forceRefresh && cached.isNotEmpty(),
                    subscriptionsLoaded = 0,
                    subscriptionsTotal = subscriptionTotal,
                ),
            )
        }
        val publishedSubscriptionProgress = AtomicInteger(0)
        homeJob = viewModelScope.launch {
            try {
                val followedChannels = visibleKnownChannels().filter { channel ->
                    channel.id in followedCreatorIds
                }
                val page = engine.loadHome(
                    feed = feed,
                    enabledSourceIds = enabledSourceIds,
                    followedChannels = followedChannels,
                    onSubscriptionProgress = { completed, total ->
                        val effectiveTotal = maxOf(subscriptionTotal, total)
                        val normalizedCompleted = completed.coerceIn(0, effectiveTotal)
                        val publishStep = (effectiveTotal / 24).coerceAtLeast(1)
                        while (true) {
                            val previous = publishedSubscriptionProgress.get()
                            if (normalizedCompleted <= previous) return@loadHome
                            if (
                                normalizedCompleted < effectiveTotal &&
                                normalizedCompleted - previous < publishStep
                            ) return@loadHome
                            if (publishedSubscriptionProgress.compareAndSet(
                                    previous,
                                    normalizedCompleted,
                                )
                            ) break
                        }
                        _uiState.update { state ->
                            if (
                                homeLoadGeneration != loadGeneration ||
                                state.home.selectedFeed != HomeFeedType.Subscriptions
                            ) {
                                state
                            } else {
                                state.copy(
                                    home = state.home.copy(
                                        subscriptionsLoaded = maxOf(
                                            state.home.subscriptionsLoaded,
                                            normalizedCompleted,
                                        ),
                                        subscriptionsTotal = effectiveTotal,
                                    ),
                                )
                            }
                        }
                    },
                )
                val previousById = cached.associateBy(VideoUiModel::id)
                val videos = page.videos.map { fresh ->
                    fresh.withPresentationFallback(previousById[fresh.id])
                        .withPersistedLibraryState()
                }.withKnownChannelPresentation(followedChannels)
                videos.forEach { remoteVideos[it.id] = it }
                videos.forEach(::registerRemoteChannel)
                homeFeedCache[feed] = videos
                homeContinuationCache[feed] = page.continuationId
                homeHasMoreCache[feed] = page.hasMore
                _uiState.update { state ->
                    if (homeLoadGeneration != loadGeneration || state.home.selectedFeed != feed) state else state.copy(
                        channels = visibleKnownChannels(),
                        subscriptionVideos = if (feed == HomeFeedType.Subscriptions) {
                            videos
                        } else {
                            state.subscriptionVideos
                        },
                        home = HomeUiState(
                            selectedFeed = feed,
                            videos = videos,
                            continuationId = page.continuationId,
                            hasMore = page.hasMore,
                            subscriptionsLoaded = if (feed == HomeFeedType.Subscriptions) {
                                state.home.subscriptionsTotal
                            } else {
                                state.home.subscriptionsLoaded
                            },
                            subscriptionsTotal = state.home.subscriptionsTotal,
                        ),
                    )
                }
                saveHomeToSession(feed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (homeLoadGeneration != loadGeneration || state.home.selectedFeed != feed) state else state.copy(
                        home = state.home.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: text(R.string.home_feed_refresh_failed),
                        ),
                    )
                }
            }
        }
    }

    fun loadMoreHome() {
        val current = _uiState.value.home
        val continuationId = current.continuationId
        if (!current.hasMore || current.isLoading || current.isLoadingMore || continuationId == null) return
        val feed = current.selectedFeed
        homePagingJob?.cancel()
        _uiState.update { it.copy(home = it.home.copy(isLoadingMore = true)) }
        homePagingJob = viewModelScope.launch {
            try {
                val page = engine.loadMoreHome(feed, continuationId)
                val newVideos = page.videos
                    .map { it.withPersistedLibraryState() }
                    .withKnownChannelPresentation()
                newVideos.forEach { remoteVideos[it.id] = it }
                newVideos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    if (state.home.selectedFeed != feed || state.home.continuationId != continuationId) state
                    else {
                        val videos = (state.home.videos + newVideos).distinctBy(VideoUiModel::id)
                        homeFeedCache[feed] = videos
                        homeContinuationCache[feed] = page.continuationId
                        homeHasMoreCache[feed] = page.hasMore
                        state.copy(
                            channels = visibleKnownChannels(),
                            subscriptionVideos = if (feed == HomeFeedType.Subscriptions) {
                                videos
                            } else {
                                state.subscriptionVideos
                            },
                            home = state.home.copy(
                                videos = videos,
                                isLoadingMore = false,
                                continuationId = page.continuationId,
                                hasMore = page.hasMore,
                            ),
                        )
                    }
                }
                saveHomeToSession(feed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.home.selectedFeed != feed) state else state.copy(
                        home = state.home.copy(
                            isLoadingMore = false,
                            errorMessage = error.localizedMessage ?: text(R.string.home_feed_refresh_failed),
                        ),
                    )
                }
            }
        }
    }

    fun switchProfile(profileId: String) {
        if (profileId == activeProfileId || profileSwitchJob?.isActive == true) return
        profileSwitchJob = viewModelScope.launch {
            switchProfileInternal(profileId)
        }
    }

    private suspend fun switchProfileInternal(profileId: String) {
        if (profileId == activeProfileId) return
        endSpeedHold()
        suppressChromecastHandoff = chromecastManager.state.value.isConnected
        chromecastManager.disconnect(stopRemotePlayback = true)
        // Make sure a newly opened item exists before storing its final playback fraction.
        historyWriteJobs.values.toList().forEach { it.join() }
        historyWriteJobs.clear()
        persistCurrentPlaybackProgress()
        // Finish the write against the old profile repository before replacing it. The write is
        // intentionally off the main thread, but a profile switch is also a durability boundary.
        watchProgressWriteJob?.join()
        invalidatePlaybackQueue()
        queueMutationJob?.cancel()
        detailsJob?.cancel()
        storyboardJob?.cancel()
        audioLanguageJob?.cancel()
        extrasPagingJob?.cancel()
        channelJob?.cancel()
        channelPagingJob?.cancel()
        remotePlaylistJob?.cancel()
        remotePlaylistPagingJob?.cancel()
        resumePromptJob?.cancel()
        homeJob?.cancel()
        homePagingJob?.cancel()
        searchJob?.cancel()
        searchPagingJob?.cancel()
        suggestionJob?.cancel()
        externalUrlJob?.cancel()
        pcHandoffJob?.cancel()
        pcHandoffJob = null
        pendingPcHandoffSeek = null
        dismissYoutubeImport()
        downloadJobs.cancelAndClearJobs()
        downloadQueueRestoreJob?.cancel()
        offlinePlaylistSyncJob?.cancel()
        downloadPreparationStates.clear()
        autoRepairedDownloadKeys.clear()
        appliedDownloadIndexSignature = null
        pendingPlaybackVideoId = null
        activePlaylistId = null
        engine.closePlayback()

        // A plugin may still be finishing an uncancellable V8 callback after its coroutine was
        // cancelled. Stopping that client waits for the V8 busy lock (up to several seconds), so
        // it must never happen in the biometric callback/main thread. Keep the old repositories
        // active until the engine transition completes, then atomically expose the new profile.
        withContext(Dispatchers.IO) {
            engine.setProfile(profileId)
        }
        activeProfileId = profileId
        profileRepository.setActiveProfile(profileId)
        val application = getApplication<Application>()
        preferences = GrayjayPreferences(application, activeProfileId)
        configureVideoTitleLanguage()
        configureYoutubeBackend()
        libraryRepository = SharedPreferencesLibraryRepository(application, activeProfileId)
        homeCacheRepository = HomeCacheRepository(application, activeProfileId)
        sourceRepository = SharedPreferencesSourceRepository(application, activeProfileId)
        engineSources = (baseEngineSources + sourceRepository.loadCustomSources())
            .distinctBy { it.engineId }
            .also(engine::registerSources)
        enabledSourceIds = sourceRepository.loadEnabledSourceIds(engineSources)
            .filterTo(mutableSetOf()) { sourceId ->
                engineSources.any {
                    it.id == sourceId && it.availability != SourceAvailability.MissingPlugin
                }
            }
        followedCreatorIds = preferences.initializeFollowedCreators(
            content.channels.map(ChannelUiModel::id).toSet(),
        )
        allVideos = libraryRepository.loadSavedVideos()
        savedVideosById = allVideos.associateBy(VideoUiModel::id)
        remoteVideos.clear()
        remoteChannels.clear()
        homeFeedCache.clear()
        preferences.loadImportedChannels().forEach { remoteChannels[it.id] = it }
        allVideos.forEach(::registerRemoteChannel)
        val visible = visibleContentForSources(
            videos = content.videos,
            channels = content.channels,
            playlists = content.playlists,
            enabledSourceIds = enabledSourceIds,
        )
        _uiState.value = GrayjayUiState(
            videos = visible.videos,
            libraryVideos = allVideos,
            channels = visibleKnownChannels(),
            playlists = libraryRepository.loadPlaylists(),
            sources = engineSources.map {
                it.copy(
                    isEnabled = it.id in enabledSourceIds,
                    isAuthenticated = engine.isSourceAuthenticated(it.id),
                )
            },
            dynamicColorsEnabled = preferences.dynamicColorsEnabled,
            themeMode = preferences.themeMode,
            privateSessionEnabled = preferences.privateSessionEnabled,
            defaultPlaybackSpeed = preferences.defaultPlaybackSpeed,
            perChannelPlaybackSpeedEnabled = preferences.perChannelPlaybackSpeedEnabled,
            holdToSpeedEnabled = preferences.holdToSpeedEnabled,
            channelPlaybackSpeeds = preferences.channelPlaybackSpeeds(),
            videoPlaybackSpeeds = preferences.videoPlaybackSpeeds(),
            preferredVideoQuality = preferences.preferredVideoQuality,
            preferredAudioBitrate = preferences.preferredAudioBitrate,
            preferredAudioLanguage = preferences.preferredAudioLanguage,
            preferOriginalAudio = preferences.preferOriginalAudio,
            preferNewPipeForYoutubePlayback = preferences.preferNewPipeForYoutubePlayback,
            youtubeBackendMode = preferences.youtubeBackendMode,
            subscriptionFetchMode = preferences.subscriptionFetchMode,
            videoTitleLanguageMode = preferences.videoTitleLanguageMode,
            stickyCaptionsEnabled = preferences.stickyCaptionsEnabled,
            showRecommendations = preferences.showRecommendations,
            searchHistoryEnabled = preferences.searchHistoryEnabled,
            crashLoggingEnabled = CrashLogStore.isEnabled(getApplication()),
            keepScreenAwake = preferences.keepScreenAwake,
            pictureInPictureEnabled = preferences.pictureInPictureEnabled,
            otherAudioDuckingEnabled = preferences.otherAudioDuckingEnabled,
            otherAudioDuckVolumePercent = preferences.otherAudioDuckVolumePercent,
            profiles = profileRepository.profiles(),
            activeProfileId = activeProfileId,
            followedCreatorIds = followedCreatorIds,
        )
        restoreDownloadQueue()
        scheduleOfflinePlaylistSync()
        if (!restoreHomeFromSession()) {
            loadHome(HomeFeedType.Subscriptions, forceRefresh = false)
        }
    }

    fun createProfile(name: String, pin: String) {
        val profile = profileRepository.createProfile(name, pin.takeIf(String::isNotBlank))
        _uiState.update { it.copy(profiles = profileRepository.profiles()) }
        switchProfile(profile.id)
    }

    fun verifyProfilePin(profileId: String, pin: String): Boolean =
        profileRepository.verifyPin(profileId, pin)

    fun setAppForeground(foreground: Boolean) {
        appIsForeground = foreground
        if (foreground) {
            schedulePluginUpdates()
            viewModelScope.launch {
                libraryLoadJob?.join()
                if (appIsForeground) syncDownloadState()
            }
        }
    }

    private fun schedulePluginUpdates() {
        val now = System.currentTimeMillis()
        if (
            pluginUpdateJob?.isActive == true ||
            now - lastPluginUpdateCheckMs < PLUGIN_UPDATE_CHECK_INTERVAL_MS
        ) return
        lastPluginUpdateCheckMs = now
        pluginUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            delay(STARTUP_BACKGROUND_WORK_DELAY_MS)
            val summary = runCatching { engine.updateSources() }
                .onFailure { error ->
                    Log.w("GrayjayViewModel", "Automatic source update check failed.", error)
                }
                .getOrNull()
                ?: return@launch
            if (summary.updatedVersions.isNotEmpty()) {
                Log.i(
                    "GrayjayViewModel",
                    "Updated source plugins: ${summary.updatedVersions.entries.joinToString { "${it.key}=${it.value}" }}",
                )
            }
            if (summary.failures > 0) {
                Log.w(
                    "GrayjayViewModel",
                    "${summary.failures}/${summary.checked} source update checks failed; cached plugins remain active.",
                )
            }
        }
    }

    fun openExternalUrl(url: String) {
        val normalizedUrl = url.trim()
        if (
            !normalizedUrl.startsWith("https://", ignoreCase = true) &&
            !normalizedUrl.startsWith("http://", ignoreCase = true)
        ) return
        externalUrlJob?.cancel()
        externalUrlJob = viewModelScope.launch {
            try {
                val route = engine.routeUrl(normalizedUrl, enabledSourceIds)
                    ?: error(text(R.string.link_not_supported_by_sources))
                val source = _uiState.value.sources.firstOrNull { it.id == route.sourceId }
                val sourceName = source?.name
                    ?: route.sourceId.replaceFirstChar(Char::uppercase)
                when (route.kind) {
                    EngineUrlKind.Video -> {
                        val video = findVideo(route.url) ?: VideoUiModel(
                            id = route.url,
                            title = externalContentLabel(route.url),
                            creator = sourceName,
                            metadata = "",
                            duration = "",
                            sourceId = route.sourceId,
                            contentUrl = route.url,
                            shareUrl = route.url,
                            sourceName = sourceName,
                            sourceIconUrl = source?.iconUrl.orEmpty(),
                        )
                        remoteVideos[video.id] = video
                        openVideo(video.id)
                        publishExternalNavigation(ExternalNavigationKind.Video, video.id)
                    }
                    EngineUrlKind.Channel -> {
                        val channel = ChannelUiModel(
                            id = route.url,
                            name = externalContentLabel(route.url),
                            sourceId = route.sourceId,
                            source = sourceName,
                            unreadCount = 0,
                            followerCount = text(R.string.creator),
                            description = "",
                        )
                        loadChannel(channel)
                        publishExternalNavigation(ExternalNavigationKind.Channel, channel.id)
                    }
                    EngineUrlKind.Playlist -> {
                        val playlist = PlaylistUiModel(
                            id = route.url,
                            title = externalContentLabel(route.url),
                            description = "",
                            videoIds = emptyList(),
                            sourceId = route.sourceId,
                        )
                        loadRemotePlaylist(playlist)
                        publishExternalNavigation(ExternalNavigationKind.Playlist, playlist.id)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "Opening external URL failed: $normalizedUrl", error)
                Toast.makeText(
                    getApplication(),
                    error.localizedMessage ?: text(R.string.link_not_supported_by_sources),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun consumeExternalNavigation(requestId: Long) {
        _uiState.update { state ->
            if (state.externalNavigation?.requestId == requestId) {
                state.copy(externalNavigation = null)
            } else state
        }
    }

    fun checkForUpdates() {
        if (releaseCheckJob?.isActive == true) return
        releaseCheckJob = viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    releaseChecker.latestUpdate(
                        currentVersionName = BuildConfig.VERSION_NAME,
                        supportedAbis = android.os.Build.SUPPORTED_ABIS.toList(),
                    )
                }
                val availableUpdate = release?.let {
                    ReleaseUpdateUiModel(
                        versionName = it.versionName,
                        releaseUrl = it.releaseUrl,
                        changelog = it.changelog,
                        debugApkUrl = it.debugApkUrl,
                    )
                }
                _uiState.update { it.copy(availableUpdate = availableUpdate) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Prefs must remain instant and usable offline. A failed background check is
                // intentionally silent; entering Prefs later retries it.
                Log.d("GrayjayViewModel", "Could not check GitHub releases.", error)
            }
        }
    }

    /**
     * Atom subscription feeds deliberately avoid opening every watch page, so some cards arrive
     * without a duration. Resolve only cards that actually become visible and cap concurrency;
     * this preserves the fast feed while filling the UI metadata progressively.
     */
    fun hydrateVideoMetadata(videoId: String) {
        val video = findVideo(videoId) ?: return
        if (
            video.duration.isNotBlank() ||
            video.isLive ||
            video.scheduledStartAtMs > System.currentTimeMillis()
        ) return
        val attemptKey = "$activeProfileId|$videoId"
        if (!metadataHydrationAttempts.add(attemptKey)) return
        val profileAtStart = activeProfileId
        metadataHydrationJobs[attemptKey] = viewModelScope.launch {
            try {
                val resolved = metadataHydrationSemaphore.withPermit {
                    resolveWithAudioPreferences(
                        video,
                        priority = EngineResolvePriority.BackgroundMetadata,
                    )
                }
                if (activeProfileId != profileAtStart) return@launch
                val resolvedDuration = resolved.duration
                if (resolvedDuration.isBlank()) return@launch
                homeFeedCache.replaceAll { _, videos ->
                    videos.map { current ->
                        if (current.id == videoId) current.copy(duration = resolvedDuration)
                        else current
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        subscriptionVideos = state.subscriptionVideos.map { current ->
                            if (current.id == videoId) current.copy(duration = resolvedDuration)
                            else current
                        },
                        home = state.home.copy(
                            videos = state.home.videos.map { current ->
                                if (current.id == videoId) current.copy(duration = resolvedDuration)
                                else current
                            },
                        ),
                    )
                }
                metadataHydrationSaveJob?.cancel()
                metadataHydrationSaveJob = viewModelScope.launch {
                    delay(1_200L)
                    if (activeProfileId == profileAtStart) saveHomeToSession()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val scheduled = error.scheduledVideoException()
                if (scheduled != null) {
                    libraryRepository.setAvailable(video.id, true)
                    libraryRepository.setScheduledStart(video.id, scheduled.scheduledStartAtMs)
                    _uiState.update { state ->
                        updateVideoEverywhere(state, video.id) { current ->
                            current.copy(
                                isAvailable = true,
                                scheduledStartAtMs = scheduled.scheduledStartAtMs,
                                metadata = scheduledVideoDate(scheduled.scheduledStartAtMs),
                            )
                        }
                    }
                    saveHomeToSession()
                } else {
                    Log.d("GrayjayViewModel", "Visible metadata hydration failed for $videoId", error)
                    metadataHydrationAttempts.remove(attemptKey)
                }
            } finally {
                metadataHydrationJobs.remove(attemptKey)
            }
        }
    }

    private fun publishExternalNavigation(kind: ExternalNavigationKind, contentId: String) {
        externalNavigationRequestId += 1L
        _uiState.update {
            it.copy(
                externalNavigation = ExternalNavigationUiModel(
                    requestId = externalNavigationRequestId,
                    kind = kind,
                    contentId = contentId,
                ),
            )
        }
    }

    fun reloadSourceAuthentication(sourceId: String) {
        engine.reloadSourceAuthentication(sourceId)
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map {
                    if (it.id == sourceId) it.copy(isAuthenticated = engine.isSourceAuthenticated(sourceId)) else it
                },
                sourceOperationMessage = text(
                    R.string.signed_in_to_source,
                    state.sources.firstOrNull { it.id == sourceId }?.name ?: sourceId,
                ),
            )
        }
        _uiState.value.nowPlaying.video
            ?.takeIf { it.sourceId == sourceId }
            ?.let { openVideo(it.id) }
    }

    fun clearSourceAuthentication(sourceId: String) {
        engine.clearSourceAuthentication(sourceId)
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map {
                    if (it.id == sourceId) it.copy(isAuthenticated = false) else it
                },
                sourceOperationMessage = text(R.string.signed_out),
            )
        }
    }

    fun importYoutubeAccount(sourceId: String, selection: YoutubeImportSelection) {
        if (_uiState.value.youtubeImport.isRunning) return
        val source = _uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        if (!source.isAuthenticated) {
            _uiState.update {
                it.copy(
                    youtubeImport = YoutubeImportUiState(
                        errorMessage = text(R.string.youtube_import_login_required),
                    ),
                )
            }
            return
        }
        if (
            !selection.subscriptions &&
            !selection.history &&
            !selection.playlists &&
            !selection.likedVideos
        ) {
            return
        }

        val profileAtStart = activeProfileId
        val libraryAtStart = libraryRepository
        val preferencesAtStart = preferences
        val importGeneration = ++youtubeImportGeneration
        _uiState.update {
            it.copy(
                youtubeImport = YoutubeImportUiState(
                    isRunning = true,
                    stage = YoutubeImportStageUi.Connecting,
                ),
            )
        }
        youtubeImportJob = viewModelScope.launch {
            try {
                val result = engine.importUserData(
                    sourceId = sourceId,
                    selection = EngineUserImportSelection(
                        subscriptions = selection.subscriptions,
                        history = selection.history,
                        playlists = selection.playlists,
                        likedVideos = selection.likedVideos,
                    ),
                ) { progress ->
                    if (importGeneration != youtubeImportGeneration) return@importUserData
                    _uiState.update { state ->
                        state.copy(
                            youtubeImport = state.youtubeImport.copy(
                                stage = when (progress.stage) {
                                    EngineUserImportStage.Connecting ->
                                        YoutubeImportStageUi.Connecting
                                    EngineUserImportStage.Subscriptions ->
                                        YoutubeImportStageUi.Subscriptions
                                    EngineUserImportStage.History ->
                                        YoutubeImportStageUi.History
                                    EngineUserImportStage.Playlists ->
                                        YoutubeImportStageUi.Playlists
                                },
                                completed = progress.completed,
                                total = progress.total,
                                currentItemCompleted = progress.currentItemCompleted,
                            ),
                        )
                    }
                }
                if (!isActive || importGeneration != youtubeImportGeneration) {
                    throw CancellationException()
                }
                if (profileAtStart != activeProfileId) {
                    error(text(R.string.profile_changed_during_import))
                }
                val importedSnapshot = withContext(Dispatchers.IO) {
                    libraryAtStart.mergeImportedData(
                        videos = result.videos,
                        playlists = result.playlists,
                        repairSyntheticHistoryDates = selection.history,
                    )
                    preferencesAtStart.mergeImportedSubscriptions(result.subscriptions)
                    libraryAtStart.loadSavedVideos() to libraryAtStart.loadPlaylists()
                }
                if (!isActive || importGeneration != youtubeImportGeneration) {
                    throw CancellationException()
                }
                result.subscriptions.forEach { remoteChannels[it.id] = it }
                result.videos.forEach { video ->
                    remoteVideos[video.id] = video
                    registerRemoteChannel(video)
                }
                followedCreatorIds = preferencesAtStart.followedCreatorIds()
                applyLibrarySnapshot(
                    savedVideos = importedSnapshot.first,
                    playlists = importedSnapshot.second,
                )
                HomeSessionCache.removeFeed(activeProfileId, HomeFeedType.Subscriptions)
                homeFeedCache.remove(HomeFeedType.Subscriptions)
                homeContinuationCache.remove(HomeFeedType.Subscriptions)
                homeHasMoreCache.remove(HomeFeedType.Subscriptions)
                _uiState.update { state ->
                    state.copy(
                        channels = visibleKnownChannels(),
                        followedCreatorIds = followedCreatorIds,
                        youtubeImport = YoutubeImportUiState(
                            resultMessage = text(
                                R.string.youtube_import_summary,
                                result.subscriptions.size,
                                result.historyCount,
                                result.playlists.size,
                            ),
                            warningMessage = result.warnings
                                .take(3)
                                .joinToString("\n")
                                .takeIf(String::isNotBlank),
                        ),
                    )
                }
                if (selection.subscriptions) refreshHome()
            } catch (error: CancellationException) {
                if (importGeneration == youtubeImportGeneration) {
                    _uiState.update {
                        it.copy(youtubeImport = YoutubeImportUiState())
                    }
                }
                throw error
            } catch (error: Throwable) {
                if (importGeneration == youtubeImportGeneration) {
                    _uiState.update {
                        it.copy(
                            youtubeImport = YoutubeImportUiState(
                                errorMessage = error.localizedMessage
                                    ?: text(R.string.youtube_import_failed),
                            ),
                        )
                    }
                }
            } finally {
                if (importGeneration == youtubeImportGeneration) {
                    youtubeImportJob = null
                }
            }
        }
    }

    fun dismissYoutubeImport() {
        youtubeImportGeneration += 1
        youtubeImportJob?.cancel()
        youtubeImportJob = null
        _uiState.update { it.copy(youtubeImport = YoutubeImportUiState()) }
    }

    fun openVideo(videoId: String) {
        val video = findVideo(videoId) ?: return
        if (video.scheduledStartAtMs > System.currentTimeMillis()) {
            showScheduledVideoDialog(video)
            return
        }
        val shouldRecheckPossibleScheduledLive =
            video.sourceId == "youtube" && video.title.contains("(live)", ignoreCase = true)
        if (!video.isAvailable && !shouldRecheckPossibleScheduledLive) {
            showVideoOpenDialog(video, permanentlyUnavailable = true)
            return
        }
        val resumePositionFraction = video.resumePositionFraction()
        val profileAtStart = activeProfileId
        videoOpenRequestGeneration += 1L
        val requestGeneration = videoOpenRequestGeneration
        val previousNowPlaying = _uiState.value.nowPlaying
        pendingPlaybackVideoId = video.id
        detailsJob?.cancel()
        storyboardJob?.cancel()
        extrasPagingJob?.cancel()
        commentRepliesJob?.cancel()
        engine.pausePlayback()
        _uiState.update { state ->
            state.copy(
                nowPlaying = NowPlayingUiState(
                    video = video,
                    isLoadingPlayback = true,
                    isLoadingExtras = true,
                    isFollowing = preferences.isCreatorFollowed(video.creatorKey()),
                    resumePositionFraction = video.resumePositionFraction(),
                ),
            )
        }
        detailsJob = viewModelScope.launch {
            try {
                val resolved = resolveForPlayback(video, profileAtStart).copy(
                    isAvailable = true,
                    scheduledStartAtMs = 0L,
                )
                if (
                    requestGeneration != videoOpenRequestGeneration ||
                    profileAtStart != activeProfileId ||
                    pendingPlaybackVideoId != video.id
                ) {
                    return@launch
                }
                val generation = invalidatePlaybackQueue()
                activePlaylistId = null
                libraryRepository.setAvailable(video.id, true)
                libraryRepository.setScheduledStart(video.id, 0L)
                remoteVideos[resolved.id] = resolved
                registerRemoteChannel(resolved)
                recordHistory(resolved)
                // Only replace the current item after the source returned a playable descriptor.
                // This keeps the existing player and screen intact for removed/private videos.
                engine.pausePlayback()
                _uiState.update { current ->
                    if (
                        requestGeneration != videoOpenRequestGeneration ||
                        pendingPlaybackVideoId != video.id ||
                        profileAtStart != activeProfileId
                    ) {
                        current
                    } else current.copy(
                        channels = visibleKnownChannels(),
                        videos = current.videos.map { if (it.id == resolved.id) resolved else it },
                        search = current.search.copy(
                            videos = current.search.videos.map { if (it.id == resolved.id) resolved else it },
                        ),
                        nowPlaying = NowPlayingUiState(
                            video = resolved,
                            isLoadingPlayback = true,
                            isLoadingExtras = true,
                            isFollowing = preferences.isCreatorFollowed(resolved.creatorKey()),
                            resumePositionFraction = resumePositionFraction,
                        ),
                    )
                }
                scheduleResumePromptDismiss(resolved.id, resumePositionFraction)
                publishExternalNavigation(ExternalNavigationKind.Video, resolved.id)
                openLocallyOrCast(
                    video = resolved,
                    playWhenReady = resolved.playbackFromDownload || resolved.contentUrl.isNotBlank(),
                )
                _uiState.update { state ->
                    if (state.nowPlaying.video?.id != resolved.id) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(isLoadingPlayback = false),
                    )
                }
                if (pendingPlaybackVideoId == video.id) pendingPlaybackVideoId = null
                applyPlaybackPreferences()
                requestStoryboard(resolved, generation)
                loadExtras(resolved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (
                    requestGeneration != videoOpenRequestGeneration ||
                    pendingPlaybackVideoId != video.id
                ) {
                    return@launch
                }
                Log.e("GrayjayViewModel", "Could not resolve video from ${video.sourceId}.", error)
                if (pendingPlaybackVideoId == video.id) pendingPlaybackVideoId = null
                val scheduled = error.scheduledVideoException()
                if (scheduled != null) {
                    libraryRepository.setAvailable(video.id, true)
                    libraryRepository.setScheduledStart(video.id, scheduled.scheduledStartAtMs)
                    _uiState.update { state ->
                        updateVideoEverywhere(state, video.id) { current ->
                            current.copy(
                                isAvailable = true,
                                scheduledStartAtMs = scheduled.scheduledStartAtMs,
                                metadata = scheduledVideoDate(scheduled.scheduledStartAtMs),
                            )
                        }.copy(
                            nowPlaying = previousNowPlaying,
                            videoOpenDialog = videoOpenDialog(
                                video = video,
                                permanentlyUnavailable = false,
                                scheduledStartAtMs = scheduled.scheduledStartAtMs,
                            ),
                        )
                    }
                    saveHomeToSession()
                    return@launch
                }
                val permanentlyUnavailable = isPermanentlyUnavailableVideo(
                    error = error,
                    videoTitle = video.title,
                    isLive = video.isLive,
                )
                if (permanentlyUnavailable) {
                    libraryRepository.setAvailable(video.id, false)
                    _uiState.update { state ->
                        updateVideoEverywhere(state, video.id) { current ->
                            current.copy(isAvailable = false)
                        }.copy(
                            nowPlaying = previousNowPlaying,
                            videoOpenDialog = videoOpenDialog(
                                video = video,
                                permanentlyUnavailable = true,
                            ),
                        )
                    }
                    saveHomeToSession()
                } else {
                    val message = if (error is ScriptLoginRequiredException) {
                        text(
                            R.string.source_login_required_for_video,
                            video.sourceName.ifBlank { video.sourceId },
                        )
                    } else {
                        error.localizedMessage ?: text(R.string.video_details_load_failed)
                    }
                    _uiState.update { state ->
                        state.copy(
                            nowPlaying = previousNowPlaying,
                            videoOpenDialog = videoOpenDialog(
                                video = video,
                                permanentlyUnavailable = false,
                                message = message,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun dismissVideoOpenDialog() {
        _uiState.update { it.copy(videoOpenDialog = null) }
    }

    private fun showVideoOpenDialog(video: VideoUiModel, permanentlyUnavailable: Boolean) {
        _uiState.update {
            it.copy(videoOpenDialog = videoOpenDialog(video, permanentlyUnavailable))
        }
    }

    private fun showScheduledVideoDialog(video: VideoUiModel) {
        _uiState.update {
            it.copy(
                videoOpenDialog = videoOpenDialog(
                    video = video,
                    permanentlyUnavailable = false,
                    scheduledStartAtMs = video.scheduledStartAtMs,
                ),
            )
        }
    }

    private fun videoOpenDialog(
        video: VideoUiModel,
        permanentlyUnavailable: Boolean,
        message: String? = null,
        scheduledStartAtMs: Long = 0L,
    ) = com.futo.platformplayer.compose.ui.VideoOpenDialogUiModel(
        videoId = video.id,
        title = video.title,
        message = message ?: when {
            scheduledStartAtMs > 0L -> text(
                R.string.video_scheduled_message,
                scheduledVideoDate(scheduledStartAtMs),
            )
            permanentlyUnavailable -> text(R.string.video_no_longer_available_message)
            else -> text(R.string.video_details_load_failed)
        },
        permanentlyUnavailable = permanentlyUnavailable,
        scheduledStartAtMs = scheduledStartAtMs,
    )

    private fun scheduledVideoDate(startAtMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(startAtMs))

    private fun requestStoryboard(video: VideoUiModel, generation: Long) {
        if (video.storyboard != null || video.isLive || video.playbackAudioOnly) return
        storyboardJob?.cancel()
        storyboardJob = viewModelScope.launch {
            val storyboard = runCatching { engine.loadStoryboard(video) }.getOrNull() ?: return@launch
            if (
                generation != playbackGeneration ||
                _uiState.value.nowPlaying.video?.id != video.id
            ) return@launch
            val updated = video.copy(storyboard = storyboard)
            remoteVideos[updated.id] = updated
            _uiState.update { state ->
                if (state.nowPlaying.video?.id != video.id) state else state.copy(
                    nowPlaying = state.nowPlaying.copy(video = updated),
                )
            }
        }
    }

    fun playQueue(videoIds: List<String>) {
        activePlaylistId = null
        startQueue(videoIds)
    }

    fun enqueueVideos(videoIds: List<String>) {
        val requested = videoIds.distinct().mapNotNull(::findVideo)
        if (requested.isEmpty()) return
        queueMutationJob?.cancel()
        queueMutationJob = viewModelScope.launch {
            enqueueVideosWithoutBlockingUi(requested)
        }
    }

    fun playNext(videoId: String) {
        val currentId = engine.playback.value.currentVideoId ?: return
        if (videoId == currentId) return
        val session = playbackQueueSession
        if (session != null && videoId in session.orderedVideoIds) {
            session.orderedVideoIds.remove(videoId)
            val currentIndex = session.orderedVideoIds.indexOf(currentId)
            session.orderedVideoIds.add((currentIndex + 1).coerceAtLeast(0), videoId)
            session.pendingVideos.firstOrNull { it.id == videoId }?.let { pending ->
                session.pendingVideos.remove(pending)
                session.pendingVideos.add(0, pending)
            }
        }
        engine.moveQueueItemNext(videoId)
        publishLogicalQueue()
        prepareQueueLookAhead()
    }

    private suspend fun enqueueVideosWithoutBlockingUi(requested: List<VideoUiModel>) {
        val profileAtStart = activeProfileId
        val repositoryAtStart = libraryRepository
        val generationAtStart = playbackGeneration
        val currentPlayback = engine.playback.value
        val currentSession = playbackQueueSession
        val additionIds = unqueuedVideoIds(
            requestedVideoIds = requested.map(VideoUiModel::id),
            activeQueueVideoIds = currentPlayback.queueVideoIds,
            knownQueueVideoIds = currentSession?.knownVideoIds.orEmpty(),
        ).toSet()
        val additions = requested.filter { it.id in additionIds }
        if (additions.isEmpty()) return

        val currentVideoId = currentPlayback.currentVideoId
            ?: _uiState.value.nowPlaying.video?.id
        if (currentVideoId == null || engine.player.mediaItemCount == 0) {
            val createdSnapshot = withContext(Dispatchers.IO) {
                val created = repositoryAtStart.createPlaylist(
                    playedOnPlaylistTitle(repositoryAtStart),
                    requested,
                ) ?: return@withContext null
                Triple(
                    created,
                    repositoryAtStart.loadSavedVideos(),
                    repositoryAtStart.loadPlaylists(),
                )
            } ?: return
            if (
                profileAtStart != activeProfileId ||
                repositoryAtStart !== libraryRepository ||
                generationAtStart != playbackGeneration
            ) return
            applyLibrarySnapshot(createdSnapshot.second, createdSnapshot.third)
            activePlaylistId = createdSnapshot.first.id
            startQueue(createdSnapshot.first.videoIds)
            return
        }

        var queueSession = currentSession
        if (activePlaylistId == null) {
            val snapshotIds = buildList {
                addAll(currentPlayback.queueVideoIds)
                currentSession?.pendingVideos?.mapTo(this, VideoUiModel::id)
                addAll(additions.map(VideoUiModel::id))
            }.distinct()
            val snapshotVideos = snapshotIds.mapNotNull(::findVideo)
            val createdSnapshot = withContext(Dispatchers.IO) {
                val created = repositoryAtStart.createPlaylist(
                    playedOnPlaylistTitle(repositoryAtStart),
                    snapshotVideos,
                ) ?: return@withContext null
                Triple(
                    created,
                    repositoryAtStart.loadSavedVideos(),
                    repositoryAtStart.loadPlaylists(),
                )
            } ?: return
            if (
                profileAtStart != activeProfileId ||
                repositoryAtStart !== libraryRepository ||
                generationAtStart != playbackGeneration
            ) return
            applyLibrarySnapshot(createdSnapshot.second, createdSnapshot.third)
            activePlaylistId = createdSnapshot.first.id
            queueSession = if (currentSession == null) {
                PlaybackQueueSession(
                    generation = generationAtStart,
                    profileId = profileAtStart,
                    playlistId = createdSnapshot.first.id,
                    pendingVideos = mutableListOf(),
                    knownVideoIds = currentPlayback.queueVideoIds.toMutableSet(),
                    orderedVideoIds = snapshotIds.toMutableList(),
                )
            } else {
                currentSession.copy(playlistId = createdSnapshot.first.id)
            }
            playbackQueueSession = queueSession
        } else if (queueSession == null) {
            queueSession = PlaybackQueueSession(
                generation = generationAtStart,
                profileId = profileAtStart,
                playlistId = activePlaylistId,
                pendingVideos = mutableListOf(),
                knownVideoIds = currentPlayback.queueVideoIds.toMutableSet(),
                orderedVideoIds = currentPlayback.queueVideoIds.toMutableList(),
            )
            playbackQueueSession = queueSession
        }

        additions.forEach { video ->
            if (queueSession.knownVideoIds.add(video.id)) {
                queueSession.pendingVideos += video
                queueSession.orderedVideoIds += video.id
            }
        }
        publishLogicalQueue()
        prepareQueueLookAhead()
    }

    fun playPlaylist(playlistId: String) {
        val playlist = libraryRepository.loadPlaylists().firstOrNull { it.id == playlistId } ?: return
        activePlaylistId = playlistId
        startQueue(playlist.videoIds)
    }

    fun playPlaylistFrom(playlistId: String, videoId: String) {
        val playlist = libraryRepository.loadPlaylists().firstOrNull { it.id == playlistId } ?: return
        val queueIds = playlistQueueFrom(playlist.videoIds, videoId)
        if (queueIds.isEmpty()) return
        activePlaylistId = playlistId
        startQueue(queueIds)
    }

    private fun startQueue(videoIds: List<String>) {
        val queue = videoIds.distinct().mapNotNull(::findVideo)
        if (queue.isEmpty()) {
            invalidatePlaybackQueue()
            activePlaylistId = null
            return
        }
        val generation = invalidatePlaybackQueue()
        val profileAtStart = activeProfileId
        pendingPlaybackVideoId = queue.first().id
        val playlistIdForQueue = activePlaylistId
        playbackQueueSession = PlaybackQueueSession(
            generation = generation,
            profileId = profileAtStart,
            playlistId = playlistIdForQueue,
            pendingVideos = queue.drop(1).toMutableList(),
            knownVideoIds = queue.mapTo(linkedSetOf(), VideoUiModel::id),
            orderedVideoIds = queue.mapTo(mutableListOf(), VideoUiModel::id),
        )
        detailsJob?.cancel()
        storyboardJob?.cancel()
        extrasPagingJob?.cancel()
        engine.pausePlayback()
        _uiState.update {
            it.copy(
                nowPlaying = NowPlayingUiState(
                    video = queue.first(),
                    isLoadingPlayback = true,
                    isLoadingExtras = true,
                    isFollowing = preferences.isCreatorFollowed(queue.first().creatorKey()),
                    resumePositionFraction = queue.first().resumePositionFraction(),
                ),
            )
        }
        scheduleResumePromptDismiss(queue.first().id, queue.first().resumePositionFraction())
        detailsJob = viewModelScope.launch {
            val first = try {
                resolveForPlayback(queue.first(), profileAtStart)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation != playbackGeneration) return@launch
                Log.e(
                    "GrayjayViewModel",
                    "Could not resolve the first playlist video from ${queue.first().sourceId}.",
                    error,
                )
                playbackQueueSession = null
                recordHistory(queue.first())
                engine.open(listOf(queue.first()), queue.first().id, playWhenReady = false)
                if (pendingPlaybackVideoId == queue.first().id) pendingPlaybackVideoId = null
                _uiState.update { state ->
                    if (state.nowPlaying.video?.id != queue.first().id) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(
                            isLoadingPlayback = false,
                            isLoadingExtras = false,
                            errorMessage = if (error is ScriptLoginRequiredException) {
                                text(
                                    R.string.source_login_required_for_video,
                                    queue.first().sourceName.ifBlank { queue.first().sourceId },
                                )
                            } else {
                                error.localizedMessage ?: text(R.string.video_details_load_failed)
                            },
                        ),
                    )
                }
                return@launch
            }
            if (
                generation != playbackGeneration ||
                profileAtStart != activeProfileId ||
                pendingPlaybackVideoId != first.id
            ) return@launch
            remoteVideos[first.id] = first
            registerRemoteChannel(first)
            recordHistory(first)
            _uiState.update { state ->
                state.copy(
                    nowPlaying = state.nowPlaying.copy(
                        video = first,
                        isFollowing = preferences.isCreatorFollowed(first.creatorKey()),
                    ),
                )
            }
            // Hand the selected item to Media3 before touching later entries. A slow, deleted,
            // restricted, or signed-out item farther down the playlist must never block startup.
            openLocallyOrCast(first, playWhenReady = true)
            _uiState.update { state ->
                if (state.nowPlaying.video?.id != first.id) state else state.copy(
                    nowPlaying = state.nowPlaying.copy(isLoadingPlayback = false),
                )
            }
            if (pendingPlaybackVideoId == first.id) pendingPlaybackVideoId = null
            applyPlaybackPreferences()
            requestStoryboard(first, generation)
            prepareQueueLookAhead()
            loadExtras(first)
        }
    }

    /**
     * Keeps a small rolling window in Media3, matching Grayjay's prepare-near-playback behavior.
     * Resolving every playlist entry up front both blocks the first item and lets signed URLs for
     * later entries expire. Completed downloads resolve locally and are appended through the same
     * path, so mixed online/offline playlists retain one deterministic order.
     */
    private fun prepareQueueLookAhead(playback: EnginePlaybackState = engine.playback.value) {
        val session = playbackQueueSession ?: return
        if (System.currentTimeMillis() < queuePreparationBlockedUntilMs) return
        if (session.generation != playbackGeneration || session.profileId != activeProfileId) return
        if (pendingPlaybackVideoId?.let { it != playback.currentVideoId } == true) return
        if (playback.currentVideoId == null || engine.player.mediaItemCount == 0) return
        if (preparedQueueItemsAhead(playback.queueVideoIds, playback.currentVideoId) >= QUEUE_LOOKAHEAD) return
        if (session.pendingVideos.isEmpty() || queuePreparationJob?.isActive == true) return

        val job = viewModelScope.launch {
            while (true) {
                val currentSession = playbackQueueSession
                if (
                    currentSession !== session ||
                    session.generation != playbackGeneration ||
                    session.profileId != activeProfileId ||
                    engine.player.mediaItemCount == 0
                ) return@launch
                val currentPlayback = engine.playback.value
                if (
                    preparedQueueItemsAhead(
                        currentPlayback.queueVideoIds,
                        currentPlayback.currentVideoId,
                    ) >= QUEUE_LOOKAHEAD
                ) return@launch
                val video = session.pendingVideos.firstOrNull() ?: return@launch
                val resolved = try {
                    resolveForPlayback(video, session.profileId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (error.hasMessageInChain("TOO_MANY_ACTIVE_STREAMS")) {
                        // Crunchyroll counts every resolved episode as an active stream. Keep the
                        // item pending and back off instead of walking the whole season and
                        // repeatedly exhausting the account's stream allowance.
                        queuePreparationBlockedUntilMs = System.currentTimeMillis() + 30_000L
                        Log.w(
                            "GrayjayViewModel",
                            "Queue look-ahead paused because the source rejected another active stream.",
                        )
                        return@launch
                    }
                    Log.w(
                        "GrayjayViewModel",
                        "Skipping unresolvable queued video ${video.id} from ${video.sourceId}.",
                        error,
                    )
                    if (playbackQueueSession === session) {
                        session.pendingVideos.remove(video)
                        session.orderedVideoIds.remove(video.id)
                        session.knownVideoIds.remove(video.id)
                        publishLogicalQueue()
                    }
                    continue
                }
                if (
                    playbackQueueSession !== session ||
                    session.generation != playbackGeneration ||
                    session.profileId != activeProfileId ||
                    engine.player.mediaItemCount == 0
                ) return@launch
                session.pendingVideos.remove(video)
                remoteVideos[resolved.id] = resolved
                registerRemoteChannel(resolved)
                try {
                    engine.appendToQueue(listOf(resolved))
                } catch (error: Throwable) {
                    Log.w(
                        "GrayjayViewModel",
                        "Could not append queued video ${resolved.id}; continuing with the queue.",
                        error,
                    )
                    session.orderedVideoIds.remove(resolved.id)
                    session.knownVideoIds.remove(resolved.id)
                    publishLogicalQueue()
                }
            }
        }
        queuePreparationJob = job
        job.invokeOnCompletion {
            if (queuePreparationJob === job) queuePreparationJob = null
        }
    }

    private fun invalidatePlaybackQueue(): Long {
        playbackGeneration += 1
        queuePreparationBlockedUntilMs = 0L
        queuePreparationJob?.cancel()
        queuePreparationJob = null
        playbackQueueSession = null
        return playbackGeneration
    }

    fun togglePlayback() {
        if (chromecastManager.state.value.isConnected) chromecastManager.togglePlayback()
        else engine.togglePlayback()
    }

    fun skipToNext() {
        if (!chromecastManager.state.value.isConnected) {
            engine.skipToNext()
            return
        }
        if (!engine.player.hasNextMediaItem()) return
        engine.player.pause()
        engine.player.seekToNextMediaItem()
        castCurrentEngineItem(positionMs = 0L)
    }

    fun skipToPrevious() {
        val cast = chromecastManager.state.value
        if (!cast.isConnected) {
            engine.skipToPrevious()
            return
        }
        if (cast.positionMs > 5_000L || !engine.player.hasPreviousMediaItem()) {
            chromecastManager.seekTo(0L)
            return
        }
        engine.player.pause()
        engine.player.seekToPreviousMediaItem()
        castCurrentEngineItem(positionMs = 0L)
    }

    fun seekPlaybackBy(deltaMs: Long) {
        val cast = chromecastManager.state.value
        if (cast.isConnected) {
            chromecastManager.seekTo(
                (cast.positionMs + deltaMs).coerceIn(0L, cast.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE),
            )
        } else engine.seekBy(deltaMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.value.nowPlaying.video?.let { video ->
            preferences.setVideoPlaybackSpeed(video.id, speed)
            _uiState.update { it.copy(videoPlaybackSpeeds = preferences.videoPlaybackSpeeds()) }
        }
        if (chromecastManager.state.value.isConnected) chromecastManager.setPlaybackSpeed(speed)
        else engine.setPlaybackSpeed(speed)
    }

    /**
     * Mirrors legacy Grayjay's hold gesture: temporarily play at 2x, then restore both the
     * previous speed and whether playback had been paused when the finger is released.
     */
    fun startSpeedHold() {
        if (!preferences.holdToSpeedEnabled || speedHoldSnapshot != null) return
        if (_uiState.value.playback.currentVideoId == null) return
        val cast = chromecastManager.state.value
        speedHoldSnapshot = SpeedHoldSnapshot(
            speed = _uiState.value.playback.playbackSpeed.coerceIn(0.25f, 3f),
            wasPlaying = if (cast.isConnected) cast.isPlaying else engine.player.isPlaying,
            wasCasting = cast.isConnected,
        )
        if (cast.isConnected) {
            chromecastManager.setPlaybackSpeed(2f)
            if (!cast.isPlaying) chromecastManager.togglePlayback()
        } else {
            engine.setPlaybackSpeed(2f)
            if (!engine.player.isPlaying) engine.player.play()
        }
    }

    fun endSpeedHold() {
        val snapshot = speedHoldSnapshot ?: return
        speedHoldSnapshot = null
        val cast = chromecastManager.state.value
        if (snapshot.wasCasting && cast.isConnected) {
            chromecastManager.setPlaybackSpeed(snapshot.speed)
            if (!snapshot.wasPlaying && cast.isPlaying) chromecastManager.togglePlayback()
        } else {
            engine.setPlaybackSpeed(snapshot.speed)
            if (snapshot.wasPlaying) engine.player.play() else engine.pausePlayback()
        }
    }

    fun useChannelPlaybackSpeedForCurrentVideo() {
        val video = _uiState.value.nowPlaying.video ?: return
        preferences.setVideoPlaybackSpeed(video.id, null)
        _uiState.update { it.copy(videoPlaybackSpeeds = preferences.videoPlaybackSpeeds()) }
        applyPlaybackSpeed(video)
    }

    fun setChannelPlaybackSpeed(channelId: String, speed: Float?) {
        preferences.setChannelPlaybackSpeed(channelId, speed)
        _uiState.update { it.copy(channelPlaybackSpeeds = preferences.channelPlaybackSpeeds()) }
        val current = _uiState.value.nowPlaying.video
        if (current?.creatorKey() == channelId && current.id !in preferences.videoPlaybackSpeeds()) {
            applyPlaybackSpeed(current)
        }
    }

    fun setVideoQuality(height: Int?) {
        engine.setVideoQuality(height)
        val cast = chromecastManager.state.value
        if (cast.isConnected) {
            _uiState.value.nowPlaying.video?.let { video ->
                chromecastManager.cast(
                    video,
                    _uiState.value.playback.copy(
                        selectedVideoQuality = height,
                        positionMs = cast.positionMs,
                        isPlaying = cast.isPlaying,
                    ),
                )
            }
        }
    }

    fun startChromecastDiscovery() = chromecastManager.startDiscovery()

    fun connectChromecast(deviceId: String) {
        val video = _uiState.value.nowPlaying.video ?: return
        val playback = _uiState.value.playback
        viewModelScope.launch {
            val castVideo = try {
                if (video.playbackFromDownload) resolveWithAudioPreferences(video.onlinePlaybackInput()) else video
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "Could not prepare online cast source.", error)
                return@launch
            }
            resumeLocalAfterFailedCast = playback.isPlaying
            engine.pausePlayback()
            chromecastManager.connect(deviceId, castVideo, playback)
        }
    }

    fun disconnectChromecast() = chromecastManager.disconnect(stopRemotePlayback = true)

    private fun openLocallyOrCast(
        video: VideoUiModel,
        playWhenReady: Boolean,
        castPositionMs: Long = 0L,
    ) {
        val casting = chromecastManager.state.value.isConnected
        engine.open(listOf(video), video.id, playWhenReady = playWhenReady && !casting)
        if (casting) {
            chromecastManager.cast(
                video,
                _uiState.value.playback.copy(
                    currentVideoId = video.id,
                    isPlaying = playWhenReady,
                    positionMs = castPositionMs,
                    errorMessage = null,
                ),
            )
        }
    }

    private fun castCurrentEngineItem(positionMs: Long) {
        val videoId = engine.player.currentMediaItem?.mediaId.orEmpty()
        val video = findVideo(videoId) ?: return
        chromecastManager.cast(
            video,
            _uiState.value.playback.copy(
                currentVideoId = video.id,
                isPlaying = true,
                positionMs = positionMs,
                errorMessage = null,
            ),
        )
    }

    private fun handoffChromecastToLocal(cast: ChromecastUiState) {
        if (engine.player.mediaItemCount == 0) return
        val localPosition = engine.playback.value.positionMs
        engine.seekBy(cast.positionMs - localPosition)
        if (cast.isPlaying && !engine.playback.value.isPlaying) engine.togglePlayback()
    }

    fun setCaptionsEnabled(enabled: Boolean) {
        engine.setCaptionsEnabled(enabled)
        if (preferences.stickyCaptionsEnabled) preferences.captionsEnabled = enabled
    }

    fun setSubtitleLanguage(language: String?) {
        val selectedTrack = _uiState.value.nowPlaying.video?.subtitleTracks
            ?.withIndex()
            ?.firstOrNull { it.value.uri == language }
        if (selectedTrack != null) {
            engine.setSubtitleTrack(selectedTrack.index)
        } else {
            engine.setSubtitleLanguage(language)
        }
        if (preferences.stickyCaptionsEnabled) {
            preferences.captionsEnabled = language != null
            preferences.subtitleLanguage = selectedTrack?.value?.language ?: language
        }
    }

    fun setAudioLanguage(language: String?) {
        val video = _uiState.value.nowPlaying.video ?: return
        if (video.playbackFromDownload || video.contentUrl.isBlank()) {
            engine.setAudioLanguage(language)
            return
        }
        audioLanguageJob?.cancel()
        val profileAtStart = activeProfileId
        val currentVideoId = video.id
        val playback = _uiState.value.playback
        audioLanguageJob = viewModelScope.launch {
            try {
                val resolved = resolveWithAudioPreferences(
                    video = video.onlinePlaybackInput(),
                    audioLanguageOverride = language,
                ).withPersistedLibraryState()
                if (
                    profileAtStart != activeProfileId ||
                    _uiState.value.nowPlaying.video?.id != currentVideoId
                ) {
                    return@launch
                }
                remoteVideos[resolved.id] = resolved
                registerRemoteChannel(resolved)
                _uiState.update { state ->
                    if (state.nowPlaying.video?.id != currentVideoId) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(video = resolved),
                    )
                }
                if (chromecastManager.state.value.isConnected) {
                    chromecastManager.cast(
                        resolved,
                        playback.copy(
                            currentVideoId = resolved.id,
                            positionMs = playback.positionMs,
                            isPlaying = playback.isPlaying,
                            errorMessage = null,
                        ),
                    )
                } else {
                    engine.replaceCurrent(
                        video = resolved,
                        positionMs = playback.positionMs,
                        playWhenReady = playback.isPlaying,
                    )
                    engine.setAudioLanguage(language)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "Could not change audio language.", error)
                Toast.makeText(
                    getApplication(),
                    error.localizedMessage ?: text(R.string.audio_language_change_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun retryPlayback() = engine.retryPlayback()

    fun toggleFollowing() {
        val video = _uiState.value.nowPlaying.video ?: return
        val creatorKey = video.creatorKey()
        if (creatorKey.isBlank()) return
        setCreatorFollowed(creatorKey, !_uiState.value.nowPlaying.isFollowing)
    }

    fun setCreatorFollowed(creatorId: String, followed: Boolean) {
        if (creatorId.isBlank()) return
        preferences.setCreatorFollowed(creatorId, followed)
        followedCreatorIds = if (followed) followedCreatorIds + creatorId else followedCreatorIds - creatorId
        _uiState.update { state ->
            val currentCreatorId = state.nowPlaying.video?.creatorKey()
            state.copy(
                followedCreatorIds = followedCreatorIds,
                nowPlaying = if (currentCreatorId == creatorId) {
                    state.nowPlaying.copy(isFollowing = followed)
                } else {
                    state.nowPlaying
                },
            )
        }
        homeFeedCache.remove(HomeFeedType.Subscriptions)
        homeContinuationCache.remove(HomeFeedType.Subscriptions)
        homeHasMoreCache.remove(HomeFeedType.Subscriptions)
        HomeSessionCache.removeFeed(activeProfileId, HomeFeedType.Subscriptions)
        homeCacheRepository.removeFeed(HomeFeedType.Subscriptions)
        if (_uiState.value.home.selectedFeed == HomeFeedType.Subscriptions) refreshHome()
    }

    fun loadChannel(channel: ChannelUiModel) {
        if (channel.id.isBlank()) return
        val currentDetail = _uiState.value.channelDetail
        if (
            currentDetail.channelId == channel.id &&
            (currentDetail.isLoading || currentDetail.isLoaded)
        ) return
        val initialChannel = registerRemoteChannel(channel)
        channelJob?.cancel()
        channelPagingJob?.cancel()
        _uiState.update { state ->
            state.copy(
                channels = visibleKnownChannels(),
                channelDetail = ChannelDetailUiState(
                    channelId = initialChannel.id,
                    channel = initialChannel,
                    isLoading = true,
                ),
            )
        }
        channelJob = viewModelScope.launch {
            try {
                val details = engine.loadChannel(initialChannel)
                val detailedChannel = registerRemoteChannel(details.channel)
                val channelVideos = details.videos
                    .map { it.withPersistedLibraryState() }
                    .distinctBy(VideoUiModel::id)
                channelVideos.forEach { remoteVideos[it.id] = it }
                channelVideos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    if (state.channelDetail.channelId != initialChannel.id) state else state.copy(
                        channels = visibleKnownChannels(),
                        channelDetail = ChannelDetailUiState(
                            channelId = detailedChannel.id,
                            channel = detailedChannel,
                            videos = channelVideos,
                            isLoading = false,
                            isLoaded = true,
                            loadedTabs = setOf(ChannelContentTab.Videos),
                            continuationIds = details.continuationId?.let {
                                mapOf(ChannelContentTab.Videos to it)
                            }.orEmpty(),
                            tabsWithMore = setOf(ChannelContentTab.Videos)
                                .takeIf { details.hasMore }
                                .orEmpty(),
                            continuationId = details.continuationId,
                            hasMore = details.hasMore,
                            supportsShorts = details.supportsShorts,
                            supportsPlaylists = details.supportsPlaylists,
                            liveContentType = details.liveContentType,
                            supportsPopularSort = details.supportsPopularSort,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.channelDetail.channelId != initialChannel.id) state else state.copy(
                        channelDetail = state.channelDetail.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage
                                ?: text(R.string.creator_load_failed),
                        ),
                    )
                }
            }
        }
    }

    fun selectChannelTab(tab: ChannelContentTab) {
        val current = _uiState.value.channelDetail
        val channel = current.channel ?: return
        if (tab == ChannelContentTab.Shorts && !current.supportsShorts) return
        if (tab == ChannelContentTab.Playlists && !current.supportsPlaylists) return
        if (tab == ChannelContentTab.Live && current.liveContentType == null) return
        _uiState.update {
            it.copy(
                channelDetail = it.channelDetail.copy(
                    selectedTab = tab,
                    isLoading = tab !in it.channelDetail.loadedTabs,
                    isLoadingMore = false,
                    errorMessage = null,
                    continuationId = it.channelDetail.continuationIds[tab],
                    hasMore = tab in it.channelDetail.tabsWithMore,
                ),
            )
        }
        if (tab in current.loadedTabs) return
        channelJob?.cancel()
        channelPagingJob?.cancel()
        channelJob = viewModelScope.launch {
            try {
                val page = engine.loadChannelPage(
                    channel = channel,
                    tab = tab,
                    contentType = current.liveContentType.takeIf {
                        tab == ChannelContentTab.Live
                    },
                )
                val videos = page.videos.map { it.withPersistedLibraryState() }
                videos.forEach { remoteVideos[it.id] = it }
                videos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    if (
                        state.channelDetail.channelId != channel.id ||
                        state.channelDetail.selectedTab != tab
                    ) state else state.copy(
                        channels = visibleKnownChannels(),
                        channelDetail = state.channelDetail.copy(
                            shorts = if (tab == ChannelContentTab.Shorts) videos else state.channelDetail.shorts,
                            liveStreams = if (tab == ChannelContentTab.Live) {
                                videos
                            } else {
                                state.channelDetail.liveStreams
                            },
                            playlists = if (tab == ChannelContentTab.Playlists) page.playlists else state.channelDetail.playlists,
                            loadedTabs = state.channelDetail.loadedTabs + tab,
                            continuationIds = state.channelDetail.continuationIds
                                .toMutableMap()
                                .apply {
                                    if (page.continuationId == null) remove(tab)
                                    else put(tab, page.continuationId)
                                },
                            tabsWithMore = if (page.hasMore) {
                                state.channelDetail.tabsWithMore + tab
                            } else {
                                state.channelDetail.tabsWithMore - tab
                            },
                            isLoading = false,
                            continuationId = page.continuationId,
                            hasMore = page.hasMore,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.channelDetail.channelId != channel.id) state else state.copy(
                        channelDetail = state.channelDetail.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: text(R.string.creator_load_failed),
                        ),
                    )
                }
            }
        }
    }

    fun loadMoreChannel() {
        val current = _uiState.value.channelDetail
        val tab = current.selectedTab
        val continuationId = current.continuationIds[tab]
        if (!current.hasMore || current.isLoading || current.isLoadingMore || continuationId == null) return
        val channelId = current.channelId ?: return
        channelPagingJob?.cancel()
        _uiState.update { it.copy(channelDetail = it.channelDetail.copy(isLoadingMore = true)) }
        channelPagingJob = viewModelScope.launch {
            try {
                val page = engine.loadMoreChannel(continuationId)
                val videos = page.videos.map { it.withPersistedLibraryState() }
                videos.forEach { remoteVideos[it.id] = it }
                videos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    if (
                        state.channelDetail.channelId != channelId ||
                        state.channelDetail.continuationId != continuationId
                    ) state else state.copy(
                        channels = visibleKnownChannels(),
                        channelDetail = state.channelDetail.copy(
                            videos = if (tab == ChannelContentTab.Videos) {
                                (state.channelDetail.videos + videos).distinctBy(VideoUiModel::id)
                            } else state.channelDetail.videos,
                            shorts = if (tab == ChannelContentTab.Shorts) {
                                (state.channelDetail.shorts + videos).distinctBy(VideoUiModel::id)
                            } else state.channelDetail.shorts,
                            liveStreams = if (tab == ChannelContentTab.Live) {
                                (state.channelDetail.liveStreams + videos)
                                    .distinctBy(VideoUiModel::id)
                            } else state.channelDetail.liveStreams,
                            playlists = if (tab == ChannelContentTab.Playlists) {
                                (state.channelDetail.playlists + page.playlists).distinctBy(PlaylistUiModel::id)
                            } else state.channelDetail.playlists,
                            isLoadingMore = false,
                            continuationIds = state.channelDetail.continuationIds
                                .toMutableMap()
                                .apply {
                                    if (page.continuationId == null) remove(tab)
                                    else put(tab, page.continuationId)
                                },
                            tabsWithMore = if (page.hasMore) {
                                state.channelDetail.tabsWithMore + tab
                            } else state.channelDetail.tabsWithMore - tab,
                            continuationId = page.continuationId,
                            hasMore = page.hasMore,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.channelDetail.channelId != channelId) state else state.copy(
                        channelDetail = state.channelDetail.copy(
                            isLoadingMore = false,
                            errorMessage = error.localizedMessage ?: text(R.string.creator_load_failed),
                        ),
                    )
                }
            }
        }
    }

    fun loadRemotePlaylist(playlist: PlaylistUiModel) {
        if (playlist.sourceId.isBlank()) return
        val current = _uiState.value.remotePlaylistDetail
        if (current.playlist?.id == playlist.id && (current.isLoading || current.videos.isNotEmpty())) return
        remotePlaylistJob?.cancel()
        remotePlaylistPagingJob?.cancel()
        _uiState.update {
            it.copy(
                remotePlaylistDetail = RemotePlaylistDetailUiState(
                    playlist = playlist,
                    isLoading = true,
                ),
            )
        }
        remotePlaylistJob = viewModelScope.launch {
            try {
                val details = engine.loadPlaylist(playlist)
                val videos = details.videos.map { it.withPersistedLibraryState() }
                videos.forEach { remoteVideos[it.id] = it }
                videos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    if (state.remotePlaylistDetail.playlist?.id != playlist.id) state else state.copy(
                        channels = visibleKnownChannels(),
                        remotePlaylistDetail = RemotePlaylistDetailUiState(
                            playlist = details.playlist.copy(videoIds = videos.map(VideoUiModel::id)),
                            videos = videos,
                            continuationId = details.continuationId,
                            hasMore = details.hasMore,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "Loading remote playlist ${playlist.id} failed.", error)
                _uiState.update { state ->
                    if (state.remotePlaylistDetail.playlist?.id != playlist.id) state else state.copy(
                        remotePlaylistDetail = state.remotePlaylistDetail.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: text(R.string.playlist_load_failed),
                        ),
                    )
                }
            }
        }
    }

    fun loadMoreRemotePlaylist() {
        val current = _uiState.value.remotePlaylistDetail
        val playlistId = current.playlist?.id ?: return
        val continuationId = current.continuationId ?: return
        if (!current.hasMore || current.isLoading || current.isLoadingMore || current.isLoadingAll) return
        remotePlaylistPagingJob?.cancel()
        _uiState.update {
            it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingMore = true))
        }
        remotePlaylistPagingJob = viewModelScope.launch {
            try {
                appendRemotePlaylistPage(playlistId, continuationId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "Loading another page for remote playlist $playlistId failed.", error)
                _uiState.update { state ->
                    if (state.remotePlaylistDetail.playlist?.id != playlistId) state else state.copy(
                        remotePlaylistDetail = state.remotePlaylistDetail.copy(
                            isLoadingMore = false,
                            errorMessage = error.localizedMessage ?: text(R.string.playlist_load_failed),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun appendRemotePlaylistPage(
        playlistId: String,
        continuationId: String,
    ): RemotePlaylistDetailUiState {
        val page = engine.loadMoreChannel(continuationId)
        val videos = page.videos.map { it.withPersistedLibraryState() }
        videos.forEach { remoteVideos[it.id] = it }
        videos.forEach(::registerRemoteChannel)
        var updated = _uiState.value.remotePlaylistDetail
        _uiState.update { state ->
            if (state.remotePlaylistDetail.playlist?.id != playlistId) state else {
                val combined = (state.remotePlaylistDetail.videos + videos)
                    .distinctBy(VideoUiModel::id)
                updated = state.remotePlaylistDetail.copy(
                    playlist = requireNotNull(state.remotePlaylistDetail.playlist).copy(
                        videoIds = combined.map(VideoUiModel::id),
                    ),
                    videos = combined,
                    isLoadingMore = false,
                    continuationId = page.continuationId,
                    hasMore = page.hasMore,
                    errorMessage = null,
                )
                state.copy(
                    channels = visibleKnownChannels(),
                    remotePlaylistDetail = updated,
                )
            }
        }
        return updated
    }

    private suspend fun fullyLoadRemotePlaylist(playlistId: String): List<VideoUiModel> {
        var current = _uiState.value.remotePlaylistDetail
        while (current.playlist?.id == playlistId && current.hasMore) {
            val continuationId = current.continuationId ?: break
            current = appendRemotePlaylistPage(playlistId, continuationId)
        }
        return current.videos
    }

    fun createLocalPlaylistFromRemote(title: String) {
        val playlist = _uiState.value.remotePlaylistDetail.playlist ?: return
        if (_uiState.value.remotePlaylistDetail.isLoadingAll) return
        remotePlaylistPagingJob?.cancel()
        remotePlaylistJob = viewModelScope.launch {
            _uiState.update {
                it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = true))
            }
            try {
                val videos = fullyLoadRemotePlaylist(playlist.id)
                if (videos.isNotEmpty()) {
                    val resolvedTitle = uniqueRemotePlaylistTitle(
                        requestedTitle = title.trim().ifBlank { playlist.title },
                        channelName = videos.firstOrNull()?.creator.orEmpty(),
                        existingTitles = libraryRepository.loadPlaylists().map(PlaylistUiModel::title),
                        fallbackTitle = text(R.string.imported_playlist),
                    )
                    if (libraryRepository.createPlaylist(resolvedTitle, videos) != null) {
                        reloadLibrary()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        remotePlaylistDetail = it.remotePlaylistDetail.copy(
                            errorMessage = error.localizedMessage ?: text(R.string.playlist_load_failed),
                        ),
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = false))
                }
            }
        }
    }

    fun downloadRemotePlaylist(mediaType: DownloadMediaType) {
        val playlist = _uiState.value.remotePlaylistDetail.playlist ?: return
        if (_uiState.value.remotePlaylistDetail.isLoadingAll) return
        remotePlaylistPagingJob?.cancel()
        _uiState.update {
            it.copy(
                remotePlaylistDetail = it.remotePlaylistDetail.copy(
                    activeDownloadMediaTypes =
                        it.remotePlaylistDetail.activeDownloadMediaTypes + mediaType,
                ),
            )
        }
        remotePlaylistJob = viewModelScope.launch {
            _uiState.update {
                it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = true))
            }
            try {
                val videos = fullyLoadRemotePlaylist(playlist.id)
                downloadVideos(videos.filter(VideoUiModel::supportsOfflineDownload).map(VideoUiModel::id), mediaType)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        remotePlaylistDetail = it.remotePlaylistDetail.copy(
                            errorMessage = error.localizedMessage ?: text(R.string.playlist_load_failed),
                        ),
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = false))
                }
            }
        }
    }

    fun cancelRemotePlaylistDownload(mediaType: DownloadMediaType) {
        val detail = _uiState.value.remotePlaylistDetail
        if (mediaType !in detail.activeDownloadMediaTypes) return
        if (detail.isLoadingAll) remotePlaylistJob?.cancel()
        _uiState.update {
            it.copy(
                remotePlaylistDetail = it.remotePlaylistDetail.copy(
                    isLoadingAll = false,
                    activeDownloadMediaTypes =
                        it.remotePlaylistDetail.activeDownloadMediaTypes - mediaType,
                ),
            )
        }
        val locallyOwned = offlinePlaylistStore.all(activeProfileId)
            .filter { it.mediaType == mediaType }
            .flatMapTo(mutableSetOf()) { it.managedVideoIds - it.excludedVideoIds }
        detail.videos
            .asSequence()
            .filter(VideoUiModel::supportsOfflineDownload)
            .filterNot { video ->
                _uiState.value.downloads[video.id]?.isComplete(mediaType) == true
            }
            .filterNot { it.id in locallyOwned }
            .forEach { performDownloadRemoval(it.id, mediaType) }
        syncDownloadState()
    }

    fun playRemotePlaylist() {
        val playlist = _uiState.value.remotePlaylistDetail.playlist ?: return
        if (_uiState.value.remotePlaylistDetail.isLoadingAll) return
        remotePlaylistPagingJob?.cancel()
        remotePlaylistJob = viewModelScope.launch {
            _uiState.update {
                it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = true))
            }
            try {
                val videos = fullyLoadRemotePlaylist(playlist.id)
                activePlaylistId = null
                startQueue(videos.map(VideoUiModel::id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        remotePlaylistDetail = it.remotePlaylistDetail.copy(
                            errorMessage = error.localizedMessage ?: text(R.string.playlist_load_failed),
                        ),
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = false))
                }
            }
        }
    }

    fun playRemotePlaylistFrom(videoId: String) {
        val playlist = _uiState.value.remotePlaylistDetail.playlist ?: return
        if (_uiState.value.remotePlaylistDetail.isLoadingAll) return
        remotePlaylistPagingJob?.cancel()
        remotePlaylistJob = viewModelScope.launch {
            _uiState.update {
                it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = true))
            }
            try {
                val videos = fullyLoadRemotePlaylist(playlist.id)
                val queueIds = playlistQueueFrom(videos.map(VideoUiModel::id), videoId)
                if (queueIds.isNotEmpty()) {
                    activePlaylistId = null
                    startQueue(queueIds)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        remotePlaylistDetail = it.remotePlaylistDetail.copy(
                            errorMessage = error.localizedMessage ?: text(R.string.playlist_load_failed),
                        ),
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(remotePlaylistDetail = it.remotePlaylistDetail.copy(isLoadingAll = false))
                }
            }
        }
    }

    fun seekPlayback(fraction: Float) {
        val cast = chromecastManager.state.value
        if (cast.isConnected && cast.durationMs > 0L) {
            chromecastManager.seekTo((cast.durationMs * fraction.coerceIn(0f, 1f)).toLong())
        } else {
            engine.seekToFraction(fraction)
        }
        _uiState.value.playback.currentVideoId?.let { setWatchProgress(it, fraction) }
    }

    fun resumePlaybackFromHistory() {
        val fraction = _uiState.value.nowPlaying.resumePositionFraction ?: return
        seekPlayback(fraction)
        dismissResumePrompt()
    }

    fun dismissResumePrompt() {
        resumePromptJob?.cancel()
        resumePromptJob = null
        _uiState.update {
            it.copy(nowPlaying = it.nowPlaying.copy(resumePositionFraction = null))
        }
    }

    private fun scheduleResumePromptDismiss(videoId: String, fraction: Float?) {
        resumePromptJob?.cancel()
        resumePromptJob = null
        if (fraction == null) return
        resumePromptJob = viewModelScope.launch {
            delay(10_000)
            _uiState.update { state ->
                if (
                    state.nowPlaying.video?.id != videoId ||
                    state.nowPlaying.resumePositionFraction != fraction
                ) state else state.copy(
                    nowPlaying = state.nowPlaying.copy(resumePositionFraction = null),
                )
            }
            resumePromptJob = null
        }
    }

    fun closePlayback() {
        endSpeedHold()
        suppressChromecastHandoff = chromecastManager.state.value.isConnected
        chromecastManager.disconnect(stopRemotePlayback = true)
        resumePromptJob?.cancel()
        resumePromptJob = null
        invalidatePlaybackQueue()
        pendingPlaybackVideoId = null
        activePlaylistId = null
        persistCurrentPlaybackProgress()
        engine.closePlayback()
        detailsJob?.cancel()
        storyboardJob?.cancel()
        _uiState.update { it.copy(nowPlaying = NowPlayingUiState()) }
    }

    fun setSearchQuery(query: String) {
        searchJob?.cancel()
        suggestionJob?.cancel()
        val normalizedQuery = query.take(160)
        if (normalizedQuery.isBlank()) {
            _uiState.update { it.copy(search = SearchUiState(query = normalizedQuery)) }
            return
        }

        _uiState.update {
            it.copy(
                search = SearchUiState(
                    query = normalizedQuery,
                    isLoadingSuggestions = true,
                ),
            )
        }
        suggestionJob = viewModelScope.launch {
            delay(120)
            try {
                val localSuggestions = if (preferences.searchHistoryEnabled) {
                    preferences.searchHistory().filter {
                        it.contains(normalizedQuery, ignoreCase = true)
                    }
                } else {
                    emptyList()
                }
                val suggestions = (localSuggestions + engine.suggestions(normalizedQuery, enabledSourceIds))
                    .distinctBy(String::lowercase)
                    .take(12)
                _uiState.update { state ->
                    if (state.search.query != normalizedQuery || state.search.hasSearched) state
                    else state.copy(
                        search = state.search.copy(
                            suggestions = suggestions,
                            isLoadingSuggestions = false,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.update { state ->
                    if (state.search.query != normalizedQuery) state else state.copy(
                        search = state.search.copy(isLoadingSuggestions = false),
                    )
                }
            }
        }
    }

    fun submitSearch(
        query: String,
        type: SearchContentType = SearchContentType.Videos,
        selectedSourceIds: Set<String> = enabledSourceIds,
    ) {
        val normalizedQuery = query.trim().take(160)
        if (normalizedQuery.isBlank()) return
        if (!_uiState.value.privateSessionEnabled) preferences.addSearchHistory(normalizedQuery)
        val searchSourceIds = selectedSourceIds.intersect(enabledSourceIds)
        if (searchSourceIds.isEmpty()) {
            _uiState.update {
                it.copy(
                    search = SearchUiState(
                        query = normalizedQuery,
                        hasSearched = true,
                        errorMessage = "Select at least one active source.",
                    ),
                )
            }
            return
        }
        suggestionJob?.cancel()
        searchJob?.cancel()
        searchPagingJob?.cancel()
        _uiState.update {
            it.copy(
                search = it.search.copy(
                    query = normalizedQuery,
                    suggestions = emptyList(),
                    isLoadingSuggestions = false,
                    isLoading = true,
                    hasSearched = false,
                    videos = emptyList(),
                    channels = emptyList(),
                    playlists = emptyList(),
                    errorMessage = null,
                ),
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val result = engine.search(
                    query = normalizedQuery,
                    enabledSourceIds = searchSourceIds,
                    corpus = SearchCorpus(
                        videos = allVideos,
                        channels = content.channels,
                        playlists = content.playlists,
                    ),
                    type = type,
                )
                val resultVideos = result.videos.map { it.withPersistedLibraryState() }
                resultVideos.forEach { remoteVideos[it.id] = it }
                resultVideos.forEach(::registerRemoteChannel)
                val resultChannels = result.channels.distinctBy(ChannelUiModel::id)
                _uiState.update { state ->
                    if (state.search.query != normalizedQuery) state else state.copy(
                        channels = visibleKnownChannels(),
                        search = SearchUiState(
                            query = normalizedQuery,
                            isLoading = false,
                            hasSearched = true,
                            videos = resultVideos,
                            channels = resultChannels,
                            playlists = result.playlists,
                            continuationId = result.continuationId,
                            hasMore = result.hasMore,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.search.query != normalizedQuery) state else state.copy(
                        search = state.search.copy(
                            isLoading = false,
                            hasSearched = true,
                            errorMessage = error.localizedMessage ?: text(R.string.search_failed),
                        ),
                    )
                }
            }
        }
    }

    fun loadMoreSearch() {
        val current = _uiState.value.search
        val continuationId = current.continuationId
        if (!current.hasMore || current.isLoading || current.isLoadingMore || continuationId == null) return
        searchPagingJob?.cancel()
        _uiState.update { it.copy(search = it.search.copy(isLoadingMore = true)) }
        searchPagingJob = viewModelScope.launch {
            try {
                val result = engine.loadMoreSearch(continuationId)
                val videos = result.videos.map { it.withPersistedLibraryState() }
                videos.forEach { remoteVideos[it.id] = it }
                videos.forEach(::registerRemoteChannel)
                result.channels.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    if (state.search.continuationId != continuationId) state else state.copy(
                        channels = visibleKnownChannels(),
                        search = state.search.copy(
                            videos = (state.search.videos + videos).distinctBy(VideoUiModel::id),
                            channels = (state.search.channels + result.channels)
                                .distinctBy(ChannelUiModel::id),
                            playlists = (state.search.playlists + result.playlists)
                                .distinctBy { it.id },
                            isLoadingMore = false,
                            continuationId = result.continuationId,
                            hasMore = result.hasMore,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.search.continuationId != continuationId) state else state.copy(
                        search = state.search.copy(
                            isLoadingMore = false,
                            errorMessage = error.localizedMessage ?: text(R.string.search_failed),
                        ),
                    )
                }
            }
        }
    }

    fun toggleWatchLater(videoId: String) {
        val video = findVideo(videoId) ?: return
        val enabled = !video.isWatchLater
        libraryRepository.saveVideo(video)
        libraryRepository.setWatchLater(videoId, enabled)
        _uiState.update { state ->
            updateVideoEverywhere(state, videoId) { it.copy(isWatchLater = enabled) }
                .copy(libraryVideos = libraryRepository.loadSavedVideos())
        }
    }

    fun toggleDownloaded(videoId: String) {
        toggleDownloadType(videoId, DownloadMediaType.Video)
    }

    fun toggleAudioDownloaded(videoId: String) {
        toggleDownloadType(videoId, DownloadMediaType.Audio)
    }

    fun removeDownloads(videoIds: List<String>) {
        videoIds.distinct().forEach { videoId ->
            DownloadMediaType.entries.forEach { mediaType ->
                offlinePlaylistStore.excludeVideo(activeProfileId, videoId, mediaType)
                removeDownloadType(videoId, mediaType)
            }
        }
    }

    fun exportDownloads(
        videoIds: List<String>,
        mediaType: DownloadMediaType,
        directoryUri: Uri,
    ) {
        val profileAtStart = activeProfileId
        val videos = videoIds.distinct().mapNotNull(::findVideo)
        if (videos.size != videoIds.distinct().size) return
        viewModelScope.launch {
            try {
                val exported = downloadExporter.export(
                    profileId = profileAtStart,
                    videos = videos,
                    mediaType = mediaType,
                    directoryUri = directoryUri,
                )
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.downloads_exported, exported),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("GrayjayViewModel", "Exporting downloads failed.", error)
                Toast.makeText(
                    getApplication(),
                    R.string.downloads_export_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** Starts or retries a video download using either the app default or an explicit height. */
    fun downloadVideo(videoId: String, targetVideoHeight: Int?) {
        if (findVideo(videoId)?.isLive != false) return
        val current = _uiState.value.downloads[videoId]
        if (
            current?.isComplete(DownloadMediaType.Video) == true ||
            current?.isActive(DownloadMediaType.Video) == true
        ) return
        startDownload(
            videoId = videoId,
            mediaType = DownloadMediaType.Video,
            replaceExisting = DownloadMediaType.Video in current?.failedMediaTypes.orEmpty(),
            targetVideoHeight = targetVideoHeight,
        )
    }

    /** Starts or retries an audio download using either the app default or an explicit bitrate. */
    fun downloadAudio(videoId: String, targetAudioBitrate: Int?) {
        if (findVideo(videoId)?.isLive != false) return
        val current = _uiState.value.downloads[videoId]
        if (
            current?.isComplete(DownloadMediaType.Audio) == true ||
            current?.isActive(DownloadMediaType.Audio) == true
        ) return
        startDownload(
            videoId = videoId,
            mediaType = DownloadMediaType.Audio,
            replaceExisting = DownloadMediaType.Audio in current?.failedMediaTypes.orEmpty(),
            targetAudioBitrate = targetAudioBitrate,
        )
    }

    private fun toggleDownloadType(videoId: String, mediaType: DownloadMediaType) {
        val currentDownload = _uiState.value.downloads[videoId]
        if (
            currentDownload?.isComplete(mediaType) == true ||
            currentDownload?.isActive(mediaType) == true
        ) {
            offlinePlaylistStore.excludeVideo(activeProfileId, videoId, mediaType)
            removeDownloadType(videoId, mediaType, currentDownload)
            return
        }
        if (findVideo(videoId)?.isLive != false) return

        startDownload(
            videoId = videoId,
            mediaType = mediaType,
            replaceExisting = mediaType in currentDownload?.failedMediaTypes.orEmpty(),
        )
    }

    private fun removeDownloadType(
        videoId: String,
        mediaType: DownloadMediaType,
        currentDownload: DownloadUiModel? = _uiState.value.downloads[videoId],
    ) {
        val state = _uiState.value
        val playingVideo = state.nowPlaying.video
        val removesActiveOfflineSource =
            playingVideo?.id == videoId &&
                state.playback.currentVideoId == videoId &&
                playingVideo.playbackFromDownload &&
                (
                    mediaType == DownloadMediaType.Video ||
                        currentDownload?.isComplete(DownloadMediaType.Video) != true
                    )
        if (removesActiveOfflineSource) {
            val profileAtStart = activeProfileId
            val resumePositionMs = state.playback.positionMs
            val resumePlaying = state.playback.isPlaying
            viewModelScope.launch {
                try {
                    val online = resolveWithAudioPreferences(playingVideo.onlinePlaybackInput())
                    if (
                        profileAtStart == activeProfileId &&
                        _uiState.value.playback.currentVideoId == videoId
                    ) {
                        remoteVideos[online.id] = online
                        registerRemoteChannel(online)
                        openLocallyOrCast(
                            video = online,
                            playWhenReady = resumePlaying,
                            castPositionMs = resumePositionMs,
                        )
                        if (
                            resumePositionMs > 0L &&
                            !chromecastManager.state.value.isConnected
                        ) engine.seekBy(resumePositionMs)
                        _uiState.update { current ->
                            if (current.nowPlaying.video?.id != videoId) current else current.copy(
                                nowPlaying = current.nowPlaying.copy(video = online),
                            )
                        }
                    }
                    performDownloadRemoval(videoId, mediaType, currentDownload)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    // Keep the complete offline item intact if a safe online hand-off cannot be
                    // prepared. Deleting underneath the active cache-only source would strand the
                    // player on PlaceholderDataSource.
                    _uiState.update { current ->
                        if (current.nowPlaying.video?.id != videoId) current else current.copy(
                            nowPlaying = current.nowPlaying.copy(
                                errorMessage = error.localizedMessage ?: text(R.string.download_failed),
                            ),
                        )
                    }
                }
            }
            return
        }
        performDownloadRemoval(videoId, mediaType, currentDownload)
    }

    private fun performDownloadRemoval(
        videoId: String,
        mediaType: DownloadMediaType,
        currentDownload: DownloadUiModel? = _uiState.value.downloads[videoId],
    ) {
        val jobKey = downloadJobKey(activeProfileId, videoId, mediaType)
        downloadJobs.remove(jobKey)?.cancel()
        downloadPreparationStates.remove(jobKey)
        downloadQueue.remove(activeProfileId, videoId, mediaType)
        if (!downloadStore.hasDownloadRecord(activeProfileId, videoId, mediaType)) {
            syncDownloadState()
            return
        }
        downloadStore.remove(activeProfileId, videoId, mediaType)
        currentDownload?.let { download ->
            _uiState.update { state ->
                state.copy(
                    downloads = state.downloads + (
                        videoId to download.copy(
                            status = DownloadStatus.Removing,
                            mediaType = mediaType,
                            activeMediaTypes = download.activeMediaTypes + mediaType,
                        )
                    ),
                )
            }
        }
    }

    private fun VideoUiModel.onlinePlaybackInput(): VideoUiModel = copy(
        isDownloaded = false,
        playbackFromDownload = false,
        playbackAudioOnly = false,
        playbackCacheNamespace = "",
        audioCacheNamespace = "",
        playbackStreamKeys = emptyList(),
        audioStreamKeys = emptyList(),
        playbackUrl = "",
        playbackMimeType = "",
        playbackManifest = "",
        audioUrl = "",
        audioRequestHeaders = emptyMap(),
        audioDataSourceFactory = null,
        playbackRequestHeaders = emptyMap(),
        playbackDataSourceFactory = null,
        subtitleTracks = emptyList(),
        qualityVariants = emptyList(),
        audioQualityVariants = emptyList(),
    )

    fun downloadVideos(videoIds: List<String>, mediaType: DownloadMediaType) {
        val profileAtStart = activeProfileId
        val repositoryAtStart = libraryRepository
        val candidates = videoIds.distinct().mapNotNull { videoId ->
            val video = findVideo(videoId)?.takeIf(VideoUiModel::supportsOfflineDownload)
                ?: return@mapNotNull null
            val existing = _uiState.value.downloads[videoId]
            if (existing?.isComplete(mediaType) == true || existing?.isActive(mediaType) == true) {
                null
            } else {
                video to (mediaType in existing?.failedMediaTypes.orEmpty())
            }
        }
        if (candidates.isEmpty()) return

        val batchCreatedAtMs = System.currentTimeMillis()
        val queuedDownloads = candidates.mapIndexed { index, (video, _) ->
            QueuedDownload(
                profileId = profileAtStart,
                videoId = video.id,
                mediaType = mediaType,
                status = DownloadStatus.Queued,
                createdAtMs = batchCreatedAtMs + index,
                targetVideoHeight = preferences.preferredVideoQuality.takeIf {
                    mediaType == DownloadMediaType.Video && it > 0
                },
                targetAudioBitrate = preferences.preferredAudioBitrate.takeIf {
                    mediaType == DownloadMediaType.Audio
                },
            )
        }

        // Persist the compact queue once up front so cancellation cannot race a late bulk write.
        downloadQueue.putAll(queuedDownloads)
        val videoPersistence = viewModelScope.async(Dispatchers.IO) {
            // A playlist can contain hundreds of videos. Serialize its library metadata once,
            // off the UI thread, instead of rewriting the complete library once per item.
            repositoryAtStart.saveVideos(candidates.map { it.first })
        }
        candidates.zip(queuedDownloads).forEach { (candidate, queued) ->
            val (video, replaceExisting) = candidate
            startDownload(
                videoId = video.id,
                mediaType = mediaType,
                replaceExisting = replaceExisting,
                restored = queued,
                initialVideoPersistence = videoPersistence,
            )
        }
        // Publish the whole batch in one StateFlow update. Repeating this for every playlist
        // item was the remaining source of "Grayjoy isn't responding" dialogs.
        syncDownloadState()
    }

    fun downloadPlaylist(playlistId: String, mediaType: DownloadMediaType) {
        val playlist = libraryRepository.loadPlaylists().firstOrNull { it.id == playlistId }
            ?: return
        val downloadableIds = playlist.videoIds.filter {
            findVideo(it)?.supportsOfflineDownload() == true
        }
        offlinePlaylistStore.register(
            profileId = activeProfileId,
            playlistId = playlistId,
            mediaType = mediaType,
            videoIds = downloadableIds,
            targetVideoHeight = preferences.preferredVideoQuality.takeIf {
                mediaType == DownloadMediaType.Video && it > 0
            },
        )
        syncDownloadState()
        downloadVideos(downloadableIds, mediaType)
    }

    fun cancelPlaylistDownload(playlistId: String, mediaType: DownloadMediaType) {
        val cancelled = offlinePlaylistStore.remove(activeProfileId, playlistId, mediaType)
            ?: return
        pendingPlaylistCancellationIds(
            cancelled = cancelled,
            remainingDescriptors = offlinePlaylistStore.all(activeProfileId),
            downloads = _uiState.value.downloads,
        )
            .asSequence()
            .forEach { videoId -> performDownloadRemoval(videoId, mediaType) }
        syncDownloadState()
    }

    private fun scheduleOfflinePlaylistSync() {
        offlinePlaylistSyncJob?.cancel()
        val profileAtStart = activeProfileId
        offlinePlaylistSyncJob = viewModelScope.launch {
            delay(STARTUP_BACKGROUND_WORK_DELAY_MS)
            withContext(Dispatchers.IO) { downloadStore }
            while (!downloadStore.isInitialized()) delay(100L)
            if (profileAtStart != activeProfileId) return@launch

            val playlists = libraryRepository.loadPlaylists().associateBy { it.id }
            val descriptors = offlinePlaylistStore.all(profileAtStart)
            val currentOwners = mutableMapOf<DownloadMediaType, MutableMap<String, Int>>()
            val removedCandidates = mutableListOf<Pair<String, DownloadMediaType>>()
            val downloadCandidates = mutableListOf<Pair<String, DownloadMediaType>>()

            descriptors.forEach { descriptor ->
                val playlist = playlists[descriptor.playlistId]
                if (playlist == null) {
                    descriptor.managedVideoIds.forEach {
                        removedCandidates += it to descriptor.mediaType
                    }
                    offlinePlaylistStore.remove(descriptor)
                    return@forEach
                }
                val currentIds = playlist.videoIds
                    .filter { findVideo(it)?.supportsOfflineDownload() == true }
                    .toSet()
                (descriptor.managedVideoIds - currentIds).forEach {
                    removedCandidates += it to descriptor.mediaType
                }
                val updated = descriptor.copy(
                    managedVideoIds = currentIds,
                    excludedVideoIds = descriptor.excludedVideoIds.intersect(currentIds),
                )
                offlinePlaylistStore.update(updated)
                val owners = currentOwners.getOrPut(updated.mediaType) { mutableMapOf() }
                currentIds.forEach { videoId -> owners[videoId] = owners.getOrDefault(videoId, 0) + 1 }
                (currentIds - updated.excludedVideoIds).forEach {
                    downloadCandidates += it to updated.mediaType
                }
            }

            removedCandidates.distinct().forEach { (videoId, mediaType) ->
                if ((currentOwners[mediaType]?.get(videoId) ?: 0) == 0) {
                    removeDownloadType(videoId, mediaType)
                }
            }
            downloadCandidates.distinct().forEach { (videoId, mediaType) ->
                val current = _uiState.value.downloads[videoId]
                if (current?.isComplete(mediaType) != true && current?.isActive(mediaType) != true) {
                    startDownload(
                        videoId = videoId,
                        mediaType = mediaType,
                        replaceExisting = mediaType in current?.failedMediaTypes.orEmpty(),
                        targetVideoHeight = descriptors.firstOrNull {
                            it.mediaType == mediaType &&
                                videoId in it.managedVideoIds &&
                                videoId !in it.excludedVideoIds
                        }?.targetVideoHeight,
                    )
                }
            }
        }
    }

    private fun restoreDownloadQueue() {
        downloadQueueRestoreJob?.cancel()
        val profileAtStart = activeProfileId
        downloadQueueRestoreJob = viewModelScope.launch {
            delay(STARTUP_BACKGROUND_WORK_DELAY_MS)
            libraryLoadJob?.join()
            withContext(Dispatchers.IO) { downloadStore }
            while (!downloadStore.isInitialized()) delay(100L)
            if (profileAtStart != activeProfileId) return@launch
            val unsupportedDownloadIds = downloadStore.snapshotsFor(profileAtStart)
                .keys
                .filter { videoId ->
                    findVideo(videoId)?.supportsOfflineDownload() == false
                }
            if (unsupportedDownloadIds.isNotEmpty()) {
                unsupportedDownloadIds.forEach { videoId ->
                    downloadStore.remove(profileAtStart, videoId)
                    DownloadMediaType.entries.forEach { mediaType ->
                        downloadQueue.remove(profileAtStart, videoId, mediaType)
                    }
                    libraryRepository.clearDownloadDescriptor(videoId)
                }
                reloadLibrary()
            }
            val recoveryCandidates = downloadStore.recoveryCandidates(profileAtStart)
            val recoveryKeys = recoveryCandidates
                .mapTo(mutableSetOf()) { it.videoId to it.mediaType }
            val persistedQueue = downloadQueue.all(profileAtStart)
                .associateBy { it.videoId to it.mediaType }

            // Media3 persists the request and partial cache, but a plugin's JS transport lives only
            // in memory. Re-resolve those requests after every process/device restart and replace
            // the same deterministic request IDs so completed parts and cached ranges survive.
            recoveryCandidates.forEach { candidate ->
                val video = findVideo(candidate.videoId) ?: return@forEach
                val queued = persistedQueue[candidate.videoId to candidate.mediaType]
                    ?: QueuedDownload(
                        profileId = profileAtStart,
                        videoId = candidate.videoId,
                        mediaType = candidate.mediaType,
                        status = DownloadStatus.Queued,
                        createdAtMs = candidate.preparedAtMs?.takeIf { it > 0L }
                            ?: System.currentTimeMillis(),
                        targetVideoHeight = candidate.targetVideoHeight,
                        targetAudioBitrate = candidate.targetAudioBitrate,
                    ).also(downloadQueue::put)
                downloadPreparationStates[
                    downloadJobKey(profileAtStart, queued.videoId, queued.mediaType)
                ] = candidate.copy(
                    status = DownloadStatus.Queued,
                    errorMessage = null,
                    activeMediaTypes = setOf(queued.mediaType),
                    failedMediaTypes = candidate.failedMediaTypes - queued.mediaType,
                )
                startDownload(
                    videoId = video.id,
                    mediaType = queued.mediaType,
                    replaceExisting = false,
                    restored = queued,
                )
            }

            persistedQueue.values.forEach { queued ->
                if (queued.videoId to queued.mediaType in recoveryKeys) return@forEach
                if (downloadStore.hasDownloadRecord(
                        profileAtStart,
                        queued.videoId,
                        queued.mediaType,
                    )
                ) {
                    // The process stopped after handing the transfer to Media3 but before
                    // removing its pre-transfer row. Media3 owns it from this point onward.
                    downloadQueue.remove(profileAtStart, queued.videoId, queued.mediaType)
                    return@forEach
                }
                val video = findVideo(queued.videoId) ?: return@forEach
                downloadPreparationStates[
                    downloadJobKey(queued.profileId, queued.videoId, queued.mediaType)
                ] = DownloadUiModel(
                    profileId = queued.profileId,
                    videoId = queued.videoId,
                    mediaType = queued.mediaType,
                    status = DownloadStatus.Queued,
                    errorMessage = null,
                    activeMediaTypes = setOf(queued.mediaType),
                    targetVideoHeight = queued.targetVideoHeight,
                    targetAudioBitrate = queued.targetAudioBitrate,
                )
                startDownload(
                    videoId = video.id,
                    mediaType = queued.mediaType,
                    replaceExisting = false,
                    restored = queued,
                )
            }
            // Restoring many unfinished transfers used to refresh and persist the complete
            // library once per item. A large imported library could therefore monopolize the
            // main thread for several seconds and trigger an ANR during app startup.
            syncDownloadState()
        }
    }

    private fun startDownload(
        videoId: String,
        mediaType: DownloadMediaType,
        replaceExisting: Boolean = false,
        restored: QueuedDownload? = null,
        targetVideoHeight: Int? = null,
        targetAudioBitrate: Int? = null,
        initialVideoPersistence: Deferred<Unit>? = null,
    ) {
        val video = findVideo(videoId) ?: return
        val profileAtStart = activeProfileId
        val repositoryAtStart = libraryRepository
        val isRestoredDownload = restored != null
        val jobKey = downloadJobKey(profileAtStart, videoId, mediaType)
        if (downloadJobs[jobKey]?.isActive == true) return
        val createdAtMs = restored?.createdAtMs ?: System.currentTimeMillis()
        val selectedVideoHeight = if (mediaType == DownloadMediaType.Video) {
            targetVideoHeight
                ?: restored?.targetVideoHeight
                ?: preferences.preferredVideoQuality.takeIf { it > 0 }
        } else {
            null
        }
        val selectedAudioBitrate = if (mediaType == DownloadMediaType.Audio) {
            targetAudioBitrate
                ?: restored?.targetAudioBitrate
                ?: preferences.preferredAudioBitrate
        } else {
            null
        }
        if (video.isLive) {
            downloadPreparationStates.remove(jobKey)
            downloadQueue.remove(activeProfileId, videoId, mediaType)
            syncDownloadState()
            return
        }

        downloadJobs.remove(jobKey)?.cancel()
        if (!isRestoredDownload) {
            downloadQueue.put(
                QueuedDownload(
                    profileId = activeProfileId,
                    videoId = videoId,
                    mediaType = mediaType,
                    status = DownloadStatus.Queued,
                    createdAtMs = createdAtMs,
                    targetVideoHeight = selectedVideoHeight,
                    targetAudioBitrate = selectedAudioBitrate,
                ),
            )
        }
        downloadPreparationStates[jobKey] = DownloadUiModel(
            profileId = activeProfileId,
            videoId = videoId,
            mediaType = mediaType,
            status = DownloadStatus.Queued,
            activeMediaTypes = setOf(mediaType),
            targetVideoHeight = selectedVideoHeight,
            targetAudioBitrate = selectedAudioBitrate,
        )
        if (!isRestoredDownload) syncDownloadState()
        downloadJobs[jobKey] = viewModelScope.launch {
            fun markWaitingForNetwork() {
                downloadPreparationStates[jobKey] = DownloadUiModel(
                    profileId = profileAtStart,
                    videoId = videoId,
                    mediaType = mediaType,
                    status = DownloadStatus.Paused,
                    activeMediaTypes = setOf(mediaType),
                    targetVideoHeight = selectedVideoHeight,
                    targetAudioBitrate = selectedAudioBitrate,
                )
                downloadQueue.put(
                    QueuedDownload(
                        profileId = profileAtStart,
                        videoId = videoId,
                        mediaType = mediaType,
                        status = DownloadStatus.Paused,
                        createdAtMs = createdAtMs,
                        targetVideoHeight = selectedVideoHeight,
                        targetAudioBitrate = selectedAudioBitrate,
                    ),
                )
                syncDownloadState()
            }

            try {
                initialVideoPersistence?.await()
                if (profileAtStart != activeProfileId) return@launch
                if (!isRestoredDownload) {
                    // Saving a video serializes the complete SharedPreferences-backed library.
                    // Keep that work away from Compose's main thread, especially after imports.
                    withContext(Dispatchers.IO) {
                        repositoryAtStart.saveVideo(video)
                    }
                    if (profileAtStart != activeProfileId) return@launch
                }
                var connectivityRetry = 0
                while (true) {
                    if (!networkMonitor.isAvailable()) {
                        markWaitingForNetwork()
                        networkMonitor.awaitAvailable()
                        if (profileAtStart != activeProfileId) return@launch
                    }
                    try {
                        downloadPreparationSemaphore.withPermit {
                            if (replaceExisting) {
                                downloadStore.remove(profileAtStart, videoId, mediaType)
                                while (downloadStore.hasDownloadRecord(profileAtStart, videoId, mediaType)) {
                                    delay(100L)
                                }
                            }
                            // A process restart leaves the same deterministic Media3 request in
                            // the index while its plugin transport is rebuilt. Waiting for every
                            // active request used to include that very request, permanently
                            // leaving it shown as Paused. Serialize against other transfers only.
                            while (
                                downloadStore.hasActiveTransfer(
                                    profileId = profileAtStart,
                                    excludingVideoId = videoId,
                                    excludingMediaType = mediaType,
                                )
                            ) {
                                delay(250L)
                            }
                            if (profileAtStart != activeProfileId) return@launch
                            downloadPreparationStates[jobKey] = downloadPreparationStates[jobKey]
                                ?.copy(status = DownloadStatus.Preparing)
                                ?: return@launch
                            downloadQueue.put(
                                QueuedDownload(
                                    profileId = profileAtStart,
                                    videoId = videoId,
                                    mediaType = mediaType,
                                    status = DownloadStatus.Preparing,
                                    createdAtMs = createdAtMs,
                                    targetVideoHeight = selectedVideoHeight,
                                    targetAudioBitrate = selectedAudioBitrate,
                                ),
                            )
                            syncDownloadState()
                            val freshVideo = video.copy(
                                playbackFromDownload = false,
                                playbackAudioOnly = false,
                                playbackCacheNamespace = "",
                                audioCacheNamespace = "",
                                playbackStreamKeys = emptyList(),
                                audioStreamKeys = emptyList(),
                                playbackUrl = "",
                                playbackMimeType = "",
                                playbackManifest = "",
                                audioUrl = "",
                                audioRequestHeaders = emptyMap(),
                                audioDataSourceFactory = null,
                                playbackRequestHeaders = emptyMap(),
                                playbackDataSourceFactory = null,
                                subtitleTracks = emptyList(),
                                qualityVariants = emptyList(),
                                audioQualityVariants = emptyList(),
                            )
                            val resolved = resolveWithAudioPreferences(
                                freshVideo,
                                priority = EngineResolvePriority.Download,
                            )
                            if (profileAtStart != activeProfileId) return@launch
                            val storedDescriptor = if (mediaType == DownloadMediaType.Audio) {
                                resolved.asAudioDownloadDescriptor(selectedAudioBitrate)
                            } else {
                                resolved.downloadDescriptor(selectedVideoHeight)
                            }
                            libraryRepository.saveDownloadDescriptor(storedDescriptor)
                            reloadLibrary()
                            downloadStore.enqueue(
                                profileId = profileAtStart,
                                video = storedDescriptor,
                                mediaType = mediaType,
                                preferredVideoHeight = selectedVideoHeight,
                                preferredAudioBitrate = selectedAudioBitrate,
                            )
                            while (!downloadStore.hasDownloadRecord(profileAtStart, videoId, mediaType)) {
                                delay(50L)
                            }
                            downloadQueue.remove(profileAtStart, videoId, mediaType)
                        }
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        if (networkMonitor.isAvailable() && !error.isRecoverableConnectivityFailure()) {
                            throw error
                        }
                        if (downloadStore.snapshotsFor(profileAtStart)[videoId] == null) {
                            libraryRepository.clearDownloadDescriptor(videoId)
                            reloadLibrary()
                        }
                        markWaitingForNetwork()
                        networkMonitor.awaitRecovery(connectivityRetry++)
                        if (profileAtStart != activeProfileId) return@launch
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (profileAtStart != activeProfileId) return@launch
                if (downloadStore.snapshotsFor(profileAtStart)[videoId] == null) {
                    libraryRepository.clearDownloadDescriptor(videoId)
                    reloadLibrary()
                }
                val errorMessage = when (error.message) {
                    "Live streams cannot be downloaded." -> text(R.string.live_download_unsupported)
                    "This source returned no downloadable media." -> text(R.string.download_no_media)
                    "This source returned no downloadable audio." -> text(R.string.download_no_audio)
                    "DRM-protected streams cannot be downloaded." ->
                        text(R.string.download_drm_unsupported)
                    else -> error.localizedMessage ?: text(R.string.download_failed)
                }
                downloadPreparationStates[jobKey] = DownloadUiModel(
                    profileId = profileAtStart,
                    videoId = videoId,
                    mediaType = mediaType,
                    status = DownloadStatus.Failed,
                    errorMessage = errorMessage,
                    failedMediaTypes = setOf(mediaType),
                    targetVideoHeight = selectedVideoHeight,
                    targetAudioBitrate = selectedAudioBitrate,
                )
                downloadQueue.put(
                    QueuedDownload(
                        profileId = profileAtStart,
                        videoId = videoId,
                        mediaType = mediaType,
                        status = DownloadStatus.Failed,
                        createdAtMs = createdAtMs,
                        targetVideoHeight = selectedVideoHeight,
                        targetAudioBitrate = selectedAudioBitrate,
                        errorMessage = errorMessage,
                    ),
                )
                syncDownloadState()
            } finally {
                downloadJobs.remove(jobKey)
            }
        }
    }

    fun toggleLiked(videoId: String) {
        val video = findVideo(videoId) ?: return
        val enabled = !video.isLiked
        libraryRepository.saveVideo(video)
        libraryRepository.setLiked(videoId, enabled)
        _uiState.update { state ->
            updateVideoEverywhere(state, videoId) { it.copy(isLiked = enabled) }
                .copy(libraryVideos = libraryRepository.loadSavedVideos())
        }
    }

    fun createPlaylist(title: String, videoIds: List<String>) {
        val videos = videoIds.distinct().mapNotNull(::findVideo)
        if (videos.isEmpty()) return
        val profileAtStart = activeProfileId
        val repositoryAtStart = libraryRepository
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                repositoryAtStart.createPlaylist(title, videos) ?: return@withContext null
                repositoryAtStart.loadSavedVideos() to repositoryAtStart.loadPlaylists()
            } ?: return@launch
            if (
                profileAtStart == activeProfileId &&
                repositoryAtStart === libraryRepository
            ) {
                applyLibrarySnapshot(snapshot.first, snapshot.second)
            }
        }
    }

    private fun playedOnPlaylistTitle(repository: LibraryRepository = libraryRepository): String {
        val formattedDate = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        ).format(Date())
        val base = text(R.string.played_on_date, formattedDate)
        val existing = repository.loadPlaylists().map(PlaylistUiModel::title)
        if (!playlistTitleExists(base, existing)) return base
        var suffix = 2
        while (playlistTitleExists("$base ($suffix)", existing)) suffix += 1
        return "$base ($suffix)"
    }

    fun renamePlaylist(playlistId: String, title: String) {
        libraryRepository.renamePlaylist(playlistId, title) ?: return
        reloadLibrary()
    }

    fun removePlaylists(playlistIds: List<String>) {
        val removedIds = playlistIds.distinct().toSet()
        if (libraryRepository.removePlaylists(removedIds) == 0) return
        // Deleting a collection must not also delete media the user explicitly downloaded.
        // Detach the automatic playlist-download records before a later sync sees the missing
        // playlist and treats all of its offline media as orphaned.
        offlinePlaylistStore.all(activeProfileId)
            .filter { it.playlistId in removedIds }
            .forEach(offlinePlaylistStore::remove)
        reloadLibrary()
    }

    fun addVideosToPlaylist(playlistId: String, videoIds: List<String>) {
        val videos = videoIds.distinct().mapNotNull(::findVideo)
        if (videos.isEmpty()) return
        val profileAtStart = activeProfileId
        val repositoryAtStart = libraryRepository
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val existingIds = repositoryAtStart.loadPlaylists()
                    .firstOrNull { it.id == playlistId }
                    ?.videoIds
                    ?.toSet()
                    ?: return@withContext null
                val addedVideos = videos.filterNot { it.id in existingIds }
                repositoryAtStart.addVideosToPlaylist(playlistId, videos)
                    ?: return@withContext null
                Triple(
                    addedVideos,
                    repositoryAtStart.loadSavedVideos(),
                    repositoryAtStart.loadPlaylists(),
                )
            } ?: return@launch
            if (
                profileAtStart != activeProfileId ||
                repositoryAtStart !== libraryRepository
            ) return@launch
            applyLibrarySnapshot(result.second, result.third)
            scheduleOfflinePlaylistSync()
            val session = playbackQueueSession
            if (
                playlistId == activePlaylistId &&
                session?.playlistId == playlistId &&
                session.generation == playbackGeneration &&
                result.first.isNotEmpty()
            ) {
                result.first.forEach { video ->
                    if (session.knownVideoIds.add(video.id)) {
                        session.pendingVideos += video
                        session.orderedVideoIds += video.id
                    }
                }
                publishLogicalQueue()
                prepareQueueLookAhead()
            }
        }
    }

    fun removeVideosFromPlaylist(playlistId: String, videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        libraryRepository.removeVideosFromPlaylist(playlistId, videoIds) ?: return
        reloadLibrary()
        scheduleOfflinePlaylistSync()
    }

    fun reorderPlaylist(playlistId: String, orderedVideoIds: List<String>) {
        libraryRepository.reorderPlaylist(playlistId, orderedVideoIds) ?: return
        reloadLibrary()
    }

    fun removeVideosFromHistory(videoIds: List<String>) {
        libraryRepository.removeFromHistory(videoIds)
        reloadLibrary()
    }

    fun setWatchProgress(videoId: String, progress: Float) {
        if (_uiState.value.privateSessionEnabled) return
        val normalizedProgress = progress.coerceIn(0f, 1f)
        _uiState.update { state ->
            if (state.privateSessionEnabled) state else {
                updateVideoEverywhere(state, videoId) {
                    it.copy(watchProgress = normalizedProgress)
                }
            }
        }

        // History can contain hundreds or thousands of entries. SharedPreferencesLibraryRepository
        // stores it as one JSON document, so parsing and serialising it from this method used to
        // stop the main thread every five seconds during playback. recordHistory() has already
        // ensured that the item exists; persist only the changed fraction, off the playback/UI
        // path, and collapse rapid seek updates into one write.
        val repositoryAtScheduleTime = libraryRepository
        watchProgressWriteJob?.cancel()
        watchProgressWriteJob = viewModelScope.launch(Dispatchers.IO) {
            delay(WATCH_PROGRESS_WRITE_DEBOUNCE_MS)
            repositoryAtScheduleTime.setWatchProgress(videoId, normalizedProgress)
        }
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        val source = _uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        if (source.availability == SourceAvailability.MissingPlugin) return

        sourceRepository.setEnabled(sourceId, enabled)
        enabledSourceIds = if (enabled) enabledSourceIds + sourceId else enabledSourceIds - sourceId
        val visibleContent = visibleContentForSources(
            videos = content.videos,
            channels = content.channels,
            playlists = content.playlists,
            enabledSourceIds = enabledSourceIds,
        )
        val shouldClosePlayback = _uiState.value.playback.currentVideoId?.let { currentId ->
            !enabled && findVideo(currentId)?.sourceId == sourceId
        } ?: false
        _uiState.update { state ->
            state.copy(
                videos = visibleContent.videos,
                channels = visibleKnownChannels(),
                sources = state.sources.map {
                    if (it.id == sourceId) it.copy(isEnabled = enabled) else it
                },
            )
        }
        if (shouldClosePlayback) engine.closePlayback()
        _uiState.value.search.let { search ->
            search.query.takeIf(String::isNotBlank)?.let { query ->
                if (search.hasSearched) submitSearch(query) else setSearchQuery(query)
            }
        }
        homeFeedCache.clear()
        refreshHome()
    }

    fun installSource(configUrl: String) {
        if (_uiState.value.sourceOperationInProgress) return
        _uiState.update {
            it.copy(
                sourceOperationInProgress = true,
                sourceOperationMessage = text(R.string.installing_source),
            )
        }
        viewModelScope.launch {
            try {
                completeSourceInstallation(engine.installSource(configUrl), announce = true)
            } catch (error: GrayjaySignatureMismatchException) {
                val plugin = error.plugin
                _uiState.update {
                    it.copy(
                        sourceOperationInProgress = false,
                        sourceOperationMessage = null,
                        sourceTrustRequest = SourceTrustRequestUiModel(
                            token = plugin.token,
                            pluginName = plugin.pluginName,
                            publisher = plugin.publisher,
                            publisherUrl = plugin.publisherUrl,
                            configUrl = plugin.configUrl,
                            publicKeyFingerprint = plugin.publicKeyFingerprint,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        sourceOperationInProgress = false,
                        sourceOperationMessage = error.localizedMessage
                            ?: text(R.string.source_install_failed),
                    )
                }
            }
        }
    }

    fun installSourceFromQr(content: String) {
        val pluginUrl = pluginUrlFromQrContent(content)
        if (pluginUrl == null) {
            _uiState.update { it.copy(sourceOperationMessage = text(R.string.invalid_source_qr)) }
        } else {
            installSource(pluginUrl)
        }
    }

    fun trustUnverifiedSource() {
        val request = _uiState.value.sourceTrustRequest ?: return
        if (_uiState.value.sourceOperationInProgress) return
        _uiState.update {
            it.copy(
                sourceOperationInProgress = true,
                sourceOperationMessage = text(R.string.installing_trusted_source),
            )
        }
        viewModelScope.launch {
            try {
                completeSourceInstallation(engine.trustInstallSource(request.token), announce = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        sourceOperationInProgress = false,
                        sourceTrustRequest = null,
                        sourceOperationMessage = error.localizedMessage
                            ?: text(R.string.trusted_source_install_failed),
                    )
                }
            }
        }
    }

    fun rejectUnverifiedSource() {
        val request = _uiState.value.sourceTrustRequest ?: return
        engine.discardUntrustedSource(request.token)
        _uiState.update {
            it.copy(
                sourceTrustRequest = null,
                sourceOperationInProgress = false,
                sourceOperationMessage = text(R.string.source_not_installed, request.pluginName),
            )
        }
    }

    fun refreshSource(sourceId: String) {
        val source = _uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        if (source.pluginConfigUrl.isBlank()) return
        installSource(source.pluginConfigUrl)
    }

    fun clearSourceCache(sourceId: String) {
        val source = _uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        engine.clearSourceCache(sourceId)
        _uiState.update {
            it.copy(sourceOperationMessage = text(R.string.source_cache_cleared, source.name))
        }
    }

    fun removeSource(sourceId: String) {
        val source = _uiState.value.sources.firstOrNull { it.id == sourceId } ?: return
        if (!source.isCustom) return
        if (_uiState.value.playback.currentVideoId?.let(::findVideo)?.sourceId == sourceId) {
            closePlayback()
        }
        engine.removeSource(sourceId)
        sourceRepository.removeCustomSource(sourceId)
        enabledSourceIds -= sourceId
        remoteVideos.entries.removeAll { it.value.sourceId == sourceId }
        remoteChannels.entries.removeAll { it.value.sourceId == sourceId }
        _uiState.update { state ->
            state.copy(
                sources = state.sources.filterNot { it.id == sourceId },
                channels = state.channels.filterNot { it.sourceId == sourceId },
                search = state.search.copy(
                    videos = state.search.videos.filterNot { it.sourceId == sourceId },
                    channels = state.search.channels.filterNot { it.sourceId == sourceId },
                ),
                sourceOperationMessage = text(R.string.source_removed, source.name),
            )
        }
        homeFeedCache.clear()
        refreshHome()
    }

    fun prepareDatabaseImport(uri: Uri, password: String? = null) {
        pendingDatabaseImportUri = uri
        pendingDatabaseImport = null
        pendingNewPipeImport = null
        val fileName = importDisplayName(uri)
        _uiState.update {
            it.copy(
                databaseImport = DatabaseImportUiState(
                    isBusy = true,
                    fileName = fileName,
                ),
            )
        }
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use {
                            it.readImportBytes(
                                maxBytes = MAX_GRAYJAY_IMPORT_BYTES,
                                tooLargeMessage = "The selected backup is larger than 128 MB.",
                            )
                        }
                        ?: error(text(R.string.backup_open_failed))
                    LegacyGrayjayBackupParser.parse(bytes, password)
                }
                pendingDatabaseImport = backup
                _uiState.update {
                    it.copy(
                        databaseImport = DatabaseImportUiState(
                            preview = DatabaseImportPreviewUiModel(
                                fileName = fileName,
                                sourceCount = backup.pluginConfigUrls.size,
                                pluginSettingsCount = backup.pluginSettings.size,
                                subscriptionCount = backup.subscriptionUrls.size,
                                watchLaterCount = backup.watchLaterUrls.size,
                                playlistCount = backup.playlists.size,
                                historyCount = backup.history.size,
                                hasLegacySettings = backup.hasSettings,
                            ),
                            fileName = fileName,
                        ),
                    )
                }
            } catch (error: LegacyBackupPasswordRequiredException) {
                _uiState.update {
                    it.copy(
                        databaseImport = DatabaseImportUiState(
                            passwordRequired = true,
                            fileName = fileName,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        databaseImport = DatabaseImportUiState(
                            fileName = fileName,
                            passwordRequired = password != null,
                            errorMessage = localizedBackupError(error),
                        ),
                    )
                }
            }
        }
    }

    fun prepareNewPipeImport(uri: Uri) {
        pendingDatabaseImportUri = null
        pendingDatabaseImport = null
        pendingNewPipeImport = null
        val fileName = importDisplayName(uri)
        _uiState.update {
            it.copy(
                databaseImport = DatabaseImportUiState(
                    isBusy = true,
                    fileName = fileName,
                    format = DatabaseImportFormat.NewPipe,
                ),
            )
        }
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use {
                            it.readImportBytes(
                                maxBytes = MAX_NEWPIPE_IMPORT_BYTES,
                                tooLargeMessage = "The selected NewPipe export is larger than 256 MB.",
                            )
                        }
                        ?: error(text(R.string.backup_open_failed))
                    NewPipeBackupParser.parse(bytes, getApplication<Application>().cacheDir)
                }
                pendingNewPipeImport = backup
                _uiState.update {
                    it.copy(
                        databaseImport = DatabaseImportUiState(
                            preview = DatabaseImportPreviewUiModel(
                                fileName = fileName,
                                sourceCount = 0,
                                pluginSettingsCount = 0,
                                subscriptionCount = backup.subscriptions.size,
                                watchLaterCount = 0,
                                playlistCount = backup.playlists.size,
                                historyCount = backup.history.size,
                                hasLegacySettings = false,
                                format = DatabaseImportFormat.NewPipe,
                            ),
                            fileName = fileName,
                            format = DatabaseImportFormat.NewPipe,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        databaseImport = DatabaseImportUiState(
                            fileName = fileName,
                            errorMessage = localizedNewPipeError(error),
                            format = DatabaseImportFormat.NewPipe,
                        ),
                    )
                }
            }
        }
    }

    fun retryDatabaseImport(password: String) {
        pendingDatabaseImportUri?.let { prepareDatabaseImport(it, password) }
    }

    fun dismissDatabaseImport() {
        pendingDatabaseImport = null
        pendingNewPipeImport = null
        pendingDatabaseImportUri = null
        _uiState.update { it.copy(databaseImport = DatabaseImportUiState()) }
    }

    fun confirmDatabaseImport(selection: DatabaseImportSelection) {
        pendingNewPipeImport?.let { backup ->
            confirmNewPipeImport(backup, selection)
            return
        }
        val backup = pendingDatabaseImport ?: return
        _uiState.update {
            it.copy(databaseImport = it.databaseImport.copy(isBusy = true, errorMessage = null))
        }
        viewModelScope.launch {
            val warnings = mutableListOf<String>()
            try {
                if (selection.importSources) {
                    backup.pluginConfigUrls.forEach { (pluginId, configUrl) ->
                        val existing = _uiState.value.sources.firstOrNull { it.engineId == pluginId }
                        if (existing != null) {
                            if (!existing.isEnabled) setSourceEnabled(existing.id, true)
                        } else {
                            runCatching { installSourceForImport(configUrl) }
                                .onFailure { error ->
                                    if (error is GrayjaySignatureMismatchException) {
                                        engine.discardUntrustedSource(error.plugin.token)
                                    }
                                    warnings += text(
                                        R.string.source_warning,
                                        pluginId,
                                        error.localizedMessage.orEmpty(),
                                    )
                                }
                        }
                    }
                }
                if (selection.importPluginSettings) {
                    backup.pluginSettings.forEach(engine::setPluginSettings)
                }

                val (importedVideos, parsedPlaylists) = backup.buildImportLibrary(
                    includeWatchLater = selection.importWatchLater,
                    includePlaylists = selection.importPlaylists,
                    includeHistory = selection.importHistory,
                    importedDescription = text(R.string.imported_from_grayjay),
                )
                val remappedVideos = importedVideos.map(::remapImportedVideo)
                val importedPlaylists = parsedPlaylists.map { playlist ->
                    playlist.copy(
                        title = playlist.title.takeUnless { it == "Imported playlist" }
                            ?: text(R.string.imported_playlist),
                    )
                }
                libraryRepository.mergeImportedData(remappedVideos, importedPlaylists)

                val importedChannels = if (selection.importSubscriptions) {
                    backup.subscriptionUrls.map { url ->
                        backup.cachedChannels[url] ?: ChannelUiModel(
                            id = url,
                            name = url.substringAfter("://").substringBefore('/').ifBlank {
                                text(R.string.imported_creator)
                            },
                            sourceId = inferImportedSourceId(url),
                            source = "",
                            unreadCount = 0,
                            followerCount = text(R.string.creator),
                            description = text(R.string.imported_from_grayjay),
                        )
                    }.map(::remapImportedChannel).distinctBy(ChannelUiModel::id)
                } else {
                    emptyList()
                }
                preferences.mergeImportedSubscriptions(importedChannels)
                importedChannels.forEach { remoteChannels[it.id] = it }
                followedCreatorIds = preferences.followedCreatorIds()
                reloadLibrary()
                allVideos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    state.copy(
                        channels = visibleKnownChannels(),
                        followedCreatorIds = followedCreatorIds,
                        databaseImport = DatabaseImportUiState(
                            resultMessage = buildString {
                                append(
                                    text(
                                        R.string.import_summary,
                                        remappedVideos.size,
                                        importedPlaylists.size,
                                        importedChannels.size,
                                    ),
                                )
                                if (warnings.isNotEmpty()) {
                                    append(". ")
                                    append(
                                        quantityText(
                                            R.plurals.source_warnings,
                                            warnings.size,
                                            warnings.size,
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
                pendingDatabaseImport = null
                pendingDatabaseImportUri = null
                homeFeedCache.clear()
                refreshHome()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        databaseImport = it.databaseImport.copy(
                            isBusy = false,
                            errorMessage = error.localizedMessage ?: text(R.string.database_import_failed),
                        ),
                    )
                }
            }
        }
    }

    private fun confirmNewPipeImport(
        backup: NewPipeBackup,
        selection: DatabaseImportSelection,
    ) {
        _uiState.update {
            it.copy(databaseImport = it.databaseImport.copy(isBusy = true, errorMessage = null))
        }
        viewModelScope.launch {
            try {
                val (importedVideos, parsedPlaylists) = backup.buildImportLibrary(
                    includePlaylists = selection.importPlaylists,
                    includeHistory = selection.importHistory,
                    importedDescription = text(R.string.imported_from_newpipe),
                )
                val remappedVideos = importedVideos.map(::remapImportedVideo)
                val importedPlaylists = parsedPlaylists.map { playlist ->
                    playlist.copy(
                        title = playlist.title.takeUnless { it == "Imported playlist" }
                            ?: text(R.string.imported_playlist),
                    )
                }
                libraryRepository.mergeImportedData(remappedVideos, importedPlaylists)

                val importedChannels = if (selection.importSubscriptions) {
                    backup.subscriptionChannels(text(R.string.imported_from_newpipe))
                        .map(::remapImportedChannel)
                        .distinctBy(ChannelUiModel::id)
                } else {
                    emptyList()
                }
                preferences.mergeImportedSubscriptions(importedChannels)
                importedChannels.forEach { remoteChannels[it.id] = it }
                followedCreatorIds = preferences.followedCreatorIds()
                reloadLibrary()
                allVideos.forEach(::registerRemoteChannel)
                _uiState.update { state ->
                    state.copy(
                        channels = visibleKnownChannels(),
                        followedCreatorIds = followedCreatorIds,
                        databaseImport = DatabaseImportUiState(
                            resultMessage = text(
                                R.string.newpipe_import_summary,
                                remappedVideos.size,
                                importedPlaylists.size,
                                importedChannels.size,
                            ),
                            format = DatabaseImportFormat.NewPipe,
                        ),
                    )
                }
                pendingNewPipeImport = null
                homeFeedCache.clear()
                refreshHome()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        databaseImport = it.databaseImport.copy(
                            isBusy = false,
                            errorMessage = localizedNewPipeError(error),
                        ),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        suppressChromecastHandoff = true
        chromecastManager.release()
        searchJob?.cancel()
        searchPagingJob?.cancel()
        suggestionJob?.cancel()
        detailsJob?.cancel()
        queuePreparationJob?.cancel()
        queueMutationJob?.cancel()
        extrasPagingJob?.cancel()
        channelJob?.cancel()
        channelPagingJob?.cancel()
        remotePlaylistJob?.cancel()
        remotePlaylistPagingJob?.cancel()
        resumePromptJob?.cancel()
        homeJob?.cancel()
        homePagingJob?.cancel()
        // viewModelScope is cancelled as this method returns, so a newly scheduled debounced write
        // would never run. A final synchronous write here is safe and preserves the last position.
        watchProgressWriteJob?.cancel()
        historyWriteJobs.values.forEach(Job::cancel)
        historyWriteJobs.clear()
        persistCurrentPlaybackProgressImmediately()
        networkMonitor.close()
        engine.release()
        super.onCleared()
    }

    private suspend fun installSourceForImport(configUrl: String): SourceUiModel {
        return completeSourceInstallation(engine.installSource(configUrl), announce = false)
    }

    private fun completeSourceInstallation(
        installed: SourceUiModel,
        announce: Boolean,
    ): SourceUiModel {
        val current = _uiState.value.sources.firstOrNull {
            it.id == installed.id || it.engineId == installed.engineId
        }
        val source = installed.copy(
            id = current?.id ?: installed.id,
            isCustom = current?.isCustom ?: installed.isCustom,
            isEnabled = true,
        )
        if (source.isCustom) sourceRepository.saveCustomSource(source)
        sourceRepository.setEnabled(source.id, true)
        enabledSourceIds += source.id
        engine.registerSources(listOf(source))
        _uiState.update { state ->
            state.copy(
                sources = state.sources
                    .filterNot { it.id == source.id || it.engineId == source.engineId }
                    .plus(source)
                    .sortedBy { it.name.lowercase() },
                sourceOperationInProgress = false,
                sourceTrustRequest = null,
                sourceOperationMessage = if (announce) {
                    text(R.string.source_installed_enabled, source.name)
                } else {
                    state.sourceOperationMessage
                },
            )
        }
        if (announce) {
            homeFeedCache.clear()
            refreshHome()
        }
        return source
    }

    private fun remapImportedVideo(video: VideoUiModel): VideoUiModel {
        val source = importedSource(video.sourceId, video.contentUrl)
        return video.copy(
            sourceId = source?.id ?: inferImportedSourceId(video.contentUrl),
            sourceName = source?.name.orEmpty(),
            sourceIconUrl = source?.iconUrl.orEmpty(),
            creator = video.creator.takeUnless { it == "Unknown creator" }
                ?: text(R.string.unknown_creator),
            metadata = when {
                video.metadata == "Imported from Grayjay" -> text(R.string.imported_from_grayjay)
                video.metadata.endsWith(" views") -> text(
                    R.string.views_count_compact,
                    video.metadata.removeSuffix(" views"),
                )
                else -> video.metadata
            },
        )
    }

    private fun remapImportedChannel(channel: ChannelUiModel): ChannelUiModel {
        val source = importedSource(channel.sourceId, channel.id)
        return channel.copy(
            sourceId = source?.id ?: inferImportedSourceId(channel.id),
            source = source?.name.orEmpty().ifBlank { channel.source },
            name = channel.name.takeUnless { it == "Imported creator" }
                ?: text(R.string.imported_creator),
            followerCount = when {
                channel.followerCount == "Creator" -> text(R.string.creator)
                channel.followerCount.endsWith(" followers") -> quantityText(
                    R.plurals.followers_count,
                    2,
                    channel.followerCount.removeSuffix(" followers"),
                )
                else -> channel.followerCount
            },
            description = channel.description.takeUnless { it == "Imported from Grayjay" }
                ?: text(R.string.imported_from_grayjay),
        )
    }

    private fun importedSource(rawSourceId: String, url: String) = _uiState.value.sources.firstOrNull {
        it.id == rawSourceId || it.engineId == rawSourceId
    } ?: _uiState.value.sources.firstOrNull { it.id == inferImportedSourceId(url) }

    private fun importDisplayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment ?: text(R.string.grayjay_backup) }

    private fun localizedBackupError(error: Throwable): String {
        val message = error.localizedMessage.orEmpty()
        return when {
            message == "The backup password is incorrect." -> text(R.string.incorrect_backup_password)
            message == "The selected backup is empty." -> text(R.string.backup_empty)
            message == "The selected backup is larger than 128 MB." -> text(R.string.backup_over_128_mb)
            message.startsWith("Unsupported Grayjay automatic backup version:") -> text(
                R.string.unsupported_automatic_backup_version,
                message.substringAfter(':').filter(Char::isDigit).toIntOrNull() ?: 0,
            )
            message == "This newer encrypted Grayjay backup format is not supported by the legacy app either." ->
                text(R.string.newer_encrypted_backup_unsupported)
            message == "The automatic backup header is incomplete." -> text(R.string.backup_header_incomplete)
            message.startsWith("Unsupported legacy encryption version:") -> text(
                R.string.unsupported_encryption_version,
                message.substringAfter(':').filter { it.isDigit() || it == '-' }.toIntOrNull() ?: 0,
            )
            message == "The backup contains too many files." -> text(R.string.backup_too_many_files)
            message == "The backup expands beyond the safe import limit." -> text(R.string.backup_too_large)
            message == "This is not a Grayjay export database." -> text(R.string.not_grayjay_export)
            message == "The decrypted data is not a valid Grayjay export." -> text(R.string.decrypted_data_invalid)
            message.isNotBlank() -> message
            else -> text(R.string.backup_read_failed)
        }
    }

    private fun localizedNewPipeError(error: Throwable): String {
        val detail = error.localizedMessage.orEmpty()
        return if (detail.isBlank()) text(R.string.newpipe_import_failed)
        else "${text(R.string.newpipe_import_failed)} $detail"
    }

    private fun EnginePlaybackState.toUiState(
        fullQueueVideoIds: List<String> = emptyList(),
    ) = PlaybackUiState(
        currentVideoId = currentVideoId,
        queueVideoIds = queueVideoIds,
        fullQueueVideoIds = fullQueueVideoIds.ifEmpty { queueVideoIds },
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedPercentage = bufferedPercentage,
        playbackSpeed = playbackSpeed,
        captionsEnabled = captionsEnabled,
        availableVideoQualities = availableVideoQualities,
        selectedVideoQuality = selectedVideoQuality,
        currentVideoWidth = currentVideoWidth,
        currentVideoHeight = currentVideoHeight,
        selectedSubtitleLanguage = selectedSubtitleLanguage,
        selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
        availableAudioLanguages = availableAudioLanguages,
        selectedAudioLanguage = selectedAudioLanguage,
        audioLanguageAutomatic = audioLanguageAutomatic,
        errorMessage = errorMessage,
        audioSpectrum = audioSpectrum,
    )

    private fun PlaybackUiState.withChromecast(cast: ChromecastUiState): PlaybackUiState =
        if (!cast.isConnected) copy(isCasting = false) else copy(
            isCasting = true,
            isPlaying = cast.isPlaying,
            isBuffering = cast.isConnecting,
            positionMs = cast.positionMs,
            durationMs = cast.durationMs.takeIf { it > 0L } ?: durationMs,
            errorMessage = cast.errorMessage,
        )

    private suspend fun loadExtras(video: VideoUiModel) {
        try {
            val extras = engine.loadExtras(video)
            val recommendations = if (!preferences.showRecommendations) {
                emptyList()
            } else {
                extras.recommendations
                    .map { it.withPersistedLibraryState() }
                    .ifEmpty {
                        if (extras.recommendationsAvailable) emptyList()
                        else allVideos.filter { it.id != video.id && it.sourceId == video.sourceId }.take(8)
                    }
            }
            recommendations.forEach { remoteVideos[it.id] = it }
            recommendations.forEach(::registerRemoteChannel)
            _uiState.update { state ->
                if (state.nowPlaying.video?.id != video.id) state else state.copy(
                    channels = visibleKnownChannels(),
                    nowPlaying = state.nowPlaying.copy(
                        isLoadingExtras = false,
                        recommendations = recommendations,
                        comments = extras.comments,
                        recommendationsAvailable = preferences.showRecommendations &&
                            (extras.recommendationsAvailable || recommendations.isNotEmpty()),
                        commentsAvailable = extras.commentsAvailable,
                        recommendationContinuationId = extras.recommendationContinuationId,
                        commentsContinuationId = extras.commentsContinuationId,
                        hasMoreRecommendations = extras.hasMoreRecommendations,
                        hasMoreComments = extras.hasMoreComments,
                        errorMessage = null,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _uiState.update { state ->
                if (state.nowPlaying.video?.id != video.id) state else state.copy(
                    nowPlaying = state.nowPlaying.copy(
                        isLoadingExtras = false,
                        errorMessage = error.localizedMessage ?: text(R.string.more_from_source_load_failed),
                    ),
                )
            }
        }
    }

    fun loadMoreRecommendations() = loadMoreExtras(comments = false)

    fun loadMoreComments() = loadMoreExtras(comments = true)

    fun openCommentReplies(commentId: String) {
        val parent = _uiState.value.nowPlaying.comments.firstOrNull { it.id == commentId }
            ?: return
        if (parent.id.isBlank() || (parent.replyCount ?: 0) <= 0) return
        commentRepliesJob?.cancel()
        _uiState.update { state ->
            state.copy(
                nowPlaying = state.nowPlaying.copy(
                    commentReplies = CommentRepliesUiState(
                        parent = parent,
                        isVisible = true,
                        isLoading = true,
                    ),
                ),
            )
        }
        commentRepliesJob = viewModelScope.launch {
            try {
                val page = engine.loadCommentReplies(commentId)
                _uiState.update { state ->
                    val replies = state.nowPlaying.commentReplies
                    if (!replies.isVisible || replies.parent?.id != commentId) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(
                            commentReplies = replies.copy(
                                replies = page.comments,
                                isLoading = false,
                                continuationId = page.continuationId,
                                hasMore = page.hasMore,
                                errorMessage = null,
                            ),
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    val replies = state.nowPlaying.commentReplies
                    if (!replies.isVisible || replies.parent?.id != commentId) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(
                            commentReplies = replies.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage
                                    ?: text(R.string.comment_replies_load_failed),
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun dismissCommentReplies() {
        commentRepliesJob?.cancel()
        _uiState.update { state ->
            state.copy(
                nowPlaying = state.nowPlaying.copy(
                    commentReplies = CommentRepliesUiState(),
                ),
            )
        }
    }

    fun loadMoreCommentReplies() {
        val current = _uiState.value.nowPlaying.commentReplies
        val parentId = current.parent?.id ?: return
        val continuationId = current.continuationId ?: return
        if (!current.isVisible || !current.hasMore || current.isLoading || current.isLoadingMore) {
            return
        }
        commentRepliesJob?.cancel()
        _uiState.update { state ->
            state.copy(
                nowPlaying = state.nowPlaying.copy(
                    commentReplies = state.nowPlaying.commentReplies.copy(isLoadingMore = true),
                ),
            )
        }
        commentRepliesJob = viewModelScope.launch {
            try {
                val page = engine.loadMoreComments(continuationId)
                _uiState.update { state ->
                    val replies = state.nowPlaying.commentReplies
                    if (!replies.isVisible || replies.parent?.id != parentId) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(
                            commentReplies = replies.copy(
                                replies = (replies.replies + page.comments).distinctBy { reply ->
                                    reply.id.ifBlank {
                                        "${reply.author}|${reply.age}|${reply.message}"
                                    }
                                },
                                isLoadingMore = false,
                                continuationId = page.continuationId,
                                hasMore = page.hasMore,
                                errorMessage = null,
                            ),
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    val replies = state.nowPlaying.commentReplies
                    if (!replies.isVisible || replies.parent?.id != parentId) state else state.copy(
                        nowPlaying = state.nowPlaying.copy(
                            commentReplies = replies.copy(
                                isLoadingMore = false,
                                errorMessage = error.localizedMessage
                                    ?: text(R.string.comment_replies_load_failed),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun loadMoreExtras(comments: Boolean) {
        val current = _uiState.value.nowPlaying
        val videoId = current.video?.id ?: return
        val continuationId = if (comments) {
            current.commentsContinuationId
        } else {
            current.recommendationContinuationId
        } ?: return
        val canLoad = if (comments) {
            current.hasMoreComments && !current.isLoadingMoreComments
        } else {
            current.hasMoreRecommendations && !current.isLoadingMoreRecommendations
        }
        if (!canLoad || current.isLoadingExtras) return
        extrasPagingJob?.cancel()
        _uiState.update { state ->
            state.copy(
                nowPlaying = if (comments) {
                    state.nowPlaying.copy(isLoadingMoreComments = true)
                } else {
                    state.nowPlaying.copy(isLoadingMoreRecommendations = true)
                },
            )
        }
        extrasPagingJob = viewModelScope.launch {
            try {
                if (comments) {
                    val page = engine.loadMoreComments(continuationId)
                    _uiState.update { state ->
                        if (state.nowPlaying.video?.id != videoId) state else state.copy(
                            nowPlaying = state.nowPlaying.copy(
                                comments = (state.nowPlaying.comments + page.comments).distinctBy {
                                    "${it.author}|${it.age}|${it.message}"
                                },
                                isLoadingMoreComments = false,
                                commentsContinuationId = page.continuationId,
                                hasMoreComments = page.hasMore,
                            ),
                        )
                    }
                } else {
                    val page = engine.loadMoreRecommendations(continuationId)
                    val videos = page.videos.map { it.withPersistedLibraryState() }
                    videos.forEach { remoteVideos[it.id] = it }
                    videos.forEach(::registerRemoteChannel)
                    _uiState.update { state ->
                        if (state.nowPlaying.video?.id != videoId) state else state.copy(
                            channels = visibleKnownChannels(),
                            nowPlaying = state.nowPlaying.copy(
                                recommendations = (state.nowPlaying.recommendations + videos)
                                    .distinctBy(VideoUiModel::id),
                                isLoadingMoreRecommendations = false,
                                recommendationContinuationId = page.continuationId,
                                hasMoreRecommendations = page.hasMore,
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.nowPlaying.video?.id != videoId) state else state.copy(
                        nowPlaying = if (comments) {
                            state.nowPlaying.copy(isLoadingMoreComments = false)
                        } else {
                            state.nowPlaying.copy(isLoadingMoreRecommendations = false)
                        },
                    )
                }
            }
        }
    }

    private fun VideoUiModel.withPersistedLibraryState(): VideoUiModel {
        // allVideos is the in-memory mirror of the active profile's library. The former helper
        // called LibraryRepository.load() here, which reparsed the complete history JSON once per
        // search/home/channel result (80 full parses after a typical subscription refresh) and
        // twice while opening a video.
        val saved = savedVideosById[id] ?: return this
        return copy(
            isWatchLater = saved.isWatchLater,
            isDownloaded = saved.isDownloaded,
            watchProgress = saved.watchProgress,
            isLiked = saved.isLiked,
            isAvailable = saved.isAvailable,
            scheduledStartAtMs = saved.scheduledStartAtMs,
            lastWatchedAt = saved.lastWatchedAt,
            playlistNames = saved.playlistNames,
        )
    }

    private fun VideoUiModel.withPresentationFallback(previous: VideoUiModel?): VideoUiModel {
        if (previous == null) return this
        return copy(
            thumbnailUrl = thumbnailUrl.ifBlank { previous.thumbnailUrl },
            authorThumbnailUrl = authorThumbnailUrl.ifBlank { previous.authorThumbnailUrl },
            sourceIconUrl = sourceIconUrl.ifBlank { previous.sourceIconUrl },
            duration = duration.ifBlank { previous.duration },
            creator = creator.ifBlank { previous.creator },
            metadata = metadata.ifBlank { previous.metadata },
        )
    }

    /**
     * YouTube's fast Atom subscription feed intentionally has no channel-avatar field. Reuse the
     * already persisted subscription metadata instead of issuing another request per video.
     */
    private fun List<VideoUiModel>.withKnownChannelPresentation(
        extraChannels: List<ChannelUiModel> = emptyList(),
    ): List<VideoUiModel> {
        if (isEmpty()) return this
        val knownChannels = sequenceOf(
            extraChannels.asSequence(),
            content.channels.asSequence(),
            remoteChannels.values.asSequence(),
        ).flatten().distinctBy(ChannelUiModel::id).toList()
        if (knownChannels.isEmpty()) return this

        val byReference = HashMap<String, ChannelUiModel>(knownChannels.size * 2)
        val byName = HashMap<String, ChannelUiModel>(knownChannels.size)
        knownChannels.forEach { channel ->
            creatorReferenceKey(channel.sourceId, channel.id)?.let { key ->
                byReference.putIfAbsent(key, channel)
            }
            channel.name.trim().takeIf(String::isNotEmpty)?.let { name ->
                byName.putIfAbsent(
                    "${channel.sourceId.lowercase(Locale.ROOT)}|${name.lowercase(Locale.ROOT)}",
                    channel,
                )
            }
        }
        return map { video ->
            if (video.authorThumbnailUrl.isNotBlank()) return@map video
            val channel = sequenceOf(video.authorUrl, video.channelId)
                .mapNotNull { creatorReferenceKey(video.sourceId, it) }
                .mapNotNull(byReference::get)
                .firstOrNull()
                ?: byName[
                    "${video.sourceId.lowercase(Locale.ROOT)}|" +
                        video.creator.trim().lowercase(Locale.ROOT)
                ]
            channel?.thumbnailUrl?.takeIf(String::isNotBlank)?.let { thumbnail ->
                video.copy(authorThumbnailUrl = thumbnail)
            } ?: video
        }
    }

    private fun creatorReferenceKey(sourceId: String, rawReference: String): String? {
        val reference = rawReference.trim().trimEnd('/').takeIf(String::isNotEmpty) ?: return null
        val source = sourceId.lowercase(Locale.ROOT)
        val parsed = runCatching { Uri.parse(reference) }.getOrNull()
        val host = parsed?.host?.lowercase(Locale.ROOT)
        if (!host.isNullOrBlank()) {
            val path = parsed.path.orEmpty().trimEnd('/')
            val segments = parsed.pathSegments
            val channelIndex = segments.indexOf("channel")
            if (channelIndex >= 0 && channelIndex < segments.lastIndex) {
                return "$source|channel:${segments[channelIndex + 1]}"
            }
            return "$source|$host$path"
        }
        return "$source|$reference"
    }

    private suspend fun resolveForPlayback(
        video: VideoUiModel,
        profileId: String,
    ): VideoUiModel {
        val persisted = video.withPersistedLibraryState()
        if (persisted.isDownloaded && !downloadStore.isInitialized()) {
            downloadStore.awaitInitialized()
        }
        downloadStore.playbackDescriptorFor(profileId, persisted)?.let { return it }

        // Grayjay only promotes a download to VideoLocal after every selected source has
        // completed and validated. If this record is failed/incomplete (or the old persisted
        // flag is stale), discard its signed URLs and resolve an ordinary online source.
        val resolveInput = if (
            persisted.isDownloaded || downloadStore.hasDownloadRecord(profileId, persisted.id)
        ) {
            video.copy(
                playbackFromDownload = false,
                playbackAudioOnly = false,
                playbackCacheNamespace = "",
                audioCacheNamespace = "",
                playbackStreamKeys = emptyList(),
                audioStreamKeys = emptyList(),
                playbackUrl = "",
                playbackMimeType = "",
                playbackManifest = "",
                audioUrl = "",
                audioRequestHeaders = emptyMap(),
                audioDataSourceFactory = null,
                playbackRequestHeaders = emptyMap(),
                playbackDataSourceFactory = null,
                subtitleTracks = emptyList(),
                qualityVariants = emptyList(),
                audioQualityVariants = emptyList(),
            )
        } else {
            video
        }
        val resolved = resolveWithAudioPreferences(resolveInput).withPersistedLibraryState()
        return downloadStore.playbackDescriptorFor(profileId, resolved) ?: resolved
    }

    private suspend fun resolveWithAudioPreferences(
        video: VideoUiModel,
        audioLanguageOverride: String? = null,
        priority: EngineResolvePriority = EngineResolvePriority.UserPlayback,
    ): VideoUiModel = engine.resolve(
        video = video,
        preferredAudioLanguage = audioLanguageOverride ?: preferences.preferredAudioLanguage,
        preferOriginalAudio = audioLanguageOverride == null && preferences.preferOriginalAudio,
        priority = priority,
    )

    private fun VideoUiModel.downloadDescriptor(preferredHeight: Int?): VideoUiModel {
        require(!isDrmProtected) { "DRM-protected streams cannot be downloaded." }
        val variant = when {
            qualityVariants.isEmpty() -> null
            playbackUrl.isBlank() && playbackManifest.isBlank() ->
                qualityVariants.maxByOrNull { it.height }
            preferredHeight != null -> qualityVariants
                .filter { it.height <= preferredHeight }
                .maxByOrNull { it.height }
                ?: qualityVariants.minByOrNull { it.height }
            else -> null
        }
        return if (variant == null) {
            copy(
                playbackFromDownload = false,
                playbackAudioOnly = false,
                playbackCacheNamespace = "",
                audioCacheNamespace = "",
                playbackStreamKeys = emptyList(),
                audioStreamKeys = emptyList(),
                subtitleTracks = emptyList(),
                qualityVariants = emptyList(),
                audioQualityVariants = emptyList(),
            )
        } else {
            copy(
                playbackFromDownload = false,
                playbackAudioOnly = false,
                playbackCacheNamespace = "",
                audioCacheNamespace = "",
                playbackStreamKeys = emptyList(),
                audioStreamKeys = emptyList(),
                playbackUrl = variant.playbackUrl,
                playbackMimeType = variant.playbackMimeType,
                playbackManifest = variant.playbackManifest,
                playbackRequestHeaders = variant.playbackRequestHeaders
                    .ifEmpty { playbackRequestHeaders },
                playbackDataSourceFactory = variant.playbackDataSourceFactory,
                subtitleTracks = emptyList(),
                qualityVariants = emptyList(),
                audioQualityVariants = emptyList(),
            )
        }
    }

    private fun syncDownloadState() {
        val managerStates = downloadStore.snapshotsFor(activeProfileId)
        managerStates.values
            .takeIf { appIsForeground }
            .orEmpty()
            .filter { download ->
                download.status == DownloadStatus.Failed &&
                    (download.requiresPluginTransport ||
                        download.preparedAtMs == null ||
                        download.preparedAtMs <= 0L ||
                        System.currentTimeMillis() - download.preparedAtMs >= DOWNLOAD_PREPARATION_TTL_MS)
            }
            .forEach(::scheduleDownloadRepair)
        managerStates.forEach { (videoId, managerState) ->
            val preparationKey = downloadJobKey(
                managerState.profileId,
                videoId,
                managerState.mediaType,
            )
            val preparation = downloadPreparationStates[preparationKey]
            if (
                preparation?.status == DownloadStatus.Preparing &&
                managerState.status == DownloadStatus.Failed
            ) {
                return@forEach
            }
            if (managerState.status != DownloadStatus.Failed || preparation == null) {
                downloadPreparationStates.remove(preparationKey)
            }
        }
        val visibleDownloads = managerStates.toMutableMap().apply {
            downloadPreparationStates.values
                .filter { it.profileId == activeProfileId }
                .forEach { pending ->
                    this[pending.videoId] = this[pending.videoId]?.let { existing ->
                        mergeDownloadSnapshots(existing, pending)
                    } ?: pending
                }
        }
        val activePlaylistDownloads = activePlaylistDownloadBatches(
            descriptors = offlinePlaylistStore.all(activeProfileId),
            downloads = visibleDownloads,
        )
        _uiState.update { state ->
            val remoteDownloadTypes = if (state.remotePlaylistDetail.isLoadingAll) {
                state.remotePlaylistDetail.activeDownloadMediaTypes
            } else {
                state.remotePlaylistDetail.activeDownloadMediaTypes.filterTo(mutableSetOf()) { type ->
                    state.remotePlaylistDetail.videos
                        .filter(VideoUiModel::supportsOfflineDownload)
                        .any { video -> visibleDownloads[video.id]?.isComplete(type) != true }
                }
            }
            if (
                state.downloads == visibleDownloads &&
                state.activePlaylistDownloads == activePlaylistDownloads &&
                state.remotePlaylistDetail.activeDownloadMediaTypes == remoteDownloadTypes
            ) {
                state
            } else {
                state.copy(
                    downloads = visibleDownloads,
                    activePlaylistDownloads = activePlaylistDownloads,
                    remotePlaylistDetail = state.remotePlaylistDetail.copy(
                        activeDownloadMediaTypes = remoteDownloadTypes,
                    ),
                )
            }
        }

        if (!downloadStore.isInitialized()) return
        val completedIds = managerStates.values
            .filter(DownloadUiModel::isComplete)
            .mapTo(mutableSetOf(), DownloadUiModel::videoId)
        val managedIds = managerStates.keys
        val signature = completedIds to managedIds
        if (appliedDownloadIndexSignature == signature) return
        appliedDownloadIndexSignature = signature
        var libraryChanged = false
        libraryRepository.loadSavedVideos().forEach { video ->
            val shouldBeDownloaded = video.id in completedIds
            if (video.isDownloaded != shouldBeDownloaded) {
                libraryRepository.setDownloaded(video.id, shouldBeDownloaded)
                libraryChanged = true
            }
            // A saved descriptor belongs to the download attempt that created it. Keep it only
            // while that attempt is a complete, playable offline item; failed and partial jobs
            // must use a freshly resolved plugin stream, just as upstream Grayjay does.
            if (
                !shouldBeDownloaded &&
                (video.playbackUrl.isNotBlank() || video.playbackManifest.isNotBlank())
            ) {
                libraryRepository.clearDownloadDescriptor(video.id)
                libraryChanged = true
            }
        }
        if (libraryChanged) reloadLibrary()
    }

    private fun scheduleDownloadRepair(download: DownloadUiModel) {
        val repairKey = "${download.profileId}:${download.videoId}:${download.mediaType}"
        if (!autoRepairedDownloadKeys.add(repairKey)) return
        val profileAtStart = activeProfileId
        downloadPreparationStates[
            downloadJobKey(profileAtStart, download.videoId, download.mediaType)
        ] = download.copy(
            status = DownloadStatus.Preparing,
            progress = null,
            errorMessage = null,
            activeMediaTypes = setOf(download.mediaType),
            failedMediaTypes = download.failedMediaTypes - download.mediaType,
        )
        if (profileAtStart == activeProfileId) {
            startDownload(
                videoId = download.videoId,
                mediaType = download.mediaType,
                targetVideoHeight = download.targetVideoHeight,
                targetAudioBitrate = download.targetAudioBitrate,
            )
        }
    }

    private fun recordHistory(video: VideoUiModel) {
        if (_uiState.value.privateSessionEnabled) return
        val repositoryAtScheduleTime = libraryRepository
        val profileAtScheduleTime = activeProfileId
        historyWriteJobs.remove(video.id)?.cancel()
        historyWriteJobs[video.id] = viewModelScope.launch(Dispatchers.IO) {
            repositoryAtScheduleTime.recordHistory(video)
            val savedVideos = repositoryAtScheduleTime.loadSavedVideos()
            val playlists = repositoryAtScheduleTime.loadPlaylists()
            withContext(Dispatchers.Main) {
                if (
                    profileAtScheduleTime == activeProfileId &&
                    repositoryAtScheduleTime === libraryRepository
                ) {
                    applyLibrarySnapshot(savedVideos, playlists)
                }
            }
        }
    }

    private fun reloadLibrary() {
        applyLibrarySnapshot(
            savedVideos = libraryRepository.loadSavedVideos(),
            playlists = libraryRepository.loadPlaylists(),
        )
    }

    private fun applyLibrarySnapshot(
        savedVideos: List<VideoUiModel>,
        playlists: List<PlaylistUiModel>,
    ) {
        allVideos = savedVideos
        val saved = allVideos.associateBy(VideoUiModel::id)
        savedVideosById = saved
        fun merge(video: VideoUiModel): VideoUiModel = saved[video.id]?.let { stored ->
            video.copy(
                isWatchLater = stored.isWatchLater,
                isDownloaded = stored.isDownloaded,
                isLiked = stored.isLiked,
                watchProgress = stored.watchProgress,
                lastWatchedAt = stored.lastWatchedAt,
                playlistNames = stored.playlistNames,
            )
        } ?: video
        remoteVideos.replaceAll { _, video -> merge(video) }
        homeFeedCache.replaceAll { _, videos -> videos.map(::merge) }
        _uiState.update { state ->
            state.copy(
                videos = state.videos.map(::merge),
                subscriptionVideos = state.subscriptionVideos.map(::merge),
                home = state.home.copy(videos = state.home.videos.map(::merge)),
                libraryVideos = allVideos,
                playlists = playlists,
                search = state.search.copy(videos = state.search.videos.map(::merge)),
                channelDetail = state.channelDetail.copy(
                    videos = state.channelDetail.videos.map(::merge),
                    shorts = state.channelDetail.shorts.map(::merge),
                    liveStreams = state.channelDetail.liveStreams.map(::merge),
                ),
                remotePlaylistDetail = state.remotePlaylistDetail.copy(
                    videos = state.remotePlaylistDetail.videos.map(::merge),
                ),
                nowPlaying = state.nowPlaying.copy(
                    video = state.nowPlaying.video?.let(::merge),
                    recommendations = state.nowPlaying.recommendations.map(::merge),
                ),
            )
        }
    }

    private fun updateVideoEverywhere(
        state: GrayjayUiState,
        videoId: String,
        transform: (VideoUiModel) -> VideoUiModel,
    ): GrayjayUiState {
        allVideos = allVideos.map { if (it.id == videoId) transform(it) else it }
        savedVideosById = allVideos.associateBy(VideoUiModel::id)
        remoteVideos[videoId]?.let { remoteVideos[videoId] = transform(it) }
        homeFeedCache.replaceAll { _, videos ->
            videos.map { if (it.id == videoId) transform(it) else it }
        }
        return state.copy(
            videos = state.videos.map { if (it.id == videoId) transform(it) else it },
            subscriptionVideos = state.subscriptionVideos.map {
                if (it.id == videoId) transform(it) else it
            },
            home = state.home.copy(
                videos = state.home.videos.map {
                    if (it.id == videoId) transform(it) else it
                },
            ),
            libraryVideos = state.libraryVideos.map {
                if (it.id == videoId) transform(it) else it
            },
            search = state.search.copy(
                videos = state.search.videos.map { if (it.id == videoId) transform(it) else it },
            ),
            channelDetail = state.channelDetail.copy(
                videos = state.channelDetail.videos.map {
                    if (it.id == videoId) transform(it) else it
                },
                shorts = state.channelDetail.shorts.map {
                    if (it.id == videoId) transform(it) else it
                },
                liveStreams = state.channelDetail.liveStreams.map {
                    if (it.id == videoId) transform(it) else it
                },
            ),
            remotePlaylistDetail = state.remotePlaylistDetail.copy(
                videos = state.remotePlaylistDetail.videos.map {
                    if (it.id == videoId) transform(it) else it
                },
            ),
            nowPlaying = state.nowPlaying.copy(
                video = state.nowPlaying.video?.let { if (it.id == videoId) transform(it) else it },
                recommendations = state.nowPlaying.recommendations.map {
                    if (it.id == videoId) transform(it) else it
                },
            ),
        )
    }

    private fun publishLogicalQueue() {
        val logicalIds = playbackQueueSession?.orderedVideoIds?.toList().orEmpty()
        _uiState.update { state ->
            state.copy(
                playback = state.playback.copy(
                    fullQueueVideoIds = logicalIds.ifEmpty { state.playback.queueVideoIds },
                ),
            )
        }
    }

    private fun VideoUiModel.creatorKey(): String = authorUrl.ifBlank {
        channelId.ifBlank { "$sourceId:$creator" }
    }

    private fun registerRemoteChannel(video: VideoUiModel) {
        registerRemoteChannel(channelForVideo(video))
    }

    private fun registerRemoteChannel(channel: ChannelUiModel): ChannelUiModel {
        if (channel.id.isBlank()) return channel
        val previous = remoteChannels[channel.id]
        val merged = if (previous == null) channel else channel.copy(
            thumbnailUrl = channel.thumbnailUrl.ifBlank { previous.thumbnailUrl },
            bannerUrl = channel.bannerUrl.ifBlank { previous.bannerUrl },
            followerCount = channel.followerCount.takeUnless { it == text(R.string.creator) }
                ?: previous.followerCount,
            description = channel.description.ifBlank { previous.description },
            links = channel.links.ifEmpty { previous.links },
        )
        remoteChannels[channel.id] = merged
        return merged
    }

    private fun channelForVideo(video: VideoUiModel): ChannelUiModel {
        val sourceName = video.sourceName.ifBlank {
            _uiState.value.sources.firstOrNull { it.id == video.sourceId }?.name
                ?: engineSources.firstOrNull { it.id == video.sourceId }?.name
            ?: video.sourceId.replaceFirstChar(Char::uppercase)
        }
        return ChannelUiModel(
            id = video.creatorKey(),
            name = video.creator,
            sourceId = video.sourceId,
            source = sourceName,
            unreadCount = 0,
            followerCount = video.authorSubscriberCount?.let { count ->
                quantityText(
                    R.plurals.followers_count,
                    count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    formatCompactCount(count),
                )
            } ?: text(R.string.creator),
            description = "",
            thumbnailUrl = video.authorThumbnailUrl,
        )
    }

    private fun visibleKnownChannels(): List<ChannelUiModel> =
        (content.channels + remoteChannels.values)
            .distinctBy(ChannelUiModel::id)
            .filter { it.sourceId in enabledSourceIds }

    private fun persistCurrentPlaybackProgress() {
        currentPlaybackProgress()?.let { (videoId, progress) ->
            setWatchProgress(videoId, progress)
        }
    }

    private fun persistCurrentPlaybackProgressImmediately() {
        if (_uiState.value.privateSessionEnabled) return
        val (videoId, progress) = currentPlaybackProgress() ?: return
        val currentVideo = _uiState.value.nowPlaying.video?.takeIf { it.id == videoId }
        if (currentVideo != null) libraryRepository.recordHistory(currentVideo, progress)
        else libraryRepository.setWatchProgress(videoId, progress)
    }

    private fun currentPlaybackProgress(): Pair<String, Float>? {
        val durationMs = engine.player.duration.takeIf { it > 0L } ?: return null
        val videoId = engine.player.currentMediaItem?.mediaId
            ?: engine.playback.value.currentVideoId
            ?: return null
        return videoId to (engine.player.currentPosition.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private fun applyPlaybackPreferences() {
        applyPlaybackSpeed(_uiState.value.nowPlaying.video)
        engine.setOtherAudioDucking(
            preferences.otherAudioDuckingEnabled,
            preferences.otherAudioDuckVolumePercent,
        )
        engine.setVideoQuality(preferences.preferredVideoQuality.takeIf { it > 0 })
        if (preferences.stickyCaptionsEnabled && preferences.captionsEnabled) {
            val language = preferences.subtitleLanguage
            if (language == null) engine.setCaptionsEnabled(true)
            else engine.setSubtitleLanguage(language)
        }
    }

    private fun applyPlaybackSpeed(video: VideoUiModel?) {
        val speed = resolvedPlaybackSpeed(
            videoId = video?.id,
            channelId = video?.creatorKey(),
            defaultSpeed = preferences.defaultPlaybackSpeed,
            perChannelEnabled = preferences.perChannelPlaybackSpeedEnabled,
            videoSpeeds = preferences.videoPlaybackSpeeds(),
            channelSpeeds = preferences.channelPlaybackSpeeds(),
        )
        if (chromecastManager.state.value.isConnected) chromecastManager.setPlaybackSpeed(speed)
        else engine.setPlaybackSpeed(speed)
    }

    private fun findVideo(videoId: String): VideoUiModel? {
        val state = _uiState.value
        return remoteVideos[videoId]
            ?: state.channelDetail.videos.firstOrNull { it.id == videoId }
            ?: state.channelDetail.shorts.firstOrNull { it.id == videoId }
            ?: state.channelDetail.liveStreams.firstOrNull { it.id == videoId }
            ?: state.remotePlaylistDetail.videos.firstOrNull { it.id == videoId }
            ?: state.search.videos.firstOrNull { it.id == videoId }
            ?: state.home.videos.firstOrNull { it.id == videoId }
            ?: state.subscriptionVideos.firstOrNull { it.id == videoId }
            ?: state.videos.firstOrNull { it.id == videoId }
            ?: state.libraryVideos.firstOrNull { it.id == videoId }
            ?: savedVideosById[videoId]
            ?: state.nowPlaying.video?.takeIf { it.id == videoId }
            ?: state.nowPlaying.recommendations.firstOrNull { it.id == videoId }
    }
}

private fun PcLinkSnapshot.toUiState(): PcLinkUiState {
    val now = System.currentTimeMillis()
    return PcLinkUiState(
        pairedComputers = pairedComputers.map { computer ->
            PairedComputerUiModel(
                id = computer.id,
                name = computer.name,
                lastSeenAtMs = computer.lastSeenAtMs,
                isConnected = now - computer.lastSeenAtMs <= PcLinkProtocol.STATE_STALE_AFTER_MS,
            )
        },
        activePlayback = activePlayback?.let { playback ->
            PcPlaybackUiModel(
                computerId = playback.computerId,
                computerName = playback.computerName,
                isPlaylist = playback.kind == PcMediaKind.Playlist,
                title = playback.title,
                videoTitle = playback.videoTitle,
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                receivedAtMs = playback.receivedAtMs,
            )
        },
        serverAddresses = serverAddresses,
    )
}

private fun sameYoutubeVideo(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    val firstId = youtubeVideoId(first)
    val secondId = youtubeVideoId(second)
    return if (firstId != null && secondId != null) firstId == secondId
    else first.trimEnd('/') == second.trimEnd('/')
}

private fun youtubeVideoId(value: String): String? = runCatching {
    val uri = Uri.parse(value)
    val host = uri.host.orEmpty().lowercase()
    when {
        host == "youtu.be" || host.endsWith(".youtu.be") ->
            uri.pathSegments.firstOrNull()
        host == "youtube.com" || host.endsWith(".youtube.com") -> when {
            uri.pathSegments.firstOrNull() in setOf("shorts", "live", "embed") ->
                uri.pathSegments.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }
        else -> null
    }?.takeIf(String::isNotBlank)
}.getOrNull()

internal fun VideoUiModel.resumePositionFraction(): Float? = watchProgress
    .takeIf { progress -> !isLive && progress >= 0.002f && progress < 0.95f }

internal fun pluginUrlFromQrContent(content: String): String? {
    val value = content.trim()
    if (value.isBlank()) return null
    return when {
        value.startsWith("grayjay://plugin/", ignoreCase = true) ->
            value.takeIf { it.substringAfter("grayjay://plugin/").isNotBlank() }
        value.startsWith("vfuto://", ignoreCase = true) ->
            value.takeIf { it.substringAfter("vfuto://").isNotBlank() }
        value.startsWith("https://", ignoreCase = true) -> value.takeIf {
            runCatching { URI.create(it).host?.isNotBlank() == true }.getOrDefault(false)
        }
        else -> null
    }
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).trimEnd('0').trimEnd('.')
    else -> value.toString()
}

private fun inferImportedSourceId(url: String): String = when {
    "youtube.com" in url || "youtu.be" in url -> "youtube"
    "odysee.com" in url -> "odysee"
    "rumble.com" in url -> "rumble"
    "twitch.tv" in url -> "twitch"
    "soundcloud.com" in url -> "soundcloud"
    "bilibili.com" in url -> "bilibili"
    "dailymotion.com" in url || "dai.ly" in url -> "dailymotion"
    "bitchute.com" in url -> "bitchute"
    else -> "youtube"
}

internal fun externalContentLabel(url: String): String =
    runCatching {
        Uri.parse(url).lastPathSegment
            ?.let(Uri::decode)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }.getOrNull() ?: url

private const val DOWNLOAD_PREPARATION_TTL_MS = 15L * 60L * 1_000L
private const val STARTUP_BACKGROUND_WORK_DELAY_MS = 750L
private const val PLUGIN_UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L
private const val MAX_GRAYJAY_IMPORT_BYTES = 128 * 1024 * 1024
private const val MAX_NEWPIPE_IMPORT_BYTES = 256 * 1024 * 1024

private fun downloadJobKey(
    profileId: String,
    videoId: String,
    mediaType: DownloadMediaType,
): String = "$profileId\u0000$videoId\u0000${mediaType.name}"

private fun downloadJobPrefix(profileId: String, videoId: String): String =
    "$profileId\u0000$videoId\u0000"

internal fun mergeDownloadSnapshots(
    first: DownloadUiModel,
    second: DownloadUiModel,
): DownloadUiModel {
    require(first.profileId == second.profileId && first.videoId == second.videoId)
    val completed = first.completedMediaTypes + second.completedMediaTypes
    val active = first.activeMediaTypes + second.activeMediaTypes
    val failed = (first.failedMediaTypes + second.failedMediaTypes) - active
    val display = when {
        second.status in DOWNLOAD_ACTIVE_UI_STATES -> second
        first.status in DOWNLOAD_ACTIVE_UI_STATES -> first
        second.status == DownloadStatus.Failed -> second
        else -> first
    }
    val status = when {
        display.status in DOWNLOAD_ACTIVE_UI_STATES -> display.status
        failed.isNotEmpty() -> DownloadStatus.Failed
        completed.isNotEmpty() -> DownloadStatus.Completed
        else -> display.status
    }
    return display.copy(
        status = status,
        completedMediaTypes = completed,
        activeMediaTypes = active,
        failedMediaTypes = failed,
    )
}

private val DOWNLOAD_ACTIVE_UI_STATES = setOf(
    DownloadStatus.Preparing,
    DownloadStatus.Queued,
    DownloadStatus.Downloading,
    DownloadStatus.Paused,
    DownloadStatus.Removing,
)

private fun InputStream.readImportBytes(
    maxBytes: Int,
    tooLargeMessage: String,
): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() + read > maxBytes) {
            throw IllegalArgumentException(tooLargeMessage)
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
