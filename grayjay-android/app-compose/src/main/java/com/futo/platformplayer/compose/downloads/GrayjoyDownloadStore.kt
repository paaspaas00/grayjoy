package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Requirements
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

/**
 * DASH representations often expose short cache keys such as "1" and "2". Those identifiers
 * are only unique inside one manifest, so using them globally lets one downloaded video satisfy
 * another video's requests. Scope every new cache resource to its actual media URI.
 */
private val SCOPED_CACHE_KEY_FACTORY = CacheKeyFactory { dataSpec ->
    buildString {
        append(dataSpec.uri)
        dataSpec.key?.let { key ->
            append('#')
            append(key)
        }
    }
}

@OptIn(UnstableApi::class)
class GrayjoyDownloadStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val databaseProvider = StandaloneDatabaseProvider(appContext)
    private val downloaderExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val cache = SimpleCache(
        appContext.filesDir.resolve("offline_media/cache"),
        NoOpCacheEvictor(),
        databaseProvider,
    )
    private val downloadsById = linkedMapOf<String, Download>()
    private val requestDataSourceFactories = ConcurrentHashMap<String, DataSource.Factory>()
    private val failureMessages = mutableMapOf<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private var tickerRunning = false
    private val _downloads = MutableStateFlow<List<DownloadUiModel>>(emptyList())
    val downloads: StateFlow<List<DownloadUiModel>> = _downloads.asStateFlow()

    internal val downloadManager = DownloadManager(
        appContext,
        DefaultDownloadIndex(databaseProvider, DOWNLOAD_INDEX_NAME),
        RequestAwareDownloaderFactory(
            appContext,
            cache,
            downloaderExecutor,
            requestDataSourceFactories::get,
        ),
    ).apply {
        maxParallelDownloads = 2
        requirements = Requirements(Requirements.NETWORK)
        addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    reloadDownloadIndex()
                    resumeDownloads()
                }

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    downloadsById[download.request.id] = download
                    if (download.state == Download.STATE_COMPLETED) {
                        releaseRequestDataSourceFactory(download.request.id)
                    }
                    if (finalException == null) failureMessages.remove(download.request.id)
                    else failureMessages[download.request.id] =
                        finalException.localizedMessage ?: finalException.javaClass.simpleName
                    publishDownloads()
                }

                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download,
                ) {
                    downloadsById.remove(download.request.id)
                    failureMessages.remove(download.request.id)
                    releaseRequestDataSourceFactory(download.request.id)
                    cleanupCacheIfUnused()
                    publishDownloads()
                }
            },
        )
    }

    fun cachePlayback(
        upstream: DataSource.Factory,
        offlineOnly: Boolean = false,
    ): DataSource.Factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(if (offlineOnly) null else upstream)
            .setCacheKeyFactory(
                CacheKeyFactory { dataSpec ->
                    val scopedKey = SCOPED_CACHE_KEY_FACTORY.buildCacheKey(dataSpec)
                    val lengthToCheck = 1L
                    when {
                        cache.isCached(scopedKey, dataSpec.position, lengthToCheck) -> scopedKey
                        dataSpec.key != null &&
                            cache.isCached(dataSpec.key!!, dataSpec.position, lengthToCheck) ->
                            dataSpec.key!! // Compatibility with downloads made by older builds.
                        else -> scopedKey
                    }
                },
            )
            // Playback may consume completed downloads, but ordinary streaming must not grow the
            // permanent offline cache. Only DownloadManager is allowed to write here.
            .setCacheWriteDataSinkFactory(null)

    fun enqueue(
        profileId: String,
        video: VideoUiModel,
        mediaType: DownloadMediaType = DownloadMediaType.Video,
    ) {
        require(!video.isLive) { "Live streams cannot be downloaded." }
        val parts = buildParts(video, mediaType)
        require(parts.isNotEmpty()) {
            if (mediaType == DownloadMediaType.Audio) {
                "This source returned no downloadable audio."
            } else {
                "This source returned no downloadable media."
            }
        }
        parts.forEachIndexed { index, part ->
            val metadata = DownloadRequestMetadata(
                profileId = profileId,
                videoId = video.id,
                title = video.title,
                part = part.name,
                partCount = parts.size,
                mediaType = mediaType,
                headers = part.headers,
                rawManifest = part.rawManifest,
            )
            val requestId = downloadId(profileId, video.id, mediaType, part.name, index)
            part.dataSourceFactory?.let { requestDataSourceFactories[requestId] = it }
            val requestBuilder = DownloadRequest.Builder(
                requestId,
                Uri.parse(part.uri),
            ).setData(metadata.toByteArray())
            if (part.mimeType.isNotBlank()) requestBuilder.setMimeType(part.mimeType)
            GrayjoyDownloadService.add(appContext, requestBuilder.build())
        }
    }

    fun remove(profileId: String, videoId: String) {
        downloadsById.values
            .filter { download ->
                DownloadRequestMetadata.from(download.request.data)?.let {
                    it.profileId == profileId && it.videoId == videoId
                } == true
            }
            .forEach { downloadManager.removeDownload(it.request.id) }
    }

    fun snapshotsFor(profileId: String): Map<String, DownloadUiModel> = downloads.value
        .filter { it.profileId == profileId }
        .associateBy(DownloadUiModel::videoId)

    fun isInitialized(): Boolean = downloadManager.isInitialized

    private fun reloadDownloadIndex() {
        runCatching {
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    downloadsById[download.request.id] = download
                }
            }
        }
        cleanupCacheIfUnused()
        publishDownloads()
    }

    private fun cleanupCacheIfUnused() {
        if (downloadsById.isNotEmpty()) return
        cache.keys.toList().forEach(cache::removeResource)
    }

    private fun releaseRequestDataSourceFactory(requestId: String) {
        val factory = requestDataSourceFactories.remove(requestId) ?: return
        if (requestDataSourceFactories.values.none { it === factory }) {
            (factory as? JSHttpDataSource.Factory)?.closeExecutors()
        }
    }

    private fun publishDownloads() {
        _downloads.value = downloadsById.values
            .mapNotNull { download ->
                DownloadRequestMetadata.from(download.request.data)?.let { it to download }
            }
            .groupBy { (metadata, _) -> metadata.profileId to metadata.videoId }
            .map { (_, entries) -> aggregate(entries) }
            .sortedByDescending { snapshot ->
                downloadsById.values
                    .filter { download ->
                        DownloadRequestMetadata.from(download.request.data)?.let {
                            it.profileId == snapshot.profileId && it.videoId == snapshot.videoId
                        } == true
                    }
                    .maxOfOrNull(Download::updateTimeMs) ?: 0L
            }
        if (downloadsById.values.any { it.state in ACTIVE_STATES }) startProgressTicker()
    }

    private fun aggregate(
        entries: List<Pair<DownloadRequestMetadata, Download>>,
    ): DownloadUiModel {
        val metadata = entries.first().first
        val downloads = entries.map(Pair<DownloadRequestMetadata, Download>::second)
        val expectedParts = entries.maxOf { it.first.partCount }.coerceAtLeast(1)
        val completedParts = downloads.count { it.state == Download.STATE_COMPLETED }
        val bytesDownloaded = downloads.sumOf(Download::getBytesDownloaded)
        val knownLengths = downloads.map(Download::contentLength).filter { it > 0L }
        val contentLength = knownLengths.sum().takeIf {
            knownLengths.size == expectedParts && entries.size >= expectedParts
        }
        val knownPercents = downloads.mapNotNull { download ->
            download.percentDownloaded.takeIf { it >= 0f }?.div(100f)
                ?: if (download.state == Download.STATE_COMPLETED) 1f else null
        }
        val progress = when {
            contentLength != null && contentLength > 0L ->
                (bytesDownloaded.toFloat() / contentLength).coerceIn(0f, 1f)
            knownPercents.isNotEmpty() ->
                (knownPercents.sum() / expectedParts).coerceIn(0f, 1f)
            else -> null
        }
        val complete = entries.size >= expectedParts && completedParts >= expectedParts
        val status = when {
            downloads.any { it.state == Download.STATE_FAILED } -> DownloadStatus.Failed
            downloads.any { it.state == Download.STATE_REMOVING } -> DownloadStatus.Removing
            complete -> DownloadStatus.Completed
            downloads.any { it.state == Download.STATE_DOWNLOADING } -> DownloadStatus.Downloading
            downloads.any { it.state == Download.STATE_STOPPED } -> DownloadStatus.Paused
            else -> DownloadStatus.Queued
        }
        return DownloadUiModel(
            profileId = metadata.profileId,
            videoId = metadata.videoId,
            mediaType = metadata.mediaType,
            status = status,
            progress = if (complete) 1f else progress,
            bytesDownloaded = bytesDownloaded,
            contentLength = contentLength,
            completedParts = completedParts,
            totalParts = expectedParts,
            errorMessage = entries.firstNotNullOfOrNull { (_, download) ->
                failureMessages[download.request.id]
            },
            requiresPluginTransport = metadata.rawManifest.contains("grayjay.internal") ||
                entries.any { (_, download) -> download.request.uri.host == "grayjay.internal" },
        )
    }

    private fun startProgressTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    publishDownloads()
                    if (downloadsById.values.any { it.state in ACTIVE_STATES }) {
                        handler.postDelayed(this, 750L)
                    } else {
                        tickerRunning = false
                    }
                }
            },
            750L,
        )
    }

    private fun buildParts(
        video: VideoUiModel,
        mediaType: DownloadMediaType,
    ): List<DownloadPart> = buildList {
        if (mediaType == DownloadMediaType.Audio) {
            val audioUri = video.audioUrl.ifBlank {
                video.playbackUrl.takeIf { video.playbackMimeType.startsWith("audio/") }.orEmpty()
            }
            if (audioUri.isNotBlank()) {
                add(
                    DownloadPart(
                        name = "audio",
                        uri = audioUri,
                        mimeType = video.playbackMimeType.takeIf { it.startsWith("audio/") }.orEmpty(),
                        headers = video.playbackRequestHeaders,
                        dataSourceFactory = video.playbackDataSourceFactory,
                    ),
                )
            }
            return@buildList
        }
        val mainUri = video.playbackUrl.ifBlank {
            video.contentUrl.takeIf { video.playbackManifest.isNotBlank() }.orEmpty()
        }
        if (mainUri.isNotBlank()) {
            add(
                DownloadPart(
                    name = "video",
                    uri = mainUri,
                    mimeType = if (video.playbackManifest.isNotBlank()) {
                        MimeTypes.APPLICATION_MPD
                    } else {
                        video.playbackMimeType
                    },
                    headers = video.playbackRequestHeaders,
                    rawManifest = video.playbackManifest,
                    dataSourceFactory = video.playbackDataSourceFactory,
                ),
            )
        }
        if (video.audioUrl.isNotBlank() && video.audioUrl != mainUri) {
            add(
                DownloadPart(
                    name = "audio",
                    uri = video.audioUrl,
                    mimeType = "",
                    headers = video.playbackRequestHeaders,
                    dataSourceFactory = video.playbackDataSourceFactory,
                ),
            )
        }
        video.subtitleTracks
            .filter { it.uri.isNotBlank() }
            .distinctBy { it.uri }
            .forEachIndexed { index, subtitle ->
                add(
                    DownloadPart(
                        name = "subtitle-$index",
                        uri = subtitle.uri,
                        mimeType = subtitle.mimeType,
                        headers = video.playbackRequestHeaders,
                    ),
                )
            }
    }

    private data class DownloadPart(
        val name: String,
        val uri: String,
        val mimeType: String,
        val headers: Map<String, String>,
        val rawManifest: String = "",
        val dataSourceFactory: DataSource.Factory? = null,
    )

    companion object {
        private const val DOWNLOAD_INDEX_NAME = "grayjoy_offline"
        private val ACTIVE_STATES = setOf(
            Download.STATE_QUEUED,
            Download.STATE_DOWNLOADING,
            Download.STATE_RESTARTING,
            Download.STATE_REMOVING,
        )

        @Volatile
        private var instance: GrayjoyDownloadStore? = null

        fun get(context: Context): GrayjoyDownloadStore = instance ?: synchronized(this) {
            instance ?: GrayjoyDownloadStore(context).also { instance = it }
        }

        private fun downloadId(
            profileId: String,
            videoId: String,
            mediaType: DownloadMediaType,
            part: String,
            index: Int,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(
                    "$profileId\u0000$videoId\u0000${mediaType.name}\u0000$part\u0000$index"
                        .toByteArray(),
                )
                .joinToString("") { "%02x".format(it) }
            return "grayjoy-$digest"
        }
    }
}

