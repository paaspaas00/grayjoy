package com.futo.platformplayer.backend

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Optional bounded history enrichment; never resolves streams or starts a plugin runtime. */
data class MusicMetadataResult(val isMusic: Boolean?, val retryable: Boolean = false)

class YouTubeMusicMetadataResolver : AutoCloseable {
    private data class Entry(val result: MusicMetadataResult, val expiresAt: Long)
    private val cache = linkedMapOf<String, Entry>()
    private val locks = Array(16) { Mutex() }
    private val slots = Semaphore(2)
    private val executor = java.util.concurrent.ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS,
        java.util.concurrent.LinkedBlockingQueue<Runnable>(4)) { runnable ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); runnable.run() },
            "history-metadata").apply { isDaemon = true }
    }
    private val client = OkHttpClient.Builder().dispatcher(okhttp3.Dispatcher(executor)).connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun classify(contentUrl: String): Boolean? = classifyWithStatus(contentUrl).isMusic

    suspend fun classifyWithStatus(contentUrl: String): MusicMetadataResult {
        val id = youtubeMusicVideoId(contentUrl) ?: return MusicMetadataResult(null)
        return locks[(id.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            synchronized(cache) { cache[id] }?.takeIf { it.expiresAt > System.currentTimeMillis() }
                ?.let { return@withLock it.result }
            val value = slots.withPermit { fetch(id) }
            synchronized(cache) {
                val ttl = if (value.retryable) TimeUnit.MINUTES.toMillis(10) else TimeUnit.HOURS.toMillis(24)
                cache[id] = Entry(value, System.currentTimeMillis() + ttl)
                while (cache.size > 256) cache.remove(cache.keys.first())
            }
            value
        }
    }

    private suspend fun fetch(id: String): MusicMetadataResult = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url("https://www.youtube.com/watch?v=$id&hl=en")
            .header("User-Agent", NEWPIPE_USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+cb").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(MusicMetadataResult(null, retryable = true))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        if (!response.isSuccessful) return@use MusicMetadataResult(null,
                            retryable = response.code != 404 && response.code != 410)
                        val value = response.body.charStream().use readerBlock@ { reader ->
                            val html = StringBuilder()
                            val buffer = CharArray(8192)
                            var headChecked = false
                            while (html.length < 2 * 1024 * 1024 && continuation.isActive) {
                                val count = reader.read(buffer, 0, minOf(buffer.size, 2 * 1024 * 1024 - html.length))
                                if (count < 0) break
                                html.append(buffer, 0, count)
                                if (!headChecked && html.indexOf("</head>") >= 0) {
                                    headChecked = true
                                    youtubeMusicClassificationFromWatchPage(html.toString())?.let { return@readerBlock it }
                                }
                            }
                            if (continuation.isActive) youtubeMusicClassificationFromWatchPage(html.toString()) else null
                        }
                        MusicMetadataResult(value)
                    }
                } catch (_: Exception) {
                    MusicMetadataResult(null, retryable = true)
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
    override fun close() { client.dispatcher.cancelAll(); executor.shutdown(); client.connectionPool.evictAll() }
}

internal fun youtubeMusicVideoId(value: String): String? = runCatching {
    val input = value.trim()
    if (input.matches(Regex("[A-Za-z0-9_-]{11}"))) return input
    val uri = URI(input)
    if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return null
    val host = uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
    val id = when {
        host == "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
        host == "youtube.com" || host.endsWith(".youtube.com") -> {
            uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "v" }
                ?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
                ?: uri.path.orEmpty().split('/').filter(String::isNotBlank).let {
                    it.getOrNull(1).takeIf { _ -> it.firstOrNull() in listOf("shorts", "embed", "live") }
                }
        }
        else -> null
    }
    id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}.getOrNull()
