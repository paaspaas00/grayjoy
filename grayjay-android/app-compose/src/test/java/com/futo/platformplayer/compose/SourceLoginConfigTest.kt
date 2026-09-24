package com.futo.platformplayer.compose

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SourceLoginConfigTest {
    @Test fun readsUtf8WithoutChangingConfiguration() {
        val config = "{\"name\":\"Configurazione è valida\"}"
        assertEquals(config, readSourceLoginConfig(ByteArrayInputStream(config.toByteArray())))
    }

    @Test fun oversizedConfigurationIsRejectedBeforeUnboundedAllocation() {
        val payload = ByteArray(2 * 1024 * 1024 + 1) { 'a'.code.toByte() }
        try {
            readSourceLoginConfig(ByteArrayInputStream(payload))
            fail("Oversized input was accepted")
        } catch (_: IOException) { }
    }
}
