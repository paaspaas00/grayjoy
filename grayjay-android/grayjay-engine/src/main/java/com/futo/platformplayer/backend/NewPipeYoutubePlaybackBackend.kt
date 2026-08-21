package com.futo.platformplayer.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.media3.datasource.HttpDataSource
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal const val NEWPIPE_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
private const val YOUTUBE_WATCH_PREFIX = "https://www.youtube.com/watch?v="

/**
 * Standalone NewPipe Extractor adapter used only for resolving YouTube playback streams.
 * Search, feeds, account state, and every other source continue to use Grayjay plugins.
 */
class NewPipeYoutubePlaybackBackend(
    private val providedDownloader: Downloader? = null,
) {
    private val downloader: Downloader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        providedDownloader ?: OkHttpNewPipeDownloader()
    }
    private val progressiveDataSourceFactory = NewPipeYoutubeHttpDataSource.Factory(
        useRangeParameter = false,
        useRequestNumber = true,
    )
    private val dashDataSourceFactory = NewPipeYoutubeHttpDataSource.Factory(
        useRangeParameter = true,
        useRequestNumber = true,
    )
    private val hlsDataSourceFactory = NewPipeYoutubeHttpDataSource.Factory(
        useRangeParameter = false,
        useRequestNumber = false,
    )
    private val resolveCache = ConcurrentHashMap<ResolveCacheKey, CachedPlaybackSource>()
    private val resolveLocks = ConcurrentHashMap<ResolveCacheKey, Mutex>()
    @Volatile
    private var localization = Localization.DEFAULT
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(NEWPIPE_INITIALIZATION_LOCK) {
            if (!initialized) {
                NewPipe.init(downloader, localization)
                initialized = true
            }
        }
    }

    fun configureLanguage(languageTag: String) {
        val locale = Locale.forLanguageTag(languageTag)
        val updated = Localization(
            locale.language.takeIf(String::isNotBlank) ?: "en",
            locale.country.takeIf(String::isNotBlank),
        )
        if (updated != localization) resolveCache.clear()
        localization = updated
        if (initialized) {
            synchronized(NEWPIPE_INITIALIZATION_LOCK) {
                NewPipe.setupLocalization(localization)
            }
        }
    }

    suspend fun resolve(
        contentUrl: String,
        preferredAudioLanguage: String?,
        preferOriginalAudio: Boolean,
    ): GrayjayPlaybackSource = withContext(Dispatchers.IO) {
        ensureInitialized()
        val cacheKey = ResolveCacheKey(
            contentUrl = contentUrl.toYoutubeWatchUrl(),
            preferredAudioLanguage = preferredAudioLanguage
                ?.lowercase(Locale.ROOT)
                .orEmpty(),
            preferOriginalAudio = preferOriginalAudio,
            localization = localization.localizationCode,
        )
        resolveCache[cacheKey]
            ?.takeIf { !it.isExpired() }
            ?.let { return@withContext it.source }
        val lock = resolveLocks.getOrPut(cacheKey) { Mutex() }
        lock.withLock {
            try {
                resolveCache[cacheKey]
                    ?.takeIf { !it.isExpired() }
                    ?.let { return@withLock it.source }
                val source = extractPlaybackSource(
                    youtubeUrl = cacheKey.contentUrl,
                    preferredAudioLanguage = preferredAudioLanguage,
                    preferOriginalAudio = preferOriginalAudio,
                )
                trimResolveCache()
                resolveCache[cacheKey] = CachedPlaybackSource(source)
                source
            } finally {
                resolveLocks.remove(cacheKey, lock)
            }
        }
    }

    private fun extractPlaybackSource(
        youtubeUrl: String,
        preferredAudioLanguage: String?,
        preferOriginalAudio: Boolean,
    ): GrayjayPlaybackSource {
        synchronized(NEWPIPE_INITIALIZATION_LOCK) {
            NewPipe.setupLocalization(localization)
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl)
        return info.toGrayjayPlaybackSource(
            fallbackUrl = youtubeUrl,
            preferredAudioLanguage = preferredAudioLanguage,
            preferOriginalAudio = preferOriginalAudio,
        )
    }

    private fun trimResolveCache() {
        resolveCache.entries.removeAll { it.value.isExpired() }
        while (resolveCache.size >= MAX_RESOLVE_CACHE_ENTRIES) {
            val oldest = resolveCache.entries.minByOrNull { it.value.createdAtMs } ?: break
            resolveCache.remove(oldest.key, oldest.value)
        }
    }

    private fun StreamInfo.toGrayjayPlaybackSource(
        fallbackUrl: String,
        preferredAudioLanguage: String?,
        preferOriginalAudio: Boolean,
    ): GrayjayPlaybackSource {
        val playableAudio = audioStreams.filter { it.isPlayableByMedia3() }
        val explicitOriginalAvailable = playableAudio.any {
            it.audioTrackType == AudioTrackType.ORIGINAL
        }
        val originalLanguage = languageInfo?.toLanguageTag()?.takeIf(String::isNotBlank)
        val audioDescriptors = playableAudio.mapIndexed { index, stream ->
            val language = stream.audioLocale?.toLanguageTag()?.takeIf(String::isNotBlank)
            NewPipeAudioDescriptor(
                stream = stream,
                language = language,
                isOriginal = when {
                    stream.audioTrackType == AudioTrackType.ORIGINAL -> true
                    explicitOriginalAvailable -> false
                    originalLanguage != null && language.matchesAudioLanguage(originalLanguage) -> true
                    else -> index == 0
                },
                bitrate = stream.bitrateBitsPerSecond(),
            )
        }
        val selectedAudio = selectPreferredAudioSourceIndex(
            sources = audioDescriptors.map {
                AudioSourcePreference(
                    language = it.language,
                    isOriginal = it.isOriginal,
                    isPriority = false,
                    bitrate = it.bitrate,
                )
            },
            preferredLanguage = preferredAudioLanguage,
            preferOriginal = preferOriginalAudio,
        )?.let(audioDescriptors::get)

        val videoOnly = videoOnlyStreams.filter { it.isPlayableByMedia3() }
        val muxedVideo = videoStreams.filter { it.isPlayableByMedia3() }
        val usesSeparateAudio = videoOnly.isNotEmpty() && selectedAudio != null
        val selectedVideos = if (usesSeparateAudio) videoOnly else muxedVideo
        val isLive = streamType == StreamType.LIVE_STREAM ||
            streamType == StreamType.AUDIO_LIVE_STREAM
        val isAudioOnly = streamType == StreamType.AUDIO_STREAM ||
            streamType == StreamType.AUDIO_LIVE_STREAM

        val hls = hlsUrl.takeIf { isLive && it.isNotBlank() }
        val dash = dashMpdUrl.takeIf { it.isNotBlank() }
        val selectedVideo = selectedVideos.maxWithOrNull(
            compareBy<VideoStream> { it.height.coerceAtLeast(0) }
                .thenBy { it.bitrate.coerceAtLeast(0) },
        )
        val primaryStream = when {
            hls != null -> ResolvedNewPipeStream(
                hls,
                GrayjayStreamType.Hls,
                dataSourceFactory = hlsDataSourceFactory,
            )
            selectedVideo != null -> selectedVideo.toResolvedStream(fallbackUrl, duration)
            dash != null -> ResolvedNewPipeStream(dash, GrayjayStreamType.Dash)
            selectedAudio != null -> selectedAudio.stream.toResolvedStream(fallbackUrl, duration)
            else -> error("NewPipe returned no supported YouTube playback stream.")
        }
        val audioStream = selectedAudio?.stream?.toResolvedStream(fallbackUrl, duration)
        val effectiveAudioOnly = isAudioOnly ||
            (selectedVideo == null && hls == null && dash == null && selectedAudio != null)
        val selectedContainsAudio = effectiveAudioOnly || hls != null ||
            (selectedVideo != null && !selectedVideo.isVideoOnly())

        val videoVariants = if (hls != null || effectiveAudioOnly) {
            emptyList()
        } else {
            selectedVideos.mapNotNull { stream ->
                stream.toResolvedStreamOrNull(fallbackUrl, duration)?.let { resolved ->
                    GrayjayVideoVariant(
                        height = stream.height.takeIf { it > 0 }
                            ?: stream.getResolution().resolutionHeight(),
                        videoUrl = resolved.url,
                        streamType = resolved.type,
                        requestHeaders = NEWPIPE_PLAYBACK_HEADERS,
                        rawDashManifest = resolved.rawManifest,
                        dataSourceFactory = resolved.dataSourceFactory,
                    )
                }
            }.distinctBy { it.height to it.videoUrl }
                .sortedBy(GrayjayVideoVariant::height)
        }
        val audioVariants = audioDescriptors.mapNotNull { descriptor ->
            descriptor.stream.toResolvedStreamOrNull(fallbackUrl, duration)?.let { resolved ->
                GrayjayAudioVariant(
                    bitrate = descriptor.bitrate,
                    name = descriptor.stream.audioTrackName
                        ?.takeIf(String::isNotBlank)
                        ?: descriptor.stream.format?.name
                        ?: "Audio",
                    audioUrl = resolved.url,
                    streamType = resolved.type,
                    language = descriptor.language,
                    isOriginal = descriptor.isOriginal,
                    requestHeaders = NEWPIPE_PLAYBACK_HEADERS,
                    rawDashManifest = resolved.rawManifest,
                    dataSourceFactory = resolved.dataSourceFactory,
                )
            }
        }.distinctBy { Triple(it.audioUrl, it.language, it.bitrate) }
            .sortedBy(GrayjayAudioVariant::bitrate)
        val audioLanguages = audioDescriptors
            .mapNotNull { descriptor ->
                descriptor.language?.let { language ->
                    GrayjayAudioLanguage(
                        language = language,
                        name = descriptor.stream.audioTrackName
                            ?.takeIf(String::isNotBlank)
                            ?: descriptor.stream.audioLocale
                                ?.let { locale -> locale.getDisplayName(locale) }
                                .orEmpty()
                                .ifBlank { language.uppercase(Locale.ROOT) },
                        isOriginal = descriptor.isOriginal,
                    )
                }
            }
            .distinctBy { it.language.lowercase(Locale.ROOT) }

        val selectedAudioUrl = audioStream?.url.takeIf {
            usesSeparateAudio && !effectiveAudioOnly
        }
        return GrayjayPlaybackSource(
            contentUrl = url.takeIf(String::isNotBlank) ?: fallbackUrl,
            shareUrl = originalUrl.takeIf(String::isNotBlank)
                ?: url.takeIf(String::isNotBlank)
                ?: fallbackUrl,
            videoUrl = primaryStream.url,
            streamType = primaryStream.type,
            audioUrl = selectedAudioUrl,
            audioRequestHeaders = NEWPIPE_PLAYBACK_HEADERS.takeIf {
                selectedAudioUrl != null
            }.orEmpty(),
            audioDataSourceFactory = audioStream?.dataSourceFactory.takeIf {
                selectedAudioUrl != null
            },
            audioDownloadUrl = audioStream?.url,
            audioDownloadStreamType = audioStream?.type,
            audioDownloadRawDashManifest = audioStream?.rawManifest,
            audioDownloadRequestHeaders = NEWPIPE_PLAYBACK_HEADERS.takeIf {
                audioStream != null
            }.orEmpty(),
            audioDownloadDataSourceFactory = audioStream?.dataSourceFactory,
            title = name,
            author = uploaderName.orEmpty(),
            authorUrl = uploaderUrl.orEmpty(),
            authorThumbnailUrl = uploaderAvatars.bestImageUrl(),
            authorSubscribers = uploaderSubscriberCount.takeIf { it >= 0L },
            description = cleanNewPipeDescription(description),
            thumbnailUrl = thumbnails.bestImageUrl(),
            durationSeconds = duration.coerceAtLeast(0L),
            viewCount = viewCount.coerceAtLeast(0L),
            datetime = uploadDate?.offsetDateTime(),
            likeCount = likeCount.takeIf { it >= 0L },
            dislikeCount = dislikeCount.takeIf { it >= 0L },
            subtitles = subtitles.mapNotNull { subtitle ->
                subtitle.content.takeIf { subtitle.isUrl && it.isNotBlank() }?.let { subtitleUrl ->
                    GrayjaySubtitleTrack(
                        name = buildString {
                            append(subtitle.displayLanguageName)
                            if (subtitle.isAutoGenerated) append(" (auto)")
                        },
                        language = subtitle.languageTag,
                        uri = subtitleUrl,
                        mimeType = subtitle.format?.mimeType ?: "text/vtt",
                    )
                }
            },
            requestHeaders = NEWPIPE_PLAYBACK_HEADERS,
            rawDashManifest = primaryStream.rawManifest,
            dataSourceFactory = primaryStream.dataSourceFactory,
            videoVariants = videoVariants,
            audioVariants = audioVariants,
            audioLanguages = audioLanguages,
            selectedAudioLanguage = selectedAudio?.language,
            selectedAudioIsOriginal = selectedAudio?.isOriginal == true,
            isLive = isLive,
            isAudioOnly = effectiveAudioOnly,
            videoHasMuxedAudio = selectedContainsAudio,
        )
    }

    private data class NewPipeAudioDescriptor(
        val stream: AudioStream,
        val language: String?,
        val isOriginal: Boolean,
        val bitrate: Int,
    )

    private data class ResolveCacheKey(
        val contentUrl: String,
        val preferredAudioLanguage: String,
        val preferOriginalAudio: Boolean,
        val localization: String,
    )

    private data class CachedPlaybackSource(
        val source: GrayjayPlaybackSource,
        val createdAtMs: Long = System.currentTimeMillis(),
    ) {
        fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs - createdAtMs >= RESOLVE_CACHE_TTL_MS
    }

    private data class ResolvedNewPipeStream(
        val url: String,
        val type: GrayjayStreamType,
        val rawManifest: String? = null,
        val dataSourceFactory: HttpDataSource.Factory? = null,
    )

    private fun Stream.toResolvedStream(
        fallbackUrl: String,
        durationSeconds: Long,
    ): ResolvedNewPipeStream =
        toResolvedStreamOrNull(fallbackUrl, durationSeconds)
            ?: error("NewPipe returned an unusable stream descriptor.")

    private fun Stream.toResolvedStreamOrNull(
        fallbackUrl: String,
        durationSeconds: Long,
    ): ResolvedNewPipeStream? {
        if (content.isBlank()) return null
        if (isUrl) {
            val shouldGenerateDash = deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                (this is AudioStream || (this is VideoStream && isVideoOnly()))
            if (shouldGenerateDash) {
                val manifest = runCatching {
                    YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                        content,
                        requireNotNull(itagItem),
                        durationSeconds,
                    )
                }.getOrNull()
                if (manifest != null) {
                    return ResolvedNewPipeStream(
                        url = content,
                        type = GrayjayStreamType.Dash,
                        rawManifest = manifest,
                        dataSourceFactory = dashDataSourceFactory,
                    )
                }
            }
            return ResolvedNewPipeStream(
                url = content,
                type = if (deliveryMethod == DeliveryMethod.HLS) {
                    GrayjayStreamType.Hls
                } else {
                    // NewPipe's individual DASH representations are ordinary media URLs, not
                    // MPD documents. Media3 must open them as progressive sources and merge the
                    // selected audio representation separately.
                    GrayjayStreamType.Progressive
                },
                dataSourceFactory = if (deliveryMethod == DeliveryMethod.HLS) {
                    hlsDataSourceFactory
                } else {
                    progressiveDataSourceFactory
                },
            )
        }
        if (deliveryMethod != DeliveryMethod.DASH) return null
        val otfManifest = runCatching {
            YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(
                content,
                requireNotNull(itagItem),
                durationSeconds,
            )
        }.getOrNull()
        if (otfManifest != null) {
            return ResolvedNewPipeStream(
                url = manifestUrl?.takeIf(String::isNotBlank) ?: fallbackUrl,
                type = GrayjayStreamType.Dash,
                rawManifest = otfManifest,
                dataSourceFactory = dashDataSourceFactory,
            )
        }
        return ResolvedNewPipeStream(
            url = manifestUrl?.takeIf(String::isNotBlank) ?: fallbackUrl,
            type = GrayjayStreamType.Dash,
            rawManifest = content,
            dataSourceFactory = dashDataSourceFactory,
        )
    }

    private fun Stream.isPlayableByMedia3(): Boolean = content.isNotBlank() &&
        (isUrl || (deliveryMethod == DeliveryMethod.DASH && itagItem != null))

    private fun AudioStream.bitrateBitsPerSecond(): Int {
        val extracted = averageBitrate.takeIf { it > 0 } ?: bitrate.takeIf { it > 0 } ?: 0
        return if (extracted in 1 until 10_000) extracted * 1_000 else extracted
    }

    private fun String.resolutionHeight(): Int = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0

    private fun List<Image>?.bestImageUrl(): String? = this
        ?.filter { it.url.isNotBlank() }
        ?.maxByOrNull { image ->
            image.width.coerceAtLeast(1).toLong() * image.height.coerceAtLeast(1)
        }
        ?.url

    private fun String.toYoutubeWatchUrl(): String {
        val trimmed = trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return YOUTUBE_WATCH_PREFIX + trimmed.substringAfterLast('/').substringBefore('?')
    }

    private fun String?.matchesAudioLanguage(requestedLanguage: String): Boolean {
        val actual = this?.trim()?.replace('_', '-')?.lowercase(Locale.ROOT).orEmpty()
        val requested = requestedLanguage.trim().replace('_', '-').lowercase(Locale.ROOT)
        return actual == requested ||
            actual.substringBefore('-') == requested.substringBefore('-')
    }

    companion object {
        private val NEWPIPE_INITIALIZATION_LOCK = Any()
        private val NEWPIPE_PLAYBACK_HEADERS = mapOf("User-Agent" to NEWPIPE_USER_AGENT)
        private const val RESOLVE_CACHE_TTL_MS = 10 * 60 * 1_000L
        private const val MAX_RESOLVE_CACHE_ENTRIES = 24
    }
}

private class OkHttpNewPipeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) : Downloader() {
    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val method = request.httpMethod().uppercase(Locale.ROOT)
        val body = if (method in METHODS_REQUIRING_BODY) {
            (request.dataToSend() ?: ByteArray(0)).toRequestBody(null)
        } else {
            request.dataToSend()?.toRequestBody(null)
        }
        val requestBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .header("User-Agent", NEWPIPE_USER_AGENT)
            .method(method, body)
        request.headers().forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("YouTube rate limited NewPipe Extractor", request.url())
            }
            return ExtractorResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
    }
}
