package com.futo.platformplayer.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginQrUrlTest {
    @Test
    fun acceptsSupportedPluginLinkFormats() {
        assertEquals(
            "https://plugins.example/config.json",
            pluginUrlFromQrContent("  https://plugins.example/config.json  "),
        )
        assertEquals(
            "grayjay://plugin/https://plugins.example/config.json",
            pluginUrlFromQrContent("grayjay://plugin/https://plugins.example/config.json"),
        )
        assertEquals(
            "vfuto://plugins.example/config.json",
            pluginUrlFromQrContent("vfuto://plugins.example/config.json"),
        )
    }

    @Test
    fun rejectsEmptyAndUnrelatedQrPayloads() {
        assertNull(pluginUrlFromQrContent(""))
        assertNull(pluginUrlFromQrContent("plain text"))
        assertNull(pluginUrlFromQrContent("http://plugins.example/config.json"))
        assertNull(pluginUrlFromQrContent("grayjay://plugin/"))
    }
}