private data class DownloadRequestMetadata(
    val profileId: String,
    val videoId: String,
    val title: String,
    val part: String,
    val partCount: Int,
    val mediaType: DownloadMediaType,
    val headers: Map<String, String>,
    val rawManifest: String,
) {
    fun toByteArray(): ByteArray = JSONObject().apply {
        put("profileId", profileId)
        put("videoId", videoId)
        put("title", title)
        put("part", part)
        put("partCount", partCount)
        put("mediaType", mediaType.name)
        put("headers", JSONObject(headers))
        put("rawManifest", rawManifest)
    }.toString().toByteArray()

    companion object {
        fun from(bytes: ByteArray): DownloadRequestMetadata? = runCatching {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val headersJson = json.optJSONObject("headers")
            val headers = buildMap {
                headersJson?.keys()?.forEach { key ->
                    headersJson.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                }
            }
            DownloadRequestMetadata(
                profileId = json.getString("profileId"),
                videoId = json.getString("videoId"),
                title = json.optString("title"),
                part = json.optString("part"),
                partCount = json.optInt("partCount", 1).coerceAtLeast(1),
                mediaType = runCatching {
                    DownloadMediaType.valueOf(json.optString("mediaType"))
                }.getOrDefault(DownloadMediaType.Video),
                headers = headers,
                rawManifest = json.optString("rawManifest"),
            )
        }.getOrNull()
    }
}

