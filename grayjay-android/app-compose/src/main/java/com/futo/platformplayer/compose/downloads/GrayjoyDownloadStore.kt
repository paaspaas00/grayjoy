package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
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
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DownloadStatus
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.DownloadUiModel
import com.futo.platformplayer.compose.ui.SubtitleUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

private fun namespacedCacheKeyFactory(namespace: String) = CacheKeyFactory { dataSpec ->
    "$namespace|${SCOPED_CACHE_KEY_FACTORY.buildCacheKey(dataSpec)}"
}

/**
 * Inline DASH manifests must not use the same URI as a representation's BaseURL. Range-based
 * YouTube sources commonly use the video URL for both. If they share a cache key, the MPD XML is
 * cached at byte zero of the MP4 and Media3 later tries to parse that XML as the initialization
 * segment. A fragment keeps relative-URL resolution anchored to the original URL while giving the
 * manifest its own identity; fragments are never sent to the media server.
 */
internal fun inlineManifestRequestUri(sourceUri: String, requestId: String): String =
    "${sourceUri.substringBefore('#')}#$INLINE_MANIFEST_FRAGMENT_PREFIX$requestId"

internal fun rawDashManifestContainsAudio(manifest: String): Boolean =
    Regex("""mimeType\s*=\s*[\"']audio/""", RegexOption.IGNORE_CASE).containsMatchIn(manifest)

private const val INLINE_MANIFEST_FRAGMENT_PREFIX = "grayjoy-inline-manifest-"

internal data class OfflinePlaybackPart(
    val mediaType: DownloadMediaType,
    val name: String,
    val expectedPartCount: Int,
    val uri: String,
    val mimeType: String = "",
    val headers: Map<String, String> = emptyMap(),
    val rawManifest: String = "",
    val cacheNamespace: String = "",
    val streamKeys: List<StreamKey> = emptyList(),
    val completed: Boolean = true,
)

internal data class DownloadCompletionPart(
    val name: String,
    val expectedPartCount: Int,
    val completed: Boolean,
    val bytesDownloaded: Long,
    val rootResourceCached: Boolean,
)

/**
 * Old Grayjay only moved a VideoDownload into the VideoLocal store after every selected output
 * existed and passed validation. Keep that promotion rule independent from Media3's UI state.
 */
internal fun isValidatedCompletedDownload(parts: List<DownloadCompletionPart>): Boolean {
    val expected = parts.maxOfOrNull(DownloadCompletionPart::expectedPartCount)
        ?.coerceAtLeast(1)
        ?: return false
    val uniqueParts = parts.distinctBy(DownloadCompletionPart::name)
    return uniqueParts.size >= expected &&
        uniqueParts.all { part ->
            part.completed && part.bytesDownloaded > 0L && part.rootResourceCached
        }
}

internal fun aggregateDownloadStatus(
    removing: Boolean,
    hasFailedRequest: Boolean,
    hasRemovingRequest: Boolean,
    validatedComplete: Boolean,
    media3Complete: Boolean,
    hasDownloadingRequest: Boolean,
    hasStoppedRequest: Boolean,
): DownloadStatus = when {
    removing -> DownloadStatus.Removing
    hasFailedRequest -> DownloadStatus.Failed
    hasRemovingRequest -> DownloadStatus.Removing
    validatedComplete -> DownloadStatus.Completed
    media3Complete -> DownloadStatus.Failed
    hasDownloadingRequest -> DownloadStatus.Downloading
    hasStoppedRequest -> DownloadStatus.Paused
    else -> DownloadStatus.Queued
}

private data class DownloadGroupKey(
    val profileId: String,
    val videoId: String,
    val mediaType: DownloadMediaType,
)

private data class CompletedDownloadRecord(
    val key: DownloadGroupKey,
    val requestIds: Set<String>,
    val completedAtMs: Long,
)

