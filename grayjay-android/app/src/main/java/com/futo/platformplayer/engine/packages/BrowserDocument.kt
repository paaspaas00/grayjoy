package com.futo.platformplayer.engine.packages

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Legacy WebViews need script injection, but remote HTML must not exhaust the app heap. */
internal fun readBrowserDocument(input: InputStream, limit: Int = 4 * 1024 * 1024): ByteArray {
    require(limit in 0 until Int.MAX_VALUE)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) break
        if (count == 0) {
            val byte = input.read()
            if (byte < 0) break
            output.write(byte)
        } else output.write(buffer, 0, count)
        if (output.size() > limit) throw IOException("Browser document exceeds the injection limit")
    }
    return output.toByteArray()
}
