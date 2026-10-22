package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.onboarding.GuidedSetupPreferences
import com.futo.platformplayer.compose.ui.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class GuidedSetupProfilesTest {
    @Test fun disablingSimplifiedModeKeepsExistingProfilesAndData() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "setup-test-${UUID.randomUUID()}"
        val names = mutableSetOf<String>()
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val scoped = "$prefix-$name"; names += scoped
                return base.getSharedPreferences(scoped, mode)
            }
        }
        try {
            val catalog = ProfileRepository(context)
            val old = catalog.createProfile("YouTube", "1234")
            val original = SharedPreferencesLibraryRepository(context, old.id)
            original.saveVideo(VideoUiModel("keep", "Keep", "Creator", "", "1:00"))
            context.getSharedPreferences("grayjoy_guided_setup", Context.MODE_PRIVATE).edit()
                .putBoolean("simple_startup", true).putString("profile_youtube", old.id).commit()
            val setup = GuidedSetupPreferences(context)
            assertTrue(setup.showOnStartup)
            assertFalse(setup.simpleStartup)
            setup.simpleStartup = true
            assertFalse(setup.simpleStartup)
            assertEquals(old.id, setup.profileId("youtube"))
            assertThrows(IllegalStateException::class.java) { setup.ensureProfiles(GrayjayUiState()) }
            assertEquals(3, catalog.profiles().size)
            assertEquals(ProfileProtection.Pin, catalog.profiles().single { it.id == old.id }.protection)
            assertTrue(catalog.verifyPin(old.id, "1234"))
            assertEquals("keep", original.loadSavedVideos().single().id)
            assertEquals("main", catalog.activeProfileId())
        } finally { names.forEach(base::deleteSharedPreferences); directory.deleteRecursively() }
    }
}
