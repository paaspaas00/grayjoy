package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.ChannelDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelTabsTest {
    @Test
    fun `videos are always present and optional tabs follow source capabilities`() {
        assertEquals(
            listOf(ChannelContentTab.Videos),
            channelTabsFor(ChannelDetailUiState()),
        )
        assertEquals(
            listOf(ChannelContentTab.Videos, ChannelContentTab.Shorts),
            channelTabsFor(ChannelDetailUiState(supportsShorts = true)),
        )
        assertEquals(
            listOf(
                ChannelContentTab.Videos,
                ChannelContentTab.Shorts,
                ChannelContentTab.Playlists,
            ),
            channelTabsFor(
                ChannelDetailUiState(supportsShorts = true, supportsPlaylists = true),
            ),
        )
    }
}
