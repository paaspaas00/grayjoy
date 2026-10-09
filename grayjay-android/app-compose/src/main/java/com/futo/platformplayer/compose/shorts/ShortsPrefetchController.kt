package com.futo.platformplayer.compose.shorts

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream

/** One speculative request at a time, exclusively while the visible Short is already ready. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ShortsPrefetchController(
    private val scope: CoroutineScope,
    private val resolve: suspend (VideoUiModel) -> VideoUiModel,
    private val playbackReady: (String) -> Boolean,
    private val playbackChanges: Flow<*>,
    private val onRetainedSources: (List<VideoUiModel>) -> Unit,
    private val preferredHeight: () -> Int,
) {
    private val descriptors = ShortsResolvedCache<VideoUiModel>()
    private val prefixes = BoundedMediaPrefixCache()
    private var contextKey: String? = null
    private var window = emptyList<VideoUiModel>()
    private var worker: Job? = null
    private var resolvingId: String? = null
    private var resolving: Deferred<VideoUiModel>? = null
    private var warmingId: String? = null
    private var workerGeneration = 0L
    private val attempted = mutableSetOf<String>()
    private val windowChanges = MutableStateFlow(0L)

    fun update(context: String, videos: List<VideoUiModel>) {
        if (contextKey != context) clear()
        contextKey = context
        val updated = videos.distinctBy { it.id }.take(3)
        if (updated.map { it.id } != window.map { it.id }) attempted.clear()
        window = updated
        descriptors.setWindow(window.map { it.id })
        windowChanges.value++
        if (window.isEmpty()) { clear(); return }
        val ids = window.map { it.id }.toSet()
        attempted.retainAll(ids)
        if (resolvingId !in ids) {
            resolving?.cancel(); resolving = null; resolvingId = null
        }
        // Stop warming immediately when its item becomes current: playback owns bandwidth now.
        if (warmingId != null && warmingId !in window.drop(1).map { it.id }) {
            workerGeneration++
            worker?.cancel(); worker = null; warmingId = null
        }
        startWorker()
    }

    suspend fun playback(context: String, videoId: String): VideoUiModel? {
        if (context != contextKey || window.none { it.id == videoId }) return null
        descriptors.get(videoId, SystemClock.elapsedRealtime())?.let { return it }
        val pending = resolving.takeIf { resolvingId == videoId } ?: return null
        val generation = descriptors.generation
        return try {
            val resolved = pending.await()
            remember(videoId, resolved, generation)
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
            null
        } catch (_: Exception) { null }
    }

    fun contains(context: String, videoId: String): Boolean =
        context == contextKey && window.any { it.id == videoId }

    fun rememberPlayed(context: String, video: VideoUiModel): VideoUiModel {
        if (context != contextKey || window.none { it.id == video.id } || video.playbackFromDownload) return video
        return remember(video.id, video, descriptors.generation) ?: video
    }

    fun clear() {
        workerGeneration++
        worker?.cancel(); worker = null
        resolving?.cancel(); resolving = null; resolvingId = null
        warmingId = null; contextKey = null; window = emptyList(); attempted.clear()
        descriptors.clear(); prefixes.clear()
        onRetainedSources(emptyList())
    }

    private fun startWorker() {
        if (worker?.isActive == true || window.isEmpty()) return
        val workerToken = ++workerGeneration
        worker = scope.launch {
            // Gestures and rapid swipes settle before any speculative extraction/network work.
            delay(450)
            while (isActive && window.isNotEmpty()) {
                val currentId = window.first().id
                if (!playbackReady(currentId)) {
                    combine(playbackChanges, windowChanges) { _, _ ->
                        window.firstOrNull()?.id?.let(playbackReady) == true
                    }.first { it }
                    continue
                }
                val candidate = window.drop(1).firstOrNull {
                    it.id !in attempted && !it.isDownloaded && !it.isLive && !it.isDrmProtected &&
                        descriptors.get(it.id, SystemClock.elapsedRealtime()) == null
                } ?: break
                attempted += candidate.id
                val generation = descriptors.generation
                val request = resolving?.takeIf { resolvingId == candidate.id }
                    ?: scope.async { resolve(candidate) }.also {
                        resolvingId = candidate.id; resolving = it
                    }
                try {
                    val result = withTimeoutOrNull(20_000) { request.await() }
                    if (result == null) { request.cancel(); continue }
                    val resolved = remember(candidate.id, result, generation) ?: continue
                    if (window.firstOrNull()?.id == candidate.id || !playbackReady(window.first().id)) continue
                    warmingId = candidate.id
                    val foregroundId = window.first().id
                    withTimeoutOrNull(8_000) {
                        runShortsWarmWhileReady(playbackChanges, windowChanges, ready = {
                            window.firstOrNull()?.id == foregroundId && playbackReady(foregroundId) &&
                                descriptors.accepts(candidate.id, generation)
                        }) { warmMedia(resolved, generation) }
                    }
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                } catch (_: Exception) {
                    // Speculation is optional. The ordinary playback path retains its errors,
                    // fallback, and retry UX; a failed prefetch never marks a Short unavailable.
                } finally {
                    if (resolving === request) { resolving = null; resolvingId = null }
                    if (workerToken == workerGeneration) warmingId = null
                }
            }
        }
    }

    private fun remember(id: String, video: VideoUiModel, generation: Long): VideoUiModel? {
        if (!descriptors.accepts(id, generation)) return null
        descriptors.get(id, SystemClock.elapsedRealtime())?.let { return it }
        val ttl = shortsDescriptorTtlMs(video.streamUrls(), System.currentTimeMillis())
        if (ttl <= 0L) return null
        val namespace = "${contextKey}|$id"
        fun wrapped(factory: HttpDataSource.Factory?, headers: Map<String, String>): HttpDataSource.Factory =
            factory as? PrefixCacheHttpDataSource.Factory ?: PrefixCacheHttpDataSource.Factory(
                factory ?: DefaultHttpDataSource.Factory().setConnectTimeoutMs(3_000).setReadTimeoutMs(3_000),
                prefixes, namespace, headers,
            )
        val cached = video.copy(
            playbackDataSourceFactory = wrapped(video.playbackDataSourceFactory, video.playbackRequestHeaders),
            audioDataSourceFactory = wrapped(video.audioDataSourceFactory, video.audioRequestHeaders),
            qualityVariants = video.qualityVariants.map {
                it.copy(playbackDataSourceFactory = wrapped(it.playbackDataSourceFactory, it.playbackRequestHeaders.ifEmpty { video.playbackRequestHeaders }))
            },
        )
        val accepted = descriptors.put(id, cached, SystemClock.elapsedRealtime(), ttl, generation)
        if (accepted) onRetainedSources(descriptors.values(SystemClock.elapsedRealtime()))
        return cached.takeIf { accepted }
    }

    private suspend fun warmMedia(video: VideoUiModel, generation: Long) {
        val targetHeight = preferredHeight().takeIf { it > 0 } ?: 720
        val variant = video.qualityVariants.filter { it.height > 0 }
            .minWithOrNull(compareBy<com.futo.platformplayer.compose.ui.VideoQualityUiModel> {
                kotlin.math.abs(it.height.toLong() - targetHeight)
            }.thenBy { it.height })
        val videoFactory = (variant?.playbackDataSourceFactory ?: video.playbackDataSourceFactory) as? PrefixCacheHttpDataSource.Factory
        val audioFactory = video.audioDataSourceFactory as? PrefixCacheHttpDataSource.Factory
        val streams = buildList {
            videoFactory?.let { add(WarmStream(variant?.playbackUrl ?: video.playbackUrl,
                variant?.playbackManifest ?: video.playbackManifest,
                variant?.playbackMimeType ?: video.playbackMimeType, it, 384 * 1024)) }
            if (video.audioUrl.isNotBlank() && audioFactory != null) {
                add(WarmStream(video.audioUrl, video.audioDownloadManifest.takeIf { video.audioDownloadUrl == video.audioUrl }.orEmpty(),
                    video.audioDownloadMimeType, audioFactory, 128 * 1024))
            }
        }
        val expiresAt = SystemClock.elapsedRealtime() + shortsDescriptorTtlMs(video.streamUrls(), System.currentTimeMillis())
        for (stream in streams) {
            if (!descriptors.accepts(video.id, generation) || window.firstOrNull()?.id == video.id) return
            val urls = withContext(Dispatchers.Default) {
                initialShortsMediaUrls(stream.url, stream.manifest, stream.mime, targetHeight, video.resolvedAudioLanguage)
            }
            for (url in urls) {
                if (!playbackReady(window.firstOrNull()?.id ?: return) || window.first().id == video.id) return
                stream.factory.warmPrefix(url, stream.budget / urls.size.coerceAtLeast(1), expiresAt)
            }
        }
    }
}

private fun VideoUiModel.streamUrls(): List<String> = listOf(playbackUrl, audioUrl) + qualityVariants.map { it.playbackUrl }

private data class WarmStream(
    val url: String, val manifest: String, val mime: String,
    val factory: PrefixCacheHttpDataSource.Factory, val budget: Int,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun initialShortsMediaUrls(
    url: String,
    manifest: String,
    mime: String,
    targetHeight: Int,
    audioLanguage: String?,
): List<String> {
        if (mime == MimeTypes.APPLICATION_M3U8 || (manifest.isBlank() && mime == MimeTypes.APPLICATION_MPD)) return emptyList()
        if (manifest.isBlank()) return listOf(url).filter { it.startsWith("https://") || it.startsWith("http://") }
        val parsed = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(manifest.toByteArray()))
        if (parsed.periodCount == 0) return emptyList()
        val sets = parsed.getPeriod(0).adaptationSets
        val video = sets.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { it.representations }
            .minByOrNull { kotlin.math.abs(it.format.height.toLong() - targetHeight) }
        val audioCandidates = sets.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { it.representations }
        val audio = audioCandidates.firstOrNull { !audioLanguage.isNullOrBlank() &&
            it.format.language?.substringBefore('-').equals(audioLanguage.substringBefore('-'), true) }
            ?: audioCandidates.firstOrNull()
        val selected = listOfNotNull(video, audio).ifEmpty {
            sets.flatMap { it.representations }.take(1)
        }
        // SegmentBase representations use the same URL for initialization/index/media; a bounded
        // prefix covers normal YouTube init + first media data without traversing the whole file.
        return selected.flatMap { representation ->
            val base = representation.baseUrls.firstOrNull()?.url ?: return@flatMap emptyList()
            val initial = representation.initializationUri?.resolveUriString(base)
            val firstMedia = representation.index?.let { index -> index.getSegmentUrl(index.firstSegmentNum).resolveUriString(base) }
            listOfNotNull(initial, firstMedia ?: base)
        }.distinct().take(4)
            .filter { it.startsWith("https://") || it.startsWith("http://") }
}
