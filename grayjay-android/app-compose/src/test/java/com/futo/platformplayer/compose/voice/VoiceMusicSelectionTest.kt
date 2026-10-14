package com.futo.platformplayer.compose.voice

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class VoiceMusicSelectionTest {
    private fun video(id: String, music: Boolean? = true, time: Long = 10L) =
        VideoUiModel(id, id, "Artist", "", "", isMusic = music, lastWatchedAt = time)
    @Test fun onlyWatchedAvailableMusicIsIncludedAndCurrentIsAvoided() {
        val values = listOf(video("current"), video("other"), video("unknown", null), video("talk", false),
            video("unwatched", time = 0), video("gone").copy(isAvailable = false), video("live").copy(isLive = true))
        assertEquals(listOf("other"), recentVoiceMusic(values, emptyList(), false, "current").map { it.id })
    }
    @Test fun offlineNeverReturnsANetworkItemAndUsesThePlayableDescriptor() {
        val local = video("downloaded").copy(playbackFromDownload = true, playbackUrl = "file:///test.mp4")
        val result = recentVoiceMusic(listOf(video("remote"), video("downloaded")), listOf(local), true, null)
        assertEquals(listOf(local), result)
        assertTrue(recentVoiceMusic(listOf(video("remote")), listOf(local), true, null).isEmpty())
    }
    @Test fun recentWindowIsBoundedAndSingleSongStillWorks() {
        val values = (1..150).map { video("$it", time = it.toLong()) }
        val result = recentVoiceMusic(values, emptyList(), false, null)
        assertEquals(100, result.size); assertEquals("150", result.first().id)
        assertEquals(listOf(video("one")), recentVoiceMusic(listOf(video("one")), emptyList(), false, "one"))
    }
}
