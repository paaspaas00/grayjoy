package com.futo.platformplayer.compose

import com.futo.platformplayer.compose.ui.HomeFeedType
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSubscriptionShortsTest {
    @Test
    fun youtubeShortsAreExcludedOnlyFromSubscriptionHome() {
        val regular = video("regular")
        val short = video("short").copy(isShort = true)
        val otherSourceShort = video("other-short").copy(sourceId = "other", isShort = true)
        val videos = listOf(regular, short, otherSourceShort)

        assertEquals(
            listOf("regular", "other-short"),
            videosForHomeFeed(HomeFeedType.Subscriptions, videos).map(VideoUiModel::id),
        )
        assertEquals(
            listOf("regular", "short", "other-short"),
            videosForHomeFeed(HomeFeedType.ForYou, videos).map(VideoUiModel::id),
        )
        assertEquals(
            listOf("short"),
            videosForHomeFeed(HomeFeedType.Shorts, videos).map(VideoUiModel::id),
        )
    }

    private fun video(id: String) = VideoUiModel(
        id = id,
        title = id,
        creator = "Creator",
        metadata = "",
        duration = "1:00",
        sourceId = "youtube",
    )
}
