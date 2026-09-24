package com.futo.platformplayer.compose

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal fun loadSourceLoginConfig(cached: File, configUrl: String): String {
    if (cached.isFile) return cached.inputStream().use(::readSourceLoginConfig)
    val connection = URL(configUrl).openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 15_000
    }
    try {
        return connection.getInputStream().use(::readSourceLoginConfig)
    } finally {
        (connection as? HttpURLConnection)?.disconnect()
    }
}

internal fun readSourceLoginConfig(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() > MAX_LOGIN_CONFIG_BYTES - count) {
            throw IOException("Source login configuration exceeds the size limit")
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

private const val MAX_LOGIN_CONFIG_BYTES = 2 * 1024 * 1024
