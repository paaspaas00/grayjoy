package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.data.LibraryRepository
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class MetadataPresentationPersistenceTest {
    @Test fun burstsAccumulateAndAcknowledgingOldSnapshotPreservesNewerFields() {
        val pending = MetadataPersistenceBuffer()
        pending.put("profile", "a", MetadataPresentationPatch(duration = "1:23"))
        val saving = pending.snapshot("profile")
        pending.put("profile", "b", MetadataPresentationPatch(isMusic = false))
        pending.put("profile", "a", MetadataPresentationPatch(authorThumbnailUrl = "new-avatar"))
        pending.acknowledge("profile", saving)
        assertEquals(setOf("a", "b"), pending.snapshot("profile").keys)
        assertEquals(MetadataPresentationPatch("1:23", "new-avatar"), pending.snapshot("profile")["a"])
        val latest = pending.snapshot("profile")
        pending.acknowledge("profile", latest)
        assertTrue(pending.snapshot("profile").isEmpty())
    }

    @Test fun buffersAreProfileIsolatedBoundedAndResettable() {
        val pending = MetadataPersistenceBuffer(maxEntriesPerProfile = 8, maxProfiles = 2)
        repeat(1000) { pending.put("a", "v$it", MetadataPresentationPatch(duration = "$it")) }
        pending.put("b", "v999", MetadataPresentationPatch(isMusic = true))
        assertEquals(8, pending.snapshot("a").size)
        assertEquals("999", pending.snapshot("a")["v999"]?.duration)
        assertEquals(true, pending.snapshot("b")["v999"]?.isMusic)
        pending.acknowledge("b", pending.snapshot("a"))
        assertEquals(true, pending.snapshot("b")["v999"]?.isMusic)
        pending.clear()
        assertTrue(pending.snapshot("a").isEmpty())
        assertTrue(pending.snapshot("b").isEmpty())
    }

    @Test fun persistencePatchesOnlyMetadataOnFreshRowsAndDoesNotResurrectRemovedRows() {
        val fresh = video("one").copy(title = "Edited later", description = "Updated description",
            playlistNames = listOf("New playlist"), lastWatchedAt = 900L)
        val rows = mutableListOf(fresh)
        val repository = repository(rows)
        val changed = persistMetadataPresentations(repository, mapOf(
            "one" to MetadataPresentationPatch("2:30", "avatar", true),
            "removed" to MetadataPresentationPatch("1:00"),
        ))
        assertEquals(1, changed)
        assertEquals(fresh.copy(duration = "2:30", authorThumbnailUrl = "avatar", isMusic = true), rows.single())
    }

    @Test fun cancelledGenerationDoesNotWriteEvenAfterReadingTheLatestRows() {
        val rows = mutableListOf(video("one"))
        var checks = 0
        try {
            persistMetadataPresentations(repository(rows), mapOf("one" to MetadataPresentationPatch("9:59"))) {
                if (++checks == 2) throw CancellationException("Profile changed")
            }
            fail("Cancellation must escape")
        } catch (_: CancellationException) { }
        assertEquals("", rows.single().duration)
    }

    private fun video(id: String) = VideoUiModel(id, id, "Creator", "", "")

    private fun repository(rows: MutableList<VideoUiModel>): LibraryRepository {
        val lock = Any()
        return Proxy.newProxyInstance(LibraryRepository::class.java.classLoader,
            arrayOf(LibraryRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getTransactionLock" -> lock
                "loadSavedVideos" -> {
                    check(Thread.holdsLock(lock))
                    rows.toList()
                }
                "saveVideos" -> {
                    check(Thread.holdsLock(lock))
                    @Suppress("UNCHECKED_CAST")
                    val updates = (args!![0] as Collection<VideoUiModel>).associateBy(VideoUiModel::id)
                    val replacement = rows.map { updates[it.id] ?: it }
                    rows.clear()
                    rows.addAll(replacement)
                    null
                }
                else -> error("Unexpected repository method ${method.name}")
            }
        } as LibraryRepository
    }
}
