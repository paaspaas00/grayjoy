package com.futo.platformplayer.compose.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.futo.platformplayer.compose.MainActivity
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.downloads.GrayjoyDownloadStore
import com.futo.platformplayer.backend.GrayjayPluginBackend
import com.futo.platformplayer.backend.GrayjayChannelDetails
import com.futo.platformplayer.backend.GrayjayChannelPage
import com.futo.platformplayer.backend.GrayjayPlaylistDetails
import com.futo.platformplayer.backend.GrayjayChannelRequest
import com.futo.platformplayer.backend.GrayjayComment
import com.futo.platformplayer.backend.GrayjaySearchItem
import com.futo.platformplayer.backend.GrayjaySearchChannel
import com.futo.platformplayer.backend.GrayjaySearchPlaylist
import com.futo.platformplayer.backend.GrayjaySearchType
import com.futo.platformplayer.backend.GrayjayStreamType
import com.futo.platformplayer.backend.GrayjayPluginMetadata
import com.futo.platformplayer.backend.PluginEndpoint
import com.futo.platformplayer.backend.formatRelativeDate
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.SearchContentType
import com.futo.platformplayer.compose.ui.SubtitleUiModel
import com.futo.platformplayer.compose.ui.StoryboardLevelUiModel
import com.futo.platformplayer.compose.ui.StoryboardUiModel
import com.futo.platformplayer.compose.ui.VideoCommentUiModel
import com.futo.platformplayer.compose.ui.VideoQualityUiModel
import com.futo.platformplayer.compose.ui.AudioQualityUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.compose.playback.PlaybackNotificationService
import com.futo.platformplayer.compose.playback.AudioSpectrumAnalyzer
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

data class SearchCorpus(
    val videos: List<VideoUiModel>,
    val channels: List<ChannelUiModel>,
    val playlists: List<PlaylistUiModel>,
)

