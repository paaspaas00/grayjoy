package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.*
import com.futo.platformplayer.compose.ui.screens.shuffledReorderIds
import com.futo.platformplayer.compose.ui.screens.reconcileReorderDraft
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

class SpotifyHomeTest {
    private val state = """{"home":{"data":{"sections":[{"uri":"s","title":"Novità","items":[
        {"title":"Città","uri":"spotify:track:1234567890123456789012","imageUrl":"https://i.scdn.co/a"},
        {"title":"Album","uri":"spotify:album:2234567890123456789012"},
        {"title":"Artist","uri":"spotify:artist:3234567890123456789012"},
        {"title":"Invalid","uri":"javascript:alert(1)"},
        {"title":"Duplicate","uri":"spotify:track:1234567890123456789012"}]}]}}}"""

    @Test fun decodesUtf8AndPreservesOrderedSectionsAndSupportedTypes() {
        val encoded = Base64.getEncoder().encodeToString(state.toByteArray())
        val sections = parseSpotifyHomeState(decodeSpotifyHomeState("<script type='text/plain' id='initialState'>$encoded</script>"))
        assertEquals("Novità", sections.single().title)
        assertEquals(listOf("track", "album", "artist"), sections.single().items.map { it.type })
        assertEquals("Città", sections.single().items.first().title)
        assertEquals("Spotify", sections.single().items.first().asVideo().creator)
        assertEquals("spotify", sections.single().items[1].asPlaylist().sourceId)
    }
    @Test fun rejectsMalformedOrMissingStateWithoutInventingAFeed() {
        assertTrue(parseSpotifyHomeState("{}").isEmpty())
        assertTrue(runCatching { decodeSpotifyHomeState("<html>no data</html>") }.isFailure)
        assertTrue(runCatching { decodeSpotifyHomeState("<script id='initialState'>!</script>") }.isFailure)
    }
    @Test fun shuffleIsPermutationAndKeepsPlayingTrackFirstEvenWithDuplicates() {
        repeat(500) { seed ->
            val random = Random(seed)
            val ids = List(random.nextInt(1, 200)) { "id-$it" }
            val current = ids.random(random)
            val result = shuffledReorderIds(ids + ids.take(3), current, random)
            assertEquals(current, result.first())
            assertEquals(ids.toSet(), result.toSet())
            assertEquals(ids.size, result.size)
            assertEquals(ids, reconcileReorderDraft(ids, result))
        }
    }
    @Test fun musicPresentationUsesOnlySourceOrExplicitProfileNotEverySpotifyTrack() {
        val spotify = SourceUiModel("spotify", "Spotify", "", 0L, true)
        val yt = spotify.copy(id = "youtube")
        assertTrue(GrayjayUiState(sources = listOf(spotify)).usesMusicPresentation())
        assertFalse(GrayjayUiState(sources = listOf(spotify, yt)).usesMusicPresentation())
        assertTrue(GrayjayUiState(sources = listOf(spotify, yt), profiles = listOf(
            ProfileUiModel("main", "Spotify", ProfileProtection.None))).usesMusicPresentation())
    }
    @Test fun musicPlaybackUsesCompleteAudioDescriptorWithoutASecondMergedSource() {
        val model = VideoUiModel("s", "Track", "Artist", "", "3:00", sourceId = "spotify", playbackUrl = "https://video",
            playbackManifest = "video manifest", audioUrl = "https://audio", audioDownloadUrl = "https://audio-dash",
            audioDownloadManifest = "audio manifest", audioDownloadMimeType = "application/dash+xml",
            audioDownloadRequestHeaders = mapOf("Referer" to "https://source"))
        val audio = model.preferMusicAudioSource()
        assertEquals("audio manifest", audio.playbackManifest)
        assertEquals("https://audio-dash", audio.playbackUrl)
        assertEquals(model.audioDownloadRequestHeaders, audio.playbackRequestHeaders)
        assertTrue(audio.playbackAudioOnly)
        assertEquals("", audio.audioUrl)
        val muxed = model.copy(audioUrl = "", audioDownloadUrl = "", audioDownloadManifest = "")
        assertSame(muxed, muxed.preferMusicAudioSource())
    }
    @Test fun numericSongTitlesAreNotDiscardedAsNoise() {
        val target = VideoUiModel("s", "7/7", "Artist Name", "", "3:00", sourceId = "spotify")
        val match = target.copy(id = "yt", title = "Artist Name - 7/7 (Official Audio)", sourceId = "youtube")
        assertEquals(match, selectSpotifyYoutubeCandidate(target, listOf(match)))
        assertNull(selectSpotifyYoutubeCandidate(target, listOf(match.copy(title = "Different Song", creator = "Other Artist"))))
    }
}
