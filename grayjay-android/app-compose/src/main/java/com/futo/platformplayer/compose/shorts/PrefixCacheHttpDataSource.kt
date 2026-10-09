package com.futo.platformplayer.compose.shorts

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read-through only: current playback can consume warmed bytes but cannot grow the cache. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PrefixCacheHttpDataSource private constructor(
    private val factory: Factory,
) : HttpDataSource {
    private val requestProperties = mutableMapOf<String, String>()
    private val listeners = mutableListOf<TransferListener>()
    private var spec: DataSpec? = null
    private var prefix: BoundedMediaPrefixCache.Prefix? = null
    private var upstream: HttpDataSource? = null
    private var delivered = 0L
    private var expectedLength = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        close()
        val headers = factory.headers() + requestProperties + dataSpec.httpRequestHeaders
        val request = dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
        spec = request
        prefix = factory.cache.get(factory.key(request), SystemClock.elapsedRealtime())
            ?.takeIf { request.position < it.bytes.size }
        delivered = 0L
        expectedLength = request.length.takeIf { it != C.LENGTH_UNSET.toLong() }
            ?: prefix?.totalLength?.let { (it - request.position).coerceAtLeast(0L) }
            ?: C.LENGTH_UNSET.toLong()
        if (prefix == null) return openUpstream(request).also { expectedLength = it }
        return expectedLength
    }

    private fun openUpstream(request: DataSpec): Long {
        val source = factory.originalFactory.createDataSource()
        listeners.forEach(source::addTransferListener)
        upstream = source
        return source.open(request)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val request = spec ?: throw IOException("Media source is closed")
        val remaining = if (expectedLength == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else expectedLength - delivered
        if (remaining <= 0L) return C.RESULT_END_OF_INPUT
        val requested = minOf(length.toLong(), remaining).toInt()
        val position = request.position + delivered
        prefix?.let { cached ->
            if (position < cached.bytes.size) {
                val count = minOf(requested, cached.bytes.size - position.toInt())
                cached.bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
                delivered += count
                return count
            }
        }
        if (upstream == null) {
            val resumed = request.buildUpon().setPosition(position)
                .setLength(if (expectedLength == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else remaining).build()
            openUpstream(resumed)
        }
        val count = requireNotNull(upstream).read(buffer, offset, requested)
        if (count > 0) delivered += count
        return count
    }

    override fun close() {
        val source = upstream
        upstream = null
        spec = null
        prefix = null
        source?.close()
    }
    override fun getUri(): Uri? = upstream?.uri ?: spec?.uri
    override fun getResponseCode(): Int = upstream?.responseCode ?: if (spec != null) 200 else -1
    override fun getResponseHeaders(): Map<String, List<String>> = upstream?.responseHeaders.orEmpty()
    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }
    override fun setRequestProperty(name: String, value: String) { requestProperties[name] = value }
    override fun clearRequestProperty(name: String) { requestProperties.remove(name) }
    override fun clearAllRequestProperties() { requestProperties.clear() }

    class Factory(
        val originalFactory: HttpDataSource.Factory,
        internal val cache: BoundedMediaPrefixCache,
        private val namespace: String,
        headers: Map<String, String>,
    ) : HttpDataSource.Factory {
        private var defaultHeaders = headers.toMap()
        @Synchronized override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory = apply {
            defaultHeaders = defaultRequestProperties.toMap()
        }
        @Synchronized internal fun headers(): Map<String, String> = defaultHeaders
        internal fun key(spec: DataSpec): String = namespace + "|" + spec.uri + "|" +
            spec.httpRequestHeaders.entries.sortedBy { it.key.lowercase() }.joinToString("|") { "${it.key.lowercase()}:${it.value}" }
        override fun createDataSource(): HttpDataSource = PrefixCacheHttpDataSource(this)

        /** Always starts at byte zero. A server ignoring Range cannot force a large prefix skip. */
        suspend fun warmPrefix(url: String, byteLimit: Int, expiresAtMs: Long) {
            val boundedLength = minOf(byteLimit.toLong(), mediaLengthFromUrl(url) ?: Long.MAX_VALUE)
            val request = DataSpec.Builder().setUri(url).setLength(boundedLength)
                .setHttpRequestHeaders(headers()).build()
            val key = key(request)
            if (cache.get(key, SystemClock.elapsedRealtime()) != null) return
            val generation = cache.generation()
            val warmed = readBoundedPrefix(originalFactory, request, byteLimit)
            cache.put(key, BoundedMediaPrefixCache.Prefix(warmed, expiresAtMs, mediaLengthFromUrl(url)), generation)
        }
    }
}

internal fun HttpDataSource.Factory.withoutShortsPrefixCache(): HttpDataSource.Factory =
    (this as? PrefixCacheHttpDataSource.Factory)?.originalFactory ?: this

private fun mediaLengthFromUrl(url: String): Long? = runCatching {
    Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()?.takeIf { it > 0L }
}.getOrNull()

/** Cancellation closes the blocking transport on IO, never on the UI/gesture thread. */
private suspend fun readBoundedPrefix(
    factory: HttpDataSource.Factory,
    request: DataSpec,
    byteLimit: Int,
): ByteArray = suspendCancellableCoroutine { continuation ->
    val source = factory.createDataSource()
    continuation.invokeOnCancellation {
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { runCatching { source.close() } })
    }
    Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
        try {
            if (!continuation.isActive) return@Runnable
            source.open(request)
            val bytes = ByteArray(byteLimit)
            var read = 0
            while (read < bytes.size && continuation.isActive) {
                val count = source.read(bytes, read, bytes.size - read)
                if (count < 0) break
                if (count == 0) break
                read += count
            }
            if (continuation.isActive) continuation.resume(bytes.copyOf(read))
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        } finally { runCatching { source.close() } }
    })
}
