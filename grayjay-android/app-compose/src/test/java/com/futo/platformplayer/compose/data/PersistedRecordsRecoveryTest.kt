package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.downloads.parseOfflinePlaylistRecords
import com.futo.platformplayer.compose.ui.DownloadMediaType
import org.junit.Assert.*
import org.junit.Test

class PersistedRecordsRecoveryTest {
    @Test fun obsoleteOrDamagedDownloadRecordDoesNotDisableOtherPlaylists() {
        val parsed = parseOfflinePlaylistRecords("""[
            {"profileId":"main","playlistId":"first","mediaType":"Audio","managedVideoIds":["one",null,{},42],"targetVideoHeight":-20},
            {"profileId":"main","playlistId":"obsolete","mediaType":"RemovedType","managedVideoIds":[]},
            false,
            {"profileId":"second","playlistId":"last","mediaType":"Video","managedVideoIds":["two"],"excludedVideoIds":["three"]},
            {"profileId":"main","playlistId":"bad-list","mediaType":"Video","managedVideoIds":{}}
        ]""")
        assertEquals(listOf("first", "last"), parsed.map { it.playlistId })
        assertEquals(DownloadMediaType.Audio, parsed[0].mediaType)
        assertEquals(setOf("one"), parsed[0].managedVideoIds)
        assertNull(parsed[0].targetVideoHeight)
        assertEquals(setOf("three"), parsed[1].excludedVideoIds)
    }

    @Test fun damagedArtworkRecordCannotDiscardTheOtherCreators() {
        val parsed = parseChannelArtworkRecords("""[
            {"id":"first","source":"youtube","name":"First","image":"https://example.com/one.jpg","updated":123},
            {"id":"broken","source":{},"image":"https://example.com/no.jpg"},
            null,
            {"id":"last","source":"youtube","name":"Last","image":"https://example.com/two.jpg","updated":-1}
        ]""")
        assertEquals(listOf("first", "last"), parsed.map { it.first.id })
        assertEquals(123L, parsed[0].second)
        assertEquals(0L, parsed[1].second)
    }

    @Test fun malformedPersistedRootsAndFieldsFailLocally() {
        val fragments = listOf("null", "false", "0", "[]", "{}", "\"text\"")
        for (fragment in fragments) {
            assertTrue(parseOfflinePlaylistRecords(fragment).isEmpty())
            assertTrue(parseChannelArtworkRecords(fragment).isEmpty())
            for (field in listOf("profileId", "playlistId", "mediaType", "managedVideoIds")) {
                assertTrue(parseOfflinePlaylistRecords("[{\"$field\":$fragment}]").isEmpty())
            }
            for (field in listOf("id", "source", "image", "updated")) {
                assertTrue(parseChannelArtworkRecords("[{\"$field\":$fragment}]").isEmpty())
            }
        }
    }
}
