package com.futo.platformplayer.backend

import org.junit.Assert.*
import org.junit.Test

class YoutubeAuthenticatedSettingsTest {
    @Test fun authenticatedRequestsDoNotMutateStoredPreferencesOrEnableActivityTracking() {
        val saved = mapOf("authDetails" to "false", "allowLoginFallback" to "false", "youtubeActivity" to "true", "unrelated" to "keep")
        val runtime = youtubeAuthenticatedSettings(saved, true)
        assertEquals("true", runtime["authDetails"])
        assertEquals("true", runtime["allowLoginFallback"])
        assertEquals("false", runtime["youtubeActivity"])
        assertEquals("keep", runtime["unrelated"])
        assertEquals("false", saved["authDetails"])
        assertEquals("true", saved["youtubeActivity"])
    }
    @Test fun anonymousRequestsPreservePluginSettings() {
        val saved = mapOf("authDetails" to "false", "other" to "value")
        assertEquals(saved, youtubeAuthenticatedSettings(saved, false))
    }
}
