package com.futo.polycentric.core

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import java.util.concurrent.TimeUnit

class ApiMethodTests {
    @Test
    fun testCancelThenSucceed() = runTest {
        val mockWebServer = MockWebServer()

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Log.i("ApiMethodTests", "Dispatching response in 1 second")
                return MockResponse().setResponseCode(200)
                    .setBody("Delayed response")
                    .setBodyDelay(1, TimeUnit.SECONDS)
            }
        }

        mockWebServer.start()

        //Cancel
        run {
            var responseArrived = false
            var exceptionThrown = false
            val job = launch {
                try {
                    Log.i("ApiMethodTests", "Making call")

                    val client = OkHttpClient()
                    ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder(mockWebServer.url("/test").toString()).get().build())) {
                        responseArrived = true
                        ""
                    }

                    Log.i("ApiMethodTests", "Call finished")
                } catch (e: Exception) {
                    Log.i("ApiMethodTests", "Exception was thrown $e")
                    exceptionThrown = true
                }
            }

            Log.i("ApiMethodTests", "Job launched")

            delay(500)
            Log.i("ApiMethodTests", "Cancelling job")
            job.cancel()
            Log.i("ApiMethodTests", "Cancelled job")

            Log.i("ApiMethodTests", "Joining job")
            job.join()
            Log.i("ApiMethodTests", "Joined job")

            assertTrue(exceptionThrown)
            assertFalse(responseArrived)
        }

        //Succeed
        run {
            var responseArrived = false
            val job = launch {
                Log.i("ApiMethodTests", "Making call")

                val client = OkHttpClient()
                ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder(mockWebServer.url("/test").toString()).get().build())) {
                    responseArrived = true
                    ""
                }

                Log.i("ApiMethodTests", "Call finished")
            }

            Log.i("ApiMethodTests", "Joining job")
            job.join()
            Log.i("ApiMethodTests", "Joined job")

            assertTrue(responseArrived)
        }

        mockWebServer.shutdown()
    }

    @Test
    fun testThrowThenSucceed() = runTest {
        val mockWebServer = MockWebServer()

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Log.i("ApiMethodTests", "Dispatching response")
                return MockResponse().setResponseCode(200)
                    .setBody("Immediate response")
            }
        }

        mockWebServer.start()

        //Throw network unreachable
        run {
            val job = launch {
                Log.i("ApiMethodTests", "Making call")

                runCatching {
                    val client = OkHttpClient().newBuilder().connectTimeout(10, TimeUnit.MILLISECONDS).build()
                    ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder("http://131.131.131.131/test/a").get().build())) {
                        throw Exception("Exception that must be thrown")
                    }
                }
            }

            Log.i("ApiMethodTests", "Joining job")
            try {
                job.join()
                assertFalse("Should not happen, should except", true)
            } catch (e: Throwable) {
                //Expected case
            }
            Log.i("ApiMethodTests", "Joined job")
        }

        //Throw exception
        run {
            val job = launch {
                Log.i("ApiMethodTests", "Making call")

                runCatching {
                    val client = OkHttpClient()
                    ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder(mockWebServer.url("/test").toString()).get().build())) {
                        throw Exception("Exception that must be thrown")
                    }
                }
            }

            Log.i("ApiMethodTests", "Joining job")
            job.join()
            Log.i("ApiMethodTests", "Joined job")
        }

        //Succeed
        run {
            var responseArrived = false
            val job = launch {
                Log.i("ApiMethodTests", "Making call")

                val client = OkHttpClient()
                ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder(mockWebServer.url("/test").toString()).get().build())) {
                    responseArrived = true
                    assertEquals(200, it.code)
                    assertEquals("Immediate response", it.body?.string())
                    ""
                }

                Log.i("ApiMethodTests", "Call finished")
            }

            Log.i("ApiMethodTests", "Joining job")
            job.join()
            Log.i("ApiMethodTests", "Joined job")

            assertTrue(responseArrived)
        }

        mockWebServer.shutdown()
    }

    @Test
    fun testCancellationWorks() = runTest {
        val mockWebServer = MockWebServer()

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Log.i("ApiMethodTests", "Dispatching response in 1 second")
                return MockResponse().setResponseCode(200)
                    .setBody("Delayed response")
                    .setBodyDelay(1, TimeUnit.SECONDS)
            }
        }

        mockWebServer.start()

        var responseArrived = false
        var exceptionThrown = false
        val job = launch {
            try {
                Log.i("ApiMethodTests", "Making call")

                val client = OkHttpClient()
                ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder(mockWebServer.url("/test").toString()).get().build())) {
                    responseArrived = true
                    ""
                }

                Log.i("ApiMethodTests", "Call finished")
            } catch (e: Exception) {
                Log.i("ApiMethodTests", "Exception was thrown $e")
                exceptionThrown = true
            }
        }

        Log.i("ApiMethodTests", "Job launched")

        delay(500)
        Log.i("ApiMethodTests", "Cancelling job")
        job.cancel()
        Log.i("ApiMethodTests", "Cancelled job")

        Log.i("ApiMethodTests", "Joining job")
        job.join()
        Log.i("ApiMethodTests", "Joined job")

        assertEquals(0, mockWebServer.requestCount)
        assertTrue(exceptionThrown)
        assertFalse(responseArrived)

        mockWebServer.shutdown()
    }

    @Test
    fun testMockServerWorks() = runTest {
        val mockWebServer = MockWebServer()

        var requestArrived = false
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestArrived = true
                return MockResponse().setResponseCode(200)
                    .setBody("Immediate response")
            }
        }

        mockWebServer.start()

        var responseArrived = false
        val client = OkHttpClient()
        ApiMethods.executeCall(client.newCall(ApiMethods.getRequestBuilder(mockWebServer.url("/test").toString()).get().build())) {
            responseArrived = true
            assertEquals(200, it.code)
            assertEquals("Immediate response", it.body?.string())
        }

        assertTrue(requestArrived)
        assertTrue(responseArrived)

        mockWebServer.shutdown()
    }


    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    @Test
    fun encode() {
        val data = "0801122018505d585836b6eea3772aeda12492ae6b75497ab9a10c6e069294523968ef3b".decodeHex()
        val d = data.toBase64Url()
        assertEquals("CAESIBhQXVhYNrbuo3cq7aEkkq5rdUl6uaEMbgaSlFI5aO87", d)
    }
}