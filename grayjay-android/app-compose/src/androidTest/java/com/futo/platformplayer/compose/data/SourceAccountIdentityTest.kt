package com.futo.platformplayer.compose.data

import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.backend.YoutubeAccountIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SourceAccountIdentityTest {
    /** Opt-in read of the active test-app session. Never touches another app or changes the login. */
    @Test fun activeTestAccountResolvesWithoutJavascriptRuntime() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAccountCheck") == "true")
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".graytest"))
        val profile = ProfileRepository(context).activeProfileId()
        val plugin = "35ae969a-a7db-11ed-afa1-0242ac120002"
        val before = GrayjayPluginAuthStore.load(context, profile, plugin)
        assumeTrue("No active YouTube login in the test app", before != null)
        val name = YoutubeAccountIdentity(context).refresh(profile, plugin)
        assertTrue("YouTube did not provide an active account identity", !name.isNullOrBlank())
        assertTrue("Session must remain unchanged", before == GrayjayPluginAuthStore.load(context, profile, plugin))
        assertEquals(name, GrayjayPluginAuthStore.accountName(context, profile, plugin))
    }
}
