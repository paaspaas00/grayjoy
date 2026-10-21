package com.futo.platformplayer.backend

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Limit the decoded response too: Content-Length may be missing or describe compressed bytes. */
internal fun ResponseBody.readBoundedText(maxBytes: Long): String {
    require(maxBytes in 1 until Long.MAX_VALUE)
    if (contentLength() > maxBytes) throw IOException("Source response exceeds the size limit.")
    val input = source()
    if (input.request(maxBytes + 1)) throw IOException("Source response exceeds the size limit.")
    return input.readString(contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}

/** Cancellation closes both a pending connection and a response still being read. */
internal suspend fun Call.awaitBoundedText(maxBytes: Long): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val text = response.use {
                    if (!it.isSuccessful) throw IOException("Source request failed (HTTP ${it.code}).")
                    it.body.readBoundedText(maxBytes)
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}
