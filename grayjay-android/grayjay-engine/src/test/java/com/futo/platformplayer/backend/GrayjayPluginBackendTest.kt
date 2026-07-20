package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class GrayjayPluginBackendTest {
    @Test
    fun pluginSignatureState_treatsMissingAndBlankPairsAsUnsigned() {
        assertEquals(PluginSignatureState.Unsigned, pluginSignatureState(null, null))
        assertEquals(PluginSignatureState.Unsigned, pluginSignatureState("", ""))
        assertEquals(PluginSignatureState.Unsigned, pluginSignatureState("  ", "\t"))
    }

    @Test
    fun pluginSignatureState_requiresBothSignatureFields() {
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState("signature", null))
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState(null, "public-key"))
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState("signature", ""))
        assertEquals(PluginSignatureState.Incomplete, pluginSignatureState("", "public-key"))
    }

    @Test
    fun pluginSignatureState_recognizesCompleteSignedPayload() {
        assertEquals(
            PluginSignatureState.Signed,
            pluginSignatureState("signature", "public-key"),
        )
    }

    @Test
    fun interleaveSourceResults_keepsEverySourceVisibleNearTop() {
        val merged = interleaveSourceResults(
            listOf(
                listOf("youtube-1", "youtube-2", "youtube-3"),
                listOf("odysee-1", "odysee-2"),
                listOf("custom-1"),
            ),
        )

        assertEquals(
            listOf(
                "youtube-1",
                "odysee-1",
                "custom-1",
                "youtube-2",
                "odysee-2",
                "youtube-3",
            ),
            merged,
        )
    }

    @Test
    fun interleaveSourceResults_keepsResultsWhenSomeSourcesAreEmpty() {
        assertEquals(
            listOf("odysee-1", "odysee-2"),
            interleaveSourceResults(listOf(emptyList(), listOf("odysee-1", "odysee-2"))),
        )
    }
}
