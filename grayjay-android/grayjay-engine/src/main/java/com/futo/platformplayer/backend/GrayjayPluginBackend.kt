package com.futo.platformplayer.backend

import android.content.Context
import android.util.Log
import androidx.media3.datasource.HttpDataSource
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.IPlatformChannelContent
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.ratings.RatingScaler
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IWidevineSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.PlatformMultiClientPool
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.PlatformContentPager
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestMergingRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSAudioUrlRangeSource
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlRangeSource
import com.futo.platformplayer.builders.DashBuilder
import com.futo.platformplayer.engine.exceptions.ScriptLoginRequiredException
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class GrayjaySearchItem(
    val id: String,
    val url: String,
    val sourceId: String,
    val pluginId: String,
    val title: String,
    val authorName: String,
    val authorUrl: String,
    val authorThumbnailUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val viewCount: Long,
    val datetime: OffsetDateTime?,
    val isLive: Boolean,
    val playbackTimeSeconds: Long = -1,
    val playbackDate: OffsetDateTime? = null,
)

data class GrayjaySearchChannel(
    val id: String,
    val url: String,
    val sourceId: String,
    val pluginId: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscribers: Long?,
)

data class GrayjayChannelDetails(
    val url: String,
    val name: String,
    val thumbnailUrl: String?,
    val bannerUrl: String?,
    val subscribers: Long,
    val description: String?,
    val links: Map<String, String>,
    val videos: List<GrayjaySearchItem>,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
    val supportsShorts: Boolean = false,
    val supportsPlaylists: Boolean = false,
)

