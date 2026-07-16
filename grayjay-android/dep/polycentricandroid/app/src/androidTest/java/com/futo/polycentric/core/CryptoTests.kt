package com.futo.polycentric.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoTests {
    @Test
    fun roundtripTest() {
        val keyPair = Ed25519.generateKeyPair()
        val message = "test"
        val data = message.toByteArray()
        val signature = keyPair.sign(data)
        assertEquals(true, keyPair.verify(signature, data))
    }

    @Test
    fun derivePublicKeyTest() {
        val privateKey = "56133bc295f209c360897ea6e9cb8e2f67194a8b91c0e262924ed598de2efff0".hexStringToByteArray()
        val publicKey = Ed25519.privateKeyToPublicKey(privateKey)
        assertEquals("a540d2a74534c48fb65aebf5295ab94a2d2527d4b7750d72d94b7bbc0b623500", publicKey.toHexString())
    }
}