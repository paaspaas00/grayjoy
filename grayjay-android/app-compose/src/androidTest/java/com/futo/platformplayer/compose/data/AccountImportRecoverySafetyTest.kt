package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.ui.VideoUiModel
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AccountImportRecoverySafetyTest {
    @Test fun malformedJournalIsRetainedAndCannotEraseLaterEdits() = withProfile { context, profile ->
        val repository = SharedPreferencesLibraryRepository(context, profile)
        repository.saveVideo(video("original"))
        val journal = journalFile(context, profile)
        journal.parentFile!!.mkdirs()
        journal.writeText("{ malformed recovery snapshot")

        val reopened = SharedPreferencesLibraryRepository(context, profile)
        assertEquals(setOf("original"), reopened.ids())
        reopened.saveVideo(video("saved-after-failure"))
        assertEquals(setOf("original", "saved-after-failure"),
            SharedPreferencesLibraryRepository(context, profile).ids())
        assertFalse(journal.exists())
        assertEquals("{ malformed recovery snapshot", quarantinedFiles(context).single().readText())
    }

    @Test fun quarantineFailureAllowsReadsButBlocksWritesUntilSnapshotIsSafe() = withProfile { context, profile ->
        val original = SharedPreferencesLibraryRepository(context, profile)
        original.saveVideo(video("original"))
        val journal = journalFile(context, profile)
        journal.parentFile!!.mkdirs()
        journal.writeText("invalid snapshot")
        val blocker = File(journal.parentFile, "recovery-failed")
        blocker.writeText("not a directory")

        val reopened = SharedPreferencesLibraryRepository(context, profile)
        assertEquals(setOf("original"), reopened.ids())
        try {
            reopened.saveVideo(video("must-not-be-saved"))
            fail("Edits must be blocked until the snapshot is safely retired")
        } catch (_: AccountImportRecoveryBlockedException) { }
        assertEquals(setOf("original"), reopened.ids())
        assertEquals("invalid snapshot", journal.readText())

        assertTrue(blocker.delete())
        reopened.saveVideo(video("saved-after-repair"))
        assertEquals(setOf("original", "saved-after-repair"),
            SharedPreferencesLibraryRepository(context, profile).ids())
        assertEquals("invalid snapshot", quarantinedFiles(context).single().readText())
    }

    @Test fun failedPreferenceRestoreIsQuarantinedInsteadOfReplayedOverNewWrites() = withProfile { context, profile ->
        val repository = SharedPreferencesLibraryRepository(context, profile)
        repository.saveVideo(video("original"))
        repository.importJournal.begin()
        val originalSnapshot = journalFile(context, profile).readBytes()
        repository.saveVideo(video("partial-import"))
        repository.importJournal.abandonForRecovery()

        val failing = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = context.filesDir
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = context.getSharedPreferences(name, mode)
                if (!name.startsWith("grayjay_compose_watch_progress_v1")) return preferences
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = preferences.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }
        val reopened = SharedPreferencesLibraryRepository(failing, profile)
        assertEquals(setOf("original"), reopened.ids())
        reopened.saveVideo(video("saved-after-failure"))
        assertEquals(setOf("original", "saved-after-failure"),
            SharedPreferencesLibraryRepository(context, profile).ids())
        assertArrayEquals(originalSnapshot, quarantinedFiles(context).single().readBytes())
    }

    @Test fun persistedQuarantineMarkerNeverResumesAutomaticRollback() = withProfile { context, profile ->
        val repository = SharedPreferencesLibraryRepository(context, profile)
        repository.saveVideo(video("original"))
        repository.importJournal.begin()
        repository.saveVideo(video("keep-current-data"))
        repository.importJournal.abandonForRecovery()
        val journal = journalFile(context, profile)
        File(journal.parentFile, journal.name.removeSuffix(".json") + ".quarantining")
            .writeText("test-interrupted-quarantine")
        assertEquals(setOf("original", "keep-current-data"),
            SharedPreferencesLibraryRepository(context, profile).ids())
        assertFalse(journal.exists())
        assertEquals(1, quarantinedFiles(context).size)
    }

    @Test fun watchProgressRevisionChangesForEveryOverlayMutation() = withProfile { context, profile ->
        val repository = SharedPreferencesLibraryRepository(context, profile)
        val peer = SharedPreferencesLibraryRepository(context, profile)
        val progress = context.getSharedPreferences("grayjay_compose_watch_progress_v1_$profile", 0)
        fun revision() = progress.getLong("__progress_revision", 0L)
        var previous = revision()
        fun expectRevisionChange() {
            assertNotEquals(previous, revision())
            previous = revision()
        }
        repository.saveVideo(video("watched").copy(isLiked = true))
        peer.loadSavedVideos()
        repository.recordHistory(video("watched"), 0.6f)
        expectRevisionChange()
        assertEquals(0.6f, peer.loadSavedVideos().single().watchProgress, 0f)
        repository.removeFromHistory(listOf("watched"))
        expectRevisionChange()
        assertEquals(0f, peer.loadSavedVideos().single().watchProgress, 0f)
        repository.mergeImportedData(listOf(video("watched").copy(lastWatchedAt = 42L, watchProgress = 0.7f)), emptyList(), false)
        expectRevisionChange()
        assertEquals(0.7f, peer.loadSavedVideos().single().watchProgress, 0f)
        repository.restoreImportSnapshot(LibraryImportSnapshot(listOf(video("watched")), emptyList()))
        expectRevisionChange()
        assertEquals(0f, peer.loadSavedVideos().single().watchProgress, 0f)
    }

    private fun withProfile(block: (Context, String) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val profile = "recovery-safety-${UUID.randomUUID()}"
        val directory = File(base.cacheDir, profile).apply { mkdirs() }
        val scoped = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        try { block(scoped, profile) } finally {
            listOf("grayjay_compose_library_v2", "grayjay_compose_watch_progress_v1", "grayjay_compose_preferences")
                .forEach { base.getSharedPreferences("${it}_$profile", 0).edit().clear().commit() }
            directory.deleteRecursively()
        }
    }

    private fun journalFile(context: Context, profile: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(profile.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "account-import/$hash.json")
    }

    private fun quarantinedFiles(context: Context): List<File> =
        File(context.filesDir, "account-import/recovery-failed").walkTopDown().filter { it.isFile }.toList()

    private fun SharedPreferencesLibraryRepository.ids() = loadSavedVideos().map { it.id }.toSet()
    private fun video(id: String) = VideoUiModel(id, id, "Creator", "", "1:00")
}