data class EngineSearchResult(
    val videos: List<VideoUiModel>,
    val channels: List<ChannelUiModel>,
    val playlists: List<PlaylistUiModel>,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class EngineVideoPage(
    val videos: List<VideoUiModel> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class EnginePlaybackState(
    val currentVideoId: String? = null,
    val queueVideoIds: List<String> = emptyList(),
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
    val errorMessage: String? = null,
    val audioSpectrum: List<Float> = emptyList(),
)

data class EngineVideoExtras(
    val recommendations: List<VideoUiModel> = emptyList(),
    val comments: List<VideoCommentUiModel> = emptyList(),
    val recommendationsAvailable: Boolean = false,
    val commentsAvailable: Boolean = false,
    val recommendationContinuationId: String? = null,
    val commentsContinuationId: String? = null,
    val hasMoreRecommendations: Boolean = false,
    val hasMoreComments: Boolean = false,
)

data class EngineCommentPage(
    val comments: List<VideoCommentUiModel> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class EngineChannelDetails(
    val channel: ChannelUiModel,
    val videos: List<VideoUiModel>,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
    val supportsShorts: Boolean = false,
    val supportsPlaylists: Boolean = false,
)

data class EngineChannelPage(
    val videos: List<VideoUiModel> = emptyList(),
    val playlists: List<PlaylistUiModel> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class EnginePlaylistDetails(
    val playlist: PlaylistUiModel,
    val videos: List<VideoUiModel>,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

internal fun VideoUiModel.pluginContentUrlOrNull(): String? =
    contentUrl.takeIf(String::isWebUrl)
        ?: id.takeIf(String::isWebUrl)

private fun String.isWebUrl(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

/**
 * Compose-facing boundary for Grayjay's source registry, federated search, and
 * shared player. A full checkout can replace this adapter with StatePlatform
 * and StatePlayer delegates without changing any composables.
 */
interface GrayjayEngine {
    val player: Player
    val playback: StateFlow<EnginePlaybackState>

    fun sources(fallback: List<SourceUiModel>): List<SourceUiModel>
    fun registerSources(sources: List<SourceUiModel>)
    fun setProfile(profileId: String)
    fun reloadSourceAuthentication(sourceId: String)
    fun isSourceAuthenticated(sourceId: String): Boolean
    fun clearSourceAuthentication(sourceId: String)
    suspend fun installSource(configUrl: String): SourceUiModel
    suspend fun trustInstallSource(token: String): SourceUiModel
    fun discardUntrustedSource(token: String)
    fun clearSourceCache(sourceId: String)
    fun removeSource(sourceId: String)
    fun purgePlugin(pluginId: String)
    fun setPluginSettings(pluginId: String, settings: Map<String, String?>)

    suspend fun search(
        query: String,
        enabledSourceIds: Set<String>,
        corpus: SearchCorpus,
        type: SearchContentType = SearchContentType.Videos,
    ): EngineSearchResult
    suspend fun loadMoreSearch(continuationId: String): EngineSearchResult

    suspend fun loadHome(
        feed: HomeFeedType,
        enabledSourceIds: Set<String>,
        followedChannels: List<ChannelUiModel>,
        onSubscriptionProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): EngineVideoPage
    suspend fun loadMoreHome(feed: HomeFeedType, continuationId: String): EngineVideoPage
    suspend fun suggestions(query: String, enabledSourceIds: Set<String>): List<String>
    suspend fun loadChannel(channel: ChannelUiModel): EngineChannelDetails
    suspend fun loadChannelPage(channel: ChannelUiModel, tab: ChannelContentTab): EngineChannelPage
    suspend fun loadMoreChannel(continuationId: String): EngineChannelPage
    suspend fun loadPlaylist(playlist: PlaylistUiModel): EnginePlaylistDetails
    suspend fun resolve(video: VideoUiModel): VideoUiModel
    suspend fun loadExtras(video: VideoUiModel): EngineVideoExtras
    suspend fun loadMoreRecommendations(continuationId: String): EngineVideoPage
    suspend fun loadMoreComments(continuationId: String): EngineCommentPage
    fun open(videos: List<VideoUiModel>, currentVideoId: String, playWhenReady: Boolean)
    fun appendToQueue(videos: List<VideoUiModel>)
    fun togglePlayback()
    fun pausePlayback()
    fun skipToNext()
    fun skipToPrevious()
    fun seekBy(deltaMs: Long)
    fun seekToFraction(fraction: Float)
    fun setPlaybackSpeed(speed: Float)
    fun setOtherAudioDucking(enabled: Boolean, volumePercent: Int)
    fun setVideoQuality(height: Int?)
    fun setCaptionsEnabled(enabled: Boolean)
    fun setSubtitleLanguage(language: String?)
    fun setSubtitleTrack(index: Int?)
    fun retryPlayback()
    fun closePlayback()
    fun refreshProgress()
    fun release()
}

@SuppressLint("UnsafeOptInUsageError")
class AndroidGrayjayEngine(context: Context) : GrayjayEngine {
    private val appContext = context.applicationContext
    private val _playback = MutableStateFlow(EnginePlaybackState())
    @Volatile
    private var latestAudioSpectrum: List<Float> = emptyList()
    private val audioSpectrumAnalyzer = AudioSpectrumAnalyzer { spectrum ->
        latestAudioSpectrum = spectrum
        _playback.value = _playback.value.copy(audioSpectrum = spectrum)
    }
    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildTextRenderers(
            context: Context,
            output: TextOutput,
            outputLooper: Looper,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>,
        ) {
            super.buildTextRenderers(
                context,
                output,
                outputLooper,
                extensionRendererMode,
                out,
            )
            // Grayjay plugins expose external WebVTT/SRT files. SingleSampleMediaSource keeps
            // their timestamps aligned with separately merged DASH video/audio, but Media3 1.9
            // disables that legacy text decoding path by default. Enable it only on the text
            // renderer so the plugin captions are decoded instead of aborting playback.
            out.filterIsInstance<TextRenderer>().forEach { renderer ->
                renderer.experimentalSetLegacyDecodingEnabled(true)
            }
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParameters: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
            .setAudioProcessors(arrayOf(TeeAudioProcessor(audioSpectrumAnalyzer)))
            .build()
    }
    private val exoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        // The default Media3 builder does not request audio focus. Audio can still reach a
        // Bluetooth receiver in that state, but AVRCP/Android Auto may keep another (or empty)
        // session selected, leaving the car without metadata or transport controls. Grayjay's
        // legacy playback service explicitly requested AUDIOFOCUS_GAIN; let ExoPlayer provide
        // the equivalent lifecycle-safe behavior for this shared player.
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    private val otherAudioDuckingController = OtherAudioDuckingController(appContext, exoPlayer)
    private val closeNotificationCommand = SessionCommand(CLOSE_NOTIFICATION_ACTION, Bundle.EMPTY)
    @Suppress("DEPRECATION")
    private val closeNotificationButton = CommandButton.Builder()
        .setSessionCommand(closeNotificationCommand)
        .setCustomIconResId(R.drawable.ic_notification_close)
        .setDisplayName(appContext.getString(R.string.close_playback))
        .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
        .build()
    private val mediaButtonPreferences = listOf(
        closeNotificationButton,
    )
    private val sessionActivity = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    private val mediaSession = MediaSession.Builder(appContext, exoPlayer)
        .setCallback(
            object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                .buildUpon()
                                .add(closeNotificationCommand)
                                .build(),
                        )
                        .setAvailablePlayerCommands(
                            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                                .buildUpon()
                                // DEFAULT_PLAYER_COMMANDS intentionally excludes queue
                                // navigation. Grant the standard commands explicitly so AVRCP,
                                // Android Auto and headset media keys can move through a Grayjoy
                                // playlist instead of being discarded by the session.
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .add(Player.COMMAND_SEEK_TO_NEXT)
                                .build(),
                        )
                        .setCustomLayout(listOf(closeNotificationButton))
                        .setMediaButtonPreferences(mediaButtonPreferences)
                        .build()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    val result = if (customCommand == closeNotificationCommand) {
                        closePlayback()
                        SessionResult.RESULT_SUCCESS
                    } else {
                        SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    }
                    return Futures.immediateFuture(SessionResult(result))
                }
            },
        )
        .setSessionActivity(sessionActivity)
        .setCustomLayout(listOf(closeNotificationButton))
        .setMediaButtonPreferences(mediaButtonPreferences)
        .build()
    private val sourceCatalog = GrayjaySourceCatalog(appContext)
    private val pluginBackend = GrayjayPluginBackend(appContext)
    private val pluginEndpoints = ConcurrentHashMap<String, PluginEndpoint>().apply {
        putAll(officialPluginEndpoints)
    }
    private val downloadStore = GrayjoyDownloadStore.get(appContext)
    private var queueIds: List<String> = emptyList()
    private var lastError: String? = null
    private var captionsEnabled = false
    private var selectedVideoQuality: Int? = null
    private var selectedSubtitleLanguage: String? = null
    private var selectedSubtitleTrackIndex: Int? = null
    private var activePluginDataSources: Set<JSHttpDataSource.Factory> = emptySet()
    private var openedVideos: List<VideoUiModel> = emptyList()
    private var activeQualityVariantHeight: Int? = null
    private var activeQualityVariantVideoId: String? = null

    override val player: Player = exoPlayer
    override val playback: StateFlow<EnginePlaybackState> = _playback.asStateFlow()

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = syncPlayback()

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
                    syncPlayback()

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateAudioSpectrumAnalysis(mediaItem?.mediaId)
                    syncPlayback()
                    PlaybackNotificationService.refresh(appContext)
                }

                override fun onTracksChanged(tracks: Tracks) = syncPlayback()

                override fun onVideoSizeChanged(videoSize: VideoSize) = syncPlayback()

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
                    syncPlayback()

                override fun onPlayerError(error: PlaybackException) {
                    val rootCause = generateSequence<Throwable>(error) { it.cause }.last()
                    Log.e(
                        TAG,
                        "Playback failed: ${rootCause.javaClass.simpleName}: ${rootCause.localizedMessage}",
                    )
                    lastError = rootCause.localizedMessage
                        ?.takeIf(String::isNotBlank)
                        ?: error.localizedMessage
                        ?: appContext.getString(R.string.source_could_not_play)
                    syncPlayback()
                }
            },
        )
    }

    override fun sources(fallback: List<SourceUiModel>): List<SourceUiModel> =
        sourceCatalog.load(fallback)

    override fun registerSources(sources: List<SourceUiModel>) {
        sources.forEach { source ->
            if (
                source.engineId.isNotBlank() &&
                (source.pluginConfigUrl.isNotBlank() || !source.pluginConfigPath.isNullOrBlank())
            ) {
                if (source.isCustom) {
                    pluginBackend.rememberPreviouslyApprovedPlugin(source.engineId)
                }
                pluginEndpoints[source.id] = PluginEndpoint(
                    pluginId = source.engineId,
                    configUrl = source.pluginConfigUrl,
                    iconUrl = source.iconUrl,
                    configAssetPath = source.pluginConfigPath,
                )
            }
        }
    }

    override fun setProfile(profileId: String) = pluginBackend.setProfile(profileId)

    override fun reloadSourceAuthentication(sourceId: String) {
        val endpoint = pluginEndpoints[sourceId] ?: return
        pluginBackend.reloadAuthentication(sourceId, endpoint.pluginId)
    }

    override fun isSourceAuthenticated(sourceId: String): Boolean {
        val endpoint = pluginEndpoints[sourceId] ?: return false
        return pluginBackend.isAuthenticated(endpoint.pluginId)
    }

    override fun clearSourceAuthentication(sourceId: String) {
        val endpoint = pluginEndpoints[sourceId] ?: return
        pluginBackend.clearAuthentication(sourceId, endpoint.pluginId)
    }

    override suspend fun installSource(configUrl: String): SourceUiModel {
        val normalizedUrl = normalizePluginConfigUrl(configUrl)
        return registerInstalledSource(pluginBackend.installPlugin(normalizedUrl))
    }

    override suspend fun trustInstallSource(token: String): SourceUiModel =
        registerInstalledSource(pluginBackend.trustInstallPlugin(token))

    override fun discardUntrustedSource(token: String) {
        pluginBackend.discardUntrustedPlugin(token)
    }

    private fun registerInstalledSource(metadata: GrayjayPluginMetadata): SourceUiModel {
        val sourceId = officialPluginEndpoints.entries
            .firstOrNull { it.value.pluginId == metadata.pluginId }
            ?.key
            ?: pluginEndpoints.entries.firstOrNull { it.value.pluginId == metadata.pluginId }?.key
            ?: metadata.name.toSourceId().let { base ->
                generateSequence(base) { previous ->
                    val suffix = previous.substringAfterLast('-', "1").toIntOrNull()?.plus(1) ?: 2
                    "$base-$suffix"
                }.first { !pluginEndpoints.containsKey(it) }
            }
        pluginEndpoints[sourceId] = PluginEndpoint(
            pluginId = metadata.pluginId,
            configUrl = metadata.configUrl,
            iconUrl = metadata.iconUrl,
        )
        return SourceUiModel(
            id = sourceId,
            engineId = metadata.pluginId,
            name = metadata.name,
            description = metadata.description.ifBlank {
                appContext.getString(R.string.source_description_plugin_default)
            },
            accentColor = sourceAccentColor(sourceId),
            isEnabled = true,
            pluginConfigUrl = metadata.configUrl,
            iconUrl = metadata.iconUrl,
            isCustom = sourceId !in officialPluginEndpoints,
        )
    }

    override fun clearSourceCache(sourceId: String) {
        val endpoint = pluginEndpoints[sourceId] ?: return
        pluginBackend.clearPlugin(sourceId, endpoint.pluginId)
    }

    override fun removeSource(sourceId: String) {
        if (sourceId in officialPluginEndpoints) return
        val endpoint = pluginEndpoints.remove(sourceId) ?: return
        pluginBackend.clearPlugin(sourceId, endpoint.pluginId)
    }

    override fun purgePlugin(pluginId: String) {
        val aliases = pluginEndpoints.entries
            .filter { it.value.pluginId == pluginId }
            .map(Map.Entry<String, PluginEndpoint>::key)
        if (aliases.isEmpty()) {
            pluginBackend.clearPlugin(pluginId, pluginId)
        } else {
            aliases.forEach { alias ->
                pluginEndpoints.remove(alias)
                pluginBackend.clearPlugin(alias, pluginId)
            }
        }
    }

    override fun setPluginSettings(pluginId: String, settings: Map<String, String?>) {
        pluginBackend.setPluginSettings(pluginId, settings)
    }

    override suspend fun search(
        query: String,
        enabledSourceIds: Set<String>,
        corpus: SearchCorpus,
        type: SearchContentType,
    ): EngineSearchResult = withContext(Dispatchers.IO) {
        val endpoints = enabledSourceIds.mapNotNull { sourceId ->
            pluginEndpoints[sourceId]?.let { sourceId to it }
        }.toMap()
        if (endpoints.isEmpty()) {
            val local = searchContent(query, enabledSourceIds, corpus)
            return@withContext when (type) {
                SearchContentType.Videos -> local.copy(channels = emptyList(), playlists = emptyList())
                SearchContentType.Creators -> local.copy(videos = emptyList(), playlists = emptyList())
                SearchContentType.Playlists -> local.copy(videos = emptyList(), channels = emptyList())
            }
        }

        val result = pluginBackend.search(
            query = query,
            enabledSources = endpoints,
            type = when (type) {
                SearchContentType.Videos -> GrayjaySearchType.Videos
                SearchContentType.Creators -> GrayjaySearchType.Creators
                SearchContentType.Playlists -> GrayjaySearchType.Playlists
            },
        )
        EngineSearchResult(
            videos = result.videos.map {
                it.toVideoUiModel(pluginEndpoints[it.sourceId], appContext)
            },
            channels = result.channels.map { it.toChannelUiModel(appContext) },
            playlists = result.playlists.map { it.toPlaylistUiModel(appContext) },
            continuationId = result.continuationId,
            hasMore = result.hasMore,
        )
    }

    override suspend fun loadMoreSearch(continuationId: String): EngineSearchResult {
        val result = pluginBackend.loadMoreSearch(continuationId)
        return EngineSearchResult(
            videos = result.videos.map {
                it.toVideoUiModel(pluginEndpoints[it.sourceId], appContext)
            },
            channels = result.channels.map { it.toChannelUiModel(appContext) },
            playlists = result.playlists.map { it.toPlaylistUiModel(appContext) },
            continuationId = result.continuationId,
            hasMore = result.hasMore,
        )
    }

    override suspend fun loadHome(
        feed: HomeFeedType,
        enabledSourceIds: Set<String>,
        followedChannels: List<ChannelUiModel>,
        onSubscriptionProgress: (completed: Int, total: Int) -> Unit,
    ): EngineVideoPage {
        val endpoints = enabledSourceIds.mapNotNull { sourceId ->
            pluginEndpoints[sourceId]?.let { sourceId to it }
        }.toMap()
        if (endpoints.isEmpty()) return EngineVideoPage()
        val page = if (feed == HomeFeedType.Subscriptions) {
            pluginBackend.subscriptionFeed(
                channels = followedChannels.map { channel ->
                    GrayjayChannelRequest(channel.sourceId, channel.id)
                },
                enabledSources = endpoints,
                onProgress = onSubscriptionProgress,
            )
        } else {
            pluginBackend.home(enabledSources = endpoints)
        }
        return page.toEngineVideoPage(feed)
    }

    override suspend fun loadMoreHome(
        feed: HomeFeedType,
        continuationId: String,
    ): EngineVideoPage = pluginBackend.loadMoreVideos(continuationId).toEngineVideoPage(feed)

    private fun com.futo.platformplayer.backend.GrayjayVideoPage.toEngineVideoPage(
        feed: HomeFeedType,
    ): EngineVideoPage {
        val items = videos
        val visibleItems = when (feed) {
            HomeFeedType.Subscriptions, HomeFeedType.ForYou -> items
            HomeFeedType.Trending -> items.sortedByDescending(GrayjaySearchItem::viewCount)
            HomeFeedType.Live -> items.filter(GrayjaySearchItem::isLive)
        }
        return EngineVideoPage(
            videos = visibleItems
                .distinctBy(GrayjaySearchItem::url)
                .map { it.toVideoUiModel(pluginEndpoints[it.sourceId], appContext) },
            continuationId = continuationId,
            hasMore = hasMore,
        )
    }

    override suspend fun suggestions(
        query: String,
        enabledSourceIds: Set<String>,
    ): List<String> {
        if (query.isBlank()) return emptyList()
        val endpoints = enabledSourceIds.mapNotNull { sourceId ->
            pluginEndpoints[sourceId]?.let { sourceId to it }
        }.toMap()
        return pluginBackend.suggestions(query, endpoints)
    }

    override suspend fun loadChannel(channel: ChannelUiModel): EngineChannelDetails {
        val endpoint = pluginEndpoints[channel.sourceId]
            ?: error(appContext.getString(R.string.source_plugin_unavailable, channel.source))
        val details = pluginBackend.loadChannel(
            sourceId = channel.sourceId,
            channelUrl = channel.id,
            endpoint = endpoint,
        )
        return details.toEngineChannelDetails(channel, endpoint, appContext)
    }

    override suspend fun loadChannelPage(
        channel: ChannelUiModel,
        tab: ChannelContentTab,
    ): EngineChannelPage {
        val endpoint = pluginEndpoints[channel.sourceId]
            ?: error(appContext.getString(R.string.source_plugin_unavailable, channel.source))
        val type = when (tab) {
            ChannelContentTab.Videos -> com.futo.platformplayer.api.media.models.ResultCapabilities.TYPE_VIDEOS
            ChannelContentTab.Shorts -> com.futo.platformplayer.api.media.models.ResultCapabilities.TYPE_SHORTS
            ChannelContentTab.Playlists -> GrayjayPluginBackend.CHANNEL_PLAYLISTS_TYPE
        }
        return pluginBackend.loadChannelPage(channel.sourceId, channel.id, endpoint, type)
            .toEngineChannelPage(endpoint, appContext)
    }

    override suspend fun loadMoreChannel(continuationId: String): EngineChannelPage {
        val page = pluginBackend.loadMoreChannelPage(continuationId)
        // A valid pager continuation is allowed to end with an empty page. ConcurrentHashMap
        // rejects null keys, so never infer-and-index the source in one expression here. Items
        // on non-empty pages still carry their own source id and are converted with that endpoint.
        val pageSourceId = page.videos.firstOrNull()?.sourceId
            ?: page.playlists.firstOrNull()?.sourceId
        val endpoint = pageSourceId?.let(pluginEndpoints::get)
        return page.toEngineChannelPage(endpoint, appContext)
    }

    override suspend fun loadPlaylist(playlist: PlaylistUiModel): EnginePlaylistDetails {
        val endpoint = pluginEndpoints[playlist.sourceId]
            ?: error(appContext.getString(R.string.source_plugin_unavailable, playlist.sourceId))
        return pluginBackend.loadPlaylist(playlist.sourceId, playlist.id, endpoint)
            .toEnginePlaylistDetails(playlist, endpoint, appContext)
    }

    private fun GrayjayChannelPage.toEngineChannelPage(
        endpoint: PluginEndpoint?,
        context: Context,
    ) = EngineChannelPage(
        videos = videos.map { it.toVideoUiModel(endpoint ?: pluginEndpoints[it.sourceId], context) },
        playlists = playlists.map { it.toPlaylistUiModel(context) },
        continuationId = continuationId,
        hasMore = hasMore,
    )

    override suspend fun resolve(video: VideoUiModel): VideoUiModel {
        if (video.playbackUrl.isNotBlank()) return video
        val endpoint = pluginEndpoints[video.sourceId]
            ?: error(appContext.getString(R.string.source_plugin_unavailable, video.sourceId))
        val contentUrl = video.contentUrl.ifBlank { video.id }
        val source = pluginBackend.resolve(video.sourceId, contentUrl, endpoint)
        return video.copy(
            title = source.title.ifBlank { video.title },
            creator = source.author.ifBlank { video.creator },
            metadata = buildString {
                if (source.viewCount > 0) {
                    append(appContext.getString(R.string.views_count_compact, formatCount(source.viewCount)))
                }
                formatRelativeDate(source.datetime).takeIf(String::isNotBlank)?.let {
                    if (isNotEmpty()) append(" • ")
                    append(it)
                }
            }.ifBlank { video.metadata },
            duration = if (source.isLive) {
                appContext.getString(R.string.live)
            } else {
                formatDuration(source.durationSeconds).ifBlank { video.duration }
            },
            isLive = source.isLive,
            isDrmProtected = source.isDrmProtected,
            playbackAudioOnly = source.isAudioOnly,
            playbackHasMuxedAudio = source.videoHasMuxedAudio,
            playbackUrl = source.videoUrl,
            playbackStreamKeys = emptyList(),
            audioStreamKeys = emptyList(),
            playbackMimeType = when (source.streamType) {
                GrayjayStreamType.Hls -> MimeTypes.APPLICATION_M3U8
                GrayjayStreamType.Dash -> MimeTypes.APPLICATION_MPD
                GrayjayStreamType.Progressive -> ""
            },
            playbackManifest = source.rawDashManifest.orEmpty(),
            audioUrl = source.audioUrl.orEmpty(),
            audioRequestHeaders = source.audioRequestHeaders,
            audioDataSourceFactory = source.audioDataSourceFactory,
            audioDownloadUrl = source.audioDownloadUrl.orEmpty(),
            audioDownloadMimeType = when (source.audioDownloadStreamType) {
                GrayjayStreamType.Hls -> MimeTypes.APPLICATION_M3U8
                GrayjayStreamType.Dash -> MimeTypes.APPLICATION_MPD
                GrayjayStreamType.Progressive, null -> ""
            },
            audioDownloadManifest = source.audioDownloadRawDashManifest.orEmpty(),
            audioDownloadRequestHeaders = source.audioDownloadRequestHeaders,
            audioDownloadDataSourceFactory = source.audioDownloadDataSourceFactory,
            playbackRequestHeaders = source.requestHeaders,
            playbackDataSourceFactory = source.dataSourceFactory,
            contentUrl = source.contentUrl,
            thumbnailUrl = source.thumbnailUrl.orEmpty().ifBlank { video.thumbnailUrl },
            description = source.description,
            shareUrl = source.shareUrl,
            authorUrl = source.authorUrl,
            authorThumbnailUrl = source.authorThumbnailUrl.orEmpty(),
            authorSubscriberCount = source.authorSubscribers,
            likeCount = source.likeCount,
            dislikeCount = source.dislikeCount,
            subtitleTracks = source.subtitles.map { subtitle ->
                SubtitleUiModel(
                    name = subtitle.name,
                    language = subtitle.language,
                    uri = subtitle.uri,
                    mimeType = subtitle.mimeType,
                )
            },
            qualityVariants = source.videoVariants.map { variant ->
                VideoQualityUiModel(
                    height = variant.height,
                    playbackUrl = variant.videoUrl,
                    playbackMimeType = when (variant.streamType) {
                        GrayjayStreamType.Hls -> MimeTypes.APPLICATION_M3U8
                        GrayjayStreamType.Dash -> MimeTypes.APPLICATION_MPD
                        GrayjayStreamType.Progressive -> ""
                    },
                    playbackRequestHeaders = variant.requestHeaders,
                    playbackManifest = variant.rawDashManifest.orEmpty(),
                    playbackDataSourceFactory = variant.dataSourceFactory,
                )
            },
            audioQualityVariants = source.audioVariants.map { variant ->
                AudioQualityUiModel(
                    bitrate = variant.bitrate,
                    name = variant.name,
                    playbackUrl = variant.audioUrl,
                    playbackMimeType = when (variant.streamType) {
                        GrayjayStreamType.Hls -> MimeTypes.APPLICATION_M3U8
                        GrayjayStreamType.Dash -> MimeTypes.APPLICATION_MPD
                        GrayjayStreamType.Progressive -> ""
                    },
                    playbackRequestHeaders = variant.requestHeaders,
                    playbackManifest = variant.rawDashManifest.orEmpty(),
                    playbackDataSourceFactory = variant.dataSourceFactory,
                )
            },
            storyboard = source.storyboard?.let { storyboard ->
                StoryboardUiModel(
                    levels = storyboard.levels.map { level ->
                        StoryboardLevelUiModel(
                            width = level.width,
                            height = level.height,
                            frameCount = level.frameCount,
                            columns = level.columns,
                            rows = level.rows,
                            intervalMs = level.intervalMs,
                            sheetUrlTemplate = level.sheetUrlTemplate,
                        )
                    },
                )
            },
        )
    }

    override suspend fun loadExtras(video: VideoUiModel): EngineVideoExtras {
        val endpoint = pluginEndpoints[video.sourceId] ?: return EngineVideoExtras()
        val contentUrl = video.pluginContentUrlOrNull() ?: return EngineVideoExtras()
        val extras = pluginBackend.loadExtras(
            sourceId = video.sourceId,
            contentUrl = contentUrl,
            endpoint = endpoint,
        )
        return EngineVideoExtras(
            recommendations = extras.recommendations.map { item ->
                item.toVideoUiModel(pluginEndpoints[item.sourceId], appContext)
            },
            comments = extras.comments.map { comment ->
                VideoCommentUiModel(
                    author = comment.author,
                    authorThumbnailUrl = comment.authorThumbnailUrl.orEmpty(),
                    message = comment.message,
                    age = comment.age,
                    likeCount = comment.likeCount,
                    replyCount = comment.replyCount,
                )
            },
            recommendationsAvailable = extras.recommendationsAvailable,
            commentsAvailable = extras.commentsAvailable,
            recommendationContinuationId = extras.recommendationContinuationId,
            commentsContinuationId = extras.commentsContinuationId,
            hasMoreRecommendations = extras.hasMoreRecommendations,
            hasMoreComments = extras.hasMoreComments,
        )
    }

    override suspend fun loadMoreRecommendations(continuationId: String): EngineVideoPage {
        val page = pluginBackend.loadMoreRecommendations(continuationId)
        return EngineVideoPage(
            videos = page.videos.map { it.toVideoUiModel(pluginEndpoints[it.sourceId], appContext) },
            continuationId = page.continuationId,
            hasMore = page.hasMore,
        )
    }

    override suspend fun loadMoreComments(continuationId: String): EngineCommentPage {
        val page = pluginBackend.loadMoreComments(continuationId)
        return EngineCommentPage(
            comments = page.comments.map { it.toVideoCommentUiModel() },
            continuationId = page.continuationId,
            hasMore = page.hasMore,
        )
    }

    @UnstableApi
    override fun open(
        videos: List<VideoUiModel>,
        currentVideoId: String,
        playWhenReady: Boolean,
    ) {
        val playableVideos = videos.filter {
            it.playbackUrl.isNotBlank() || it.playbackManifest.isNotBlank()
        }
        val nextPluginDataSources = playableVideos.pluginDataSourceFactories()
        activePluginDataSources
            .filterNot(nextPluginDataSources::contains)
            .forEach(JSHttpDataSource.Factory::closeExecutors)
        activePluginDataSources = nextPluginDataSources
        val currentIndex = playableVideos.indexOfFirst { it.id == currentVideoId }
        if (currentIndex == -1) {
            audioSpectrumAnalyzer.setEnabled(false)
            queueIds = listOf(currentVideoId)
            openedVideos = emptyList()
            activeQualityVariantHeight = null
            activeQualityVariantVideoId = null
            lastError = appContext.getString(R.string.no_playable_media)
            PlaybackNotificationService.dismiss(appContext)
            exoPlayer.clearMediaItems()
            syncPlayback(currentVideoId)
            return
        }

        queueIds = playableVideos.map(VideoUiModel::id)
        openedVideos = playableVideos
        updateAudioSpectrumAnalysis(currentVideoId)
        val automaticVariantHeights = playableVideos.map {
            it.nearestQualityVariantHeight(AUTOMATIC_VIDEO_HEIGHT)
        }
        activeQualityVariantHeight = automaticVariantHeights[currentIndex]
        activeQualityVariantVideoId = currentVideoId.takeIf {
            activeQualityVariantHeight != null
        }
        lastError = null
        val mediaSources = playableVideos.mapIndexed { index, video ->
            video.buildMediaSource(automaticVariantHeights[index])
        }
        captionsEnabled = false
        selectedSubtitleLanguage = null
        selectedSubtitleTrackIndex = null
        selectedVideoQuality = null
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearVideoSizeConstraints()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        exoPlayer.setMediaSources(mediaSources, currentIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        PlaybackNotificationService.show(
            context = appContext,
            player = exoPlayer,
            mediaSession = mediaSession,
            closePlayback = ::closePlayback,
        )
        syncPlayback(currentVideoId)
    }

    @UnstableApi
    override fun appendToQueue(videos: List<VideoUiModel>) {
        if (exoPlayer.mediaItemCount == 0) return
        val existingIds = openedVideos.mapTo(mutableSetOf(), VideoUiModel::id)
        val additions = videos.filter {
            it.id !in existingIds &&
                (it.playbackUrl.isNotBlank() || it.playbackManifest.isNotBlank())
        }
        if (additions.isEmpty()) return

        activePluginDataSources = activePluginDataSources + additions.pluginDataSourceFactories()
        openedVideos = openedVideos + additions
        queueIds = queueIds + additions.map(VideoUiModel::id)
        val targetHeight = selectedVideoQuality ?: AUTOMATIC_VIDEO_HEIGHT
        exoPlayer.addMediaSources(
            additions.map { video ->
                video.buildMediaSource(video.nearestQualityVariantHeight(targetHeight))
            },
        )
        syncPlayback()
        // Playlist entries are appended lazily. Explicitly reattach the foreground notification
        // after the timeline changes so Media3 cannot leave only the MediaSession/AVRCP controls
        // alive while the visible notification disappears.
        PlaybackNotificationService.refresh(appContext)
    }

    private fun VideoUiModel.nearestQualityVariantHeight(targetHeight: Int): Int? =
        qualityVariants.minWithOrNull(
            compareBy<VideoQualityUiModel> { kotlin.math.abs(it.height - targetHeight) }
                .thenBy(VideoQualityUiModel::height),
        )?.height

    private fun List<VideoUiModel>.pluginDataSourceFactories(): Set<JSHttpDataSource.Factory> = (
        mapNotNull { it.playbackDataSourceFactory as? JSHttpDataSource.Factory } +
            mapNotNull { it.audioDataSourceFactory as? JSHttpDataSource.Factory } +
            mapNotNull { it.audioDownloadDataSourceFactory as? JSHttpDataSource.Factory } +
            flatMap { video ->
                video.qualityVariants.mapNotNull {
                    it.playbackDataSourceFactory as? JSHttpDataSource.Factory
                }
            }
        ).toSet()

    private fun VideoUiModel.buildMediaSource(qualityHeight: Int? = null): MediaSource {
        val variant = qualityHeight?.let { selectedHeight ->
            qualityVariants.firstOrNull { it.height == selectedHeight }
        }
        val video = if (variant == null) this else copy(
            playbackUrl = variant.playbackUrl,
            playbackMimeType = variant.playbackMimeType,
            playbackManifest = variant.playbackManifest,
            playbackRequestHeaders = variant.playbackRequestHeaders
                .ifEmpty { playbackRequestHeaders },
            playbackDataSourceFactory = variant.playbackDataSourceFactory,
        )
        val liveLabel = appContext.getString(R.string.live)
        val creatorMetadata = if (video.isLive) {
            listOf(liveLabel, video.creator).filter(String::isNotBlank).joinToString(" • ")
        } else {
            video.creator
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(video.title)
            // Some older AVRCP head units read DISPLAY_TITLE instead of TITLE.
            .setDisplayTitle(video.title)
            .setSubtitle(creatorMetadata)
            .setDescription(
                if (video.isLive) appContext.getString(R.string.live_radio)
                else video.sourceName.ifBlank { video.sourceId },
            )
            .setArtist(creatorMetadata)
            .setAlbumArtist(video.creator)
            .setAlbumTitle(video.sourceName.ifBlank { video.sourceId })
            .setStation(video.title.takeIf { video.isLive })
            .setGenre(appContext.getString(R.string.live_radio).takeIf { video.isLive })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(
                when {
                    video.isLive && video.playbackAudioOnly -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
                    video.playbackAudioOnly -> MediaMetadata.MEDIA_TYPE_MUSIC
                    else -> MediaMetadata.MEDIA_TYPE_VIDEO
                },
            )
            .setArtworkUri(
                video.thumbnailUrl
                    .takeIf {
                        it.isWebUrl() ||
                            it.startsWith("file:///android_asset/") ||
                            it.startsWith("android.resource://")
                    }
                    ?.let(Uri::parse),
            )
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.playbackUrl)
            .setMimeType(video.playbackMimeType.takeIf(String::isNotBlank))
            .setStreamKeys(video.playbackStreamKeys)
            .setMediaMetadata(mediaMetadata)
            .apply {
                if (video.isLive) {
                    setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                }
            }
            .build()
        val streamHosts = STREAM_URL_REGEX.findAll(
            sequenceOf(video.playbackUrl, video.audioUrl, video.playbackManifest).joinToString("\n"),
        ).mapNotNull { match -> Uri.parse(match.value).host }.toSet()
        Log.i(
            TAG,
            "Preparing ${if (video.playbackManifest.isBlank()) "URL" else "raw DASH"} stream " +
                "with hosts=${streamHosts.joinToString()}",
        )
        val factory = video.mediaSourceFactory()
        val videoSource = if (video.playbackManifest.isNotBlank()) {
            val baseUri = Uri.parse(video.playbackUrl.ifBlank { video.contentUrl })
            val manifest = DashManifestParser().parse(
                baseUri,
                ByteArrayInputStream(video.playbackManifest.toByteArray()),
            )
            DashMediaSource.Factory(video.dataSourceFactory()).createMediaSource(manifest, mediaItem)
        } else {
            factory.createMediaSource(mediaItem)
        }
        val sources = buildList {
            add(videoSource)
            if (video.audioUrl.isNotBlank()) {
                val audioVideo = video.copy(
                    playbackUrl = video.audioUrl,
                    playbackMimeType = "",
                    playbackManifest = "",
                    audioUrl = "",
                    playbackCacheNamespace = video.audioCacheNamespace,
                    audioCacheNamespace = "",
                    playbackStreamKeys = video.audioStreamKeys,
                    audioStreamKeys = emptyList(),
                    playbackRequestHeaders = video.audioRequestHeaders,
                    playbackDataSourceFactory = video.audioDataSourceFactory,
                )
                add(
                    audioVideo.mediaSourceFactory().createMediaSource(
                        MediaItem.Builder()
                            .setUri(video.audioUrl)
                            .setStreamKeys(video.audioStreamKeys)
                            .build(),
                    ),
                )
            }
            video.subtitleTracks.forEach { subtitle ->
                val configuration = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                    .setMimeType(subtitle.mimeType.ifBlank { MimeTypes.TEXT_VTT })
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.name)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                add(
                    SingleSampleMediaSource.Factory(video.subtitleDataSourceFactory(subtitle))
                        .createMediaSource(configuration, C.TIME_UNSET),
                )
            }
        }
        return if (sources.size == 1) sources.first() else {
            // Grayjay attaches external captions as independent single-sample sources. Doing the
            // same is essential for generated/raw DASH, whose directly constructed DashMediaSource
            // does not consume MediaItem.subtitleConfigurations.
            MergingMediaSource(true, *sources.toTypedArray())
        }
    }

    private fun VideoUiModel.subtitleDataSourceFactory(subtitle: SubtitleUiModel): DataSource.Factory {
        if (playbackFromDownload && subtitle.cacheNamespace.isNotBlank()) {
            return downloadStore.offlinePlayback(subtitle.cacheNamespace)
        }
        val httpFactory = DefaultHttpDataSource.Factory().setUserAgent(SUBTITLE_USER_AGENT)
        if (subtitle.requestHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(subtitle.requestHeaders)
        }
        return DefaultDataSource.Factory(appContext, httpFactory)
    }

    private fun VideoUiModel.mediaSourceFactory(): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(dataSourceFactory())
    }

    private fun VideoUiModel.dataSourceFactory(): DataSource.Factory {
        // Several long-running radio endpoints use an HTTP entry URL which
        // immediately redirects to an HTTPS CDN. Media3 deliberately rejects
        // cross-protocol redirects unless the client opts in, while browsers
        // and the legacy Grayjay HTTP stack follow these redirects normally.
        val httpFactory = playbackDataSourceFactory
            ?: DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        if (playbackRequestHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(playbackRequestHeaders)
        }
        val upstream = DefaultDataSource.Factory(appContext, httpFactory)
        // Match Grayjay's VideoLocal invariant: only a descriptor reconstructed from a
        // completed download is an offline source. Failed and in-progress downloads must
        // never put the player's ordinary network stream behind the permanent download
        // cache, otherwise a partial DASH representation eventually reaches Media3's
        // PlaceholderDataSource.
        return if (playbackFromDownload) {
            downloadStore.offlinePlayback(playbackCacheNamespace)
        } else upstream
    }

    override fun togglePlayback() {
        if (exoPlayer.mediaItemCount == 0) return
        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekToDefaultPosition()
        if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
        syncPlayback()
    }

    override fun pausePlayback() {
        exoPlayer.pause()
        syncPlayback()
    }

    private companion object {
        const val TAG = "AndroidGrayjayEngine"
        // Official Grayjay maps its default/automatic preferred quality to a 1280x720 target.
        // Treating automatic as "unbounded" made raw plugin sources start at 3K/4K instead.
        const val AUTOMATIC_VIDEO_HEIGHT = 720
        const val CLOSE_NOTIFICATION_ACTION =
            "com.futo.platformplayer.compose.action.CLOSE_PLAYBACK"
        const val SUBTITLE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
        val STREAM_URL_REGEX = Regex("https?://[^\\s<\\\"]+")
    }

    override fun skipToNext() {
        if (!exoPlayer.hasNextMediaItem()) return
        exoPlayer.seekToNextMediaItem()
        exoPlayer.play()
        syncPlayback()
    }

    override fun skipToPrevious() {
        if (exoPlayer.currentPosition > 5_000L || !exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekTo(0L)
        } else {
            exoPlayer.seekToPreviousMediaItem()
        }
        exoPlayer.play()
        syncPlayback()
    }

    override fun seekBy(deltaMs: Long) {
        if (exoPlayer.mediaItemCount == 0) return
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        exoPlayer.seekTo(if (duration == null) target else target.coerceAtMost(duration))
        syncPlayback()
    }

    override fun seekToFraction(fraction: Float) {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        exoPlayer.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        syncPlayback()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
        syncPlayback()
    }

    override fun setOtherAudioDucking(enabled: Boolean, volumePercent: Int) {
        otherAudioDuckingController.configure(enabled, volumePercent)
    }

    override fun setVideoQuality(height: Int?) {
        val requestedHeight = height?.takeIf { it > 0 }
        val currentVideoId = exoPlayer.currentMediaItem?.mediaId
        val currentVideoIndex = openedVideos.indexOfFirst { it.id == currentVideoId }
        val targetHeight = requestedHeight ?: AUTOMATIC_VIDEO_HEIGHT
        val nextVariantHeight = currentVideoIndex
            .takeIf { it >= 0 }
            ?.let { openedVideos[it].nearestQualityVariantHeight(targetHeight) }
        val hasPluginVariant = nextVariantHeight != null

        selectedVideoQuality = requestedHeight
        val nextVariantVideoId = currentVideoId.takeIf { nextVariantHeight != null }
        if (
            (activeQualityVariantHeight != nextVariantHeight ||
                activeQualityVariantVideoId != nextVariantVideoId) &&
            currentVideoIndex >= 0
        ) {
            val currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            val shouldPlay = exoPlayer.playWhenReady
            activeQualityVariantHeight = nextVariantHeight
            activeQualityVariantVideoId = nextVariantVideoId
            val sources = openedVideos.map { video ->
                video.buildMediaSource(video.nearestQualityVariantHeight(targetHeight))
            }
            exoPlayer.setMediaSources(sources, currentVideoIndex, currentPosition)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = shouldPlay
        } else {
            activeQualityVariantHeight = nextVariantHeight
            activeQualityVariantVideoId = nextVariantVideoId
        }

        Log.i(
            TAG,
            "Video quality ${requestedHeight?.let { "${it}p" } ?: "automatic (${AUTOMATIC_VIDEO_HEIGHT}p target)"}" +
                (nextVariantHeight?.let { " selected plugin rendition ${it}p" } ?: " using adaptive constraints"),
        )

        val parameters = exoPlayer.trackSelectionParameters.buildUpon()
        if (hasPluginVariant) {
            parameters.clearVideoSizeConstraints()
        } else {
            parameters.setMaxVideoSize(Int.MAX_VALUE, targetHeight)
        }
        exoPlayer.trackSelectionParameters = parameters.build()
        syncPlayback()
    }

    override fun setCaptionsEnabled(enabled: Boolean) {
        if (enabled) {
            val preferredIndex = selectedSubtitleTrackIndex ?: 0
            setSubtitleTrack(preferredIndex)
        } else {
            setSubtitleTrack(null)
        }
    }

    override fun setSubtitleLanguage(language: String?) {
        if (language == null) {
            setSubtitleTrack(null)
            return
        }
        val currentVideo = openedVideos.firstOrNull { it.id == exoPlayer.currentMediaItem?.mediaId }
        val matchingIndex = currentVideo?.subtitleTracks
            ?.indexOfFirst { it.language.equals(language, ignoreCase = true) }
            ?.takeIf { it >= 0 }
        if (matchingIndex != null) {
            setSubtitleTrack(matchingIndex)
            return
        }
        captionsEnabled = true
        selectedSubtitleLanguage = language
        selectedSubtitleTrackIndex = null
        val parameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        parameters.setPreferredTextLanguage(language)
        exoPlayer.trackSelectionParameters = parameters.build()
        syncPlayback()
    }

    override fun setSubtitleTrack(index: Int?) {
        val currentVideo = openedVideos.firstOrNull { it.id == exoPlayer.currentMediaItem?.mediaId }
        val subtitle = index?.let { currentVideo?.subtitleTracks?.getOrNull(it) }
        captionsEnabled = subtitle != null
        selectedSubtitleLanguage = subtitle?.language
        selectedSubtitleTrackIndex = index.takeIf { subtitle != null }

        val parameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null)
        if (subtitle != null) {
            subtitle.language?.let(parameters::setPreferredTextLanguage)
            val matchingTrack = exoPlayer.currentTracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .flatMap { group ->
                    (0 until group.length).asSequence().map { trackIndex -> group to trackIndex }
                }
                .firstOrNull { (group, trackIndex) ->
                    val format = group.getTrackFormat(trackIndex)
                    (format.label == subtitle.name || subtitle.name.isBlank()) &&
                        (subtitle.language == null ||
                            format.language.equals(subtitle.language, ignoreCase = true))
                }
            matchingTrack?.let { (group, trackIndex) ->
                parameters.setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
                )
            }
        }
        exoPlayer.trackSelectionParameters = parameters.build()
        syncPlayback()
    }

    override fun retryPlayback() {
        lastError = null
        exoPlayer.prepare()
        exoPlayer.play()
        syncPlayback()
    }

    override fun closePlayback() {
        PlaybackNotificationService.dismiss(appContext)
        audioSpectrumAnalyzer.setEnabled(false)
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        activePluginDataSources.forEach(JSHttpDataSource.Factory::closeExecutors)
        activePluginDataSources = emptySet()
        queueIds = emptyList()
        openedVideos = emptyList()
        activeQualityVariantHeight = null
        activeQualityVariantVideoId = null
        lastError = null
        captionsEnabled = false
        selectedSubtitleLanguage = null
        selectedSubtitleTrackIndex = null
        selectedVideoQuality = null
        latestAudioSpectrum = emptyList()
        _playback.value = EnginePlaybackState()
    }

    override fun refreshProgress() = syncPlayback()

    override fun release() {
        PlaybackNotificationService.dismiss(appContext)
        otherAudioDuckingController.release()
        audioSpectrumAnalyzer.setEnabled(false)
        activePluginDataSources.forEach(JSHttpDataSource.Factory::closeExecutors)
        activePluginDataSources = emptySet()
        pluginBackend.release()
        mediaSession.release()
        exoPlayer.release()
    }

    private fun updateAudioSpectrumAnalysis(videoId: String?) {
        val audioOnly = openedVideos.firstOrNull { it.id == videoId }?.playbackAudioOnly == true
        audioSpectrumAnalyzer.setEnabled(audioOnly)
    }

    private fun syncPlayback(fallbackVideoId: String? = null) {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val qualityTracks = exoPlayer.currentTracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).asSequence().map { index ->
                    group.getTrackFormat(index).height to group.isTrackSelected(index)
                }
            }
            .filter { (height, _) -> height > 0 }
            .toList()
        val currentVideoId = exoPlayer.currentMediaItem?.mediaId ?: fallbackVideoId
        val pluginQualities = openedVideos
            .firstOrNull { it.id == currentVideoId }
            ?.qualityVariants
            .orEmpty()
            .map(VideoQualityUiModel::height)
        val availableQualities = (qualityTracks.map(Pair<Int, Boolean>::first) + pluginQualities)
            .distinct()
            .sortedDescending()
        val currentHeight = activeQualityVariantHeight
            .takeIf { activeQualityVariantVideoId == currentVideoId }
            ?: qualityTracks
            .filter(Pair<Int, Boolean>::second)
            .maxOfOrNull(Pair<Int, Boolean>::first)
        val videoSize = exoPlayer.videoSize
        val quarterTurn = videoSize.unappliedRotationDegrees.mod(180) != 0
        val currentVideoWidth = (if (quarterTurn) videoSize.height else videoSize.width)
            .takeIf { it > 0 }
        val currentVideoHeight = (if (quarterTurn) videoSize.width else videoSize.height)
            .takeIf { it > 0 }
        _playback.value = EnginePlaybackState(
            currentVideoId = currentVideoId,
            queueVideoIds = queueIds,
            isPlaying = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED,
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPercentage = exoPlayer.bufferedPercentage.coerceIn(0, 100),
            playbackSpeed = exoPlayer.playbackParameters.speed,
            captionsEnabled = captionsEnabled,
            availableVideoQualities = availableQualities,
            selectedVideoQuality = selectedVideoQuality,
            currentVideoWidth = currentVideoWidth,
            currentVideoHeight = currentVideoHeight ?: currentHeight,
            selectedSubtitleLanguage = selectedSubtitleLanguage,
            selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
            errorMessage = lastError,
            audioSpectrum = latestAudioSpectrum,
        )
    }
}

