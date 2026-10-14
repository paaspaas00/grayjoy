package com.futo.platformplayer.compose.background

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class HistoryMusicClassifierTest {
    private fun video(index: Int) = VideoUiModel(id = "video-$index", title = "Title", creator = "Channel",
        metadata = "", duration = "1:00", sourceId = "youtube", lastWatchedAt = 1000L)
    @Test fun classificationQueueIsNotLimitedToTwentyFourOrVisibleRows() {
        val all = (1..1376).map(::video)
        assertEquals(1376, all.filter(::needsMusicClassification).size)
    }
    @Test fun knownResultsAndUnwatchedOtherSourcesAreNeverClassifiedAgain() {
        assertFalse(needsMusicClassification(video(1).copy(isMusic = false)))
        assertFalse(needsMusicClassification(video(1).copy(isMusic = true)))
        assertFalse(needsMusicClassification(video(1).copy(lastWatchedAt = 0L)))
        assertFalse(needsMusicClassification(video(1).copy(sourceId = "other")))
        assertTrue(needsMusicClassification(video(1).copy(lastWatchedAt = 0L, watchProgress = 0.2f)))
    }
}
