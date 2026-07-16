package com.futo.platformplayer.views.video.datasources

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpUtil
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.math.min

/**
 * Media3 data source that preserves Grayjay plugin request executors. Modern YouTube UMP streams
 * expose a DASH manifest containing virtual grayjay.internal segment URLs; those URLs must be
 * resolved by the plugin's JavaScript executor and must never be sent to DNS.
 */
class JSHttpDataSource private constructor(
    private val defaultRequestProperties: Map<String, String>,
    private val requestModifier: IRequestModifier?,
    private val requestExecutor: JSRequestExecutor?,
    private val requestExecutor2: JSRequestExecutor?,
) : BaseDataSource(true), HttpDataSource {
    private val requestProperties = HttpDataSource.RequestProperties()
    private var delegate: HttpDataSource? = null
    private var input: ByteArrayInputStream? = null
    private var opened = false
    private var currentUri: Uri? = null
    private var bytesRemaining = 0L
    private var responseCode = -1

    override fun open(dataSpec: DataSpec): Long {
        val headers = buildMap<String, String> {
            putAll(defaultRequestProperties)
            putAll(requestProperties.snapshot)
            putAll(dataSpec.httpRequestHeaders)
            HttpUtil.buildRangeRequestHeader(dataSpec.position, dataSpec.length)?.let { range ->
                put("Range", range)
            }
        }
        val modified = requestModifier?.modifyRequest(dataSpec.uri.toString(), headers)
        val effectiveUri = Uri.parse(modified?.url ?: dataSpec.uri.toString())
        val effectiveHeaders = modified?.headers ?: headers
        val executor = when {
            requestExecutor2?.urlPrefix?.let(effectiveUri.toString()::startsWith) == true -> requestExecutor2
            requestExecutor?.urlPrefix?.let(effectiveUri.toString()::startsWith) != false -> requestExecutor
            else -> null
        }

        if (executor == null) {
            val fallback = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(effectiveHeaders)
                .createDataSource()
            delegate = fallback
            // Some Grayjay plugins move Media3's Range header into a URL query parameter and
            // explicitly disable local byte skipping. The modified URL already describes the
            // requested range, so forwarding the original position would request/skip it twice.
            val effectiveDataSpec = if (requestModifier?.allowByteSkip == false) {
                dataSpec.buildUpon()
                    .setUri(effectiveUri)
                    .setPosition(0L)
                    .setLength(C.LENGTH_UNSET.toLong())
                    .setHttpRequestHeaders(effectiveHeaders)
                    .build()
            } else {
                dataSpec.buildUpon()
                    .setUri(effectiveUri)
                    .setHttpRequestHeaders(effectiveHeaders)
                    .build()
            }
            return fallback.open(effectiveDataSpec)
        }

        transferInitializing(dataSpec)
        val method = when (dataSpec.httpMethod) {
            DataSpec.HTTP_METHOD_POST -> "POST"
            DataSpec.HTTP_METHOD_HEAD -> "HEAD"
            else -> "GET"
        }
        val result = try {
            executor.executeRequest(method, effectiveUri.toString(), dataSpec.httpBody, effectiveHeaders)
        } catch (error: Throwable) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                IOException("Plugin media request failed: ${error.localizedMessage}", error),
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        }
        // Executors return the body of the already-modified request. Byte zero in this array is
        // the first byte of the requested range, not byte zero of the complete remote resource.
        val available = result.size
        val requested = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            available
        } else {
            min(available.toLong(), dataSpec.length).toInt()
        }
        input = ByteArrayInputStream(result, 0, requested)
        bytesRemaining = requested.toLong()
        currentUri = effectiveUri
        responseCode = if (dataSpec.position > 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            206
        } else {
            200
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        delegate?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val read = input?.read(buffer, offset, min(length.toLong(), bytesRemaining).toInt())
            ?: C.RESULT_END_OF_INPUT
        if (read < 0) {
            bytesRemaining = 0L
            return C.RESULT_END_OF_INPUT
        }
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun close() {
        delegate?.close()
        delegate = null
        input?.close()
        input = null
        currentUri = null
        responseCode = -1
        bytesRemaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    override fun getUri(): Uri? = delegate?.uri ?: currentUri

    override fun getResponseCode(): Int = delegate?.responseCode ?: responseCode

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun setRequestProperty(name: String, value: String) {
        requestProperties.set(name, value)
        delegate?.setRequestProperty(name, value)
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
        delegate?.clearRequestProperty(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
        delegate?.clearAllRequestProperties()
    }

    class Factory : HttpDataSource.Factory {
        private val defaultRequestProperties = HttpDataSource.RequestProperties()
        private var requestModifier: IRequestModifier? = null
        private var requestExecutor: JSRequestExecutor? = null
        private var requestExecutor2: JSRequestExecutor? = null

        fun setRequestModifier(value: IRequestModifier?): Factory = apply {
            requestModifier = value
        }

        fun setRequestExecutor(value: JSRequestExecutor?): Factory = apply {
            requestExecutor = value
        }

        fun setRequestExecutor2(value: JSRequestExecutor?): Factory = apply {
            requestExecutor2 = value
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory = apply {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties)
        }

        override fun createDataSource(): JSHttpDataSource = JSHttpDataSource(
            defaultRequestProperties = defaultRequestProperties.snapshot,
            requestModifier = requestModifier,
            requestExecutor = requestExecutor,
            requestExecutor2 = requestExecutor2,
        )

        fun closeExecutors() {
            listOfNotNull(requestExecutor, requestExecutor2).distinct().forEach { executor ->
                runCatching { executor.close() }
            }
        }
    }
}