internal fun VideoUiModel.withOfflinePlayback(
    parts: List<OfflinePlaybackPart>,
): VideoUiModel? {
    fun completedParts(mediaType: DownloadMediaType): List<OfflinePlaybackPart> {
        val candidates = parts.filter { it.mediaType == mediaType }.distinctBy { it.name }
        val expected = candidates.maxOfOrNull(OfflinePlaybackPart::expectedPartCount)
            ?.coerceAtLeast(1)
            ?: return emptyList()
        return candidates.takeIf { it.size >= expected && it.all(OfflinePlaybackPart::completed) }
            .orEmpty()
    }

    val videoParts = completedParts(DownloadMediaType.Video)
    if (videoParts.isNotEmpty()) {
        val main = videoParts.firstOrNull { it.name == "video" } ?: return null
        val audio = videoParts.firstOrNull { it.name == "audio" }
        val downloadedSubtitles = videoParts
            .filter { it.name.startsWith("subtitle-") }
            .sortedBy { it.name.substringAfterLast('-').toIntOrNull() ?: Int.MAX_VALUE }
            .map { part ->
                val index = part.name.substringAfterLast('-').toIntOrNull()
                val original = index?.let(subtitleTracks::getOrNull)
                SubtitleUiModel(
                    name = original?.name ?: part.name,
                    language = original?.language,
                    uri = part.uri,
                    mimeType = part.mimeType.ifBlank { original?.mimeType.orEmpty() },
                )
            }
        return copy(
            isDownloaded = true,
            playbackFromDownload = true,
            playbackAudioOnly = false,
            playbackUrl = main.uri,
            playbackMimeType = main.mimeType,
            playbackManifest = main.rawManifest,
            audioUrl = audio?.uri.orEmpty(),
            audioRequestHeaders = audio?.headers.orEmpty(),
            audioDataSourceFactory = null,
            playbackRequestHeaders = main.headers,
            playbackCacheNamespace = main.cacheNamespace,
            audioCacheNamespace = audio?.cacheNamespace.orEmpty(),
            playbackStreamKeys = main.streamKeys,
            audioStreamKeys = audio?.streamKeys.orEmpty(),
            playbackDataSourceFactory = null,
            subtitleTracks = downloadedSubtitles,
            qualityVariants = emptyList(),
            audioQualityVariants = emptyList(),
        )
    }

    val audioParts = completedParts(DownloadMediaType.Audio)
    val audio = audioParts.firstOrNull { it.name == "audio" }
        ?: audioParts.firstOrNull()
        ?: return null
    return copy(
        isDownloaded = true,
        playbackFromDownload = true,
        playbackAudioOnly = true,
        playbackUrl = audio.uri,
        playbackMimeType = audio.mimeType,
        playbackManifest = audio.rawManifest,
        audioUrl = "",
        audioRequestHeaders = emptyMap(),
        audioDataSourceFactory = null,
        playbackRequestHeaders = audio.headers,
        playbackCacheNamespace = audio.cacheNamespace,
        audioCacheNamespace = "",
        playbackStreamKeys = audio.streamKeys,
        audioStreamKeys = emptyList(),
        playbackDataSourceFactory = null,
        subtitleTracks = emptyList(),
        qualityVariants = emptyList(),
        audioQualityVariants = emptyList(),
    )
}

