package com.futo.platformplayer.compose.net

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HttpRequestReaderTest {
    private fun parse(text: String) = readHttpRequest(ByteArrayInputStream(text.toByteArray()), 1024)

    @Test fun rejectsAmbiguousAndTruncatedBodies() {
        assertNull(parse("POST /v1/state HTTP/1.1\r\nContent-Length: nope\r\n\r\n"))
        assertNull(parse("POST /v1/state HTTP/1.1\r\nContent-Length: 4\r\n\r\na"))
        assertNull(parse("POST /v1/state HTTP/1.1\r\nContent-Length: 0\r\nContent-Length: 4\r\n\r\ntest"))
        assertNull(parse("POST /v1/state HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"))
        val valid = requireNotNull(parse("POST /v1/state HTTP/1.1\r\nContent-Length: 4\r\n\r\ntest"))
        assertEquals("test", valid.body.toString(Charsets.UTF_8))
    }

    @Test fun headerBudgetStopsEndlessTinyLinesAndCarriageReturns() {
        for (sample in listOf("\r", "x:a\r\n")) {
            val prefix = "GET / HTTP/1.1\r\n".toByteArray()
            var reads = 0
            val input = object : InputStream() {
                override fun read(): Int {
                    val index = reads++
                    check(reads <= 40_000) { "Header reader did not stop" }
                    return if (index < prefix.size) prefix[index].toInt()
                    else sample[(index - prefix.size) % sample.length].code
                }
            }
            assertNull(readHttpRequest(input, 0))
            assertTrue(reads <= 32 * 1024)
        }
    }

    @Test fun fuzzMalformedHeadersAndLengthsNeverAllocateOutsideBudget() {
        val random = Random(0x48545450)
        repeat(20_000) {
            val size = random.nextInt(150)
            val garbage = ByteArray(size) { random.nextInt(128).toByte() }
            val request = if (random.nextBoolean()) garbage else
                "POST /v1/state HTTP/1.1\r\nContent-Length: ".toByteArray() + garbage + "\r\n\r\n".toByteArray()
            val parsed = readHttpRequest(ByteArrayInputStream(request), 1024)
            if (parsed != null) assertTrue(parsed.body.size <= 1024)
        }
    }
}
