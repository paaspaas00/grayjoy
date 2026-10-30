package com.futo.platformplayer.compose.ui.screens

import org.junit.Assert.*
import org.junit.Test

class ChannelTabAnchorTest {
    @Test fun partiallyCollapsedSummaryRetainsItsExactOffset() {
        assertEquals(ChannelTabAnchor(0, 317), channelTabAnchor(0, 317))
        assertEquals(ChannelTabAnchor(0, 0), channelTabAnchor(0, -1))
    }
    @Test fun aPinnedHeaderStaysPinnedButOldResultScrollDoesNotCarryOver() {
        assertEquals(ChannelTabAnchor(1, 0), channelTabAnchor(1, 23))
        assertEquals(ChannelTabAnchor(1, 0), channelTabAnchor(47, 350))
        assertEquals(ChannelTabAnchor(1, 24), channelTabAnchor(47, 350, 24))
        assertEquals(ChannelTabAnchor(1, 9), channelTabAnchor(1, 9, 24))
    }
}