private fun GrayjaySearchItem.toVideoUiModel(endpoint: PluginEndpoint?, context: Context) = VideoUiModel(
    id = url,
    title = title,
    creator = authorName,
    metadata = buildString {
        if (viewCount > 0) {
            append(context.getString(R.string.views_count_compact, formatCount(viewCount)))
        }
        formatRelativeDate(datetime).takeIf(String::isNotBlank)?.let {
            if (isNotEmpty()) append(" • ")
            append(it)
        }
    },
    duration = if (isLive) context.getString(R.string.live) else formatDuration(durationSeconds),
    channelId = authorUrl,
    sourceId = sourceId,
    isLive = isLive,
    contentUrl = url,
    shareUrl = url,
    authorUrl = authorUrl,
    authorThumbnailUrl = authorThumbnailUrl.orEmpty(),
    thumbnailUrl = thumbnailUrl.orEmpty(),
    sourceName = sourceId.toDisplayName(),
    sourceIconUrl = endpoint?.iconUrl.orEmpty(),
)

private fun GrayjaySearchChannel.toChannelUiModel(context: Context) = ChannelUiModel(
    id = url.ifBlank { id },
    name = name,
    sourceId = sourceId,
    source = sourceId.toDisplayName(),
    unreadCount = 0,
    followerCount = subscribers?.let { count ->
        context.resources.getQuantityString(
            R.plurals.followers_count,
            count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            formatCount(count),
        )
    } ?: context.getString(R.string.creator),
    description = "",
    thumbnailUrl = thumbnailUrl.orEmpty(),
)

