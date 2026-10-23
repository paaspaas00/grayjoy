package com.futo.platformplayer.compose.ui

import com.futo.platformplayer.compose.data.*
import org.junit.Assert.*
import org.junit.Test

class SourceProfileRulesTest {
    @Test fun verticalSwipeThresholdRejectsHorizontalMotionAndJitter() {
        assertEquals(0, profileSwipeDirection(0f, 7f, 8f))
        assertEquals(-1, profileSwipeDirection(0f, 8f, 8f))
        assertEquals(1, profileSwipeDirection(0f, -8f, 8f))
        assertEquals(0, profileSwipeDirection(30f, 20f, 8f))
        assertEquals(0, profileSwipeDirection(20f, 20f, 8f))
        assertEquals(0, profileSwipeDirection(Float.NaN, 0f, 8f))
        assertEquals(0, profileSwipeDirection(1f, Float.POSITIVE_INFINITY, 8f))
    }
    @Test fun swipeDirectionIsDensityIndependentAndAlwaysBounded() {
        val random = kotlin.random.Random(7821)
        repeat(10000) {
            val dx = random.nextFloat() * 200 - 100
            val dy = random.nextFloat() * 200 - 100
            val density = random.nextFloat() * 5 + .5f
            val direction = profileSwipeDirection(dx, dy, 8f)
            assertTrue(direction in -1..1)
            assertEquals(direction, profileSwipeDirection(dx * density, dy * density, 8f * density))
        }
        assertEquals(0, profileSwipeDirection(1f, 5f, 0f))
    }
    private fun profile(id: String, protection: ProfileProtection = ProfileProtection.None) = ProfileUiModel(id, id, protection)
    @Test fun swipingSkipsAllProtectedProfilesInBothDirections() {
        val rows = listOf(profile("yt"), profile("pin", ProfileProtection.Pin),
            profile("spotify"), profile("private", ProfileProtection.DeviceCredential))
        assertEquals("spotify", nextUnprotectedProfile(rows, "yt", 1))
        assertEquals("spotify", nextUnprotectedProfile(rows, "yt", -1))
        assertEquals("yt", nextUnprotectedProfile(rows, "spotify", 1))
        assertEquals("yt", nextUnprotectedProfile(rows, "pin", 1))
        assertNull(nextUnprotectedProfile(listOf(profile("only")), "only", 1))
        assertNull(nextUnprotectedProfile(listOf(profile("pin", ProfileProtection.Pin)), "pin", 1))
    }
    @Test fun splitKeepsMetadataAndPartitionsMixedPlaylistsWithStableIds() {
        val yt = VideoUiModel("yt", "Video", "Channel", "", "", sourceId = "youtube",
            isWatchLater = true, isLiked = true, lastWatchedAt = 100, watchProgress = .4f)
        val music = yt.copy(id = "sp", sourceId = "spotify")
        val snapshot = LibrarySnapshot(listOf(yt, music),
            listOf(PlaylistUiModel("mixed", "Mixed", "", listOf("sp", "yt"))), 0)
        val result = partitionSourceLibrary(snapshot, "youtube", "main")
        assertEquals(listOf(yt), result.videos)
        assertEquals(listOf("yt"), result.playlists.single().videoIds)
        assertEquals(result, partitionSourceLibrary(snapshot, "youtube", "main"))
        assertNotEquals(result.playlists.single().id, partitionSourceLibrary(snapshot, "spotify", "main").playlists.single().id)
    }
    @Test fun privateProtectedAndAlreadyDedicatedProfilesAreNotMigrationOrigins() {
        assertTrue(profileCopyEligible(profile("main")))
        assertFalse(profileCopyEligible(profile("private")))
        assertFalse(profileCopyEligible(profile("pin", ProfileProtection.Pin)))
        assertFalse(profileCopyEligible(profile("locked", ProfileProtection.DeviceCredential)))
        assertFalse(profileCopyEligible(profile("yt").copy(sourceId = "youtube")))
    }
    @Test fun remotePlaylistKeepsItsResolvableAddress() {
        val playlist = PlaylistUiModel("https://example.test/list/123", "Album", "", emptyList(),
            sourceId = "spotify", videoCount = 20)
        val result = partitionSourceLibrary(LibrarySnapshot(emptyList(), listOf(playlist), 0), "spotify", "main")
        assertEquals(playlist.id, result.playlists.single().id)
        assertEquals(20, result.playlists.single().videoCount)
    }
    @Test fun emptyPlaylistKeepsTheOriginalProfilesSourceAffinity() {
        val snapshot = LibrarySnapshot(emptyList(), listOf(PlaylistUiModel("empty", "Later", "", emptyList())), 0)
        assertEquals(1, partitionSourceLibrary(snapshot, "spotify", "old", setOf("spotify")).playlists.size)
        assertTrue(partitionSourceLibrary(snapshot, "youtube", "old", setOf("spotify")).playlists.isEmpty())
    }
    @Test fun youtubeTerminologyIsOnlyUsedWhenItIsTheOnlyEnabledSource() {
        val yt = SourceUiModel("youtube", "YouTube", "", 0, true)
        val sp = SourceUiModel("spotify", "Spotify", "", 0, true)
        assertTrue(listOf(yt).isYoutubeOnly())
        assertFalse(listOf(yt, sp).isYoutubeOnly())
        assertTrue(listOf(yt, sp.copy(isEnabled = false)).isYoutubeOnly())
        assertEquals(com.futo.platformplayer.compose.R.string.channels_label,
            sourceTerminologyResource(com.futo.platformplayer.compose.R.string.creators, true))
    }
}