data class GrayjayChannelPage(
    val videos: List<GrayjaySearchItem> = emptyList(),
    val playlists: List<GrayjaySearchPlaylist> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class GrayjayPlaylistDetails(
    val playlist: GrayjaySearchPlaylist,
    val videos: List<GrayjaySearchItem>,
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

enum class GrayjayUserImportStage {
    Connecting,
    Subscriptions,
    History,
    Playlists,
}

data class GrayjayUserImportSelection(
    val subscriptions: Boolean = true,
    val history: Boolean = true,
    val playlists: Boolean = true,
    val likedVideos: Boolean = true,
)

data class GrayjayUserImportProgress(
    val stage: GrayjayUserImportStage,
    val completed: Int = 0,
    val total: Int? = null,
)

data class GrayjayUserImportResult(
    val subscriptions: List<GrayjaySearchChannel> = emptyList(),
    val history: List<GrayjaySearchItem> = emptyList(),
    val playlists: List<GrayjayPlaylistDetails> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class GrayjayChannelRequest(
    val sourceId: String,
    val url: String,
)

data class GrayjaySearchPlaylist(
    val id: String,
    val url: String,
    val sourceId: String,
    val pluginId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val videoCount: Int,
)

data class GrayjayPluginSearchResult(
    val videos: List<GrayjaySearchItem> = emptyList(),
    val channels: List<GrayjaySearchChannel> = emptyList(),
    val playlists: List<GrayjaySearchPlaylist> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class GrayjayVideoPage(
    val videos: List<GrayjaySearchItem> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

enum class GrayjaySearchType { Videos, Creators, Playlists }

enum class GrayjayUrlKind { Video, Channel, Playlist }

data class GrayjayUrlRoute(
    val sourceId: String,
    val pluginId: String,
    val kind: GrayjayUrlKind,
)

data class GrayjayPlaybackSource(
    val contentUrl: String,
    val shareUrl: String,
    val videoUrl: String,
    val streamType: GrayjayStreamType,
    val audioUrl: String? = null,
    val audioRequestHeaders: Map<String, String> = emptyMap(),
    val audioDataSourceFactory: HttpDataSource.Factory? = null,
    val audioDownloadUrl: String? = null,
    val audioDownloadStreamType: GrayjayStreamType? = null,
    val audioDownloadRawDashManifest: String? = null,
    val audioDownloadRequestHeaders: Map<String, String> = emptyMap(),
    val audioDownloadDataSourceFactory: HttpDataSource.Factory? = null,
    val title: String,
    val author: String,
    val authorUrl: String,
    val authorThumbnailUrl: String? = null,
    val authorSubscribers: Long? = null,
    val description: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0,
    val viewCount: Long = 0,
    val datetime: OffsetDateTime? = null,
    val likeCount: Long? = null,
    val dislikeCount: Long? = null,
    val subtitles: List<GrayjaySubtitleTrack> = emptyList(),
    val requestHeaders: Map<String, String> = emptyMap(),
    val rawDashManifest: String? = null,
    val dataSourceFactory: HttpDataSource.Factory? = null,
    val videoVariants: List<GrayjayVideoVariant> = emptyList(),
    val audioVariants: List<GrayjayAudioVariant> = emptyList(),
    val storyboard: GrayjayStoryboard? = null,
    val isDrmProtected: Boolean = false,
    val isLive: Boolean = false,
    val isAudioOnly: Boolean = false,
    /** The selected video representation already contains its audio track. */
    val videoHasMuxedAudio: Boolean = false,
)

enum class GrayjayStreamType { Hls, Dash, Progressive }

data class GrayjayVideoVariant(
    val height: Int,
    val videoUrl: String,
    val streamType: GrayjayStreamType,
    val requestHeaders: Map<String, String> = emptyMap(),
    val rawDashManifest: String? = null,
    val dataSourceFactory: HttpDataSource.Factory? = null,
)

data class GrayjayAudioVariant(
    val bitrate: Int,
    val name: String,
    val audioUrl: String,
    val streamType: GrayjayStreamType,
    val requestHeaders: Map<String, String> = emptyMap(),
    val rawDashManifest: String? = null,
    val dataSourceFactory: HttpDataSource.Factory? = null,
)

data class GrayjaySubtitleTrack(
    val name: String,
    val language: String?,
    val uri: String,
    val mimeType: String,
)

data class GrayjayComment(
    val author: String,
    val authorThumbnailUrl: String?,
    val message: String,
    val age: String,
    val likeCount: Long?,
    val replyCount: Int?,
)

data class GrayjayContentExtras(
    val recommendations: List<GrayjaySearchItem>,
    val comments: List<GrayjayComment>,
    val recommendationsAvailable: Boolean,
    val commentsAvailable: Boolean,
    val recommendationContinuationId: String? = null,
    val commentsContinuationId: String? = null,
    val hasMoreRecommendations: Boolean = false,
    val hasMoreComments: Boolean = false,
)

data class GrayjayCommentPage(
    val comments: List<GrayjayComment> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

data class GrayjayPluginMetadata(
    val pluginId: String,
    val name: String,
    val description: String,
    val configUrl: String,
    val iconUrl: String,
    val version: Int,
    val warnings: List<String>,
)

data class GrayjayUntrustedPlugin(
    val token: String,
    val pluginId: String,
    val pluginName: String,
    val publisher: String,
    val publisherUrl: String,
    val configUrl: String,
    val publicKeyFingerprint: String,
)

class GrayjaySignatureMismatchException(
    val plugin: GrayjayUntrustedPlugin,
) : SecurityException("Plugin signature validation failed for ${plugin.pluginName}.")

private data class PendingUntrustedPlugin(
    val config: SourcePluginConfig,
    val configText: String,
    val scriptText: String,
)

private enum class PagerContentKind { Videos, Creators, Playlists, Comments }

private data class SourcePagerSession(
    val sourceId: String,
    val pluginId: String,
    val pager: IPager<*>,
    val emittedKeys: MutableSet<String> = linkedSetOf(),
    var failed: Boolean = false,
)

private data class PagerSession(
    val kind: PagerContentKind,
    val sources: List<SourcePagerSession>,
    val newestFirst: Boolean = false,
    val mutex: Mutex = Mutex(),
)

private data class PagerBatch(
    val videos: List<GrayjaySearchItem> = emptyList(),
    val channels: List<GrayjaySearchChannel> = emptyList(),
    val playlists: List<GrayjaySearchPlaylist> = emptyList(),
    val comments: List<GrayjayComment> = emptyList(),
    val continuationId: String? = null,
    val hasMore: Boolean = false,
)

private data class CachedStoryboard(
    val storyboard: GrayjayStoryboard,
    val cachedAtMs: Long,
)

/**
 * Headless host for Grayjay's existing JSClient/V8 plugin machinery. Only the
 * legacy Fragment/View dependencies are replaced; plugin execution, packages,
 * HTTP bridge, model conversion, paging, and URL resolution are unchanged.
 */
class GrayjayPluginBackend(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder().build()
    private val clients = ConcurrentHashMap<String, JSClient>()
    private val sourceAliases = ConcurrentHashMap<String, String>()
    private val pendingUntrustedPlugins = ConcurrentHashMap<String, PendingUntrustedPlugin>()
    private val pagerSessions = ConcurrentHashMap<String, PagerSession>()
    // JS execution is protected by one lock per client. Running subscription coroutines against
    // the same client therefore remains completely serial. Legacy Grayjay uses a six-client pool
    // for channel refreshes; share that machinery here so independent RSS/channel requests can
    // actually progress concurrently without making the plugin runtime unsafe.
    private val subscriptionClientPool = PlatformMultiClientPool(
        "Compose subscriptions",
        SUBSCRIPTION_CONCURRENCY,
    )
    // Match StatePlatform's user-critical pool. A plugin call is synchronous once it enters V8,
    // so cancelling its coroutine cannot necessarily stop an in-flight browser/network promise.
    // Keeping two credentialed clients prevents that stale call from blocking the next video for
    // the V8 lock timeout (65 seconds).
    private val mainClientPool = PlatformMultiClientPool(
        "Compose main",
        MAIN_CLIENT_CONCURRENCY,
    )
    private val storyboardCache = ConcurrentHashMap<String, CachedStoryboard>()
    private val storyboardFailures = ConcurrentHashMap<String, Long>()
    private val loadMutex = Mutex()
    @Volatile
    private var profileId: String = "main"
    private val pluginDirectory = File(appContext.filesDir, "grayjay-js-plugins").apply { mkdirs() }
    private val pluginSettings = appContext.getSharedPreferences(
        "grayjay-js-plugin-settings",
        Context.MODE_PRIVATE,
    )
    private val pluginTrust = appContext.getSharedPreferences(
        "grayjay-js-plugin-trust",
        Context.MODE_PRIVATE,
    )

    init {
        StateApp.instance.attach(appContext)
    }

    suspend fun search(
        query: String,
        enabledSources: Map<String, PluginEndpoint>,
        type: GrayjaySearchType = GrayjaySearchType.Videos,
        pageSize: Int = 30,
    ): GrayjayPluginSearchResult = withContext(Dispatchers.IO) {
        val outcomes = coroutineScope {
            enabledSources.entries.map { (alias, endpoint) ->
                async {
                    runCatching {
                        val plugin = getOrLoad(alias, endpoint)
                        val pager = when (type) {
                            GrayjaySearchType.Videos -> plugin.search(query)
                            GrayjaySearchType.Creators -> if (plugin.capabilities.hasChannelSearch) {
                                plugin.searchChannelsAsContent(query)
                            } else null
                            GrayjaySearchType.Playlists -> if (plugin.capabilities.hasSearchPlaylists) {
                                plugin.searchPlaylists(query)
                            } else null
                        }
                        pager?.let { SourcePagerSession(alias, plugin.id, it) }
                    }.onFailure { error ->
                        Log.e(TAG, "Search failed for source $alias (${endpoint.pluginId}).", error)
                    }
                }
            }.awaitAll()
        }
        if (outcomes.isNotEmpty() && outcomes.all { it.isFailure }) {
            throw outcomes.firstNotNullOf { it.exceptionOrNull() }
        }
        val session = PagerSession(
            kind = when (type) {
                GrayjaySearchType.Videos -> PagerContentKind.Videos
                GrayjaySearchType.Creators -> PagerContentKind.Creators
                GrayjaySearchType.Playlists -> PagerContentKind.Playlists
            },
            sources = outcomes.mapNotNull { it.getOrNull() },
        )
        readNewSession(session, pageSize).toSearchResult()
    }

    suspend fun loadMoreSearch(
        continuationId: String,
        pageSize: Int = 30,
    ): GrayjayPluginSearchResult = withContext(Dispatchers.IO) {
        readExistingSession(continuationId, pageSize).toSearchResult()
    }

    /**
     * Migrates sources installed by the first consent-dialog build, which cached
     * the approved bytes but did not yet persist their digest. Only the already
     * cached payload is adopted; future changed bytes still need fresh consent.
     */
    fun rememberPreviouslyApprovedPlugin(pluginId: String) {
        if (pluginTrust.contains(pluginId)) return
        val (configText, scriptText) = cachedPlugin(pluginId) ?: return
        val config = runCatching { SourcePluginConfig.fromJson(configText, "") }.getOrNull() ?: return
        if (
            config.id == pluginId &&
            pluginSignatureState(config.scriptSignature, config.scriptPublicKey) ==
                PluginSignatureState.Signed &&
            !config.validate(scriptText)
        ) {
            trustPluginPayload(pluginId, configText, scriptText)
        }
    }

    private data class LoadedPluginPayload(
        val config: SourcePluginConfig,
        val configText: String,
        val scriptText: String,
    )

    private fun validatePluginPayload(
        endpoint: PluginEndpoint,
        payload: Pair<String, String>,
    ): LoadedPluginPayload {
        val (configText, scriptText) = payload
        val config = SourcePluginConfig.fromJson(configText, endpoint.configUrl)
        require(config.id == endpoint.pluginId) {
            "Plugin ID mismatch for ${config.name}: expected ${endpoint.pluginId}, received ${config.id}."
        }
        when (pluginSignatureState(config.scriptSignature, config.scriptPublicKey)) {
            PluginSignatureState.Unsigned -> Unit
            PluginSignatureState.Incomplete -> error(
                "Plugin signature metadata is incomplete for ${config.name}.",
            )
            PluginSignatureState.Signed -> {
                require(
                    config.validate(scriptText) ||
                        isTrustedPluginPayload(config.id, configText, scriptText),
                ) { "Plugin signature validation failed for ${config.name}." }
            }
        }
        return LoadedPluginPayload(config, configText, scriptText)
    }

    private fun loadPluginPayload(endpoint: PluginEndpoint): LoadedPluginPayload {
        endpoint.configAssetPath?.let { assetPath ->
            return validatePluginPayload(endpoint, loadEmbeddedPlugin(assetPath))
        }
        val cached = cachedPlugin(endpoint.pluginId)
        val downloaded = runCatching { downloadPlugin(endpoint.configUrl) }
        val downloadedPayload = downloaded.mapCatching { validatePluginPayload(endpoint, it) }
        downloadedPayload.getOrNull()?.let { return it }

        val cachedPayload = cached?.let { runCatching { validatePluginPayload(endpoint, it) } }
        cachedPayload?.getOrNull()?.let { return it }

        throw downloadedPayload.exceptionOrNull()
            ?: cachedPayload?.exceptionOrNull()
            ?: downloaded.exceptionOrNull()
            ?: error("No plugin payload is available for ${endpoint.pluginId}.")
    }

    /**
     * Loads a plugin shipped in the APK through the same JSClient path as downloaded plugins.
     * Keeping this in the backend (instead of special-casing station data in Compose) means the
     * source remains a real Grayjay JavaScript plugin with the standard models and capabilities.
     */
    private fun loadEmbeddedPlugin(configAssetPath: String): Pair<String, String> {
        val normalizedConfigPath = configAssetPath.normalizedAssetPath()
        val configText = appContext.assets.open(normalizedConfigPath)
            .bufferedReader()
            .use { it.readText() }
        val config = SourcePluginConfig.fromJson(configText)
        val scriptUrl = config.scriptUrl.trim()
        require(!scriptUrl.contains("://")) {
            "Embedded plugin ${config.name} must use a relative script URL."
        }
        val configDirectory = normalizedConfigPath.substringBeforeLast('/', "")
        val scriptPath = sequenceOf(configDirectory, scriptUrl.removePrefix("./"))
            .filter(String::isNotBlank)
            .joinToString("/")
            .normalizedAssetPath()
        val scriptText = appContext.assets.open(scriptPath)
            .bufferedReader()
            .use { it.readText() }
        return configText to scriptText
    }

    private fun String.normalizedAssetPath(): String {
        val normalized = replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." }) {
            "Invalid embedded plugin asset path."
        }
        return normalized
    }

    private fun isTrustedPluginPayload(
        pluginId: String,
        configText: String,
        scriptText: String,
    ): Boolean = pluginTrust.getString(pluginId, null) == pluginPayloadDigest(configText, scriptText)

    private fun trustPluginPayload(pluginId: String, configText: String, scriptText: String) {
        check(
            pluginTrust.edit()
                .putString(pluginId, pluginPayloadDigest(configText, scriptText))
                .commit(),
        ) { "Could not remember the source trust decision." }
    }

    private fun clearPluginTrust(pluginId: String) {
        pluginTrust.edit().remove(pluginId).apply()
    }

    private fun pluginPayloadDigest(configText: String, scriptText: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(configText.toByteArray())
        digest.update(0)
        digest.update(scriptText.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        private const val TAG = "GrayjayPluginBackend"
        private const val YOUTUBE_PLUGIN_ID = "35ae969a-a7db-11ed-afa1-0242ac120002"
        private const val YOUTUBE_LIKED_PLAYLIST_ID = "LL"
        private const val YOUTUBE_LIKED_PLAYLIST_URL =
            "https://www.youtube.com/playlist?list=$YOUTUBE_LIKED_PLAYLIST_ID"
        private const val MAX_USER_IMPORT_SUBSCRIPTIONS = 5_000
        private const val MAX_USER_IMPORT_PLAYLISTS = 2_000
        private const val MAX_USER_IMPORT_PLAYLIST_VIDEOS = 20_000
        private const val MAX_USER_IMPORT_HISTORY = 20_000
        private const val MAX_USER_IMPORT_PAGES = 2_000
        private const val SUBSCRIPTION_CONCURRENCY = 6
        private const val MAIN_CLIENT_CONCURRENCY = 2
        const val CHANNEL_PLAYLISTS_TYPE = "PLAYLISTS"
        private const val STORYBOARD_CACHE_TTL_MS = 30L * 60L * 1_000L
        private const val STORYBOARD_FAILURE_TTL_MS = 2L * 60L * 1_000L
        private const val STORYBOARD_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"
        private val YOUTUBE_VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{6,32}")
    }

    suspend fun suggestions(
        query: String,
        enabledSources: Map<String, PluginEndpoint>,
        limit: Int = 12,
    ): List<String> = withContext(Dispatchers.IO) {
        enabledSources.entries.flatMap { (alias, endpoint) ->
            runCatching { getOrLoad(alias, endpoint).searchSuggestions(query).asList() }
                .getOrDefault(emptyList())
        }.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .take(limit)
            .toList()
    }

    suspend fun home(
        enabledSources: Map<String, PluginEndpoint>,
        pageSize: Int = 30,
    ): GrayjayVideoPage = withContext(Dispatchers.IO) {
        val outcomes = coroutineScope {
            enabledSources.entries.map { (sourceId, endpoint) ->
                async {
                    runCatching {
                        val plugin = getOrLoad(sourceId, endpoint)
                        if (!plugin.enableInHome) return@runCatching null
                        SourcePagerSession(sourceId, plugin.id, plugin.getHome())
                    }.onFailure { error ->
                        Log.e(TAG, "Home failed for source $sourceId (${endpoint.pluginId}).", error)
                    }
                }
            }.awaitAll()
        }
        if (outcomes.isNotEmpty() && outcomes.all { it.isFailure }) {
            throw outcomes.firstNotNullOf { it.exceptionOrNull() }
        }
        readNewSession(
            PagerSession(
                kind = PagerContentKind.Videos,
                sources = outcomes.mapNotNull { it.getOrNull() },
            ),
            pageSize,
        ).toVideoPage()
    }

    suspend fun subscriptionFeed(
        channels: List<GrayjayChannelRequest>,
        enabledSources: Map<String, PluginEndpoint>,
        perChannelLimit: Int = 12,
        resultLimit: Int = 80,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): GrayjayVideoPage = withContext(Dispatchers.IO) {
        val requests = channels
            .filter { it.url.isNotBlank() && it.sourceId in enabledSources }
            .distinctBy { "${it.sourceId}:${it.url}" }
        val requestOrdinals = requests
            .groupBy(GrayjayChannelRequest::sourceId)
            .values
            .flatMap { sourceRequests ->
                sourceRequests.mapIndexed { index, request ->
                    "${request.sourceId}:${request.url}" to index
                }
            }
            .toMap()
        val completedRequests = AtomicInteger(0)
        val fullRequests = AtomicInteger(0)
        val peekRequests = AtomicInteger(0)
        onProgress(0, requests.size)
        val outcomes = coroutineScope {
            requests.map { request ->
                async {
                    val outcome = runCatching {
                        val endpoint = requireNotNull(enabledSources[request.sourceId])
                        val basePlugin = getOrLoad(request.sourceId, endpoint)
                        val plugin = subscriptionClientPool.getClientPooled(
                            basePlugin,
                            SUBSCRIPTION_CONCURRENCY,
                        )
                        val ordinal = requestOrdinals["${request.sourceId}:${request.url}"] ?: 0
                        val rateLimit = basePlugin.getSubscriptionRateLimit()
                            ?.takeIf { it > 0 }
                            ?: Int.MAX_VALUE
                        val peekType = if (
                            ordinal >= rateLimit && basePlugin.capabilities.hasPeekChannelContents
                        ) {
                            basePlugin.getPeekChannelTypes().let { types ->
                                when {
                                    ResultCapabilities.TYPE_MIXED in types ->
                                        ResultCapabilities.TYPE_MIXED
                                    ResultCapabilities.TYPE_VIDEOS in types ->
                                        ResultCapabilities.TYPE_VIDEOS
                                    else -> null
                                }
                            }
                        } else null
                        val pager = if (peekType != null) {
                            peekRequests.incrementAndGet()
                            PlatformContentPager(
                                plugin.peekChannelContents(request.url, peekType),
                                perChannelLimit.coerceAtLeast(1),
                            )
                        } else {
                            fullRequests.incrementAndGet()
                            plugin.getChannelContents(request.url)
                        }
                        SourcePagerSession(
                            request.sourceId,
                            basePlugin.id,
                            pager,
                        )
                    }.onFailure { error ->
                        Log.e(TAG, "Subscription feed failed for ${request.url}.", error)
                    }
                    onProgress(completedRequests.incrementAndGet(), requests.size)
                    outcome
                }
            }.awaitAll()
        }
        Log.i(
            TAG,
            "Subscription refresh completed with ${fullRequests.get()} full and " +
                "${peekRequests.get()} lightweight peek requests.",
        )
        if (outcomes.isNotEmpty() && outcomes.all { it.isFailure }) {
            throw outcomes.firstNotNullOf { it.exceptionOrNull() }
        }
        readNewSession(
            PagerSession(
                kind = PagerContentKind.Videos,
                sources = outcomes.mapNotNull { it.getOrNull() },
                newestFirst = true,
            ),
            minOf(perChannelLimit, resultLimit),
        ).toVideoPage(limit = resultLimit)
    }

    suspend fun loadMoreVideos(
        continuationId: String,
        pageSize: Int = 30,
    ): GrayjayVideoPage = withContext(Dispatchers.IO) {
        readExistingSession(continuationId, pageSize).toVideoPage()
    }

    suspend fun loadMoreChannelPage(
        continuationId: String,
        pageSize: Int = 30,
    ): GrayjayChannelPage = withContext(Dispatchers.IO) {
        readExistingSession(continuationId, pageSize).toChannelPage()
    }

    suspend fun loadChannel(
        sourceId: String,
        channelUrl: String,
        endpoint: PluginEndpoint,
        videoLimit: Int = 30,
    ): GrayjayChannelDetails = withContext(Dispatchers.IO) {
        require(channelUrl.isNotBlank()) { "This video did not provide a creator URL." }
        val plugin = getOrLoad(sourceId, endpoint)
        val channel = plugin.getChannel(channelUrl)
        val capabilities = plugin.getChannelCapabilities()
        val resolvedUrl = channel.url.ifBlank { channelUrl }
        val pager = if (capabilities.hasType(ResultCapabilities.TYPE_VIDEOS)) {
            plugin.getChannelContents(resolvedUrl, ResultCapabilities.TYPE_VIDEOS)
        } else {
            channel.getContents(plugin)
        }
        val page = readNewSession(
            PagerSession(
                kind = PagerContentKind.Videos,
                sources = listOf(SourcePagerSession(sourceId, plugin.id, pager)),
            ),
            videoLimit,
        )

        GrayjayChannelDetails(
            url = channel.url.ifBlank { channelUrl },
            name = channel.name,
            thumbnailUrl = channel.thumbnail,
            bannerUrl = channel.banner,
            subscribers = channel.subscribers,
            description = channel.description,
            links = channel.links,
            videos = page.videos,
            continuationId = page.continuationId,
            hasMore = page.hasMore,
            supportsShorts = capabilities.hasType(ResultCapabilities.TYPE_SHORTS),
            supportsPlaylists = plugin.capabilities.hasGetChannelPlaylists,
        )
    }

    suspend fun loadChannelPage(
        sourceId: String,
        channelUrl: String,
        endpoint: PluginEndpoint,
        type: String,
        pageSize: Int = 30,
    ): GrayjayChannelPage = withContext(Dispatchers.IO) {
        val plugin = getOrLoad(sourceId, endpoint)
        val isPlaylists = type == CHANNEL_PLAYLISTS_TYPE
        val pager = if (isPlaylists) {
            require(plugin.capabilities.hasGetChannelPlaylists) {
                "This source does not expose channel playlists."
            }
            plugin.getChannelPlaylists(channelUrl)
        } else {
            plugin.getChannelContents(channelUrl, type)
        }
        val page = readNewSession(
            PagerSession(
                kind = if (isPlaylists) PagerContentKind.Playlists else PagerContentKind.Videos,
                sources = listOf(SourcePagerSession(sourceId, plugin.id, pager)),
            ),
            pageSize,
        )
        page.toChannelPage()
    }

    suspend fun loadPlaylist(
        sourceId: String,
        playlistUrl: String,
        endpoint: PluginEndpoint,
        videoLimit: Int = 30,
    ): GrayjayPlaylistDetails = withContext(Dispatchers.IO) {
        require(playlistUrl.isNotBlank()) { "The source playlist URL is missing." }
        val plugin = getOrLoad(sourceId, endpoint)
        require(plugin.capabilities.hasGetPlaylist) {
            "This source does not support opening playlists."
        }
        val playlist = plugin.getPlaylist(playlistUrl)
        val page = readNewSession(
            PagerSession(
                kind = PagerContentKind.Videos,
                sources = listOf(SourcePagerSession(sourceId, plugin.id, playlist.contents)),
            ),
            videoLimit,
        )
        GrayjayPlaylistDetails(
            playlist = playlist.toSearchPlaylist(sourceId, plugin.id),
            videos = page.videos,
            continuationId = page.continuationId,
            hasMore = page.hasMore,
        )
    }

    /**
     * Imports the authenticated account through the source's own migration APIs. This is the same
     * machinery used by legacy Grayjay: no YouTube page markup is scraped in Compose, and the
     * plugin remains responsible for authenticated requests, cookies, continuations, and models.
     */
    suspend fun importUserData(
        sourceId: String,
        endpoint: PluginEndpoint,
        selection: GrayjayUserImportSelection,
        onProgress: (GrayjayUserImportProgress) -> Unit = {},
    ): GrayjayUserImportResult = withContext(Dispatchers.IO) {
        require(isAuthenticated(endpoint.pluginId)) {
            "Sign in to this source before importing account data."
        }
        onProgress(GrayjayUserImportProgress(GrayjayUserImportStage.Connecting))
        val plugin = getOrLoad(sourceId, endpoint)
        val warnings = mutableListOf<String>()

        val subscriptions = if (
            selection.subscriptions &&
            plugin.capabilities.hasGetUserSubscriptions
        ) {
            val urls = runCatching {
                plugin.getUserSubscriptions()
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .take(MAX_USER_IMPORT_SUBSCRIPTIONS)
                    .toList()
            }.onFailure { error ->
                Log.w(TAG, "Could not list account subscriptions.", error)
                warnings += "Subscriptions: ${error.userImportMessage()}"
            }.getOrDefault(emptyList())
            onProgress(
                GrayjayUserImportProgress(
                    GrayjayUserImportStage.Subscriptions,
                    completed = 0,
                    total = urls.size,
                ),
            )
            buildList {
                urls.forEachIndexed { index, url ->
                    runCatching { plugin.getChannel(url) }
                        .onSuccess { channel ->
                            add(
                                GrayjaySearchChannel(
                                    id = channel.url.ifBlank { channel.id.value.orEmpty() },
                                    url = channel.url.ifBlank { url },
                                    sourceId = sourceId,
                                    pluginId = plugin.id,
                                    name = channel.name,
                                    thumbnailUrl = channel.thumbnail,
                                    subscribers = channel.subscribers.takeIf { it >= 0 },
                                ),
                            )
                        }
                        .onFailure { error ->
                            Log.w(TAG, "Could not import subscription $url.", error)
                            warnings += "Subscription $url: ${error.userImportMessage()}"
                        }
                    onProgress(
                        GrayjayUserImportProgress(
                            GrayjayUserImportStage.Subscriptions,
                            completed = index + 1,
                            total = urls.size,
                        ),
                    )
                }
            }
        } else {
            if (selection.subscriptions) {
                warnings += "Subscriptions: this source does not support account import."
            }
            emptyList()
        }

        val history = if (selection.history && plugin.capabilities.hasGetUserHistory) {
            runCatching { plugin.getUserHistory() }
                .onFailure { error ->
                    Log.w(TAG, "Could not open account watch history.", error)
                    warnings += "Watch history: ${error.userImportMessage()}"
                }
                .getOrNull()
                ?.let { pager ->
                    drainVideoPager(
                        pager = pager,
                        maxItems = MAX_USER_IMPORT_HISTORY,
                        maxPages = MAX_USER_IMPORT_PAGES,
                        onProgress = { count ->
                            onProgress(
                                GrayjayUserImportProgress(
                                    GrayjayUserImportStage.History,
                                    completed = count,
                                ),
                            )
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Watch history import stopped after a partial page.", error)
                            warnings += "Watch history: ${error.userImportMessage()}"
                        },
                    ).map { it.toSearchItem(sourceId, plugin.id) }
                }
                .orEmpty()
        } else {
            if (selection.history) {
                warnings += "Watch history: this source does not support account import."
            }
            emptyList()
        }

        val playlistUrls = if (
            selection.playlists &&
            plugin.capabilities.hasGetUserPlaylists &&
            plugin.capabilities.hasGetPlaylist
        ) {
            runCatching {
                plugin.getUserPlaylists()
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filterNot { it.youtubePlaylistId() == YOUTUBE_LIKED_PLAYLIST_ID }
                    .distinct()
                    .take(MAX_USER_IMPORT_PLAYLISTS)
                    .toMutableList()
            }.onFailure { error ->
                Log.w(TAG, "Could not list account playlists.", error)
                warnings += "Playlists: ${error.userImportMessage()}"
            }.getOrDefault(mutableListOf())
        } else {
            if (selection.playlists) {
                warnings += "Playlists: this source does not support account import."
            }
            mutableListOf()
        }
        if (
            selection.likedVideos &&
            endpoint.pluginId == YOUTUBE_PLUGIN_ID &&
            plugin.capabilities.hasGetPlaylist
        ) {
            playlistUrls += YOUTUBE_LIKED_PLAYLIST_URL
        }

        onProgress(
            GrayjayUserImportProgress(
                GrayjayUserImportStage.Playlists,
                completed = 0,
                total = playlistUrls.size,
            ),
        )
        val playlists = buildList {
            playlistUrls.distinct().forEachIndexed { index, url ->
                runCatching {
                    val playlist = plugin.getPlaylist(url)
                    val videos = drainVideoPager(
                        pager = playlist.contents,
                        maxItems = MAX_USER_IMPORT_PLAYLIST_VIDEOS,
                        maxPages = MAX_USER_IMPORT_PAGES,
                        onFailure = { error ->
                            Log.w(TAG, "Playlist $url stopped after a partial page.", error)
                            warnings += "Playlist $url: ${error.userImportMessage()}"
                        },
                    ).map { it.toSearchItem(sourceId, plugin.id) }
                    val playlistModel = playlist.toSearchPlaylist(sourceId, plugin.id)
                    GrayjayPlaylistDetails(
                        playlist = playlistModel.copy(
                            id = playlistModel.id.ifBlank { url },
                            url = playlistModel.url.ifBlank { url },
                        ),
                        videos = videos,
                    )
                }.onSuccess(::add)
                    .onFailure { error ->
                        Log.w(TAG, "Could not import playlist $url.", error)
                        // A YouTube account may have no liked videos or may keep them private.
                        // That is not fatal to the rest of the account migration.
                        warnings += "Playlist $url: ${error.userImportMessage()}"
                    }
                onProgress(
                    GrayjayUserImportProgress(
                        GrayjayUserImportStage.Playlists,
                        completed = index + 1,
                        total = playlistUrls.size,
                    ),
                )
            }
        }

        GrayjayUserImportResult(
            subscriptions = subscriptions.distinctBy(GrayjaySearchChannel::url),
            history = history.distinctBy(GrayjaySearchItem::url),
            playlists = playlists.distinctBy { it.playlist.url },
            warnings = warnings,
        )
    }

    /**
     * Asks each enabled plugin whether it owns [url], using the same video -> channel -> playlist
     * priority as legacy Grayjay's StatePlatform URL router. URL predicates are plugin code, so
     * this also supports custom sources received through Android's Share sheet.
     */
    suspend fun routeUrl(
        url: String,
        endpoints: Map<String, PluginEndpoint>,
    ): GrayjayUrlRoute? = withContext(Dispatchers.IO) {
        endpoints.entries.firstNotNullOfOrNull { (sourceId, endpoint) ->
            val plugin = runCatching { getOrLoad(sourceId, endpoint) }
                .onFailure { error ->
                    Log.w(TAG, "Could not load $sourceId while routing an external URL.", error)
                }
                .getOrNull()
                ?: return@firstNotNullOfOrNull null
            val kind = when {
                runCatching { plugin.isContentDetailsUrl(url) }.getOrDefault(false) ->
                    GrayjayUrlKind.Video
                runCatching { plugin.isChannelUrl(url) }.getOrDefault(false) ->
                    GrayjayUrlKind.Channel
                runCatching { plugin.isPlaylistUrl(url) }.getOrDefault(false) ->
                    GrayjayUrlKind.Playlist
                else -> null
            }
            kind?.let { GrayjayUrlRoute(sourceId, plugin.id, it) }
        }
    }

    suspend fun resolve(
        sourceId: String,
        contentUrl: String,
        endpoint: PluginEndpoint,
    ): GrayjayPlaybackSource = withContext(Dispatchers.IO) {
        if (endpoint.pluginId == YOUTUBE_PLUGIN_ID && !isAuthenticated(endpoint.pluginId)) {
            val settings = loadPluginSettings(endpoint.pluginId)
            if (settings.remove("composeLegacyAgeFallback") != null) {
                setPluginSettings(endpoint.pluginId, settings)
            }
        }
        var plugin = mainClientPool.getClientPooled(
            getOrLoad(sourceId, endpoint),
            MAIN_CLIENT_CONCURRENCY,
        ) as JSClient
        val detailsResult = runCatching { plugin.getContentDetails(contentUrl) }
        val rawDetails = detailsResult.getOrElse { error ->
            if (
                endpoint.pluginId == YOUTUBE_PLUGIN_ID &&
                error is ScriptLoginRequiredException &&
                error.message.orEmpty().contains("confirm your age", ignoreCase = true)
            ) {
                val updatedSettings = loadPluginSettings(endpoint.pluginId).apply {
                    put("allowAgeRestricted", "true")
                    put("composeLegacyAgeFallback", "true")
                }
                setPluginSettings(endpoint.pluginId, updatedSettings)
                plugin = mainClientPool.getClientPooled(
                    getOrLoad(sourceId, endpoint),
                    MAIN_CLIENT_CONCURRENCY,
                ) as JSClient
                runCatching { plugin.getContentDetails(contentUrl) }.getOrElse { fallbackError ->
                    Log.w(TAG, "Anonymous age-restriction fallback failed; source login is required.", fallbackError)
                    val restoredSettings = loadPluginSettings(endpoint.pluginId).apply {
                        remove("composeLegacyAgeFallback")
                    }
                    setPluginSettings(endpoint.pluginId, restoredSettings)
                    throw error
                }
            } else {
                throw error
            }
        }
        val details = rawDetails as? IPlatformVideoDetails
            ?: error("The source returned content that is not playable video.")
        // This is deliberately independent of the plugin result model: current YouTube
        // plugins do not expose storyboard metadata. Run the watch-page lookup alongside
        // stream normalization so it does not add a second serial network wait.
        val storyboardDeferred = if (
            endpoint.pluginId == YOUTUBE_PLUGIN_ID && details.duration > 0L
        ) {
            async { loadYouTubeStoryboard(contentUrl, details.duration) }
        } else {
            null
        }

        val descriptorSources = details.video.videoSources
        val hlsSource = details.hls ?: descriptorSources
            .filterIsInstance<IHLSManifestSource>()
            .filter { it.url.isNotBlank() }
            .bestSource()
        val dashSource = details.dash ?: descriptorSources
            .filterIsInstance<IDashManifestSource>()
            .filter { it.url.isNotBlank() }
            .bestSource()
        val liveSource = details.live as? IVideoUrlSource
        val hlsUrl = hlsSource?.url?.takeIf(String::isNotBlank)
        val dashUrl = dashSource?.url?.takeIf(String::isNotBlank)
        val liveUrl = liveSource
            ?.getVideoUrl()
            ?.takeIf(String::isNotBlank)
        val manifestUrl = hlsUrl ?: dashUrl ?: liveUrl
        val unMuxedDescriptor = details.video as? VideoUnMuxedSourceDescriptor
        val rawVideoSources = if (manifestUrl == null) {
            descriptorSources.filterIsInstance<JSDashManifestRawSource>()
                .filterNot { it is IWidevineSource }
                .filter { it.url?.isNotBlank() == true || it.manifest?.isNotBlank() == true || it.hasGenerate }
        } else {
            emptyList()
        }
        val rawVideoSource = rawVideoSources.bestSource()
        val rawAudioSource = unMuxedDescriptor
            ?.audioSources
            ?.filterIsInstance<JSDashManifestRawAudioSource>()
            ?.maxWithOrNull(
                compareBy<JSDashManifestRawAudioSource> { it.priority }
                    .thenBy { it.bitrate },
            )
        val rawDashSource = if (rawVideoSource != null && rawAudioSource != null) {
            rawVideoSource.getUnderlyingPlugin()?.busy {
                JSDashManifestMergingRawSource(rawVideoSource, rawAudioSource)
            } ?: rawVideoSource
        } else {
            rawVideoSource
        }
        val rawDashManifest = rawDashSource
            ?.generate()
            ?.takeIf(String::isNotBlank)
        val rawDataSourceFactory = rawDashSource?.dataSourceFactoryOrNull()
        val video = if (manifestUrl == null && rawDashManifest == null) {
            descriptorSources.bestVideoUrl()
        } else {
            null
        }
        val audioUrlSource = unMuxedDescriptor
            ?.audioSources
            ?.filterIsInstance<IAudioUrlSource>()
            ?.filterNot { it is IWidevineSource }
            ?.maxWithOrNull(compareBy<IAudioUrlSource> { it.priority }.thenBy { it.bitrate })
        val hlsAudioSource = unMuxedDescriptor
            ?.audioSources
            ?.filterIsInstance<IHLSManifestAudioSource>()
            ?.filterNot { it is IWidevineSource }
            ?.filter { it.url.isNotBlank() }
            ?.maxWithOrNull(compareBy<IHLSManifestAudioSource> { it.priority }.thenBy { it.bitrate })
        val playbackAudioSource: IAudioSource? = audioUrlSource ?: hlsAudioSource
        val audioDataSourceFactory = playbackAudioSource?.dataSourceFactoryOrNull()
        val audioRequestUrl = when (playbackAudioSource) {
            is IAudioUrlSource -> playbackAudioSource.getAudioUrl()
            is IHLSManifestAudioSource -> playbackAudioSource.url
            else -> null
        }
        val audioRequest = audioRequestUrl?.takeIf(String::isNotBlank)?.let { url ->
            if (audioDataSourceFactory == null) requireNotNull(playbackAudioSource).resolveRequest(url)
            else ResolvedRequest(url, emptyMap())
        }
        val audio = audioRequest?.url
        val rawAudioDownloadManifest = rawAudioSource
            ?.generate()
            ?.takeIf(String::isNotBlank)
        val rawAudioDownloadFactory = rawAudioSource?.dataSourceFactoryOrNull()
        val rawAudioDownloadUrl = rawAudioSource?.url
            ?.takeIf(String::isNotBlank)
            ?: contentUrl.takeIf { rawAudioDownloadManifest != null }
        val audioDownloadUrl = rawAudioDownloadUrl ?: audio
        val audioDownloadStreamType = when {
            rawAudioDownloadManifest != null -> GrayjayStreamType.Dash
            playbackAudioSource is IHLSManifestAudioSource -> GrayjayStreamType.Hls
            audioDownloadUrl != null -> inferStreamType(audioDownloadUrl)
            else -> null
        }
        val rangeDashManifest = video?.rangeDashManifestOrNull(audioUrlSource)
        val subtitles = details.subtitles.mapNotNull { subtitle ->
            try {
                subtitle.getSubtitlesURI()?.toString()?.takeIf(String::isNotBlank)?.let { uri ->
                    GrayjaySubtitleTrack(
                        name = subtitle.name,
                        language = subtitle.language,
                        uri = uri,
                        mimeType = subtitleMimeType(subtitle.format),
                    )
                }
            } catch (_: Throwable) {
                null
            }
        }
        val (likeCount, dislikeCount) = details.rating.counts()

        // A plugin can return one URL per rendition instead of one adaptive
        // manifest. Keep every distinct rendition so the Compose player can
        // display and select the complete plugin-provided quality list.
        val urlVideoVariants = descriptorSources
            .filterIsInstance<IVideoUrlSource>()
            .filterNot { it is IHLSManifestSource || it is IDashManifestSource }
            .filterNot { it is JSDashManifestRawSource }
            .filterNot { it is IWidevineSource }
            .filter { it.height > 0 && it.getVideoUrl().isNotBlank() }
            .groupBy(IVideoSource::height)
            .values
            .mapNotNull { sameHeight ->
                sameHeight.maxWithOrNull(
                    compareBy<IVideoUrlSource> { it.priority }
                        .thenBy { it.bitrate ?: 0 },
                )
            }
            .map { source ->
                val dataSourceFactory = source.dataSourceFactoryOrNull()
                val rangeManifest = source.rangeDashManifestOrNull(audioUrlSource)
                val variantRequest = if (dataSourceFactory == null) {
                    source.resolveRequest(source.getVideoUrl())
                } else {
                    ResolvedRequest(source.getVideoUrl(), emptyMap())
                }
                GrayjayVideoVariant(
                    height = source.height,
                    videoUrl = variantRequest.url,
                    streamType = if (rangeManifest == null) {
                        inferStreamType(variantRequest.url)
                    } else {
                        GrayjayStreamType.Dash
                    },
                    requestHeaders = variantRequest.headers,
                    rawDashManifest = rangeManifest,
                    dataSourceFactory = dataSourceFactory,
                )
            }
            .sortedByDescending(GrayjayVideoVariant::height)
        val rawVideoVariants = rawVideoSources
            .filter { it.height > 0 }
            .groupBy(IVideoSource::height)
            .values
            .mapNotNull { sameHeight ->
                sameHeight.maxWithOrNull(
                    compareBy<JSDashManifestRawSource> { it.priority }
                        .thenBy { it.bitrate ?: 0 },
                )
            }
            .mapNotNull { rawSource ->
                val variantSource = if (rawSource === rawVideoSource) {
                    rawDashSource ?: rawSource
                } else if (rawAudioSource != null) {
                    rawSource.getUnderlyingPlugin()?.busy {
                        JSDashManifestMergingRawSource(rawSource, rawAudioSource)
                    } ?: rawSource
                } else {
                    rawSource
                }
                val manifest = if (rawSource === rawVideoSource) {
                    rawDashManifest
                } else {
                    runCatching { variantSource.generate() }.getOrNull()
                }?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                GrayjayVideoVariant(
                    height = rawSource.height,
                    videoUrl = variantSource.url?.takeIf(String::isNotBlank) ?: contentUrl,
                    streamType = GrayjayStreamType.Dash,
                    rawDashManifest = manifest,
                    dataSourceFactory = variantSource.dataSourceFactoryOrNull(),
                )
            }
            .sortedByDescending(GrayjayVideoVariant::height)
        val videoVariants = (rawVideoVariants + urlVideoVariants)
            .distinctBy(GrayjayVideoVariant::height)
            .sortedByDescending(GrayjayVideoVariant::height)

        // Preserve the plugin's distinct audio renditions just like video renditions. Old
        // Grayjay selects the source nearest the requested target bitrate; collapsing this list
        // here made an audio-quality picker impossible in the Compose UI.
        val audioVariants = unMuxedDescriptor
            ?.audioSources
            .orEmpty()
            .filterNot { it is IWidevineSource }
            .filter {
                it is JSDashManifestRawAudioSource ||
                    it is IAudioUrlSource ||
                    it is IHLSManifestAudioSource
            }
            .groupBy { it.bitrate }
            .values
            .mapNotNull { sameBitrate ->
                sameBitrate.maxWithOrNull(
                    compareBy<IAudioSource> { if (it.priority) 1 else 0 }
                        .thenBy { if (it.original) 1 else 0 },
                )
            }
            .mapNotNull { source ->
                when (source) {
                    is JSDashManifestRawAudioSource -> {
                        val manifest = runCatching { source.generate() }
                            .getOrNull()
                            ?.takeIf(String::isNotBlank)
                            ?: return@mapNotNull null
                        GrayjayAudioVariant(
                            bitrate = source.bitrate,
                            name = source.name,
                            audioUrl = source.url?.takeIf(String::isNotBlank) ?: contentUrl,
                            streamType = GrayjayStreamType.Dash,
                            rawDashManifest = manifest,
                            dataSourceFactory = source.dataSourceFactoryOrNull(),
                        )
                    }
                    is IAudioUrlSource -> {
                        val factory = source.dataSourceFactoryOrNull()
                        val request = if (factory == null) {
                            source.resolveRequest(source.getAudioUrl())
                        } else {
                            ResolvedRequest(source.getAudioUrl(), emptyMap())
                        }
                        GrayjayAudioVariant(
                            bitrate = source.bitrate,
                            name = source.name,
                            audioUrl = request.url,
                            streamType = inferStreamType(request.url),
                            requestHeaders = request.headers,
                            dataSourceFactory = factory,
                        )
                    }
                    is IHLSManifestAudioSource -> {
                        val factory = source.dataSourceFactoryOrNull()
                        val request = if (factory == null) {
                            source.resolveRequest(source.url)
                        } else {
                            ResolvedRequest(source.url, emptyMap())
                        }
                        GrayjayAudioVariant(
                            bitrate = source.bitrate,
                            name = source.name,
                            audioUrl = request.url,
                            streamType = GrayjayStreamType.Hls,
                            requestHeaders = request.headers,
                            dataSourceFactory = factory,
                        )
                    }
                    else -> null
                }
            }
            .filter { it.audioUrl.isNotBlank() }
            .distinctBy(GrayjayAudioVariant::bitrate)
            .sortedByDescending(GrayjayAudioVariant::bitrate)

        val selectedVideoSource: IVideoSource? =
            hlsSource ?: dashSource ?: liveSource ?: rawDashSource ?: video
        val isAudioOnly = selectedVideoSource == null && playbackAudioSource != null
        require(selectedVideoSource != null || isAudioOnly) {
            "The plugin returned no supported video or audio stream."
        }
        val playbackDashManifest = if (isAudioOnly) null else rawDashManifest ?: rangeDashManifest
        val requestUrl = if (isAudioOnly) {
            requireNotNull(audioRequest).url
        } else {
            manifestUrl
                ?: rawDashSource?.url?.takeIf(String::isNotBlank)
                ?: video?.getVideoUrl()
                ?: contentUrl
        }
        val selectedDataSourceFactory = if (isAudioOnly) {
            audioDataSourceFactory
        } else {
            rawDataSourceFactory ?: requireNotNull(selectedVideoSource).dataSourceFactoryOrNull()
        }
        // Custom request modifiers are stateful and must see every manifest, segment, and byte
        // range request. Keep the original URL here and let the playback data source invoke them.
        val request = if (isAudioOnly) {
            requireNotNull(audioRequest)
        } else if (selectedDataSourceFactory == null) {
            requireNotNull(selectedVideoSource).resolveRequest(requestUrl)
        } else {
            ResolvedRequest(requestUrl, emptyMap())
        }
        val resolvedVideoUrl = request.url
        val streamType = when {
            isAudioOnly && playbackAudioSource is IHLSManifestAudioSource -> GrayjayStreamType.Hls
            hlsUrl != null -> GrayjayStreamType.Hls
            dashUrl != null || playbackDashManifest != null -> GrayjayStreamType.Dash
            else -> inferStreamType(resolvedVideoUrl)
        }

        GrayjayPlaybackSource(
            contentUrl = details.url.ifBlank { contentUrl },
            shareUrl = details.shareUrl.ifBlank { details.url.ifBlank { contentUrl } },
            videoUrl = resolvedVideoUrl,
            streamType = streamType,
            audioUrl = audio.takeIf {
                !isAudioOnly &&
                rangeDashManifest == null && rawDashManifest == null
            },
            audioRequestHeaders = audioRequest?.headers.orEmpty(),
            audioDataSourceFactory = audioDataSourceFactory,
            audioDownloadUrl = audioDownloadUrl,
            audioDownloadStreamType = audioDownloadStreamType,
            audioDownloadRawDashManifest = rawAudioDownloadManifest,
            audioDownloadRequestHeaders = if (rawAudioDownloadManifest != null) {
                emptyMap()
            } else audioRequest?.headers.orEmpty(),
            audioDownloadDataSourceFactory = rawAudioDownloadFactory ?: audioDataSourceFactory,
            title = details.name,
            author = details.author.name,
            authorUrl = details.author.url,
            authorThumbnailUrl = details.author.thumbnail,
            authorSubscribers = details.author.subscribers,
            description = details.description,
            thumbnailUrl = details.thumbnails.getHQThumbnail(),
            durationSeconds = details.duration,
            viewCount = details.viewCount,
            datetime = details.datetime,
            likeCount = likeCount,
            dislikeCount = dislikeCount,
            subtitles = subtitles,
            requestHeaders = request.headers,
            rawDashManifest = playbackDashManifest,
            dataSourceFactory = selectedDataSourceFactory,
            videoVariants = videoVariants,
            audioVariants = audioVariants,
            storyboard = storyboardDeferred?.await(),
            isDrmProtected = selectedVideoSource is IWidevineSource && videoVariants.isEmpty(),
            isLive = details.isLive,
            isAudioOnly = isAudioOnly,
            videoHasMuxedAudio = !isAudioOnly && !details.video.isUnMuxed,
        )
    }

    suspend fun loadExtras(
        sourceId: String,
        contentUrl: String,
        endpoint: PluginEndpoint,
        recommendationLimit: Int = 20,
        commentLimit: Int = 40,
    ): GrayjayContentExtras = withContext(Dispatchers.IO) {
        val plugin = mainClientPool.getClientPooled(
            getOrLoad(sourceId, endpoint),
            MAIN_CLIENT_CONCURRENCY,
        )
        val details = plugin.getContentDetails(contentUrl) as? IPlatformVideoDetails
            ?: return@withContext GrayjayContentExtras(emptyList(), emptyList(), false, false)

        var recommendationsAvailable = false
        val recommendationPage = runCatching {
            val pager = details.getContentRecommendations(plugin)
                ?: if (plugin.capabilities.hasGetContentRecommendations) {
                    plugin.getContentRecommendations(details.url)
                } else {
                    null
                }
            recommendationsAvailable = pager != null
            pager?.let {
                readNewSession(
                    PagerSession(
                        kind = PagerContentKind.Videos,
                        sources = listOf(SourcePagerSession(sourceId, plugin.id, it)),
                    ),
                    recommendationLimit,
                )
            } ?: PagerBatch()
        }.getOrDefault(PagerBatch())

        var commentsAvailable = false
        val commentsPage = runCatching {
            val pager = details.getComments(plugin)
                ?: if (plugin.capabilities.hasGetComments) plugin.getComments(details.url) else null
            commentsAvailable = pager != null
            pager?.let {
                readNewSession(
                    PagerSession(
                        kind = PagerContentKind.Comments,
                        sources = listOf(SourcePagerSession(sourceId, plugin.id, it)),
                    ),
                    commentLimit,
                )
            } ?: PagerBatch()
        }.getOrDefault(PagerBatch())

        GrayjayContentExtras(
            recommendations = recommendationPage.videos,
            comments = commentsPage.comments,
            recommendationsAvailable = recommendationsAvailable,
            commentsAvailable = commentsAvailable,
            recommendationContinuationId = recommendationPage.continuationId,
            commentsContinuationId = commentsPage.continuationId,
            hasMoreRecommendations = recommendationPage.hasMore,
            hasMoreComments = commentsPage.hasMore,
        )
    }

    suspend fun loadMoreRecommendations(
        continuationId: String,
        pageSize: Int = 20,
    ): GrayjayVideoPage = withContext(Dispatchers.IO) {
        readExistingSession(continuationId, pageSize).toVideoPage()
    }

    suspend fun loadMoreComments(
        continuationId: String,
        pageSize: Int = 40,
    ): GrayjayCommentPage = withContext(Dispatchers.IO) {
        readExistingSession(continuationId, pageSize).toCommentPage()
    }

    private suspend fun readNewSession(session: PagerSession, pageSize: Int): PagerBatch {
        if (session.sources.isEmpty()) return PagerBatch()
        val continuationId = UUID.randomUUID().toString()
        pagerSessions[continuationId] = session
        return readExistingSession(continuationId, pageSize)
    }

    private suspend fun readExistingSession(
        continuationId: String,
        pageSize: Int,
    ): PagerBatch {
        val session = pagerSessions[continuationId] ?: return PagerBatch()
        return session.mutex.withLock {
            val sourceBatches = session.sources.map { source ->
                readSourcePage(session.kind, source, pageSize.coerceAtLeast(1))
            }
            val hasMore = session.sources.any(::sourceHasMore)
            if (!hasMore) pagerSessions.remove(continuationId, session)
            PagerBatch(
                videos = if (session.newestFirst) {
                    sourceBatches.flatMap(PagerBatch::videos)
                        .distinctBy(GrayjaySearchItem::url)
                        .sortedByDescending { it.datetime ?: OffsetDateTime.MIN }
                } else {
                    interleaveSourceResults(sourceBatches.map(PagerBatch::videos))
                        .distinctBy(GrayjaySearchItem::url)
                },
                channels = interleaveSourceResults(sourceBatches.map(PagerBatch::channels))
                    .distinctBy(GrayjaySearchChannel::url),
                playlists = interleaveSourceResults(sourceBatches.map(PagerBatch::playlists))
                    .distinctBy(GrayjaySearchPlaylist::url),
                comments = interleaveSourceResults(sourceBatches.map(PagerBatch::comments)),
                continuationId = continuationId.takeIf { hasMore },
                hasMore = hasMore,
            )
        }
    }

    private fun readSourcePage(
        kind: PagerContentKind,
        source: SourcePagerSession,
        pageSize: Int,
    ): PagerBatch {
        if (source.failed) return PagerBatch()

        fun unreadResults(): List<Any> = source.pager.getResults()
            .asSequence()
            .filter { item -> item != null && pagerItemKey(item) !in source.emittedKeys }
            .take(pageSize)
            .map { requireNotNull(it) }
            .toList()

        var rawItems = runCatching(::unreadResults).getOrElse {
            source.failed = true
            Log.e(TAG, "Reading a plugin page failed for ${source.sourceId}.", it)
            emptyList()
        }
        if (rawItems.isEmpty() && !source.failed && source.pager.hasMorePages()) {
            runCatching(source.pager::nextPage).onFailure {
                source.failed = true
                Log.e(TAG, "Loading the next plugin page failed for ${source.sourceId}.", it)
            }
            if (!source.failed) rawItems = runCatching(::unreadResults).getOrDefault(emptyList())
        }
        rawItems.forEach { source.emittedKeys += pagerItemKey(it) }

        return when (kind) {
            PagerContentKind.Videos -> PagerBatch(
                videos = rawItems.filterIsInstance<IPlatformVideo>()
                    .map { it.toSearchItem(source.sourceId, source.pluginId) },
            )
            PagerContentKind.Creators -> PagerBatch(
                channels = rawItems.filterIsInstance<IPlatformChannelContent>()
                    .map { it.toSearchChannel(source.sourceId, source.pluginId) },
            )
            PagerContentKind.Playlists -> PagerBatch(
                playlists = rawItems.filterIsInstance<IPlatformPlaylist>()
                    .map { it.toSearchPlaylist(source.sourceId, source.pluginId) },
            )
            PagerContentKind.Comments -> PagerBatch(
                comments = rawItems.filterIsInstance<IPlatformComment>().map { it.toComment() },
            )
        }
    }

    private fun sourceHasMore(source: SourcePagerSession): Boolean {
        if (source.failed) return false
        val hasUnreadCurrent = runCatching {
            source.pager.getResults().any { item ->
                item != null && pagerItemKey(item) !in source.emittedKeys
            }
        }.getOrDefault(false)
        return hasUnreadCurrent || runCatching(source.pager::hasMorePages).getOrDefault(false)
    }

    private fun pagerItemKey(item: Any): String = when (item) {
        is IPlatformVideo -> "video:${item.url.ifBlank { item.id.value.orEmpty() }}"
        is IPlatformChannelContent -> "channel:${item.url.ifBlank { item.id.value.orEmpty() }}"
        is IPlatformPlaylist -> "playlist:${item.url.ifBlank { item.id.value.orEmpty() }}"
        is IPlatformComment -> "comment:${item.author.url}|${item.date}|${item.message}"
        else -> "${item::class.java.name}:${item.hashCode()}"
    }

    private fun PagerBatch.toSearchResult() = GrayjayPluginSearchResult(
        videos = videos,
        channels = channels,
        playlists = playlists,
        continuationId = continuationId,
        hasMore = hasMore,
    )

    private fun PagerBatch.toChannelPage() = GrayjayChannelPage(
        videos = videos,
        playlists = playlists,
        continuationId = continuationId,
        hasMore = hasMore,
    )

    private fun PagerBatch.toVideoPage(limit: Int = Int.MAX_VALUE) = GrayjayVideoPage(
        videos = videos.take(limit),
        continuationId = continuationId,
        hasMore = hasMore,
    )

    private fun PagerBatch.toCommentPage() = GrayjayCommentPage(
        comments = comments,
        continuationId = continuationId,
        hasMore = hasMore,
    )

    suspend fun installPlugin(configUrl: String): GrayjayPluginMetadata =
        withContext(Dispatchers.IO) {
            require(configUrl.startsWith("https://", ignoreCase = true)) {
                "Source configuration URLs must use HTTPS."
            }
            val (configText, scriptText) = downloadPlugin(configUrl)
            val config = SourcePluginConfig.fromJson(configText, configUrl)
            when (pluginSignatureState(config.scriptSignature, config.scriptPublicKey)) {
                PluginSignatureState.Unsigned -> Unit
                PluginSignatureState.Incomplete -> error(
                    "Plugin signature metadata is incomplete for ${config.name}.",
                )
                PluginSignatureState.Signed -> if (!config.validate(scriptText)) {
                    pendingUntrustedPlugins.entries.removeAll {
                        it.value.config.sourceUrl == configUrl
                    }
                    val token = UUID.randomUUID().toString()
                    pendingUntrustedPlugins[token] = PendingUntrustedPlugin(
                        config = config,
                        configText = configText,
                        scriptText = scriptText,
                    )
                    throw GrayjaySignatureMismatchException(
                        GrayjayUntrustedPlugin(
                            token = token,
                            pluginId = config.id,
                            pluginName = config.name,
                            publisher = config.author,
                            publisherUrl = config.authorUrl,
                            configUrl = configUrl,
                            publicKeyFingerprint = config.scriptPublicKey.orEmpty().fingerprint(),
                        ),
                    )
                }
            }
            finishPluginInstallation(config, configText, scriptText)
        }

    suspend fun trustInstallPlugin(token: String): GrayjayPluginMetadata =
        withContext(Dispatchers.IO) {
            val pending = pendingUntrustedPlugins.remove(token)
                ?: error("The pending untrusted source is no longer available. Please add it again.")
            finishPluginInstallation(
                pending.config,
                pending.configText,
                pending.scriptText,
                trustSignatureMismatch = true,
            )
        }

    fun discardUntrustedPlugin(token: String) {
        pendingUntrustedPlugins.remove(token)
    }

    fun clearPlugin(alias: String, pluginId: String) {
        runCatching { clients.remove(alias)?.disable() }
        sourceAliases.remove(pluginId)
        clearPluginTrust(pluginId)
        val root = pluginDirectory.canonicalFile
        val target = File(root, pluginId).canonicalFile
        if (target.parentFile == root) target.deleteRecursively()
    }

    fun setPluginSettings(pluginId: String, settings: Map<String, String?>) {
        val json = JSONObject().apply {
            settings.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        }
        pluginSettings.edit().putString(profileKey(pluginId), json.toString()).apply()
        sourceAliases.remove(pluginId)?.let { alias ->
            runCatching { clients.remove(alias)?.disable() }
        }
    }

    fun setProfile(profileId: String) {
        if (this.profileId == profileId) return
        pagerSessions.clear()
        clients.values.forEach { runCatching { it.disable() } }
        clients.clear()
        sourceAliases.clear()
        storyboardCache.clear()
        storyboardFailures.clear()
        this.profileId = profileId
    }

    fun reloadAuthentication(alias: String, pluginId: String) {
        runCatching { clients.remove(alias)?.disable() }
        sourceAliases.remove(pluginId)
        if (isAuthenticated(pluginId)) {
            val settings = loadPluginSettings(pluginId)
            if (settings.remove("composeLegacyAgeFallback") != null) {
                val json = JSONObject().apply {
                    settings.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
                }
                pluginSettings.edit().putString(profileKey(pluginId), json.toString()).apply()
            }
        }
    }

    fun isAuthenticated(pluginId: String): Boolean =
        GrayjayPluginAuthStore.has(appContext, profileId, pluginId)

    fun clearAuthentication(alias: String, pluginId: String) {
        GrayjayPluginAuthStore.clear(appContext, profileId, pluginId)
        reloadAuthentication(alias, pluginId)
    }

    fun release() {
        clients.values.forEach { runCatching { it.disable() } }
        clients.clear()
        pendingUntrustedPlugins.clear()
        pagerSessions.clear()
        storyboardCache.clear()
        storyboardFailures.clear()
    }

    private fun loadYouTubeStoryboard(
        contentUrl: String,
        durationSeconds: Long,
    ): GrayjayStoryboard? {
        val videoId = youtubeVideoId(contentUrl) ?: return null
        val cacheKey = "$profileId:$videoId"
        val now = System.currentTimeMillis()
        storyboardCache[cacheKey]?.takeIf {
            now - it.cachedAtMs < STORYBOARD_CACHE_TTL_MS
        }?.let { return it.storyboard }
        storyboardFailures[cacheKey]?.takeIf {
            now - it < STORYBOARD_FAILURE_TTL_MS
        }?.let { return null }

        val storyboard = runCatching {
            // Use the same profile-scoped auth/cookie machinery as the JS source host.
            // Storyboard sprite URLs themselves are signed and are then cached by Glide.
            val descriptor = StatePlugins.instance.getPlugin(YOUTUBE_PLUGIN_ID)
            val auth = descriptor?.getAuth()
            val http = JSHttpClient(
                jsClient = null,
                auth = auth,
                captcha = null,
                config = descriptor?.config,
            ).apply {
                user_agent = auth?.userAgent?.takeIf(String::isNotBlank)
                    ?: STORYBOARD_USER_AGENT
                rebuildClient { builder ->
                    builder
                        .connectTimeout(Duration.ofSeconds(3))
                        .readTimeout(Duration.ofSeconds(4))
                        .callTimeout(Duration.ofSeconds(5))
                }
            }
            val watchUrl = "https://www.youtube.com/watch?v=$videoId" +
                "&hl=en&bpctr=9999999999&has_verified=1"
            val response = http.get(
                watchUrl,
                hashMapOf(
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Cookie" to "CONSENT=YES+cb",
                ),
            )
            if (!response.isOk) return@runCatching null
            response.body?.use { body ->
                YouTubeStoryboardParser.parseWatchHtml(body.string(), durationSeconds)
            }
        }.onFailure { error ->
            Log.d(TAG, "Storyboard lookup failed for YouTube video $videoId.", error)
        }.getOrNull()

        if (storyboard == null) {
            storyboardFailures[cacheKey] = now
        } else {
            storyboardFailures.remove(cacheKey)
            storyboardCache[cacheKey] = CachedStoryboard(storyboard, now)
        }
        return storyboard
    }

    private fun youtubeVideoId(url: String): String? {
        val parsed = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
        val candidate = when {
            host == "youtu.be" || host.endsWith(".youtu.be") -> parsed.pathSegments.firstOrNull()
            host == "youtube.com" || host.endsWith(".youtube.com") -> {
                parsed.getQueryParameter("v")
                    ?: parsed.pathSegments.zipWithNext().firstOrNull { (prefix, _) ->
                        prefix in setOf("shorts", "embed", "live")
                    }?.second
            }
            else -> null
        }
        return candidate?.takeIf { it.matches(YOUTUBE_VIDEO_ID_REGEX) }
    }

    private suspend fun getOrLoad(alias: String, endpoint: PluginEndpoint): JSClient {
        clients[alias]?.let { return it }
        return loadMutex.withLock {
            clients[alias]?.let { return@withLock it }
            val (config, configText, scriptText) = loadPluginPayload(endpoint)
            cachePlugin(config.id, configText, scriptText)
            val descriptor = SourcePluginDescriptor(
                config,
                auth = GrayjayPluginAuthStore.load(appContext, profileId, config.id),
                flags = listOf(SourcePluginDescriptor.FLAG_EMBEDDED),
                settings = loadPluginSettings(config.id),
            )
            val runtimeScript = scriptText.withComposeCompatibility(endpoint.pluginId)
            StatePlugins.instance.register(descriptor, runtimeScript)
            JSClient(appContext, descriptor, null, runtimeScript).also { plugin ->
                plugin.initialize()
                plugin.enable()
                clients[alias] = plugin
                sourceAliases[plugin.id] = alias
            }
        }
    }

    private fun downloadPlugin(configUrl: String): Pair<String, String> {
        val configText = executeText(configUrl)
        val config = SourcePluginConfig.fromJson(configText, configUrl)
        return configText to executeText(config.absoluteScriptUrl)
    }

    private fun finishPluginInstallation(
        config: SourcePluginConfig,
        configText: String,
        scriptText: String,
        trustSignatureMismatch: Boolean = false,
    ): GrayjayPluginMetadata {
        cachePlugin(config.id, configText, scriptText)
        if (trustSignatureMismatch) {
            trustPluginPayload(config.id, configText, scriptText)
        } else {
            clearPluginTrust(config.id)
        }
        sourceAliases.remove(config.id)?.let { alias ->
            runCatching { clients.remove(alias)?.disable() }
        }
        return GrayjayPluginMetadata(
            pluginId = config.id,
            name = config.name,
            description = config.description,
            configUrl = config.sourceUrl.orEmpty(),
            iconUrl = config.absoluteIconUrl.orEmpty(),
            version = config.version,
            warnings = config.getWarnings(scriptText).map { (title, detail) ->
                "$title: $detail"
            },
        )
    }

    private fun executeText(url: String): String {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            if (!it.isSuccessful) error("Plugin request failed (${it.code}) for $url")
            return it.body.string()
        }
    }

    private fun cachedPlugin(id: String): Pair<String, String>? {
        val directory = File(pluginDirectory, id)
        val config = File(directory, "config.json")
        val script = File(directory, "script.js")
        if (!config.isFile || !script.isFile) return null
        return config.readText() to script.readText()
    }

    private fun cachePlugin(id: String, config: String, script: String) {
        val directory = File(pluginDirectory, id).apply { mkdirs() }
        File(directory, "config.json").writeText(config)
        File(directory, "script.js").writeText(script)
    }

    private fun loadPluginSettings(pluginId: String): HashMap<String, String?> {
        val json = runCatching {
            val raw = pluginSettings.getString(profileKey(pluginId), null)
                ?: if (profileId == "main") pluginSettings.getString(pluginId, "{}") else "{}"
            JSONObject(raw ?: "{}")
        }.getOrElse { JSONObject() }
        return hashMapOf<String, String?>().apply {
            json.keys().forEach { key ->
                this[key] = if (json.isNull(key)) null else json.optString(key)
            }
        }
    }

    private fun profileKey(pluginId: String) = "$profileId:$pluginId"

    private fun String.withComposeCompatibility(pluginId: String): String {
        if (pluginId != YOUTUBE_PLUGIN_ID) return this
        return replace(
            "true/*_settings?.use_session_client*/ && canBatchDummy",
            "!(_settings?.composeLegacyAgeFallback) && canBatchDummy",
        )
    }

    private fun IPlatformVideo.toSearchItem(alias: String, pluginId: String) = GrayjaySearchItem(
        id = url,
        url = url,
        sourceId = alias,
        pluginId = pluginId,
        title = name,
        authorName = author.name,
        authorUrl = author.url,
        authorThumbnailUrl = author.thumbnail,
        thumbnailUrl = thumbnails.getHQThumbnail(),
        durationSeconds = duration,
        viewCount = viewCount,
        datetime = datetime,
        isLive = isLive,
        playbackTimeSeconds = playbackTime,
        playbackDate = playbackDate,
    )

    private fun IPlatformChannelContent.toSearchChannel(
        alias: String,
        pluginId: String,
    ) = GrayjaySearchChannel(
        id = url.ifBlank { id.value.orEmpty() },
        url = url,
        sourceId = alias,
        pluginId = pluginId,
        name = name,
        thumbnailUrl = thumbnail,
        subscribers = subscribers,
    )

    private fun IPlatformPlaylist.toSearchPlaylist(
        alias: String,
        pluginId: String,
    ) = GrayjaySearchPlaylist(
        id = url.ifBlank { id.value.orEmpty() },
        url = url,
        sourceId = alias,
        pluginId = pluginId,
        title = name,
        author = author.name,
        thumbnailUrl = thumbnail,
        videoCount = videoCount,
    )

    private fun Array<IVideoSource>.bestVideoUrl(): IVideoUrlSource? =
        filterIsInstance<IVideoUrlSource>()
            .filter { it.getVideoUrl().isNotBlank() }
            .maxWithOrNull(
                compareBy<IVideoUrlSource> { it.priority }
                    .thenBy { it.height }
                    .thenBy { it.bitrate ?: 0 },
            )

    private fun <T : IVideoSource> List<T>.bestSource(): T? =
        maxWithOrNull(
            compareBy<T> { it.priority }
                .thenBy { it.height }
                .thenBy { it.bitrate ?: 0 },
        )

    private data class ResolvedRequest(
        val url: String,
        val headers: Map<String, String>,
    )

    private fun IVideoSource.resolveRequest(url: String): ResolvedRequest {
        val modified = (this as? JSSource)
            ?.getRequestModifier()
            ?.let { modifier -> runCatching { modifier.modifyRequest(url, emptyMap()) }.getOrNull() }
        return ResolvedRequest(
            url = modified?.url?.takeIf(String::isNotBlank) ?: url,
            headers = modified?.headers.orEmpty(),
        )
    }

    private fun IAudioSource.resolveRequest(url: String): ResolvedRequest {
        val modified = (this as? JSSource)
            ?.getRequestModifier()
            ?.let { modifier -> runCatching { modifier.modifyRequest(url, emptyMap()) }.getOrNull() }
        return ResolvedRequest(
            url = modified?.url?.takeIf(String::isNotBlank) ?: url,
            headers = modified?.headers.orEmpty(),
        )
    }

    /**
     * Old Grayjay does not play YouTube's VideoUrlRangeSource as one progressive response.
     * It uses the supplied initialization/index byte ranges to create an on-demand DASH
     * manifest, allowing Media3 to request bounded media ranges without CDN backoff stalls.
     */
    private fun IVideoUrlSource.rangeDashManifestOrNull(
        audioSource: IAudioUrlSource?,
    ): String? {
        val rangeVideo = this as? JSVideoUrlRangeSource ?: return null
        if (!rangeVideo.hasItag) return null
        val rangeAudio = when (audioSource) {
            null -> null
            is JSAudioUrlRangeSource -> audioSource.takeIf { it.hasItag } ?: return null
            else -> return null
        }
        return runCatching {
            DashBuilder.generateOnDemandDash(
                rangeVideo,
                rangeVideo.getVideoUrl(),
                rangeAudio,
                rangeAudio?.getAudioUrl(),
                null,
                null,
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to create range-based DASH manifest; using progressive media.", error)
        }.getOrNull()
    }

    private fun IVideoSource.dataSourceFactoryOrNull(): HttpDataSource.Factory? {
        val jsSource = this as? JSSource ?: return null
        if (!jsSource.requiresCustomDatasource) return null
        val requestExecutor = jsSource.getRequestExecutor()
        val requestModifier = jsSource.getRequestModifier()
        val secondaryExecutor = (this as? JSDashManifestMergingRawSource)
            ?.audio
            ?.getRequestExecutor()
        return if (requestExecutor == null && requestModifier == null && secondaryExecutor == null) {
            null
        } else {
            JSHttpDataSource.Factory()
                .setRequestExecutor(requestExecutor)
                .setRequestExecutor2(secondaryExecutor)
                .setRequestModifier(requestModifier)
        }
    }

    private fun IAudioSource.dataSourceFactoryOrNull(): HttpDataSource.Factory? {
        val jsSource = this as? JSSource ?: return null
        if (!jsSource.requiresCustomDatasource) return null
        val requestExecutor = jsSource.getRequestExecutor()
        val requestModifier = jsSource.getRequestModifier()
        return if (requestExecutor == null && requestModifier == null) null else {
            JSHttpDataSource.Factory()
                .setRequestExecutor(requestExecutor)
                .setRequestModifier(requestModifier)
        }
    }

    private fun IPlatformComment.toComment(): GrayjayComment {
        val (likes, _) = rating.counts()
        return GrayjayComment(
            author = author.name,
            authorThumbnailUrl = author.thumbnail,
            message = message,
            age = formatRelativeDate(date),
            likeCount = likes,
            replyCount = replyCount,
        )
    }
}

internal fun <T> interleaveSourceResults(sourceResults: List<List<T>>): List<T> {
    val largestSource = sourceResults.maxOfOrNull(List<T>::size) ?: return emptyList()
    return buildList(sourceResults.sumOf(List<T>::size)) {
        repeat(largestSource) { index ->
            sourceResults.forEach { results ->
                results.getOrNull(index)?.let(::add)
            }
        }
    }
}

internal enum class PluginSignatureState {
    Unsigned,
    Signed,
    Incomplete,
}

internal fun pluginSignatureState(
    signature: String?,
    publicKey: String?,
): PluginSignatureState {
    val hasSignature = !signature.isNullOrBlank()
    val hasPublicKey = !publicKey.isNullOrBlank()
    return when {
        !hasSignature && !hasPublicKey -> PluginSignatureState.Unsigned
        hasSignature && hasPublicKey -> PluginSignatureState.Signed
        else -> PluginSignatureState.Incomplete
    }
}

data class PluginEndpoint(
    val pluginId: String,
    val configUrl: String,
    val iconUrl: String = "",
    val configAssetPath: String? = null,
)

private fun IRating.counts(): Pair<Long?, Long?> = when (this) {
    is RatingLikes -> likes to null
    is RatingLikeDislikes -> likes to dislikes
    is RatingScaler -> null to null
    else -> null to null
}

private fun subtitleMimeType(format: String?): String {
    val normalized = format?.trim()?.lowercase()?.substringBefore(';').orEmpty()
    return when (normalized) {
        "vtt", "webvtt" -> "text/vtt"
        "srt", "subrip" -> "application/x-subrip"
        "ttml", "dfxp", "xml", "text/xml" -> "application/ttml+xml"
        "ssa", "ass" -> "text/x-ssa"
        "text/vtt", "application/x-subrip", "application/ttml+xml", "text/x-ssa" ->
            normalized
        else -> "text/vtt"
    }
}

private fun inferStreamType(url: String): GrayjayStreamType {
    val normalized = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        normalized.endsWith(".m3u8") -> GrayjayStreamType.Hls
        normalized.endsWith(".mpd") -> GrayjayStreamType.Dash
        else -> GrayjayStreamType.Progressive
    }
}

fun formatRelativeDate(value: OffsetDateTime?): String {
    if (value == null || value == OffsetDateTime.MIN || value == OffsetDateTime.MAX) return ""
    val now = OffsetDateTime.now()
    val hours = ChronoUnit.HOURS.between(value, now).coerceAtLeast(0)
    if (Locale.getDefault().language == Locale.ITALIAN.language) {
        return when {
            hours < 1 -> "adesso"
            hours < 24 -> "${hours} h fa"
            hours < 24 * 7 -> "${hours / 24} g fa"
            hours < 24 * 30 -> "${hours / (24 * 7)} sett fa"
            hours < 24 * 365 -> "${hours / (24 * 30)} mesi fa"
            else -> "${hours / (24 * 365)} anni fa"
        }
    }
    return when {
        hours < 1 -> "just now"
        hours < 24 -> "${hours}h ago"
        hours < 24 * 7 -> "${hours / 24}d ago"
        hours < 24 * 30 -> "${hours / (24 * 7)}w ago"
        hours < 24 * 365 -> "${hours / (24 * 30)}mo ago"
        else -> "${hours / (24 * 365)}y ago"
    }
}

private fun String.fingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .take(8)
    .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }

private fun String.youtubePlaylistId(): String? = runCatching {
    android.net.Uri.parse(this).getQueryParameter("list")
}.getOrNull()

private fun Throwable.userImportMessage(): String =
    localizedMessage?.takeIf(String::isNotBlank) ?: javaClass.simpleName

private fun GrayjayPluginBackend.drainVideoPager(
    pager: IPager<*>,
    maxItems: Int,
    maxPages: Int,
    onProgress: (Int) -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
): List<IPlatformVideo> {
    val videos = linkedMapOf<String, IPlatformVideo>()
    var pages = 0
    while (pages < maxPages && videos.size < maxItems) {
        val results = runCatching { pager.getResults() }
            .onFailure(onFailure)
            .getOrNull()
            ?: break
        results
            .filterIsInstance<IPlatformVideo>()
            .forEach { video ->
                val key = video.url.ifBlank { video.id.value.orEmpty() }
                if (key.isNotBlank() && key !in videos) videos[key] = video
            }
        pages += 1
        onProgress(videos.size)
        if (videos.size >= maxItems) break
        val hasMore = runCatching { pager.hasMorePages() }
            .onFailure(onFailure)
            .getOrNull()
            ?: break
        if (!hasMore) break
        if (runCatching { pager.nextPage() }.onFailure(onFailure).isFailure) break
    }
    return videos.values.take(maxItems)
}