private fun GrayjayChannelDetails.toEngineChannelDetails(
    fallback: ChannelUiModel,
    endpoint: PluginEndpoint,
    context: Context,
) = EngineChannelDetails(
    channel = fallback.copy(
        id = fallback.id,
        name = name.ifBlank { fallback.name },
        followerCount = subscribers
            .takeIf { it >= 0 }
            ?.let(::formatCount)
            ?.let { formatted ->
                context.resources.getQuantityString(
                    R.plurals.followers_count,
                    subscribers.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    formatted,
                )
            }
            ?: fallback.followerCount,
        description = description.orEmpty().ifBlank { fallback.description },
        thumbnailUrl = thumbnailUrl.orEmpty().ifBlank { fallback.thumbnailUrl },
        bannerUrl = bannerUrl.orEmpty().ifBlank { fallback.bannerUrl },
        links = links.ifEmpty { fallback.links },
    ),
    videos = videos.map { it.toVideoUiModel(endpoint, context) },
    continuationId = continuationId,
    hasMore = hasMore,
    supportsShorts = supportsShorts,
    supportsPlaylists = supportsPlaylists,
)

private fun GrayjayPlaylistDetails.toEnginePlaylistDetails(
    fallback: PlaylistUiModel,
    endpoint: PluginEndpoint,
    context: Context,
) = EnginePlaylistDetails(
    playlist = playlist.toPlaylistUiModel(context).copy(
        id = fallback.id,
        title = playlist.title.ifBlank { fallback.title },
        description = playlist.toPlaylistUiModel(context).description
            .ifBlank { fallback.description },
        sourceId = fallback.sourceId,
        thumbnailUrl = playlist.thumbnailUrl.orEmpty().ifBlank { fallback.thumbnailUrl },
        videoCount = playlist.videoCount.takeIf { it >= 0 } ?: fallback.videoCount,
    ),
    videos = videos.map { it.toVideoUiModel(endpoint, context) },
    continuationId = continuationId,
    hasMore = hasMore,
)

