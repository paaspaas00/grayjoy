package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.ui.DownloadStatus
import org.junit.Assert.*
import org.junit.Test

class DownloadQueueParsingTest {
    @Test
    fun badRecordDoesNotRemoveItsValidNeighbours() {
        val records = parseDownloadQueueRecords("""[
            {"profileId":"main","videoId":"one","mediaType":"Audio","status":"Preparing"},
            {"profileId":"main","videoId":"bad","mediaType":"UnknownFutureType"},
            null,
            {"profileId":"main","videoId":"two","mediaType":"Video","status":"Paused"}
        ]""", 123L)
        assertEquals(listOf("one", "two"), records.map { it.videoId })
        assertEquals(DownloadStatus.Queued, records[0].status)
        assertEquals(DownloadStatus.Paused, records[1].status)
        assertEquals(123L, records[0].createdAtMs)
    }

    @Test
    fun invalidOptionalFieldsDoNotDropAnOtherwiseValidDownload() {
        val record = parseDownloadQueueRecords("""[
            {"profileId":"main","videoId":"one","mediaType":"Audio","status":"Unknown", "targetAudioBitrate":{}, "errorMessage":null}
        ]""").single()
        assertEquals(DownloadStatus.Queued, record.status)
        assertNull(record.targetAudioBitrate)
        assertNull(record.errorMessage)
    }

    @Test
    fun malformedContainerAndMissingIdentityDoNotCrash() {
        assertTrue(parseDownloadQueueRecords("not json").isEmpty())
        assertTrue(parseDownloadQueueRecords("""[{"mediaType":"Audio"}]""").isEmpty())
    }
}