@OptIn(UnstableApi::class)
private class RequestAwareDownloaderFactory(
    private val context: Context,
    private val cache: SimpleCache,
    private val executor: ExecutorService,
    private val requestDataSourceFactory: (String) -> DataSource.Factory?,
) : DownloaderFactory {
    override fun createDownloader(request: DownloadRequest): Downloader {
        val metadata = DownloadRequestMetadata.from(request.data)
        val registeredFactory = requestDataSourceFactory(request.id)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        metadata?.headers?.takeIf(Map<String, String>::isNotEmpty)?.let {
            httpFactory.setDefaultRequestProperties(it)
            (registeredFactory as? HttpDataSource.Factory)?.setDefaultRequestProperties(it)
        }
        val networkFactory: DataSource.Factory = when (registeredFactory) {
            is HttpDataSource.Factory -> DefaultDataSource.Factory(context, registeredFactory)
            null -> DefaultDataSource.Factory(context, httpFactory)
            else -> registeredFactory
        }
        val upstreamFactory = if (
            metadata != null && metadata.rawManifest.isNotBlank()
        ) {
            ManifestAwareDataSourceFactory(
                manifestUri = request.uri,
                manifest = metadata.rawManifest.toByteArray(),
                fallback = networkFactory,
            )
        } else {
            networkFactory
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory(SCOPED_CACHE_KEY_FACTORY)
        return DefaultDownloaderFactory(cacheFactory, executor).createDownloader(request)
    }
}

private class ManifestAwareDataSourceFactory(
    private val manifestUri: Uri,
    private val manifest: ByteArray,
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            val selected = if (dataSpec.uri == manifestUri) {
                ByteArrayDataSource(manifest)
            } else {
                fallback.createDataSource()
            }
            listeners.forEach(selected::addTransferListener)
            delegate = selected
            return selected.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            requireNotNull(delegate).read(buffer, offset, length)

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            delegate?.responseHeaders.orEmpty()

        @Throws(IOException::class)
        override fun close() {
            delegate?.close()
            delegate = null
        }
    }
}