private fun GrayjayComment.toVideoCommentUiModel() = VideoCommentUiModel(
    author = author,
    authorThumbnailUrl = authorThumbnailUrl.orEmpty(),
    message = message,
    age = age,
    likeCount = likeCount,
    replyCount = replyCount,
)

private fun GrayjaySearchPlaylist.toPlaylistUiModel(context: Context) = PlaylistUiModel(
    id = url.ifBlank { id },
    title = title,
    description = buildString {
        if (author.isNotBlank()) append(author)
        if (videoCount > 0) {
            if (isNotEmpty()) append(" • ")
            append(
                context.resources.getQuantityString(
                    R.plurals.video_count,
                    videoCount,
                    videoCount,
                ),
            )
        }
    }.ifBlank { context.getString(R.string.source_playlist) },
    videoIds = emptyList(),
    sourceId = sourceId,
    thumbnailUrl = thumbnailUrl.orEmpty(),
    videoCount = videoCount.coerceAtLeast(0),
)

private val officialPluginIconUrls = mapOf(
    "youtube" to "https://plugins.grayjay.app/Youtube/youtube.png",
    "odysee" to "https://plugins.grayjay.app/Odysee/OdyseeIcon.png",
    "rumble" to "https://plugins.grayjay.app/Rumble/rumble.png",
    "peertube" to "https://plugins.grayjay.app/PeerTube/peertube.png",
    "soundcloud" to "https://plugins.grayjay.app/Soundcloud/soundcloud.png",
    "twitch" to "https://plugins.grayjay.app/Twitch/twitch.png",
    "kick" to "https://plugins.grayjay.app/Kick/kick.png",
    "patreon" to "https://plugins.grayjay.app/Patreon/patreon_logo.png",
    "nebula" to "https://plugins.grayjay.app/Nebula/NebulaIcon.png",
    "bilibili" to "https://plugins.grayjay.app/Bilibili/BiliBiliIcon.png",
    "dailymotion" to "https://plugins.grayjay.app/Dailymotion/DailymotionIcon.png",
    "bitchute" to "https://plugins.grayjay.app/Bitchute/BitchuteIcon.png",
    "apple-podcasts" to "https://plugins.grayjay.app/ApplePodcasts/ApplePodcastsIcon.png",
    "tedtalks" to "https://plugins.grayjay.app/TedTalks/TedTalksIcon.png",
    "curiositystream" to "https://plugins.grayjay.app/CuriosityStream/CuriosityStreamIcon.png",
    "mixcloud" to "https://plugins.grayjay.app/Mixcloud/MixcloudIcon.png",
    "radiobrowser" to "https://plugins.grayjay.app/RadioBrowser/RadioBrowserIcon.png",
    "redbull-tv" to "https://plugins.grayjay.app/RedbullTv/RedBullTvIcon.png",
    "fosdem" to "https://plugins.grayjay.app/FOSDEM/FOSDEMIcon.png",
    "nasa-plus" to "https://plugins.grayjay.app/NASA-Plus/NASA-PlusIcon.png",
)

