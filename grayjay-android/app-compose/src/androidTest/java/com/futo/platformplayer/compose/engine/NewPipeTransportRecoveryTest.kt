package com.futo.platformplayer.compose.engine

import android.net.Uri
import androidx.media3.datasource.DataSpec
import com.futo.platformplayer.backend.NewPipeYoutubeHttpDataSource
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.IOException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NewPipeTransportRecoveryTest {
    @Test fun rangeExactlyAtEndReturnsCleanEof() {
        withResponses(listOf("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */10\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")) { port, _ ->
            val source = NewPipeYoutubeHttpDataSource.Factory(false, false).createDataSource()
            try {
                assertEquals(0L, source.open(DataSpec.Builder().setUri("http://127.0.0.1:$port/media")
                    .setPosition(10).build()))
                assertEquals(-1, source.read(ByteArray(1), 0, 1))
            } finally { source.close() }
        }
    }

    @Test fun closingBlockedReadCancelsItWithoutReconnectingOrThrowingRuntimeException() {
        ServerSocket(0).use { server ->
            val executor = Executors.newFixedThreadPool(2)
            val finishServer = java.util.concurrent.CountDownLatch(1)
            val readStarted = java.util.concurrent.CountDownLatch(1)
            val source = NewPipeYoutubeHttpDataSource.Factory(true, true).createDataSource()
            try {
                executor.submit {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\n".toByteArray(),
                        )
                        socket.getOutputStream().flush()
                        finishServer.await(10, TimeUnit.SECONDS)
                    }
                }
                source.open(DataSpec.Builder().setUri("http://127.0.0.1:${server.localPort}/videoplayback").build())
                val result = executor.submit<Throwable?> {
                    readStarted.countDown()
                    try { source.read(ByteArray(10), 0, 10); null } catch (error: Throwable) { error }
                }
                assertTrue(readStarted.await(2, TimeUnit.SECONDS))
                source.close()
                assertTrue(result.get(2, TimeUnit.SECONDS) is IOException)
            } finally {
                source.close()
                finishServer.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test fun nonQueryEndpointStillUsesRangeHeaderDuringRecovery() {
        withResponses(listOf(
            "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 2-7/10\r\nContent-Length: 6\r\nConnection: close\r\n\r\ncde",
            "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 5-7/10\r\nContent-Length: 3\r\nConnection: close\r\n\r\nfgh",
        )) { port, requests ->
            val source = NewPipeYoutubeHttpDataSource.Factory(true, true).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri("http://127.0.0.1:$port/media")
                    .setPosition(2).setLength(6).build())
                assertEquals("cdefgh", source.readFully())
                val recorded = requests.get(10, TimeUnit.SECONDS)
                assertTrue(recorded[0].any { it.equals("Range: bytes=2-7", true) })
                assertTrue(recorded[1].any { it.equals("Range: bytes=5-7", true) })
            } finally { source.close() }
        }
    }

    @Test fun ignoredRangeHeaderSkipsResourcePrefixWithoutDuplicatingMediaBytes() {
        withResponses(listOf("HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\nabcdefghij")) { port, _ ->
            val source = NewPipeYoutubeHttpDataSource.Factory(false, false).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri("http://127.0.0.1:$port/media")
                    .setPosition(3).setLength(4).build())
                assertEquals("defg", source.readFully())
            } finally { source.close() }
        }
    }

    @Test fun wrongResponseRangeFailsInsteadOfFeedingCorruptedBytes() {
        withResponses(listOf("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-3/10\r\nContent-Length: 4\r\nConnection: close\r\n\r\nabcd")) { port, _ ->
            val source = NewPipeYoutubeHttpDataSource.Factory(false, false).createDataSource()
            try {
                try {
                    source.open(DataSpec.Builder().setUri("http://127.0.0.1:$port/media")
                        .setPosition(3).setLength(4).build())
                    fail("A mismatched response range must fail")
                } catch (_: IOException) { }
            } finally { source.close() }
        }
    }

    @Test fun staleQueryRangeIsRemovedWhenOpeningTheWholeResource() {
        withResponses(listOf("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc")) { port, requests ->
            val source = NewPipeYoutubeHttpDataSource.Factory(true, true).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri("http://127.0.0.1:$port/videoplayback?range=99-999&rn=old").build())
                assertEquals("abc", source.readFully())
                val uri = Uri.parse(requests.get(10, TimeUnit.SECONDS).single().first().split(' ')[1])
                assertNull(uri.getQueryParameter("range"))
                assertNotEquals("old", uri.getQueryParameter("rn"))
            } finally { source.close() }
        }
    }

    @Test fun persistentTruncationHasABoundedRetryBudget() {
        val broken = "HTTP/1.1 200 OK\r\nContent-Length: 10\r\nConnection: close\r\n\r\n"
        withResponses(List(3) { broken }) { port, requests ->
            val source = NewPipeYoutubeHttpDataSource.Factory(true, true).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri("http://127.0.0.1:$port/videoplayback").setLength(10).build())
                try {
                    source.readFully()
                    fail("Repeatedly truncated responses must fail")
                } catch (_: IOException) { }
                assertEquals(3, requests.get(10, TimeUnit.SECONDS).size)
            } finally { source.close() }
        }
    }

    private fun NewPipeYoutubeHttpDataSource.readFully(): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(32)
        while (true) {
            val count = read(buffer, 0, buffer.size)
            if (count < 0) break
            bytes.write(buffer, 0, count)
        }
        return bytes.toString("UTF-8")
    }

    private fun withResponses(
        responses: List<String>,
        block: (Int, java.util.concurrent.Future<List<List<String>>>) -> Unit,
    ) {
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val requests = executor.submit<List<List<String>>> {
                    responses.map { response ->
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            val reader = socket.getInputStream().bufferedReader()
                            val lines = buildList {
                                while (true) {
                                    val line = reader.readLine()
                                    if (line.isNullOrEmpty()) break
                                    add(line)
                                }
                            }
                            val bodyLength = lines.firstOrNull { it.startsWith("Content-Length:", true) }
                                ?.substringAfter(':')?.trim()?.toInt() ?: 0
                            repeat(bodyLength) { reader.read() }
                            socket.getOutputStream().write(response.toByteArray())
                            socket.getOutputStream().flush()
                            lines
                        }
                    }
                }
                block(server.localPort, requests)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun truncatedBodyResumesAtFirstMissingByteAndReplacesRangeParameter() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            val executor = Executors.newSingleThreadExecutor()
            val requests = executor.submit<List<String>> {
                (0..1).map { attempt ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine()
                        var contentLength = 0
                        while (true) {
                            val line = reader.readLine()
                            if (line.isNullOrEmpty()) break
                            if (line.startsWith("Content-Length:", true)) {
                                contentLength = line.substringAfter(':').trim().toInt()
                            }
                        }
                        repeat(contentLength) { reader.read() }
                        val payload = if (attempt == 0) "abcde" else "fghij"
                        val declared = if (attempt == 0) 10 else 5
                        socket.getOutputStream().write(
                            ("HTTP/1.1 200 OK\r\nContent-Length: $declared\r\nConnection: close\r\n\r\n" +
                                payload).toByteArray(),
                        )
                        socket.getOutputStream().flush()
                        request
                    }
                }
            }
            val source = NewPipeYoutubeHttpDataSource.Factory(true, true).createDataSource()
            try {
                source.open(DataSpec.Builder()
                    .setUri("http://127.0.0.1:${server.localPort}/videoplayback?range=0-9&rn=old")
                    .setLength(10).build())
                val received = ByteArrayOutputStream()
                val buffer = ByteArray(32)
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    received.write(buffer, 0, count)
                }
                assertEquals("abcdefghij", received.toString("UTF-8"))
                val urls = requests.get(10, TimeUnit.SECONDS).map { Uri.parse(it.split(' ')[1]) }
                assertEquals(listOf("0-9"), urls[0].getQueryParameters("range"))
                assertEquals(listOf("5-9"), urls[1].getQueryParameters("range"))
                assertNotEquals(urls[0].getQueryParameter("rn"), urls[1].getQueryParameter("rn"))
            } finally {
                source.close()
                executor.shutdownNow()
            }
        }
    }
}
