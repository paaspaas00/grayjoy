package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayjayAppSignatureTest {
    @Test
    fun rootComposableKeepsCallbacksOutOfItsDexSignature() {
        val entryPoints = Class.forName("com.futo.platformplayer.compose.ui.GrayjayAppKt")
            .declaredMethods
            .filter { it.name == "GrayjayApp" }

        assertEquals(1, entryPoints.size)
        assertTrue(
            "GrayjayApp has ${entryPoints.single().parameterCount} JVM parameters; " +
                "an oversized Compose signature can be rejected by ART during startup",
            entryPoints.single().parameterCount <= 12,
        )
    }
}