internal val officialPluginEndpoints = mapOf(
    "youtube" to PluginEndpoint("35ae969a-a7db-11ed-afa1-0242ac120002", "https://plugins.grayjay.app/Youtube/YoutubeConfig.json"),
    "odysee" to PluginEndpoint("1c05bfc3-08b9-42d0-93d3-6d52e0fd34d8", "https://plugins.grayjay.app/Odysee/OdyseeConfig.json"),
    "rumble" to PluginEndpoint("2ce7b35e-d2b2-4adb-a728-a34a30d30359", "https://plugins.grayjay.app/Rumble/RumbleConfig.json"),
    "peertube" to PluginEndpoint("1c291164-294c-4c2d-800d-7bc6d31d0019", "https://plugins.grayjay.app/PeerTube/PeerTubeConfig.json"),
    "soundcloud" to PluginEndpoint("5fb74e28-2fba-406a-9418-38af04f63c08", "https://plugins.grayjay.app/Soundcloud/SoundcloudConfig.json"),
    "twitch" to PluginEndpoint("c0f315f9-0992-4508-a061-f2738724c331", "https://plugins.grayjay.app/Twitch/TwitchConfig.json"),
    "kick" to PluginEndpoint("4a78c2ff-c20f-43ac-8f75-34515df1d320", "https://plugins.grayjay.app/Kick/KickConfig.json"),
    "patreon" to PluginEndpoint("aac9e9f0-24b5-11ee-be56-0242ac120002", "https://plugins.grayjay.app/Patreon/PatreonConfig.json"),
    "nebula" to PluginEndpoint("9d703ff5-c556-4962-a990-4f000829cb87", "https://plugins.grayjay.app/Nebula/NebulaConfig.json"),
    "bilibili" to PluginEndpoint("cf8ea74d-ad9b-489e-a083-539b6aa8648c", "https://plugins.grayjay.app/Bilibili/BiliBiliConfig.json"),
    "dailymotion" to PluginEndpoint("9c87e8db-e75d-48f4-afe5-2d203d4b95c5", "https://plugins.grayjay.app/Dailymotion/DailymotionConfig.json"),
    "bitchute" to PluginEndpoint("e8b1ad5f-0c6d-497d-a5fa-0a785a16d902", "https://plugins.grayjay.app/Bitchute/BitchuteConfig.json"),
    "apple-podcasts" to PluginEndpoint("89ae4889-0420-4d16-ad6c-19c776b28f99", "https://plugins.grayjay.app/ApplePodcasts/ApplePodcastsConfig.json"),
    "tedtalks" to PluginEndpoint("8d029a7f-5507-4e36-8bd8-c19a3b77d383", "https://plugins.grayjay.app/TedTalks/TedTalksConfig.json"),
    "curiositystream" to PluginEndpoint("273b6523-5438-44e2-9f5d-78e0325a8fd9", "https://plugins.grayjay.app/CuriosityStream/CuriosityStreamConfig.json"),
    "mixcloud" to PluginEndpoint("84331338-b045-419c-88e4-c86036f4cbf5", "https://plugins.grayjay.app/Mixcloud/MixcloudConfig.json"),
    "radiobrowser" to PluginEndpoint("009775f8-9173-48a2-8df3-d730d08d198d", "https://plugins.grayjay.app/RadioBrowser/RadioBrowserConfig.json"),
    "redbull-tv" to PluginEndpoint("5f6658bb-96cc-4965-ba04-c81f8686ab67", "https://plugins.grayjay.app/RedbullTv/RedBullTvConfig.json"),
    "fosdem" to PluginEndpoint("d890ff43-7d9f-4f0e-a52d-239014fd512d", "https://plugins.grayjay.app/FOSDEM/FOSDEMConfig.json"),
    "nasa-plus" to PluginEndpoint("a1b2c3d4-5e6f-7890-abcd-ef1234567890", "https://plugins.grayjay.app/NASA-Plus/NASA-PlusConfig.json"),
).mapValues { (sourceId, endpoint) ->
    endpoint.copy(iconUrl = officialPluginIconUrls[sourceId].orEmpty())
}

