package com.futo.platformplayer.compose.voice

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class VoiceArtistSelectionTest {
    private fun video(id: String, title: String, creator: String = "Queen Official", views: Long = 1) =
        VideoUiModel(id, title, creator, "", "", viewCount = views, isMusic = true)
    @Test fun artistMatchingRejectsUnrelatedAndDiscussionVideos() {
        val values = listOf(video("a", "Queen - A"), video("b", "Other - B", "Other", 99),
            video("c", "Queen interview"), video("d", "Queen - D").copy(isMusic = false))
        assertEquals(listOf("a"), artistVideos("Queen", values).map { it.id })
    }
    @Test fun nextChoiceAvoidsCurrentTrackIncludingAlternateUploads() {
        val first = video("a", "Queen - We Will Rock You (Official Video)")
        val duplicate = video("b", "Queen - We Will Rock You [Lyrics]", "Some Music")
        val other = video("c", "Queen - Bohemian Rhapsody")
        assertEquals(other, chooseArtistVideo(listOf(first, duplicate, other), setOf("a"), "a", true, "Queen",
            setOf(artistTrackKey(first, "Queen")), artistTrackKey(first, "Queen")))
        assertNull(chooseArtistVideo(listOf(first, duplicate), setOf("a"), "a", true, "Queen", emptySet(), artistTrackKey(first, "Queen")))
    }
    @Test fun contextCannotCrossProfilesOrAnUnrelatedManualSelection() {
        val session = VoiceArtistSession("profile", "Queen", "song", setOf("song"), listOf(video("a", "A")), 1000)
        assertTrue(session.applies("profile", "song", null))
        assertTrue(session.applies("profile", "old", "song"))
        assertFalse(session.applies("other", "song", null))
        assertFalse(session.applies("profile", "other song", null))
        assertTrue(session.freshCandidates(121001).isEmpty())
        assertTrue(session.freshCandidates(900).isEmpty())
    }
    @Test fun aNormalUploaderIsNotAssumedToBeThePerformer() {
        assertEquals("Queen", inferVoiceArtist(video("a", "Song", "Queen - Topic")))
        assertNull(inferVoiceArtist(video("a", "Music video", "Compilation channel").copy(isMusic = null)))
        assertNull(inferVoiceArtist(video("a", "Queen - Song", "Compilation channel")))
        assertEquals("Queen", inferVoiceArtist(video("a", "Queen - Song", "Queen")))
    }
    @Test fun meaningfulTitlesAreNotCollapsedByTechnicalAnnotationRemoval() {
        assertNotEquals(artistTrackKey(video("a", "Queen - Song (Part 1)"), "Queen"),
            artistTrackKey(video("b", "Queen - Song (Part 2)"), "Queen"))
        assertNotEquals(artistTrackKey(video("a", "Queen - Video Games"), "Queen"),
            artistTrackKey(video("b", "Queen - Games"), "Queen"))
    }
}
