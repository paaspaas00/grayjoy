package com.futo.platformplayer.compose.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeBackupTest {
    @Test
    fun parsesOfficialSubscriptionJsonShape() {
        val json = """
            {
              "app_version": "0.28.0",
              "subscriptions": [
                {"service_id": 0, "url": "https://www.youtube.com/channel/one", "name": "One"},
                {"service_id": 1, "url": "https://soundcloud.com/two", "name": "Two"}
              ]
            }
        """.trimIndent().toByteArray()

        val backup = NewPipeBackupParser.parse(json, File("."))

        assertEquals(2, backup.subscriptions.size)
        assertEquals("One", backup.subscriptions.first().name)
        assertEquals("soundcloud", backup.subscriptionChannels("Imported")[1].sourceId)
    }

    @Test
    fun materializesHistoryProgressAndPlaylistOrder() {
        val first = NewPipeStream(
            id = 1,
            serviceId = 0,
            url = "https://www.youtube.com/watch?v=one",
            title = "One",
            streamType = 0,
            durationSeconds = 100,
            uploader = "Creator",
            uploaderUrl = "https://www.youtube.com/channel/c",
            thumbnailUrl = "https://example.com/one.jpg",
        )
        val second = first.copy(
            id = 2,
            url = "https://www.youtube.com/watch?v=two",
            title = "Two",
        )
        val backup = NewPipeBackup(
            subscriptions = emptyList(),
            streams = mapOf(1L to first, 2L to second),
            history = listOf(NewPipeHistoryEntry(1, 1_700_000_000_000L, 25_000L)),
            playlists = listOf(NewPipePlaylistEntry(7, "Road trip", listOf(2, 1))),
        )

        val (videos, playlists) = backup.buildImportLibrary(
            includePlaylists = true,
            includeHistory = true,
            importedDescription = "Imported",
        )

        assertEquals(listOf(second.url, first.url), playlists.single().videoIds)
        assertEquals(0.25f, videos.single { it.id == first.url }.watchProgress, 0.0001f)
        assertEquals(1_700_000_000_000L, videos.single { it.id == first.url }.lastWatchedAt)
        assertTrue(videos.any { it.id == second.url })
    }
}
