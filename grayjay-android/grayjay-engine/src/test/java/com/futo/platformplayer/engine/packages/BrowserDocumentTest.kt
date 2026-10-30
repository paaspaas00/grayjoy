package com.futo.platformplayer.engine.packages

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BrowserDocumentTest {
    @Test fun exactlyAtLimitPreservesContent() {
        val bytes = ByteArray(8192) { it.toByte() }
        assertArrayEquals(bytes, readBrowserDocument(ByteArrayInputStream(bytes), bytes.size))
    }
    @Test fun oversizedAndUnknownLengthDocumentsAreBounded() {
        var read = 0
        val input = object : java.io.InputStream() {
            override fun read(): Int { read++; return 65 }
        }
        try { readBrowserDocument(input, 100); fail("Expected size guard") }
        catch (_: IOException) { assertEquals(101, read) }
    }
    @Test fun variedChunkSizesNeverCorruptData() {
        val random = java.util.Random(63)
        repeat(200) {
            val bytes = ByteArray(random.nextInt(30_000)).also(random::nextBytes)
            val input = object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, off: Int, len: Int): Int =
                    super.read(buffer, off, minOf(len, 1 + random.nextInt(100)))
            }
            assertArrayEquals(bytes, readBrowserDocument(input, bytes.size))
        }
    }
}
