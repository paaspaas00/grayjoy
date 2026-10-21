package com.futo.platformplayer.backend

import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackendHttpTextTest {
    @Test fun rejectsOversizedBodiesEvenWithoutADeclaredLength() {
        val body = object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = -1L
            override fun source() = Buffer().writeUtf8("123456789")
        }
        body.use { assertTrue(runCatching { it.readBoundedText(8) }.exceptionOrNull() is IOException) }
        "12345678".toResponseBody().use { assertEquals("12345678", it.readBoundedText(8)) }
    }

    @Test fun cancellationCancelsTheHttpCallBeforeItFinishes() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("done".toResponseBody()).build()
        }.build()
        val call = client.newCall(Request.Builder().url("https://www.youtube.com/test").build())
        val task = launch { call.awaitBoundedText(1024) }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
            task.cancelAndJoin()
            assertTrue(call.isCanceled())
        } finally {
            release.countDown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