@OptIn(UnstableApi::class)
class GrayjoyDownloadStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val completedCatalogPreferences = appContext.getSharedPreferences(
        COMPLETED_CATALOG_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val completedRecords = loadCompletedRecords()
    private val initialized = CompletableDeferred<Unit>()
    private val databaseProvider = StandaloneDatabaseProvider(appContext)
    private val downloaderExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val cache = SimpleCache(
        appContext.filesDir.resolve("offline_media/cache"),
        NoOpCacheEvictor(),
        databaseProvider,
    )
    private val downloadsById = linkedMapOf<String, Download>()
    private val removingGroups = mutableSetOf<DownloadGroupKey>()
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
        // Match Grayjay's privacy/data-friendly default: queue on metered networks and resume
        // automatically when an unmetered connection is available.
        requirements = Requirements(Requirements.NETWORK_UNMETERED)
        addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    reloadDownloadIndex()
                    initialized.complete(Unit)
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
                    val removedMetadata = DownloadRequestMetadata.from(download.request.data)
                    downloadsById.remove(download.request.id)
                    failureMessages.remove(download.request.id)
                    releaseRequestDataSourceFactory(download.request.id)
                    cleanupCacheIfUnused()
                    removedMetadata?.let { metadata ->
                        val key = DownloadGroupKey(
                            metadata.profileId,
                            metadata.videoId,
                            metadata.mediaType,
                        )
                        val groupStillExists = downloadsById.values.any { remaining ->
                            DownloadRequestMetadata.from(remaining.request.data)?.let {
                                it.profileId == key.profileId && it.videoId == key.videoId &&
                                    it.mediaType == key.mediaType
                            } == true
                        }
                        if (!groupStillExists) removingGroups.remove(key)
                    }
                    publishDownloads()
                }
            },
        )
    }

    /**
     * Builds a cache-only source for a descriptor returned by [playbackDescriptorFor].
     *
     * Like Grayjay's VideoLocal sources, this must never be used merely because a download
     * record exists: failed and partial jobs are not playable offline media.
     */
    fun offlinePlayback(cacheNamespace: String): DataSource.Factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(null)
            .setCacheKeyFactory(
                CacheKeyFactory { dataSpec ->
                    val namespacedKey = cacheNamespace.takeIf(String::isNotBlank)?.let {
                        namespacedCacheKeyFactory(it).buildCacheKey(dataSpec)
                    }
                    val scopedKey = SCOPED_CACHE_KEY_FACTORY.buildCacheKey(dataSpec)
                    val lengthToCheck = 1L
                    when {
                        namespacedKey != null &&
                            cache.isCached(namespacedKey, dataSpec.position, lengthToCheck) ->
                            namespacedKey
                        cache.isCached(scopedKey, dataSpec.position, lengthToCheck) -> scopedKey
                        dataSpec.key != null &&
                            cache.isCached(dataSpec.key!!, dataSpec.position, lengthToCheck) ->
                            dataSpec.key!! // Compatibility with downloads made by older builds.
                        else -> scopedKey
                    }
                },
            )
            // Only DownloadManager is allowed to write the permanent offline cache.
            .setCacheWriteDataSinkFactory(null)

    suspend fun enqueue(
        profileId: String,
        video: VideoUiModel,
        mediaType: DownloadMediaType = DownloadMediaType.Video,
        preferredVideoHeight: Int? = null,
        preferredAudioBitrate: Int? = null,
    ) {
        require(!video.isLive) { "Live streams cannot be downloaded." }
        removingGroups.remove(DownloadGroupKey(profileId, video.id, mediaType))
        val parts = buildParts(video, mediaType)
        require(parts.isNotEmpty()) {
            if (mediaType == DownloadMediaType.Audio) {
                "This source returned no downloadable audio."
            } else {
                "This source returned no downloadable media."
            }
        }
        val preparedAtMs = System.currentTimeMillis()
        // Prepare and validate every selected output before handing any work to Media3. This
        // preserves Grayjay's VideoDownload invariant: a two-part video/audio job cannot start
        // as a one-part orphan merely because probing the second manifest failed.
        val preparedRequests = parts.mapIndexed { index, part ->
            val metadata = DownloadRequestMetadata(
                profileId = profileId,
                videoId = video.id,
                title = video.title,
                part = part.name,
                partCount = parts.size,
                mediaType = mediaType,
                headers = part.headers,
                rawManifest = part.rawManifest,
                preparedAtMs = preparedAtMs,
                targetVideoHeight = preferredVideoHeight,
                targetAudioBitrate = preferredAudioBitrate,
                requiresPluginTransport = part.dataSourceFactory != null ||
                    part.rawManifest.contains("grayjay.internal") ||
                    Uri.parse(part.uri).host == "grayjay.internal",
            )
            val requestId = downloadId(profileId, video.id, mediaType, part.name, index)
            val requestUri = if (part.rawManifest.isNotBlank()) {
                inlineManifestRequestUri(part.uri, requestId)
            } else {
                part.uri
            }
            val requestBuilder = DownloadRequest.Builder(
                requestId,
                Uri.parse(requestUri),
            ).setData(metadata.toByteArray())
            if (part.mimeType.isNotBlank()) requestBuilder.setMimeType(part.mimeType)
            val streamKeys = selectAdaptiveStreamKeys(
                part = part,
                mediaType = mediaType,
                preferredVideoHeight = preferredVideoHeight,
                preferredAudioBitrate = preferredAudioBitrate,
            )
            if (streamKeys.isNotEmpty()) requestBuilder.setStreamKeys(streamKeys)
            PreparedDownloadRequest(requestBuilder.build(), part.dataSourceFactory)
        }
        preparedRequests.forEach { prepared ->
            prepared.dataSourceFactory?.let {
                requestDataSourceFactories[prepared.request.id] = it
            }
        }
        val submittedRequestIds = mutableListOf<String>()
        try {
            preparedRequests.forEach { prepared ->
                GrayjoyDownloadService.add(appContext, prepared.request)
                submittedRequestIds += prepared.request.id
            }
        } catch (error: Throwable) {
            submittedRequestIds.forEach(downloadManager::removeDownload)
            preparedRequests.forEach { releaseRequestDataSourceFactory(it.request.id) }
            throw error
        }
    }

    /**
     * A Media3 adaptive request without stream keys means "download every rendition". Old
     * Grayjay instead resolves the manifest immediately before transfer and fixes one video and
     * one audio rendition. Use DownloadHelper to produce that same bounded request while the
     * source's plugin request executor/modifier is still alive.
     */
    @Suppress("DEPRECATION")
    private suspend fun selectAdaptiveStreamKeys(
        part: DownloadPart,
        mediaType: DownloadMediaType,
        preferredVideoHeight: Int?,
        preferredAudioBitrate: Int?,
    ): List<StreamKey> {
        if (part.rawManifest.isNotBlank() || !part.isAdaptiveManifest()) return emptyList()

        val registeredFactory = part.dataSourceFactory
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        part.headers.takeIf(Map<String, String>::isNotEmpty)?.let { headers ->
            httpFactory.setDefaultRequestProperties(headers)
            (registeredFactory as? HttpDataSource.Factory)?.setDefaultRequestProperties(headers)
        }
        val networkFactory: DataSource.Factory = when (registeredFactory) {
            is HttpDataSource.Factory -> DefaultDataSource.Factory(appContext, registeredFactory)
            null -> DefaultDataSource.Factory(appContext, httpFactory)
            else -> registeredFactory
        }
        val mediaItem = MediaItem.Builder()
            .setUri(part.uri)
            .apply { if (part.mimeType.isNotBlank()) setMimeType(part.mimeType) }
            .build()

        return suspendCancellableCoroutine { continuation ->
            val helper = DownloadHelper.forMediaItem(appContext, mediaItem, networkFactory)
            continuation.invokeOnCancellation { helper.release() }
            helper.prepare(
                object : DownloadHelper.Callback {
                    override fun onPrepared(
                        downloadHelper: DownloadHelper,
                        tracksInfoAvailable: Boolean,
                    ) {
                        try {
                            check(tracksInfoAvailable && downloadHelper.periodCount > 0) {
                                "The adaptive manifest returned no downloadable tracks."
                            }
                            val parameters = DefaultTrackSelector.Parameters.Builder(appContext)
                                .setForceHighestSupportedBitrate(true)
                                .setAllowMultipleAdaptiveSelections(false)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, true)
                                .apply {
                                    if (mediaType == DownloadMediaType.Audio) {
                                        setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                                        preferredAudioBitrate
                                            ?.takeIf { it > 0 && it < Int.MAX_VALUE }
                                            ?.let(::setMaxAudioBitrate)
                                    } else {
                                        preferredVideoHeight
                                            ?.takeIf { it > 0 }
                                            ?.let { setMaxVideoSize(Int.MAX_VALUE, it) }
                                    }
                                }
                                .build()
                            repeat(downloadHelper.periodCount) { periodIndex ->
                                downloadHelper.clearTrackSelections(periodIndex)
                                downloadHelper.addTrackSelection(periodIndex, parameters)
                            }
                            val streamKeys = downloadHelper.getDownloadRequest(byteArrayOf()).streamKeys
                            check(streamKeys.isNotEmpty()) {
                                "The adaptive manifest returned no selectable download streams."
                            }
                            if (continuation.isActive) continuation.resume(streamKeys)
                        } catch (error: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        } finally {
                            downloadHelper.release()
                        }
                    }

                    override fun onPrepareError(
                        downloadHelper: DownloadHelper,
                        error: IOException,
                    ) {
                        downloadHelper.release()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                },
            )
        }
    }

    @Synchronized
    fun remove(
        profileId: String,
        videoId: String,
        mediaType: DownloadMediaType? = null,
    ) {
        val recordKeys = completedRecords.keys.filter { key ->
            key.profileId == profileId && key.videoId == videoId &&
                (mediaType == null || key.mediaType == mediaType)
        }
        if (recordKeys.isNotEmpty()) {
            recordKeys.forEach(completedRecords::remove)
            saveCompletedRecords()
        }
        downloadsById.values.mapNotNull { download ->
            DownloadRequestMetadata.from(download.request.data)?.let { metadata ->
                DownloadGroupKey(metadata.profileId, metadata.videoId, metadata.mediaType)
            }
        }.filterTo(removingGroups) { key ->
            key.profileId == profileId && key.videoId == videoId &&
                (mediaType == null || key.mediaType == mediaType)
        }
        downloadsById.values
            .filter { download ->
                DownloadRequestMetadata.from(download.request.data)?.let {
                    it.profileId == profileId && it.videoId == videoId &&
                        (mediaType == null || it.mediaType == mediaType)
                } == true
            }
            .forEach { downloadManager.removeDownload(it.request.id) }
        publishDownloads()
    }

    fun snapshotsFor(profileId: String): Map<String, DownloadUiModel> = downloads.value
        .filter { it.profileId == profileId }
        .associateBy(DownloadUiModel::videoId)

    @Synchronized
    fun playbackDescriptorFor(profileId: String, video: VideoUiModel): VideoUiModel? {
        reconcileCompletedCatalog()
        val completedRequestIds = completedRecords.values
            .filter { record ->
                record.key.profileId == profileId && record.key.videoId == video.id
            }
            .flatMapTo(mutableSetOf(), CompletedDownloadRecord::requestIds)
        if (completedRequestIds.isEmpty()) return null
        val parts = downloadsById.values.mapNotNull { download ->
            if (download.request.id !in completedRequestIds) return@mapNotNull null
            val metadata = DownloadRequestMetadata.from(download.request.data)
                ?.takeIf { it.profileId == profileId && it.videoId == video.id }
                ?: return@mapNotNull null
            OfflinePlaybackPart(
                mediaType = metadata.mediaType,
                name = metadata.part,
                expectedPartCount = metadata.partCount,
                uri = download.request.uri.toString(),
                mimeType = download.request.mimeType.orEmpty(),
                headers = metadata.headers,
                rawManifest = metadata.rawManifest,
                cacheNamespace = download.request.id,
                streamKeys = download.request.streamKeys,
                completed = download.state == Download.STATE_COMPLETED,
            )
        }
        return video.withOfflinePlayback(parts)
    }

    @Synchronized
    fun hasDownloadRecord(
        profileId: String,
        videoId: String,
        mediaType: DownloadMediaType? = null,
    ): Boolean =
        downloadsById.values.any { download ->
            DownloadRequestMetadata.from(download.request.data)?.let { metadata ->
                metadata.profileId == profileId && metadata.videoId == videoId &&
                    (mediaType == null || metadata.mediaType == mediaType)
            } == true
        }

    @Synchronized
    fun hasActiveTransfer(profileId: String): Boolean = downloadsById.values.any { download ->
        download.state in ACTIVE_STATES &&
            DownloadRequestMetadata.from(download.request.data)?.profileId == profileId
    }

    @Synchronized
    fun completedMediaTypes(profileId: String, videoId: String): Set<DownloadMediaType> {
        reconcileCompletedCatalog()
        return completedRecords.keys
            .filterTo(mutableSetOf()) { key ->
                key.profileId == profileId && key.videoId == videoId
            }
            .mapTo(mutableSetOf(), DownloadGroupKey::mediaType)
    }

    fun isInitialized(): Boolean = initialized.isCompleted

    /**
     * The Media3 index is loaded asynchronously. Without this short gate, tapping a downloaded
     * item immediately after process start can miss its completed record and unnecessarily resolve
     * an online plugin stream instead of the local VideoLocal-style descriptor.
     */
    suspend fun awaitInitialized(timeoutMs: Long = 3_000L) {
        if (initialized.isCompleted) return
        withTimeoutOrNull(timeoutMs) { initialized.await() }
    }

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

    @Synchronized
    private fun reconcileCompletedCatalog() {
        if (!downloadManager.isInitialized) return
        val groups = downloadsById.values
            .mapNotNull { download ->
                DownloadRequestMetadata.from(download.request.data)?.let { metadata ->
                    DownloadGroupKey(metadata.profileId, metadata.videoId, metadata.mediaType) to
                        (metadata to download)
                }
            }
            .groupBy({ it.first }, { it.second })
        var changed = false

        completedRecords.keys.toList().forEach { key ->
            val entries = groups[key]
            if (entries == null || !isValidatedCompletedGroup(entries)) {
                completedRecords.remove(key)
                changed = true
            }
        }
        groups.forEach { (key, entries) ->
            if (
                key !in removingGroups &&
                key !in completedRecords &&
                isValidatedCompletedGroup(entries)
            ) {
                completedRecords[key] = CompletedDownloadRecord(
                    key = key,
                    requestIds = entries.mapTo(mutableSetOf()) { it.second.request.id },
                    completedAtMs = System.currentTimeMillis(),
                )
                changed = true
            }
        }
        if (changed) saveCompletedRecords()
    }

    private fun isValidatedCompletedGroup(
        entries: List<Pair<DownloadRequestMetadata, Download>>,
    ): Boolean = isValidatedCompletedDownload(
        entries.map { (metadata, download) ->
            DownloadCompletionPart(
                name = metadata.part,
                expectedPartCount = metadata.partCount,
                completed = download.state == Download.STATE_COMPLETED,
                bytesDownloaded = download.bytesDownloaded,
                rootResourceCached = isRootResourceCached(download.request),
            )
        },
    )

    private fun isRootResourceCached(request: DownloadRequest): Boolean {
        val customKey = request.customCacheKey
        if (customKey != null && cache.isCached(customKey, 0L, 1L)) return true
        val dataSpec = DataSpec(request.uri)
        val namespacedKey = namespacedCacheKeyFactory(request.id).buildCacheKey(dataSpec)
        val scopedKey = SCOPED_CACHE_KEY_FACTORY.buildCacheKey(dataSpec)
        return cache.isCached(namespacedKey, 0L, 1L) ||
            cache.isCached(scopedKey, 0L, 1L) ||
            cache.isCached(request.uri.toString(), 0L, 1L)
    }

    private fun loadCompletedRecords(): MutableMap<DownloadGroupKey, CompletedDownloadRecord> {
        val raw = completedCatalogPreferences.getString(COMPLETED_CATALOG_KEY, null)
            ?: return linkedMapOf()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                repeat(array.length()) { index ->
                    val json = array.getJSONObject(index)
                    val key = DownloadGroupKey(
                        profileId = json.getString("profileId"),
                        videoId = json.getString("videoId"),
                        mediaType = DownloadMediaType.valueOf(json.getString("mediaType")),
                    )
                    val requestIdsJson = json.getJSONArray("requestIds")
                    val requestIds = buildSet {
                        repeat(requestIdsJson.length()) { add(requestIdsJson.getString(it)) }
                    }
                    put(
                        key,
                        CompletedDownloadRecord(
                            key = key,
                            requestIds = requestIds,
                            completedAtMs = json.optLong("completedAtMs", 0L),
                        ),
                    )
                }
            }.toMutableMap()
        }.getOrElse { linkedMapOf() }
    }

    private fun saveCompletedRecords() {
        val array = JSONArray().apply {
            completedRecords.values.forEach { record ->
                put(
                    JSONObject().apply {
                        put("profileId", record.key.profileId)
                        put("videoId", record.key.videoId)
                        put("mediaType", record.key.mediaType.name)
                        put("completedAtMs", record.completedAtMs)
                        put("requestIds", JSONArray(record.requestIds.toList()))
                    },
                )
            }
        }
        completedCatalogPreferences.edit().putString(COMPLETED_CATALOG_KEY, array.toString()).apply()
    }

    private fun publishDownloads() {
        reconcileCompletedCatalog()
        val perType = downloadsById.values
            .mapNotNull { download ->
                DownloadRequestMetadata.from(download.request.data)?.let { it to download }
            }
            .groupBy { (metadata, _) ->
                DownloadGroupKey(metadata.profileId, metadata.videoId, metadata.mediaType)
            }
            .map { (key, entries) -> aggregate(key, entries) }
        _downloads.value = perType
            .groupBy { snapshot -> snapshot.profileId to snapshot.videoId }
            .map { (_, snapshots) -> combineMediaTypes(snapshots) }
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
        key: DownloadGroupKey,
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
        val complete = key in completedRecords
        val media3Complete = entries.size >= expectedParts && completedParts >= expectedParts
        // remove() clears the validated catalog before Media3 asynchronously changes each request
        // from COMPLETED to REMOVING. Preserve the explicit group marker during that interval so
        // the repair loop cannot mistake intentional removal for a corrupt completed download.
        val status = aggregateDownloadStatus(
            removing = key in removingGroups,
            hasFailedRequest = downloads.any { it.state == Download.STATE_FAILED },
            hasRemovingRequest = downloads.any { it.state == Download.STATE_REMOVING },
            validatedComplete = complete,
            media3Complete = media3Complete,
            hasDownloadingRequest = downloads.any { it.state == Download.STATE_DOWNLOADING },
            hasStoppedRequest = downloads.any { it.state == Download.STATE_STOPPED },
        )
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
            } ?: if (media3Complete && !complete) {
                appContext.getString(R.string.download_failed)
            } else null,
            requiresPluginTransport = entries.any { it.first.requiresPluginTransport } ||
                metadata.rawManifest.contains("grayjay.internal") ||
                entries.any { (_, download) -> download.request.uri.host == "grayjay.internal" },
            preparedAtMs = entries.maxOfOrNull { it.first.preparedAtMs },
            targetVideoHeight = metadata.targetVideoHeight,
            targetAudioBitrate = metadata.targetAudioBitrate,
            completedMediaTypes = setOfNotNull(metadata.mediaType.takeIf { complete }),
            activeMediaTypes = setOfNotNull(metadata.mediaType.takeIf { status in ACTIVE_UI_STATES }),
            failedMediaTypes = setOfNotNull(metadata.mediaType.takeIf { status == DownloadStatus.Failed }),
        )
    }

    private fun combineMediaTypes(snapshots: List<DownloadUiModel>): DownloadUiModel {
        val display = snapshots.firstOrNull { it.status == DownloadStatus.Removing }
            ?: snapshots.firstOrNull { it.status == DownloadStatus.Downloading }
            ?: snapshots.firstOrNull { it.status == DownloadStatus.Queued }
            ?: snapshots.firstOrNull { it.status == DownloadStatus.Paused }
            ?: snapshots.firstOrNull { it.status == DownloadStatus.Failed }
            ?: snapshots.firstOrNull { it.mediaType == DownloadMediaType.Video }
            ?: snapshots.first()
        val completedTypes = snapshots.flatMapTo(mutableSetOf()) { it.completedMediaTypes }
        val activeTypes = snapshots.flatMapTo(mutableSetOf()) { it.activeMediaTypes }
        val failedTypes = snapshots.flatMapTo(mutableSetOf()) { it.failedMediaTypes }
        val combinedStatus = when {
            display.status in ACTIVE_UI_STATES -> display.status
            failedTypes.isNotEmpty() -> DownloadStatus.Failed
            completedTypes.isNotEmpty() -> DownloadStatus.Completed
            else -> display.status
        }
        return display.copy(
            status = combinedStatus,
            completedMediaTypes = completedTypes,
            activeMediaTypes = activeTypes,
            failedMediaTypes = failedTypes,
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
            val audioUri = video.audioUrl.ifBlank { video.playbackUrl }
            if (audioUri.isNotBlank()) {
                val usesSeparateAudio = video.audioUrl.isNotBlank()
                add(
                    DownloadPart(
                        name = "audio",
                        uri = audioUri,
                        mimeType = if (video.playbackManifest.isNotBlank()) {
                            MimeTypes.APPLICATION_MPD
                        } else video.playbackMimeType,
                        rawManifest = video.playbackManifest,
                        headers = if (usesSeparateAudio) {
                            video.audioRequestHeaders
                        } else video.playbackRequestHeaders,
                        dataSourceFactory = if (usesSeparateAudio) {
                            video.audioDataSourceFactory
                        } else video.playbackDataSourceFactory,
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
        // A generated on-demand MPD already carries its one selected video and audio
        // representation. Scheduling audioUrl again would download the same audio twice and make
        // the group unnecessarily depend on a redundant request.
        val mainManifestCarriesAudio = rawDashManifestContainsAudio(video.playbackManifest)
        if (
            video.audioUrl.isNotBlank() &&
            video.audioUrl != mainUri &&
            !mainManifestCarriesAudio
        ) {
            add(
                DownloadPart(
                    name = "audio",
                    uri = video.audioUrl,
                    mimeType = "",
                    headers = video.audioRequestHeaders,
                    dataSourceFactory = video.audioDataSourceFactory,
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
    ) {
        fun isAdaptiveManifest(): Boolean {
            val normalized = uri.substringBefore('?').substringBefore('#').lowercase()
            return mimeType == MimeTypes.APPLICATION_M3U8 ||
                mimeType == MimeTypes.APPLICATION_MPD ||
                normalized.endsWith(".m3u8") ||
                normalized.endsWith(".mpd")
        }
    }

    private data class PreparedDownloadRequest(
        val request: DownloadRequest,
        val dataSourceFactory: DataSource.Factory?,
    )

    companion object {
        private const val DOWNLOAD_INDEX_NAME = "grayjoy_offline"
        private const val DOWNLOAD_REQUEST_SCHEMA_VERSION = 3
        private const val COMPLETED_CATALOG_PREFERENCES = "grayjoy_completed_downloads_v1"
        private const val COMPLETED_CATALOG_KEY = "records"
        private val ACTIVE_STATES = setOf(
            Download.STATE_QUEUED,
            Download.STATE_DOWNLOADING,
            Download.STATE_RESTARTING,
            Download.STATE_REMOVING,
        )
        private val ACTIVE_UI_STATES = setOf(
            DownloadStatus.Preparing,
            DownloadStatus.Queued,
            DownloadStatus.Downloading,
            DownloadStatus.Paused,
            DownloadStatus.Removing,
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
                    "$DOWNLOAD_REQUEST_SCHEMA_VERSION\u0000$profileId\u0000$videoId\u0000${mediaType.name}\u0000$part\u0000$index"
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
    val preparedAtMs: Long,
    val targetVideoHeight: Int?,
    val targetAudioBitrate: Int?,
    val requiresPluginTransport: Boolean,
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
        put("preparedAtMs", preparedAtMs)
        targetVideoHeight?.let { put("targetVideoHeight", it) }
        targetAudioBitrate?.let { put("targetAudioBitrate", it) }
        put("requiresPluginTransport", requiresPluginTransport)
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
                preparedAtMs = json.optLong("preparedAtMs", 0L),
                targetVideoHeight = json.optInt("targetVideoHeight").takeIf { it > 0 },
                targetAudioBitrate = json.optInt("targetAudioBitrate").takeIf { it > 0 },
                requiresPluginTransport = json.optBoolean("requiresPluginTransport") ||
                    json.optString("rawManifest").contains("grayjay.internal"),
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
            .setCacheKeyFactory(namespacedCacheKeyFactory(request.id))
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
            val isVersionedInlineManifest =
                manifestUri.fragment?.startsWith(INLINE_MANIFEST_FRAGMENT_PREFIX) == true
            val isLegacyManifestRootRequest =
                dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong()
            val selected = if (
                dataSpec.uri == manifestUri &&
                (isVersionedInlineManifest || isLegacyManifestRootRequest)
            ) {
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
