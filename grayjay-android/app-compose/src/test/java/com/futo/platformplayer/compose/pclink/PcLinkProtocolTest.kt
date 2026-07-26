package com.futo.platformplayer.compose.pclink

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcLinkProtocolTest {
    private val secret = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32) { index -> index.toByte() })

    @Test
    fun parsesExtensionPairingQr() {
        val payload = PcLinkProtocol.parsePairingPayload(
            "grayjoy://pc-pair?v=1&id=12345678-abcd&name=Living+Room+PC&secret=$secret",
        )

        assertNotNull(payload)
        assertEquals("12345678-abcd", payload?.computerId)
        assertEquals("Living Room PC", payload?.computerName)
        assertEquals(secret, payload?.secret)
    }

    @Test
    fun rejectsWrongHostAndShortSecrets() {
        assertNull(
            PcLinkProtocol.parsePairingPayload(
                "grayjoy://plugin?v=1&id=12345678&name=PC&secret=$secret",
            ),
        )
        assertNull(
            PcLinkProtocol.parsePairingPayload(
                "grayjoy://pc-pair?v=1&id=12345678&name=PC&secret=YWJj",
            ),
        )
    }

    @Test
    fun requestSignatureCoversTargetAndBody() {
        val timestamp = "1770000000000"
        val nonce = "abcdefghijklmnop"
        val body = """{"active":true}""".toByteArray()
        val signature = requireNotNull(
            PcLinkProtocol.signature(
                secret,
                timestamp,
                nonce,
                "POST",
                "/v1/state",
                body,
            ),
        )
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)

        assertTrue(
            PcLinkProtocol.verifySignature(
                secret,
                timestamp,
                nonce,
                "POST",
                "/v1/state",
                body,
                encoded,
            ),
        )
        assertFalse(
            PcLinkProtocol.verifySignature(
                secret,
                timestamp,
                nonce,
                "POST",
                "/v1/state?tampered=true",
                body,
                encoded,
            ),
        )
        assertFalse(
            PcLinkProtocol.verifySignature(
                secret,
                timestamp,
                nonce,
                "POST",
                "/v1/state",
                """{"active":false}""".toByteArray(),
                encoded,
            ),
        )
    }
}
