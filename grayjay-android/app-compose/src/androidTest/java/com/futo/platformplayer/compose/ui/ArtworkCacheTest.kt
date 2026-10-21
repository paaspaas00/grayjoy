package com.futo.platformplayer.compose.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.images.ArtworkCache
import com.futo.platformplayer.compose.images.ArtworkKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ArtworkCacheTest {
    @Test fun rapidScrollBoundsPendingWorkAndRevivesAnEvictedFlow() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "cache-stress-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = root
        }
        val bytes = ByteArrayOutputStream().use { out ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            check(release.await(15, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Result")
                .body(bytes.toResponseBody("image/png".toMediaType())).build()
        }.build()
        val cache = ArtworkCache(context, networkEvents = false, httpClient = client)
        try {
            val oldFlow = cache.observe("https://images.example/old")
            val visible = async(start = CoroutineStart.UNDISPATCHED) {
                cache.observe("https://images.example/visible").filterNotNull().first()
            }
            repeat(5_000) { index ->
                cache.observe("https://images.example/scroll-$index")
                assertTrue("Entries grew while network requests stalled", cache.retainedEntryCount <= 256)
                assertTrue("Pending requests grew while network requests stalled", cache.retainedRequestCount <= 10)
            }
            val returned = async(start = CoroutineStart.UNDISPATCHED) { oldFlow.filterNotNull().first() }
            val newest = async(start = CoroutineStart.UNDISPATCHED) {
                cache.observe("https://images.example/scroll-4999").filterNotNull().first()
            }
            release.countDown()
            withTimeout(10_000) {
                for (image in listOf(visible.await(), returned.await(), newest.await())) assertTrue(image.file?.isFile == true)
            }
            assertTrue(cache.retainedEntryCount <= 256)
        } finally {
            release.countDown()
            cache.invalidate()
            cache.close()
            root.deleteRecursively()
        }
    }

    @Test fun invalidationInterruptsAStalledResponseBodyWithoutLeavingPartialFiles() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "cache-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = root
        }
        val server = ServerSocket(0).apply { soTimeout = 5000 }
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val cache = ArtworkCache(context, networkEvents = false)
        executor.submit {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nContent-Type: image/png\r\n\r\nx".toByteArray())
                    flush()
                }
                release.await(10, TimeUnit.SECONDS)
            }
        }
        try {
            cache.observe("http://127.0.0.1:${server.localPort}/stalled.png")
            withTimeout(5000) {
                while (File(root, "artwork-v1").listFiles().orEmpty().none { it.extension == "part" }) delay(10)
            }
            val started = android.os.SystemClock.elapsedRealtime()
            withTimeout(2000) { cache.invalidate() }
            assertTrue("Invalidation waited for the network read timeout", android.os.SystemClock.elapsedRealtime() - started < 2000)
            assertTrue(File(root, "artwork-v1").listFiles().orEmpty().isEmpty())
        } finally {
            release.countDown(); server.close(); executor.shutdownNow(); cache.close()
            root.deleteRecursively()
        }
    }

    @Test fun freshCacheRevalidationBackoffAndSafeInvalidation() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "cache-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = root
        }
        val requests = AtomicInteger()
        val clock = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val status = AtomicInteger(200)
        val headers = java.util.concurrent.CopyOnWriteArrayList<String>()
        val bytes = ByteArrayOutputStream().use { out -> Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            .compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                request.headers.forEach { (name, value) -> headers += "$name: $value" }
                requests.incrementAndGet()
                val code = status.get()
                val body = if (code == 200) bytes else byteArrayOf()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Result")
                    .header("ETag", "sample").body(body.toResponseBody("image/png".toMediaType())).build()
        }.build()
        val cache = ArtworkCache(context, networkEvents = false, httpClient = client, clock = clock::get)
        val url = "https://images.example/image"
        suspend fun await(condition: () -> Boolean) { withTimeout(5000) { while (!condition()) delay(20) } }
        try {
            val image = withTimeout(5000) { cache.observe(url).filterNotNull().first() }
            assertNotNull(image.file)
            await { File(root, "artwork-v1").listFiles().orEmpty().any { it.extension == "json" } }
            val metadata = File(root, "artwork-v1").listFiles()!!.first { it.extension == "json" }
            await { metadata.isFile }
            delay(100)
            cache.observe(url)
            delay(150)
            assertEquals(1, requests.get())
            fun expire() { clock.addAndGet(ArtworkKind.Thumbnail.maxAgeMs + 1) }
            expire(); status.set(304)
            cache.observe(url)
            await { requests.get() == 2 }
            await { JSONObject(metadata.readText()).optLong("validatedAt") == clock.get() }
            assertTrue(headers.any { it.equals("If-None-Match: sample", true) })
            assertEquals(image.revision, cache.observe(url).value?.revision)
            delay(100); expire(); status.set(200)
            cache.observe(url)
            await { requests.get() == 3 }
            await { JSONObject(metadata.readText()).optLong("validatedAt") == clock.get() }
            assertEquals("Identical image bytes must not trigger another decoded-image load", image.revision, cache.observe(url).value?.revision)
            delay(100); expire(); status.set(503)
            cache.observe(url)
            await { requests.get() == 4 }
            await { JSONObject(metadata.readText()).optLong("retryAt") > System.currentTimeMillis() }
            assertTrue(cache.observe(url).value?.file?.exists() == true)
            delay(100)
            assertEquals(4, requests.get())
            val unrelated = File(root, "protected-library-fixture").apply { writeText("keep") }
            cache.invalidate()
            assertEquals("keep", unrelated.readText())
            status.set(200)
            withTimeout(5000) { cache.observe(url).filterNotNull().first() }
            assertEquals(5, requests.get())
            val shared = "$url/shared"
            withTimeout(5000) { cache.observe(shared, kind = ArtworkKind.Avatar).filterNotNull().first() }
            assertEquals(6, requests.get())
            clock.addAndGet(ArtworkKind.Thumbnail.maxAgeMs * 2)
            status.set(304)
            withTimeout(5000) {
                while (requests.get() < 7) { cache.observe(shared, kind = ArtworkKind.Thumbnail); delay(20) }
            }
            assertEquals(7, requests.get())
        } finally { cache.close(); root.deleteRecursively() }
    }
}
