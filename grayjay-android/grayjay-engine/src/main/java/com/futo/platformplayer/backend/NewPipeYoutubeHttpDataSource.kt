package com.futo.platformplayer.backend

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.HttpUtil
import androidx.media3.datasource.TransferListener
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isAndroidStreamingUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isIosStreamingUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isVisionOsStreamingUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebEmbeddedPlayerStreamingUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebStreamingUrl
import java.io.IOException
import java.io.EOFException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Media3 transport for NewPipe's YouTube playback URLs.
 *
 * YouTube's player clients use POST requests for media, attach client-specific headers, add a
 * monotonically increasing request number, and use a query parameter for DASH byte ranges. A
 * plain progressive GET can serve an initial buffer and then stop producing bytes for tens of
 * seconds. This adapter mirrors NewPipe's player transport while retaining OkHttp connection
 * pooling and Media3 transfer callbacks.
 */
class NewPipeYoutubeHttpDataSource private constructor(
    private val client: OkHttpClient,
    private val defaultRequestProperties: Map<String, String>,
    private val useRangeParameter: Boolean,
    private val useRequestNumber: Boolean,
) : BaseDataSource(true), HttpDataSource {
    private val requestProperties = linkedMapOf<String, String>()
    private var openedDataSpec: DataSpec? = null
    private var call: Call? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var opened = false
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var deliveredBytes = 0L
    private var reconnectAttempts = 0
    @Volatile private var closed = true

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        openedDataSpec = dataSpec
        deliveredBytes = 0L
        reconnectAttempts = 0
        transferInitializing(dataSpec)
        return openResponse(dataSpec, startingTransfer = true)
    }

    private fun openResponse(dataSpec: DataSpec, startingTransfer: Boolean): Long {
        if (closed) throw IOException("Media source closed")
        val queryRange = usesRangeParameter(dataSpec)
        val requestUrl = youtubeRequestUrl(dataSpec)
        val headers = linkedMapOf<String, String>().apply {
            putAll(defaultRequestProperties)
            synchronized(requestProperties) { putAll(requestProperties) }
            putAll(dataSpec.httpRequestHeaders)
            putYoutubeHeaders(requestUrl)
            if (!queryRange) {
                HttpUtil.buildRangeRequestHeader(dataSpec.position, dataSpec.length)?.let {
                    put("Range", it)
                }
            }
            put("Accept-Encoding", "identity")
        }
        val request = okhttp3.Request.Builder()
            .url(requestUrl)
            .post(POST_BODY.toRequestBody(null))
            .apply { headers.forEach(::header) }
            .build()
        try {
            val nextCall = client.newCall(request)
            call = nextCall
            if (closed) {
                nextCall.cancel()
                throw IOException("Media source closed")
            }
            val nextResponse = nextCall.execute()
            response = nextResponse
            if (closed) throw IOException("Media source closed")
            if (!nextResponse.isSuccessful) {
                // Seeking exactly to the end is a successful empty read, not a retryable
                // playback failure. Several CDNs encode that boundary as HTTP 416.
                if (nextResponse.code == 416 && dataSpec.position ==
                    HttpUtil.getDocumentSize(nextResponse.header("Content-Range"))) {
                    bytesRemaining = 0L
                    if (startingTransfer) {
                        opened = true
                        transferStarted(dataSpec)
                    }
                    return 0L
                }
                val body = nextResponse.peekBody(64L * 1024L).bytes()
                val exception = InvalidResponseCodeException(
                    nextResponse.code,
                    nextResponse.message,
                    null,
                    nextResponse.headers.toMultimap(),
                    dataSpec,
                    body,
                )
                closeResponse()
                throw exception
            }
            val body = nextResponse.body
                ?: throw IOException("YouTube returned an empty response body.")
            input = body.byteStream()
            if (nextResponse.code == 206) {
                val actualStart = nextResponse.header("Content-Range")
                    ?.substringAfter("bytes ", "")?.substringBefore('-')?.toLongOrNull()
                if (actualStart != null && actualStart != dataSpec.position) {
                    throw IOException("Media server returned the wrong byte range")
                }
            }
            // A server may ignore a Range header and return the complete resource. In that
            // case consume its prefix rather than giving duplicated bytes to the extractor.
            // Query-range endpoints legitimately respond with 200 and already start at position.
            val bytesToSkip = if (!queryRange && nextResponse.code == 200) dataSpec.position else 0L
            skipResponsePrefix(bytesToSkip)
            bytesRemaining = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() }
                ?: body.contentLength().takeIf { it >= 0L }?.let { (it - bytesToSkip).coerceAtLeast(0L) }
                ?: C.LENGTH_UNSET.toLong()
            if (startingTransfer) {
                opened = true
                transferStarted(dataSpec)
            }
            return bytesRemaining
        } catch (error: HttpDataSourceException) {
            throw error
        } catch (error: IOException) {
            closeResponse()
            throw HttpDataSourceException.createForIOException(
                error,
                dataSpec,
                HttpDataSourceException.TYPE_OPEN,
            )
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        while (true) {
            try {
                if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
                val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                    length
                } else {
                    minOf(length.toLong(), bytesRemaining).toInt()
                }
                val stream = input ?: throw IOException("Media source closed")
                if (closed) throw IOException("Media source closed")
                val read = stream.read(buffer, offset, requested)
                if (read < 0) {
                    if (bytesRemaining > 0L) throw EOFException("Truncated media response")
                    return C.RESULT_END_OF_INPUT
                }
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                deliveredBytes += read
                bytesTransferred(read)
                return read
            } catch (error: IOException) {
                val original = openedDataSpec ?: throw error
                if (closed || reconnectAttempts++ >= MAX_READ_RECONNECTS || Thread.currentThread().isInterrupted) {
                    throw HttpDataSourceException.createForIOException(
                        error, original, HttpDataSourceException.TYPE_READ,
                    )
                }
                val remaining = bytesRemaining
                closeResponse()
                // Continue at the first undelivered byte. Do not restart the extractor or feed it
                // the already-consumed prefix when a CDN closes a response halfway through.
                val resumed = original.buildUpon()
                    .setPosition(original.position + deliveredBytes)
                    .setLength(remaining)
                    .build()
                openResponse(resumed, startingTransfer = false)
            }
        }
    }

    override fun getUri(): Uri? = response?.request?.url?.toString()?.let(Uri::parse)

    override fun getResponseCode(): Int = response?.code ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> =
        response?.headers?.toMultimap().orEmpty()

    override fun setRequestProperty(name: String, value: String) {
        synchronized(requestProperties) { requestProperties[name] = value }
    }

    override fun clearRequestProperty(name: String) {
        synchronized(requestProperties) { requestProperties.remove(name) }
    }

    override fun clearAllRequestProperties() {
        synchronized(requestProperties) { requestProperties.clear() }
    }

    override fun close() {
        closed = true
        val wasOpened = opened
        opened = false
        bytesRemaining = C.LENGTH_UNSET.toLong()
        openedDataSpec = null
        closeResponse()
        if (wasOpened) transferEnded()
    }

    private fun closeResponse() {
        // Cancel first: closing an unread OkHttp body may otherwise try to drain it.
        call?.cancel()
        call = null
        runCatching { input?.close() }
        input = null
        response?.close()
        response = null
    }

    private fun skipResponsePrefix(byteCount: Long) {
        var remaining = byteCount
        if (remaining == 0L) return
        val buffer = ByteArray(8 * 1024)
        while (remaining > 0L) {
            if (closed || Thread.currentThread().isInterrupted) throw IOException("Media source closed")
            val count = input?.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                ?: throw IOException("Media source closed")
            if (count < 0) throw EOFException("Media response ended before requested position")
            remaining -= count
        }
    }

    private fun usesRangeParameter(dataSpec: DataSpec): Boolean =
        useRangeParameter && dataSpec.uri.path.orEmpty().startsWith("/videoplayback")

    private fun youtubeRequestUrl(dataSpec: DataSpec): String {
        val raw = dataSpec.uri.toString()
        if (!dataSpec.uri.path.orEmpty().startsWith("/videoplayback")) return raw
        val builder = dataSpec.uri.buildUpon()
        val hasRange = useRangeParameter &&
            (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong())
        val replaced = buildSet {
            if (useRequestNumber) add("rn")
            if (useRangeParameter) add("range")
        }
        builder.encodedQuery(dataSpec.uri.encodedQuery?.split('&')?.filterNot {
            Uri.decode(it.substringBefore('=')) in replaced
        }?.joinToString("&")?.takeIf { it.isNotEmpty() })
        if (useRequestNumber) {
            builder.appendQueryParameter("rn", REQUEST_NUMBER.getAndIncrement().toString())
        }
        if (
            hasRange
        ) {
            val rangeEnd = dataSpec.length
                .takeIf { it != C.LENGTH_UNSET.toLong() }
                ?.let { dataSpec.position + minOf(it - 1L, Long.MAX_VALUE - dataSpec.position) }
            builder.appendQueryParameter(
                "range",
                buildString {
                    append(dataSpec.position)
                    append('-')
                    rangeEnd?.let(::append)
                },
            )
        }
        return builder.build().toString()
    }

    private fun MutableMap<String, String>.putYoutubeHeaders(url: String) {
        if (isWebStreamingUrl(url) || isWebEmbeddedPlayerStreamingUrl(url)) {
            put("Origin", YOUTUBE_BASE_URL)
            put("Referer", YOUTUBE_BASE_URL)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
        }
        put("TE", "trailers")
        put(
            "User-Agent",
            when {
                isAndroidStreamingUrl(url) -> getAndroidUserAgent(null)
                isIosStreamingUrl(url) -> getIosUserAgent(null)
                isVisionOsStreamingUrl(url) -> getVisionOsUserAgent(null)
                else -> NEWPIPE_USER_AGENT
            },
        )
    }

    class Factory(
        private val useRangeParameter: Boolean,
        private val useRequestNumber: Boolean,
    ) : HttpDataSource.Factory {
        private val requestProperties = linkedMapOf<String, String>()
        private var transferListener: TransferListener? = null
        private val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        override fun setDefaultRequestProperties(
            defaultRequestProperties: Map<String, String>,
        ): Factory = apply {
            synchronized(requestProperties) {
                requestProperties.clear()
                requestProperties.putAll(defaultRequestProperties)
            }
        }

        fun setTransferListener(listener: TransferListener?): Factory = apply {
            transferListener = listener
        }

        override fun createDataSource(): NewPipeYoutubeHttpDataSource =
            NewPipeYoutubeHttpDataSource(
                client = client,
                defaultRequestProperties = synchronized(requestProperties) {
                    requestProperties.toMap()
                },
                useRangeParameter = useRangeParameter,
                useRequestNumber = useRequestNumber,
            ).also { dataSource -> transferListener?.let(dataSource::addTransferListener) }
    }

    companion object {
        private val POST_BODY = byteArrayOf(0x78, 0)
        private val REQUEST_NUMBER = AtomicLong(0L)
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val MAX_READ_RECONNECTS = 2
    }
}
