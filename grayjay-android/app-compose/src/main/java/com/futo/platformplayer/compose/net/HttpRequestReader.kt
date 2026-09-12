package com.futo.platformplayer.compose.net

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class HttpRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/** One bounded request per socket; both LAN endpoints close the connection after responding. */
internal fun readHttpRequest(input: InputStream, maxBodyBytes: Int): HttpRequest? {
    require(maxBodyBytes >= 0)
    var remainingHeaderBytes = 32 * 1024
    fun line(): String? {
        val bytes = ByteArrayOutputStream()
        while (remainingHeaderBytes-- > 0 && bytes.size() <= 8 * 1024) {
            val value = input.read()
            if (value < 0) return null
            if (value == 10) {
                val raw = bytes.toByteArray()
                val size = if (raw.lastOrNull() == 13.toByte()) raw.size - 1 else raw.size
                if ((0 until size).any { raw[it] < 32 && raw[it] != 9.toByte() }) return null
                return String(raw, 0, size, StandardCharsets.US_ASCII)
            }
            bytes.write(value)
        }
        return null
    }
    val start = line()?.split(' ') ?: return null
    if (start.size != 3 || !HTTP_TOKEN.matches(start[0]) ||
        start[2] !in setOf("HTTP/1.0", "HTTP/1.1") || !start[1].startsWith("/") ||
        start[1].length > 4096) return null
    val headers = linkedMapOf<String, String>()
    while (true) {
        val header = line() ?: return null
        if (header.isEmpty()) break
        val colon = header.indexOf(':')
        if (colon <= 0 || !HTTP_TOKEN.matches(header.substring(0, colon))) return null
        val name = header.substring(0, colon).lowercase(Locale.ROOT)
        // Ambiguous framing/authentication headers must never be silently overwritten.
        if (headers.containsKey(name)) return null
        headers[name] = header.substring(colon + 1).trim()
    }
    if ("transfer-encoding" in headers) return null
    val length = headers["content-length"]?.let { value ->
        if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
        value.toIntOrNull() ?: return null
    } ?: 0
    if (length !in 0..maxBodyBytes) return null
    val body = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = input.read(body, offset, length - offset)
        if (read <= 0) return null
        offset += read
    }
    return HttpRequest(start[0].uppercase(Locale.ROOT), start[1], headers, body)
}

private val HTTP_TOKEN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
