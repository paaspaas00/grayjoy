package com.futo.platformplayer.compose.ui

import org.junit.Assert.*
import org.junit.Test

class SavedUiValuesTest {
    @Test fun pageProtectionIncludesRootsRecentParentsAndPlayingPlaylistOnlyForCurrentProfile() {
        val history = (1..80).map { BrowseRoute("Search", channelId = "channel-$it") }
        val keys = protectedNavigationPageKeys("profile", history, "playing")
        assertTrue(keys.containsAll(GrayjayDestination.entries.map { "profile:destination:${it.name}" }))
        assertTrue("profile:channel:channel-80" in keys)
        assertFalse("profile:channel:channel-1" in keys)
        assertTrue("profile:playlist:playing" in keys)
        assertTrue(keys.all { it.startsWith("profile:") })
        assertTrue(keys.size <= GrayjayDestination.entries.size + 40 + 1)
    }
    @Test fun restoredRoutesRejectUnknownDestinationsAndBoundLongIdentityPayloads() {
        val bad = listOf("routes-v2", "RemovedScreen", "", "", "History", "", "")
        assertTrue(decodeBrowseHistory(bad).isEmpty())
        val histories = (1..40).map { BrowseRoute("Search", channelId = "$it-" + "x".repeat(3_000),
            libraryFilter = "RemovedFilter", parentDestination = "RemovedParent") }
        val encoded = encodeBrowseHistory(histories)
        assertTrue(encoded.sumOf(String::length) <= 16_384 + "routes-v2".length)
        val restored = decodeBrowseHistory(encoded)
        assertEquals(histories.last().channelId, restored.last().channelId)
        assertTrue(restored.all { it.libraryFilter == "History" && it.parentDestination == null })
        assertEquals(GrayjayDestination.Home, restoredEnumValue("RemovedScreen", GrayjayDestination.Home))
    }

    @Test fun largeOrMalformedBulkSelectionsAreDroppedWholeRatherThanPartiallyApplied() {
        assertTrue(savedBulkSelection(List(2_000) { "video-$it" }).isEmpty())
        assertTrue(savedBulkSelection(listOf("valid", 42)).isEmpty())
        assertTrue(savedBulkSelection(listOf("x".repeat(32_769))).isEmpty())
        assertEquals(listOf("one", "two"), savedBulkSelection(listOf("one", "one", "two")))
    }
}
