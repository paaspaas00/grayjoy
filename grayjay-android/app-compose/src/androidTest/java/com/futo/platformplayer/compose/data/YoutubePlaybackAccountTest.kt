package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.backend.GrayjayPluginBackend
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.engine.AuthenticatedYoutubePlayback
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class YoutubePlaybackAccountTest {
    private fun fixture(test: (Context) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "youtube-account-test-${UUID.randomUUID()}"
        val names = mutableSetOf<String>()
        val files = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = files
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val key = "$prefix-$name"; synchronized(names) { names += key }
                return base.getSharedPreferences(key, mode)
            }
        }
        try { test(context) } finally { names.forEach(base::deleteSharedPreferences); files.deleteRecursively() }
    }
    private fun auth(value: String) = SourceAuth(cookieMap = hashMapOf(".youtube.com" to hashMapOf("SAPISID" to value)),
        headers = mapOf(".youtube.com" to mapOf("X-Goog-AuthUser" to "2", "Authorization" to "old-captured-authorization")))

    @Test fun linkingUsesTheOriginalSessionAndHonorsRefreshLogoutProtectionAndOptOut() = fixture { context ->
        val profiles = ProfileRepository(context)
        val spotify = profiles.createProfile("Spotify")
        val youtube = profiles.createProfile("YouTube")
        profiles.setActiveProfile(spotify.id)
        val accounts = YoutubePlaybackAccounts(context)
        GrayjayPluginAuthStore.save(context, youtube.id, YOUTUBE_ACCOUNT_PLUGIN_ID, auth("one"))
        assertNull(accounts.session(spotify.id))
        accounts.link(spotify.id, youtube.id)
        assertEquals(youtube.id, accounts.session(spotify.id)?.owner)
        assertNull(GrayjayPluginAuthStore.load(context, spotify.id, YOUTUBE_ACCOUNT_PLUGIN_ID))
        GrayjayPluginAuthStore.save(context, youtube.id, YOUTUBE_ACCOUNT_PLUGIN_ID, auth("two"))
        assertEquals(auth("two"), accounts.session(spotify.id)?.auth)
        profiles.setDeviceCredentialProtection(youtube.id, true)
        assertNull(accounts.session(spotify.id)); assertTrue(accounts.candidates(spotify.id).isEmpty())
        profiles.setDeviceCredentialProtection(youtube.id, false)
        GrayjayPreferences(context, youtube.id).privateSessionEnabled = true
        assertNull(accounts.session(spotify.id))
        GrayjayPreferences(context, youtube.id).privateSessionEnabled = false
        GrayjayPluginAuthStore.clear(context, youtube.id, YOUTUBE_ACCOUNT_PLUGIN_ID)
        assertNull(accounts.session(spotify.id))
        accounts.link(spotify.id, null)
        assertNull(accounts.linkedProfile(spotify.id))
    }

    @Test fun requestUsesFreshAuthorizationOnlyOnYoutubeHosts() {
        val client = JSHttpClient(null, auth("test-session"))
        val youtube = client.beforeRequest(okhttp3.Request.Builder().url("https://www.youtube.com/youtubei/v1/player").build())
        assertTrue(youtube.header("Cookie").orEmpty().contains("SAPISID=test-session"))
        assertTrue(youtube.header("Authorization").orEmpty().startsWith("SAPISIDHASH "))
        assertNotEquals("old-captured-authorization", youtube.header("Authorization"))
        assertEquals("2", youtube.header("X-Goog-AuthUser"))
        for (host in listOf("youtube.com.example.org", "example.org", "googlevideo.com")) {
            val external = client.beforeRequest(okhttp3.Request.Builder().url("https://$host/video").build())
            assertNull(external.header("Cookie")); assertNull(external.header("Authorization"))
        }
    }

    @Test fun anInFlightAccountRequestCannotPublishAfterTheOwnerBecomesProtected() = fixture { context -> runBlocking {
        val profiles = ProfileRepository(context)
        val spotify = profiles.createProfile("Spotify"); val youtube = profiles.createProfile("YouTube")
        profiles.setActiveProfile(spotify.id)
        GrayjayPluginAuthStore.save(context, youtube.id, YOUTUBE_ACCOUNT_PLUGIN_ID, auth("original"))
        YoutubePlaybackAccounts(context).link(spotify.id, youtube.id)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller = AuthenticatedYoutubePlayback(context, { spotify.id }, scope) { GrayjayPluginBackend(context, authenticatedYoutubePlayback = true) }
        val entered = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
        try {
            assertTrue(controller.hasSession())
            val job = async { controller.request({ error("Anonymous fallback must not be used") }) {
                entered.complete(Unit); proceed.await(); "not-publishable"
            } }
            entered.await()
            profiles.setDeviceCredentialProtection(youtube.id, true)
            proceed.complete(Unit)
            try { job.await(); fail("Stale account result was published") } catch (_: CancellationException) { }
        } finally { controller.release(); scope.cancel() }
    } }
}
