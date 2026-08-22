package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.ChannelUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSwitchRoutingTest {
    private val youtube = channel("youtube-channel", "youtube")
    private val nebula = channel("nebula-channel", "nebula")

    @Test
    fun newPipeModeRoutesOnlyYoutubeAwayFromPluginBackend() {
        val partition = partitionSubscriptionChannels(true, listOf(youtube, nebula))

        assertEquals(listOf(youtube), partition.newPipeChannels)
        assertEquals(listOf(nebula), partition.pluginChannels)
    }

    @Test
    fun grayjayModeRoutesYoutubeAndOtherSourcesToPlugins() {
        val partition = partitionSubscriptionChannels(false, listOf(youtube, nebula))

        assertTrue(partition.newPipeChannels.isEmpty())
        assertEquals(listOf(youtube, nebula), partition.pluginChannels)
    }

    private fun channel(id: String, sourceId: String) = ChannelUiModel(
        id = id,
        name = id,
        sourceId = sourceId,
        source = sourceId,
        unreadCount = 0,
        followerCount = "",
        description = "",
    )
}
