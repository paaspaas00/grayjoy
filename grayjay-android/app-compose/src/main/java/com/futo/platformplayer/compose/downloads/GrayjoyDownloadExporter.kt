package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ProgressHolder
import com.futo.platformplayer.compose.ui.DownloadMediaType
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class MediaExportStage { Preparing, Converting, Copying }

internal data class MediaExportProgress(
    val completed: Int,
    val total: Int,
    val currentTitle: String,
    val stage: MediaExportStage,
    val itemProgress: Float?,
) {
    val fraction: Float get() {
        val progress = itemProgress?.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val item = when (stage) {
            MediaExportStage.Preparing -> 0f
            MediaExportStage.Converting -> progress * 0.9f
            MediaExportStage.Copying -> 0.9f + progress * 0.1f
        }
        return if (total <= 0) 0f else ((completed.coerceIn(0, total) + item) / total).coerceIn(0f, 1f)
    }
}

/** Copies validated offline downloads out of Media3's private cache as ordinary media files. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class GrayjoyDownloadExporter(
    context: Context,
    private val downloadStore: GrayjoyDownloadStore,
) {
    private val appContext = context.applicationContext
    // Hardware encoders are scarce, especially on older devices. Keep concurrent jobs queued
    // rather than starting several transformers, and reserve destination names inside this lock.
    private val itemMutex = Mutex()

    suspend fun export(
        profileId: String,
        videos: List<VideoUiModel>,
        mediaType: DownloadMediaType,
        directoryUri: Uri,
        onProgress: (MediaExportProgress) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val destination = DocumentFile.fromTreeUri(appContext, directoryUri)
            ?.takeIf { it.isDirectory && it.canWrite() }
            ?: throw IOException("The selected folder is not writable.")
        val exportDirectory = appContext.cacheDir.resolve("download_exports").apply {
            if (!isDirectory && !mkdirs() && !isDirectory) {
                throw IOException("Could not create the export folder.")
            }
        }
        var exported = 0
        val uniqueVideos = videos.distinctBy(VideoUiModel::id)
        uniqueVideos.forEach { video ->
            currentCoroutineContext().ensureActive()
            val progressTracker = MediaExportItemProgressTracker()
            fun report(stage: MediaExportStage, value: Float?) = onProgress(
                MediaExportProgress(
                    exported, uniqueVideos.size, video.title, stage, progressTracker.update(stage, value),
                ),
            )
            report(MediaExportStage.Preparing, null)
            itemMutex.withLock {
                val descriptor = downloadStore.playbackDescriptorFor(
                    profileId = profileId,
                    video = video,
                    mediaType = mediaType,
                ) ?: throw IOException("The selected ${mediaType.name.lowercase()} download is incomplete.")
                val extension = if (mediaType == DownloadMediaType.Audio) "m4a" else "mp4"
                val mimeType = if (mediaType == DownloadMediaType.Audio) "audio/mp4" else "video/mp4"
                val temporaryFile = exportDirectory.resolve("${UUID.randomUUID()}.$extension")
                try {
                    withExportStorageGuard(checkSpace = {
                        DownloadStorageGuard.requireCapacity(appContext, 0L)
                    }) {
                        transformToFile(descriptor, mediaType, temporaryFile) {
                            report(MediaExportStage.Converting, it)
                        }
                        validateExportedFile(
                            output = temporaryFile,
                            mediaType = mediaType,
                            expectsAudio = mediaType == DownloadMediaType.Audio ||
                                descriptor.expectsAudioInVideoExport(),
                        )
                        copyToDestination(
                            input = temporaryFile,
                            destination = destination,
                            mimeType = mimeType,
                            requestedName = uniqueFileName(destination, video.title, extension),
                            onProgress = { report(MediaExportStage.Copying, it) },
                        )
                        exported += 1
                    }
                } finally {
                    temporaryFile.delete()
                }
            }
        }
        exported
    }

    private suspend fun transformToFile(
        video: VideoUiModel,
        mediaType: DownloadMediaType,
        output: File,
        onProgress: (Float?) -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        val handler = Handler(Looper.getMainLooper())
        var activeTransformer: Transformer? = null
        var progressPoll: Runnable? = null
        var finished = false
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                if (!continuation.isActive) return@suspendCancellableCoroutine
                val mediaSourceFactory = FixedMediaSourceFactory { createOfflineMediaSource(video) }
                val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
                    appContext,
                    DefaultDecoderFactory.Builder(appContext)
                        .setEnableDecoderFallback(true)
                        .build(),
                    Clock.DEFAULT,
                    mediaSourceFactory,
                )
                lateinit var transformer: Transformer
                val progress = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (!continuation.isActive) return
                        try {
                            val state = transformer.getProgress(progress)
                            onProgress(
                                if (state == Transformer.PROGRESS_STATE_AVAILABLE) progress.progress / 100f
                                else null,
                            )
                            handler.postDelayed(this, 250L)
                        } catch (error: Exception) {
                            // Listener/progress errors belong to this job, not the main looper.
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
                progressPoll = poll
                transformer = Transformer.Builder(appContext)
                    .setAssetLoaderFactory(assetLoaderFactory)
                    // MP4 can technically carry Opus, but a large number of gallery, automotive,
                    // and desktop players silently ignore that combination. Export ordinary AAC so
                    // an MP4/M4A produced by Grayjoy has broadly playable audio.
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                finished = true
                                handler.removeCallbacks(poll)
                                if (continuation.isActive) {
                                    try {
                                        onProgress(1f)
                                        continuation.resume(Unit)
                                    } catch (error: Exception) {
                                        if (continuation.isActive) continuation.resumeWithException(error)
                                    }
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                finished = true
                                handler.removeCallbacks(poll)
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        },
                    )
                    .build()
                activeTransformer = transformer
                val input = MediaItem.Builder()
                    .setMediaId(video.id)
                    .setUri(video.playbackUrl)
                    .build()
                val edited = EditedMediaItem.Builder(input)
                    .setRemoveVideo(mediaType == DownloadMediaType.Audio)
                    .build()
                try {
                    if (!continuation.isActive) return@suspendCancellableCoroutine
                    transformer.start(edited, output.absolutePath)
                    handler.post(poll)
                } catch (error: Exception) {
                    handler.removeCallbacks(poll)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        } finally {
            // Await main-thread cancellation before the IO caller deletes the temporary output
            // or another export acquires the encoder. Posting cancellation allowed both to race.
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                progressPoll?.let(handler::removeCallbacks)
                if (!finished) activeTransformer?.cancel()
            }
        }
    }

    private fun validateExportedFile(
        output: File,
        mediaType: DownloadMediaType,
        expectsAudio: Boolean,
    ) {
        val extractor = MediaExtractor()
        val trackMimeTypes = try {
            extractor.setDataSource(output.absolutePath)
            buildList {
                repeat(extractor.trackCount) { trackIndex ->
                    extractor.getTrackFormat(trackIndex)
                        .getString(MediaFormat.KEY_MIME)
                        ?.let(::add)
                }
            }
        } finally {
            extractor.release()
        }
        if (!isCompatibleExport(mediaType, expectsAudio, trackMimeTypes)) {
            throw IOException(
                "Exported ${mediaType.name.lowercase()} has incompatible tracks: " +
                    trackMimeTypes.joinToString().ifBlank { "none" },
            )
        }
    }

    private fun createOfflineMediaSource(video: VideoUiModel): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.playbackUrl)
            .setMimeType(video.playbackMimeType.takeIf(String::isNotBlank))
            .setStreamKeys(video.playbackStreamKeys)
            .build()
        val mainFactory = downloadStore.offlinePlayback(video.playbackCacheNamespace)
        val mainSource = if (video.playbackManifest.isNotBlank()) {
            val baseUri = video.playbackUrl.ifBlank { video.contentUrl }.toUri()
            val manifest = DashManifestParser().parse(
                baseUri,
                ByteArrayInputStream(video.playbackManifest.toByteArray()),
            )
            DashMediaSource.Factory(mainFactory).createMediaSource(manifest, mediaItem)
        } else {
            DefaultMediaSourceFactory(mainFactory).createMediaSource(mediaItem)
        }
        if (video.audioUrl.isBlank()) return mainSource
        val audioItem = MediaItem.Builder()
            .setUri(video.audioUrl)
            .setStreamKeys(video.audioStreamKeys)
            .build()
        val audioSource = DefaultMediaSourceFactory(
            downloadStore.offlinePlayback(video.audioCacheNamespace),
        ).createMediaSource(audioItem)
        return MergingMediaSource(true, mainSource, audioSource)
    }

    private suspend fun copyToDestination(
        input: File,
        destination: DocumentFile,
        mimeType: String,
        requestedName: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val output = destination.createFile(mimeType, requestedName)
            ?: throw IOException("Could not create $requestedName in the selected folder.")
        try {
            var copied = 0L
            var lastUpdate = 0L
            val length = input.length().coerceAtLeast(1L)
            onProgress(0f)
            appContext.contentResolver.openOutputStream(output.uri, "w")?.use { outputStream ->
                input.inputStream().buffered().use { inputStream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = inputStream.read(buffer)
                        if (count < 0) break
                        outputStream.write(buffer, 0, count)
                        copied += count
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 150L) {
                            onProgress((copied.toDouble() / length).toFloat().coerceIn(0f, 1f))
                            lastUpdate = now
                        }
                    }
                    currentCoroutineContext().ensureActive()
                }
            } ?: throw IOException("Could not open $requestedName for writing.")
            onProgress(1f)
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private fun uniqueFileName(
        destination: DocumentFile,
        title: String,
        extension: String,
    ): String {
        val base = mediaExportFileBaseName(title)
        var candidate = "$base.$extension"
        var suffix = 2
        while (destination.findFile(candidate) != null) {
            candidate = "$base ($suffix).$extension"
            suffix += 1
        }
        return candidate
    }
}

internal fun VideoUiModel.expectsAudioInVideoExport(): Boolean =
    audioUrl.isNotBlank() ||
        playbackHasMuxedAudio ||
        rawDashManifestContainsAudio(playbackManifest) ||
        playbackManifest.contains("TYPE=AUDIO", ignoreCase = true)

internal fun isCompatibleExport(
    mediaType: DownloadMediaType,
    expectsAudio: Boolean,
    trackMimeTypes: List<String>,
): Boolean {
    val hasVideo = trackMimeTypes.any { it.startsWith("video/") }
    val audioTypes = trackMimeTypes.filter { it.startsWith("audio/") }
    val hasCompatibleAudio = audioTypes.any {
        it == MimeTypes.AUDIO_AAC || it == "audio/aac"
    }
    return when (mediaType) {
        DownloadMediaType.Audio -> hasCompatibleAudio && !hasVideo
        DownloadMediaType.Video -> hasVideo &&
            (!expectsAudio || hasCompatibleAudio) &&
            (audioTypes.isEmpty() || hasCompatibleAudio)
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private class FixedMediaSourceFactory(
    private val createSource: () -> MediaSource,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = this

    override fun getSupportedTypes(): IntArray = intArrayOf(
        C.CONTENT_TYPE_DASH,
        C.CONTENT_TYPE_HLS,
        C.CONTENT_TYPE_OTHER,
    )

    override fun createMediaSource(mediaItem: MediaItem): MediaSource = createSource()
}
