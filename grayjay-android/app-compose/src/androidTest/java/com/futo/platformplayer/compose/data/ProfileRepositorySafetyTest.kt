package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.ui.ProfileProtection
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProfileRepositorySafetyTest {
    @Test fun unknownDeletionMetadataAllowsReadsButBlocksWritesAndPreservesRecovery() = withIsolatedProfileStore { context ->
        val profiles = ProfileRepository(context)
        val profile = profiles.createProfile("Keep recoverable")
        val library = SharedPreferencesLibraryRepository(context, profile.id)
        library.saveVideo(video("original"))
        library.importJournal.begin()
        library.saveVideo(video("partial"))
        library.importJournal.abandonForRecovery()
        val journal = File(context.filesDir, "account-import").listFiles().orEmpty().single { it.extension == "json" }
        val snapshot = journal.readBytes()
        val metadata = context.getSharedPreferences("grayjay_compose_profiles", 0)
        metadata.edit().putBoolean("deleted_profile_ids", true).commit()

        val reopened = SharedPreferencesLibraryRepository(context, profile.id)
        assertEquals(setOf("original", "partial"), reopened.loadSavedVideos().map { it.id }.toSet())
        assertTrue(ProfileRepository(context).profiles().any { it.id == profile.id })
        assertThrows(ProfileDataUnavailableException::class.java) { reopened.saveVideo(video("late")) }
        assertThrows(ProfileDataUnavailableException::class.java) { profiles.requireAvailableProfile(profile.id) }
        assertThrows(ProfileDataUnavailableException::class.java) { profiles.createProfile("Cannot overwrite") }
        assertArrayEquals(snapshot, journal.readBytes())
        assertEquals(true, metadata.all["deleted_profile_ids"])

        metadata.edit().putStringSet("deleted_profile_ids", emptySet()).commit()
        val recovered = SharedPreferencesLibraryRepository(context, profile.id)
        assertEquals(listOf("original"), recovered.loadSavedVideos().map { it.id })
        recovered.saveVideo(video("after recovery"))
        assertFalse(journal.exists())
    }

    @Test fun missingCatalogDoesNotResetUnknownDeletionMetadataOnStartup() = withIsolatedProfileStore { context ->
        val metadata = context.getSharedPreferences("grayjay_compose_profiles", 0)
        metadata.edit().putString("deleted_profile_ids", "damaged").commit()
        val repository = ProfileRepository(context)
        assertEquals("main", repository.activeProfileId())
        assertEquals(ProfileProtection.DeviceCredential, repository.profiles().single().protection)
        assertFalse(metadata.contains("profiles"))
        assertEquals("damaged", metadata.getString("deleted_profile_ids", null))
        assertThrows(ProfileDataUnavailableException::class.java) { repository.createProfile("New") }
    }

    @Test fun malformedCatalogIsKeptAndCannotSilentlyRemoveProtection() = withIsolatedProfileStore { context ->
        val initial = ProfileRepository(context)
        initial.setDeviceCredentialProtection("main", true)
        val prefs = context.getSharedPreferences("grayjay_compose_profiles", 0)
        prefs.edit().putString("profiles", "{ broken catalog").commit()
        val reopened = ProfileRepository(context)
        assertEquals(ProfileProtection.DeviceCredential, reopened.profiles().single().protection)
        assertEquals("main", reopened.activeProfileId())
        assertThrows(ProfileDataUnavailableException::class.java) { reopened.createProfile("New") }
        assertThrows(ProfileDataUnavailableException::class.java) { reopened.requireAvailableProfile("main") }
        assertEquals("{ broken catalog", prefs.getString("profiles", null))
    }

    @Test fun malformedSiblingCannotEraseValidProfilesOrReachAFileName() = withIsolatedProfileStore { context ->
        val initial = ProfileRepository(context)
        val existing = initial.createProfile("Keep me", "1234")
        val prefs = context.getSharedPreferences("grayjay_compose_profiles", 0)
        val corrupted = JSONArray(prefs.getString("profiles", "[]")).put(JSONObject()
            .put("id", "../escape").put("name", "Invalid").put("protection", "None")).toString()
        prefs.edit().putString("profiles", corrupted).putInt("active_profile", 42).commit()
        val reopened = ProfileRepository(context)
        assertTrue(reopened.profiles().any { it.id == existing.id && it.protection == ProfileProtection.Pin })
        assertFalse(reopened.profiles().any { it.id.contains('/') })
        assertEquals("main", reopened.activeProfileId())
        assertThrows(ProfileDataUnavailableException::class.java) { reopened.renameProfile(existing.id, "Changed") }
        assertEquals(corrupted, prefs.getString("profiles", null))
    }

    @Test fun deletedProfileCannotBeRecreatedByStaleWritersOrRecovery() = withIsolatedProfileStore { context ->
        val profiles = ProfileRepository(context)
        val profile = profiles.createProfile("Delete me")
        val library = SharedPreferencesLibraryRepository(context, profile.id)
        library.saveVideo(video("before"))
        library.importJournal.begin()
        library.saveVideo(video("partial"))
        library.importJournal.abandonForRecovery()
        assertTrue(profiles.deleteProfile(profile.id))
        assertThrows(DeletedProfileException::class.java) { profiles.requireAvailableProfile(profile.id) }
        assertThrows(DeletedProfileException::class.java) { library.saveVideo(video("late worker")) }
        assertTrue(SharedPreferencesLibraryRepository(context, profile.id).loadSavedVideos().isEmpty())
        assertFalse(profiles.profiles().any { it.id == profile.id })
        assertFalse(profiles.deleteProfile("main"))
    }

    @Test fun separateRepositoryInstancesDoNotLoseConcurrentProfileCreations() = withIsolatedProfileStore { context ->
        val first = ProfileRepository(context)
        val second = ProfileRepository(context)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val jobs = listOf(first, second).mapIndexed { owner, repository -> executor.submit {
                start.await()
                repeat(12) { repository.createProfile("$owner-$it") }
            } }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(26, first.profiles().size)
            assertEquals(26, first.profiles().map { it.id }.distinct().size)
        } finally { executor.shutdownNow() }
    }

    private fun video(id: String) = VideoUiModel(id, id, "Creator", "", "1:00")

    private fun withIsolatedProfileStore(block: (Context) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "profile-audit-${UUID.randomUUID()}"
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        val names = mutableSetOf<String>()
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val scopedName = "$prefix-$name"
                synchronized(names) { names.add(scopedName) }
                return base.getSharedPreferences(scopedName, mode)
            }
        }
        try { block(isolated) } finally {
            names.forEach { base.deleteSharedPreferences(it) }
            directory.deleteRecursively()
        }
    }
}
