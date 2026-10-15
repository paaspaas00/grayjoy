package com.futo.platformplayer.compose.images

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resumeWithException

enum class ArtworkKind(val maxAgeMs: Long) {
    Thumbnail(TimeUnit.HOURS.toMillis(24)),
    Avatar(TimeUnit.DAYS.toMillis(7)),
}

data class CachedArtwork(val file: File?, val revision: Long)

/** Encoded-image LRU. Existing images stay visible while expired entries are revalidated. */
class ArtworkCache internal constructor(context: Context, networkEvents: Boolean = true, httpClient: OkHttpClient? = null, private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "artwork-v1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)
    private val epoch = AtomicLong(0)
    private val lastTrimAt = AtomicLong(Long.MIN_VALUE)
    private val maintenanceLock = Any()
    private val startLock = Any()
    private val invalidationMutex = Mutex()
    @Volatile private var invalidating = false
    private val entries = ConcurrentHashMap<String, Entry>()
    private val client = httpClient ?: OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val origin = request.tag(String::class.java)
            chain.proceed(if (origin != null && origin != request.url.host) request.newBuilder()
                .removeHeader("Cookie").removeHeader("Authorization").build() else request)
        }.build()
    val generation = MutableStateFlow(0L)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private class Entry(val key: String, val url: String, val headers: Map<String, String>, val kind: ArtworkKind) {
        val state = MutableStateFlow<CachedArtwork?>(null)
        var job: Job? = null
        @Volatile var call: Call? = null
        var failed = false
        var lastUsed = System.currentTimeMillis()
        @Volatile var loaded = false
        @Volatile var validatedAt = 0L
        @Volatile var retryAt = 0L
        @Volatile var maxAgeMs = kind.maxAgeMs
    }

    init {
        if (networkEvents) runCatching {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { retryFailed() }
                }.also { app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(it) }
        }
    }

    fun observe(url: String, headers: Map<String, String> = emptyMap(), kind: ArtworkKind = ArtworkKind.Thumbnail): StateFlow<CachedArtwork?> {
        val canonicalHeaders = headers.entries.sortedBy { it.key.lowercase(java.util.Locale.ROOT) }.joinToString("\n") { "${it.key.lowercase(java.util.Locale.ROOT)}:${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256").digest((url + "\n" + canonicalHeaders).toByteArray())
        val key = buildString(64) { digest.forEach { byte -> val value = byte.toInt() and 255; append("0123456789abcdef"[value shr 4]); append("0123456789abcdef"[value and 15]) } }
        val entry = entries.getOrPut(key) { Entry(key, url, headers.toMap(), kind) }
        synchronized(entry) { entry.maxAgeMs = minOf(entry.maxAgeMs, kind.maxAgeMs) }
        entry.lastUsed = clock()
        start(entry)
        return entry.state
    }

    fun retryFailed() {
        entries.values.filter { it.failed && it.state.subscriptionCount.value > 0 }.forEach { start(it, force = true) }
    }

    fun refreshActive() {
        entries.values.filter { it.state.subscriptionCount.value > 0 }.forEach { start(it, verifyDisk = true) }
    }

    private fun start(entry: Entry, force: Boolean = false, verifyDisk: Boolean = false) {
        synchronized(startLock) {
            if (invalidating) return
            synchronized(entry) {
                if (entry.job?.isActive == true) return
                val now = clock()
                if (!force && !verifyDisk && entry.loaded && (now < entry.retryAt ||
                        (entry.state.value?.file != null && now - entry.validatedAt in 0 until entry.maxAgeMs))) return
                val requestEpoch = epoch.get()
                entry.job = scope.launch(start = CoroutineStart.LAZY) {
                    try { slots.withPermit { refresh(entry, requestEpoch, force) } }
                    finally { synchronized(entry) { if (entry.job === coroutineContext[Job]) entry.job = null } }
                }.also(Job::start)
            }
        }
    }

    private suspend fun refresh(entry: Entry, requestEpoch: Long, force: Boolean) {
        var temporary: File? = null
        try {
            directory.mkdirs()
            val file = File(directory, "${entry.key}.img")
            val metadata = File(directory, "${entry.key}.json")
            var meta = runCatching { JSONObject(metadata.readText()) }.getOrDefault(JSONObject())
            val now = clock()
            entry.validatedAt = meta.optLong("validatedAt")
            entry.retryAt = meta.optLong("retryAt")
            entry.failed = entry.retryAt > 0L
            entry.loaded = true
            if (file.isFile && file.length() > 0) {
                entry.state.value = CachedArtwork(file, meta.optLong("revision", file.lastModified()))
                metadata.setLastModified(now)
            } else entry.state.value = if (entry.failed || meta.optLong("retryAt") > 0L) CachedArtwork(null, 0L) else null
            val age = now - meta.optLong("validatedAt")
            if (!force && ((file.isFile && age in 0 until entry.maxAgeMs) || now < meta.optLong("retryAt"))) return
            val request = Request.Builder().url(entry.url).apply {
                tag(String::class.java, entry.url.toHttpUrl().host)
                entry.headers.forEach { (name, value) -> header(name, value) }
                if (file.isFile) {
                    meta.optString("etag").takeIf(String::isNotBlank)?.let { header("If-None-Match", it) }
                    meta.optString("modified").takeIf(String::isNotBlank)?.let { header("If-Modified-Since", it) }
                }
            }.build()
            client.newCall(request).also { entry.call = it }.awaitResponse().use { response ->
                currentCoroutineContext().ensureActive()
                check(epoch.get() == requestEpoch)
                if (response.code == 304 && file.isFile) {
                    meta.put("validatedAt", now).put("retryAt", 0L)
                } else {
                    check(response.isSuccessful)
                    // Stream encoded images to disk. Two simultaneous 8 MiB images used to
                    // need growing byte buffers, toByteArray copies and copies of the old files.
                    val pending = File.createTempFile(entry.key, ".part", directory).also { temporary = it }
                    val digest = MessageDigest.getInstance("SHA-256")
                    response.body.byteStream().use { input ->
                        pending.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                total += size
                                check(total <= 8L * 1024 * 1024)
                                digest.update(buffer, 0, size)
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(pending.absolutePath, bounds)
                    check(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384)
                    currentCoroutineContext().ensureActive()
                    check(epoch.get() == requestEpoch)
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    val unchanged = file.isFile && file.length() == pending.length() &&
                        (meta.optString("hash").takeIf(String::isNotBlank) ?: artworkDigest(file)) == hash
                    if (!unchanged) {
                        val atomic = AtomicFile(file)
                        val output = atomic.startWrite()
                        try {
                            pending.inputStream().use { it.copyTo(output, 8192) }
                            atomic.finishWrite(output)
                        } catch (error: Throwable) { atomic.failWrite(output); throw error }
                    }
                    val revision = if (unchanged) meta.optLong("revision", file.lastModified())
                        else maxOf(now, meta.optLong("revision") + 1L)
                    meta = JSONObject().put("validatedAt", now).put("revision", revision).put("retryAt", 0L)
                        .put("hash", hash)
                        .put("etag", response.header("ETag").orEmpty()).put("modified", response.header("Last-Modified").orEmpty())
                    entry.state.value = CachedArtwork(file, revision)
                }
                entry.failed = false
                entry.validatedAt = now
                entry.retryAt = 0L
                atomicWrite(metadata, meta.toString().toByteArray())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (epoch.get() == requestEpoch) {
                entry.failed = true
                entry.retryAt = clock() + TimeUnit.MINUTES.toMillis(15)
                if (entry.state.value?.file == null) entry.state.value = CachedArtwork(null, 0L)
                val metadata = File(directory, "${entry.key}.json")
                runCatching {
                    val saved = runCatching { JSONObject(metadata.readText()) }.getOrDefault(JSONObject())
                    saved.put("retryAt", entry.retryAt)
                    atomicWrite(metadata, saved.toString().toByteArray())
                }
            }
        } finally { entry.call = null; temporary?.delete(); trimIfDue() }
    }

    suspend fun invalidate() = withContext(Dispatchers.IO) {
        invalidationMutex.withLock {
            val pending = synchronized(startLock) {
                invalidating = true
                epoch.incrementAndGet()
                entries.values.map { it to it.job }
            }
            try {
                withContext(NonCancellable) {
                    // Cancel the coroutine first, then read its current call. Headers can arrive
                    // concurrently; cancelling the call also interrupts a blocking body read.
                    pending.forEach { (entry, job) -> job?.cancel(); entry.call?.cancel() }
                    pending.mapNotNull { it.second }.joinAll()
                    synchronized(maintenanceLock) { directory.listFiles()?.forEach { it.delete() } }
                    entries.clear()
                }
            } finally {
                invalidating = false
                generation.value = epoch.get()
            }
        }
    }

    private fun trimIfDue() {
        val now = clock()
        val previous = lastTrimAt.get()
        if (previous != Long.MIN_VALUE && now - previous in 0 until 60_000L) return
        if (!lastTrimAt.compareAndSet(previous, now)) return
        synchronized(maintenanceLock) { trim() }
    }

    private fun trim() {
        val images = directory.listFiles { file -> file.extension == "img" }.orEmpty()
        var bytes = images.sumOf(File::length)
        var count = images.size
        if (bytes > MAX_BYTES || count > 2048) images.sortedBy { File(directory, it.nameWithoutExtension + ".json").lastModified() }.forEach { file ->
            if (bytes <= MAX_BYTES && count <= 2048) return@forEach
            if (entries[file.nameWithoutExtension]?.state?.subscriptionCount?.value?.let { it > 0 } == true) return@forEach
            val length = file.length()
            if (file.delete()) {
                bytes -= length
                count--
                File(directory, file.nameWithoutExtension + ".json").delete()
                entries[file.nameWithoutExtension]?.let { it.loaded = false; it.state.value = null }
            }
        }
        if (entries.size > 256) entries.values.sortedBy { it.lastUsed }.forEach { entry ->
            if (entries.size > 256 && entry.state.subscriptionCount.value == 0 && entry.job?.isActive != true) entries.remove(entry.key, entry)
        }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) } catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    private fun artworkDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    override fun close() {
        epoch.incrementAndGet()
        entries.values.forEach { it.call?.cancel() }
        scope.cancel()
        networkCallback?.let { callback -> runCatching { app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) } }
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val MAX_BYTES = 128L * 1024 * 1024
        @Volatile private var instance: ArtworkCache? = null
        fun get(context: Context): ArtworkCache = instance ?: synchronized(this) {
            instance ?: ArtworkCache(context.applicationContext).also { instance = it }
        }
    }
}