private fun normalizePluginConfigUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("grayjay://plugin/", ignoreCase = true) ->
            trimmed.substringAfter("grayjay://plugin/")
        trimmed.startsWith("vfuto://", ignoreCase = true) ->
            "https://${trimmed.substringAfter("vfuto://")}" 
        else -> trimmed
    }
}

private fun String.toSourceId(): String = lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .ifBlank { "custom-source" }

private fun String.toDisplayName(): String =
    split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun sourceAccentColor(sourceId: String): Long =
    0xFF000000L or (sourceId.hashCode().toLong() and 0x00FFFFFFL)

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).trimEnd('0').trimEnd('.')
    value >= 1_000 -> "%.1fK".format(value / 1_000.0).trimEnd('0').trimEnd('.')
    else -> value.toString()
}

internal fun searchContent(
    query: String,
    enabledSourceIds: Set<String>,
    corpus: SearchCorpus,
): EngineSearchResult {
    val needle = query.trim().lowercase()
    if (needle.isEmpty() || enabledSourceIds.isEmpty()) return EngineSearchResult(
        videos = emptyList(),
        channels = emptyList(),
        playlists = emptyList(),
    )

    val sourceVideos = corpus.videos.filter { it.sourceId in enabledSourceIds }
    val videoIds = sourceVideos.mapTo(hashSetOf(), VideoUiModel::id)

    fun rank(vararg values: String): Int {
        val normalized = values.map(String::lowercase)
        return when {
            normalized.any { it == needle } -> 0
            normalized.any { it.startsWith(needle) } -> 1
            normalized.any { it.contains(needle) } -> 2
            else -> Int.MAX_VALUE
        }
    }

    val videos = sourceVideos
        .map { it to rank(it.title, it.creator, it.metadata) }
        .filter { it.second != Int.MAX_VALUE }
        .sortedWith(compareBy<Pair<VideoUiModel, Int>> { it.second }.thenBy { it.first.title })
        .map(Pair<VideoUiModel, Int>::first)

    val channels = corpus.channels
        .filter { it.sourceId in enabledSourceIds }
        .map { it to rank(it.name, it.source, it.description) }
        .filter { it.second != Int.MAX_VALUE }
        .sortedWith(compareBy<Pair<ChannelUiModel, Int>> { it.second }.thenBy { it.first.name })
        .map(Pair<ChannelUiModel, Int>::first)

    val playlists = corpus.playlists
        .map { playlist -> playlist.copy(videoIds = playlist.videoIds.filter(videoIds::contains)) }
        .filter { it.videoIds.isNotEmpty() }
        .map { playlist ->
            val videoTitles = sourceVideos
                .filter { it.id in playlist.videoIds }
                .joinToString(" ", transform = VideoUiModel::title)
            playlist to rank(playlist.title, playlist.description, videoTitles)
        }
        .filter { it.second != Int.MAX_VALUE }
        .sortedWith(compareBy<Pair<PlaylistUiModel, Int>> { it.second }.thenBy { it.first.title })
        .map(Pair<PlaylistUiModel, Int>::first)

    return EngineSearchResult(videos = videos, channels = channels, playlists = playlists)
}
