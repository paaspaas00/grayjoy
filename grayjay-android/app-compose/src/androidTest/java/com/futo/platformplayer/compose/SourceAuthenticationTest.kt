package com.futo.platformplayer.compose

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.others.LoginWebViewClient
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SourceAuthenticationTest {
    @Test fun clearingMainAuthenticationAlsoRemovesLegacyFallbackAndKeepsOtherProfiles() {
        withIsolatedAuthStore { context, preferences ->
            preferences.edit().putString("plugin-test", "{\"cookies\":{},\"headers\":{}}").commit()
            GrayjayPluginAuthStore.save(context, "other", "plugin-test", SourceAuth())
            assertNotNull(GrayjayPluginAuthStore.load(context, "main", "plugin-test"))
            GrayjayPluginAuthStore.clear(context, "main", "plugin-test")
            assertNull(GrayjayPluginAuthStore.load(context, "main", "plugin-test"))
            assertNotNull(GrayjayPluginAuthStore.load(context, "other", "plugin-test"))
        }
    }

    @Test fun nullUserAgentRoundTripsAndMainSaveMigratesLegacyKey() {
        withIsolatedAuthStore { context, preferences ->
            preferences.edit().putString("plugin-test", "{}").commit()
            GrayjayPluginAuthStore.save(context, "main", "plugin-test", SourceAuth(userAgent = null))
            assertNull(GrayjayPluginAuthStore.load(context, "main", "plugin-test")?.userAgent)
            assertFalse(preferences.contains("plugin-test"))
        }
    }

    @Test fun concurrentLoginRequestsPublishIndependentAuthenticationSnapshots() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CookieManager.getInstance() }
        val client = LoginWebViewClient(SourcePluginAuthConfig(
            loginUrl = "https://example.test/", headersToFind = listOf("Authorization"),
        ))
        val authentications = Collections.synchronizedList(mutableListOf<SourceAuth>())
        client.onLogin.subscribe { authentications += it }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 200).map { index -> executor.submit {
                client.shouldInterceptRequest(null, object : WebResourceRequest {
                    override fun getUrl() = Uri.parse("https://example.test/$index")
                    override fun isForMainFrame() = false
                    override fun isRedirect() = false
                    override fun hasGesture() = false
                    override fun getMethod() = "GET"
                    override fun getRequestHeaders() = mapOf("Authorization" to "token-$index")
                })
            } }
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
            assertEquals(200, authentications.size)
            assertEquals((0 until 200).map { "token-$it" }.toSet(),
                authentications.map { it.headers["example.test"]?.get("authorization") }.toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun withIsolatedAuthStore(block: (Context, SharedPreferences) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "auth-regression-${UUID.randomUUID()}"
        val preferences = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = preferences
        }
        try { block(context, preferences) } finally { base.deleteSharedPreferences(name) }
    }
}
