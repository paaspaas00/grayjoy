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

class ArtworkCacheTest {
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
            delay(100); expire(); status.set(503)
            cache.observe(url)
            await { requests.get() == 3 }
            await { JSONObject(metadata.readText()).optLong("retryAt") > System.currentTimeMillis() }
            assertTrue(cache.observe(url).value?.file?.exists() == true)
            delay(100)
            assertEquals(3, requests.get())
            val unrelated = File(root, "protected-library-fixture").apply { writeText("keep") }
            cache.invalidate()
            assertEquals("keep", unrelated.readText())
            status.set(200)
            withTimeout(5000) { cache.observe(url).filterNotNull().first() }
            assertEquals(4, requests.get())
            val shared = "$url/shared"
            withTimeout(5000) { cache.observe(shared, kind = ArtworkKind.Avatar).filterNotNull().first() }
            assertEquals(5, requests.get())
            clock.addAndGet(ArtworkKind.Thumbnail.maxAgeMs * 2)
            status.set(304)
            withTimeout(5000) {
                while (requests.get() < 6) { cache.observe(shared, kind = ArtworkKind.Thumbnail); delay(20) }
            }
            assertEquals(6, requests.get())
        } finally { cache.close(); root.deleteRecursively() }
    }
}
