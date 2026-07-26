package com.futo.platformplayer.compose.pclink

import java.net.NetworkInterface
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PcLinkProtocol {
    const val PORT = 43821
    const val VERSION = 1
    const val PAIR_SCHEME = "grayjoy"
    const val PAIR_HOST = "pc-pair"
    const val MAX_CLOCK_SKEW_MS = 2 * 60_000L
    const val STATE_STALE_AFTER_MS = 12_000L

    fun parsePairingPayload(raw: String): PcPairingPayload? {
        val uri = runCatching { URI.create(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(PAIR_SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(PAIR_HOST, ignoreCase = true)) return null
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator < 0) return@mapNotNull null
            val key = URLDecoder.decode(entry.substring(0, separator), Charsets.UTF_8.name())
            val value = URLDecoder.decode(entry.substring(separator + 1), Charsets.UTF_8.name())
            key to value
        }.toMap()
        if (query["v"]?.toIntOrNull() != VERSION) return null
        val id = query["id"]?.trim().orEmpty()
        val name = query["name"]?.trim().orEmpty()
        val secret = query["secret"]?.trim().orEmpty()
        if (!SAFE_ID.matches(id) || name.isBlank() || name.length > 80) return null
        val secretBytes = secret.base64UrlDecodeOrNull() ?: return null
        if (secretBytes.size != 32) return null
        return PcPairingPayload(id, name, secret)
    }

    fun canonicalRequest(
        timestamp: String,
        nonce: String,
        method: String,
        requestTarget: String,
        body: ByteArray,
    ): ByteArray = buildString {
        append(timestamp)
        append('\n')
        append(nonce)
        append('\n')
        append(method.uppercase())
        append('\n')
        append(requestTarget)
        append('\n')
        append(sha256(body).toHex())
    }.toByteArray(Charsets.UTF_8)

    fun signature(
        secret: String,
        timestamp: String,
        nonce: String,
        method: String,
        requestTarget: String,
        body: ByteArray,
    ): ByteArray? {
        val key = secret.base64UrlDecodeOrNull() ?: return null
        return runCatching {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(canonicalRequest(timestamp, nonce, method, requestTarget, body))
            }
        }.getOrNull()
    }

    fun verifySignature(
        secret: String,
        timestamp: String,
        nonce: String,
        method: String,
        requestTarget: String,
        body: ByteArray,
        suppliedSignature: String,
    ): Boolean {
        val supplied = suppliedSignature.base64UrlDecodeOrNull() ?: return false
        val expected = signature(secret, timestamp, nonce, method, requestTarget, body) ?: return false
        return MessageDigest.isEqual(expected, supplied)
    }

    fun localServerUrls(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { address ->
                !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    address.hostAddress?.contains(':') == false
            }
            .mapNotNull { it.hostAddress }
            .distinct()
            .sorted()
            .map { address -> "http://$address:$PORT" }
    }.getOrDefault(emptyList())

    private fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value)

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private fun String.base64UrlDecodeOrNull(): ByteArray? = runCatching {
        Base64.getUrlDecoder().decode(this + "=".repeat((4 - length % 4) % 4))
    }.getOrNull()

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{8,80}")
}
