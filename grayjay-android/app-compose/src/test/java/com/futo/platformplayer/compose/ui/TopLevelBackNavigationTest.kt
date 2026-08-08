package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelBackNavigationTest {
    @Test
    fun bottomNavigationDestinationsReturnHome() {
        GrayjayDestination.entries
            .filter { it.showInCompactNavigation && it != GrayjayDestination.Home }
            .forEach { destination ->
                assertEquals(GrayjayDestination.Home, topLevelBackDestination(destination))
            }
    }

    @Test
    fun homeDelegatesBackToAndroid() {
        assertNull(topLevelBackDestination(GrayjayDestination.Home))
    }

    @Test
    fun nestedOnlyDestinationDoesNotMasqueradeAsTopLevel() {
        assertNull(topLevelBackDestination(GrayjayDestination.Sources))
    }

    @Test
    fun localPlaylistBackReturnsToLibraryPlaylistContext() {
        val playlists = listOf(
            PlaylistUiModel("local", "Local", "", emptyList()),
            PlaylistUiModel("remote", "Remote", "", emptyList(), sourceId = "youtube"),
        )

        assertTrue(isLocalPlaylistSelection("local", playlists))
        assertFalse(isLocalPlaylistSelection("remote", playlists))
        assertFalse(isLocalPlaylistSelection("missing", playlists))
        assertFalse(isLocalPlaylistSelection(null, playlists))
    }
}
