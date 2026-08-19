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

    override fun open(dataSpec: DataSpec): Long {
        openedDataSpec = dataSpec
        transferInitializing(dataSpec)
        val requestUrl = youtubeRequestUrl(dataSpec)
        val headers = linkedMapOf<String, String>().apply {
            putAll(defaultRequestProperties)
            synchronized(requestProperties) { putAll(requestProperties) }
            putAll(dataSpec.httpRequestHeaders)
            putYoutubeHeaders(requestUrl)
            if (!useRangeParameter) {
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
            val nextResponse = nextCall.execute()
            response = nextResponse
            if (!nextResponse.isSuccessful) {
                val body = nextResponse.body?.bytes() ?: ByteArray(0)
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
            bytesRemaining = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() }
                ?: body.contentLength().takeIf { it >= 0L }
                ?: C.LENGTH_UNSET.toLong()
            opened = true
            transferStarted(dataSpec)
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
        val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        return try {
            val read = requireNotNull(input).read(buffer, offset, requested)
            if (read < 0) {
                C.RESULT_END_OF_INPUT
            } else {
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                bytesTransferred(read)
                read
            }
        } catch (error: IOException) {
            throw HttpDataSourceException.createForIOException(
                error,
                requireNotNull(openedDataSpec),
                HttpDataSourceException.TYPE_READ,
            )
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
        val wasOpened = opened
        opened = false
        bytesRemaining = C.LENGTH_UNSET.toLong()
        openedDataSpec = null
        closeResponse()
        if (wasOpened) transferEnded()
    }

    private fun closeResponse() {
        runCatching { input?.close() }
        input = null
        response?.close()
        response = null
        call?.cancel()
        call = null
    }

    private fun youtubeRequestUrl(dataSpec: DataSpec): String {
        val raw = dataSpec.uri.toString()
        if (!dataSpec.uri.path.orEmpty().startsWith("/videoplayback")) return raw
        val builder = dataSpec.uri.buildUpon()
        if (useRequestNumber && !raw.contains("&rn=") && !raw.contains("?rn=")) {
            builder.appendQueryParameter("rn", REQUEST_NUMBER.getAndIncrement().toString())
        }
        if (
            useRangeParameter &&
            (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong())
        ) {
            val rangeEnd = dataSpec.length
                .takeIf { it != C.LENGTH_UNSET.toLong() }
                ?.let { dataSpec.position + it - 1L }
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
    }
}
