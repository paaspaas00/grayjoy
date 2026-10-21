package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.ui.DownloadMediaType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OfflinePlaylistStoreConcurrencyTest {
    @Test fun reconciliationCannotOverwriteCancelRemoveOrReregisterActions() = withStore { store ->
        val first = store.register("main", "first", DownloadMediaType.Audio, listOf("one", "two"))
        val second = store.register("main", "second", DownloadMediaType.Audio, listOf("three"))
        val snapshot = store.all("main")
        store.excludeVideo("main", "two", DownloadMediaType.Audio)
        assertFalse(store.replaceProfileIfUnchanged("main", snapshot, listOf(first.copy(managedVideoIds = setOf("one")), second)))
        assertEquals(setOf("two"), store.all("main").first { it.playlistId == "first" }.excludedVideoIds)
        assertEquals(setOf("one", "two"), store.all("main").first { it.playlistId == "first" }.managedVideoIds)

        val beforeRemove = store.all("main")
        store.remove("main", "first", DownloadMediaType.Audio)
        assertFalse(store.replaceProfileIfUnchanged("main", beforeRemove, snapshot))
        assertFalse(store.all("main").any { it.playlistId == "first" })

        val beforeRegister = store.all("main")
        store.register("main", "second", DownloadMediaType.Audio, listOf("new"))
        assertFalse(store.replaceProfileIfUnchanged("main", beforeRegister, beforeRegister))
        assertEquals(setOf("new"), store.all("main").single().managedVideoIds)
    }

    @Test fun successfulReconciliationIsAtomicAndDoesNotTouchOtherProfiles() = withStore { store ->
        store.register("main", "first", DownloadMediaType.Audio, listOf("one"))
        val other = store.register("other", "first", DownloadMediaType.Audio, listOf("keep"))
        val snapshot = store.all("main")
        assertTrue(store.replaceProfileIfUnchanged("main", snapshot, listOf(snapshot.single().copy(managedVideoIds = setOf("two")))))
        assertEquals(setOf("two"), store.all("main").single().managedVideoIds)
        assertEquals(listOf(other), store.all("other"))
    }

    private fun withStore(block: (GrayjoyOfflinePlaylistStore) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "offline-sync-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try { block(GrayjoyOfflinePlaylistStore(context)) } finally { base.deleteSharedPreferences(name) }
    }
}
