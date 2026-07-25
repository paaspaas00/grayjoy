package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.SourceUiModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeImportSourceTest {
    @Test
    fun `account import is offered only for youtube source`() {
        assertTrue(source(id = "youtube", name = "YouTube").isYoutubeAccountImportSource())
        assertTrue(
            source(
                id = "renamed",
                name = "Renamed source",
                engineId = "35ae969a-a7db-11ed-afa1-0242ac120002",
            ).isYoutubeAccountImportSource(),
        )
        assertFalse(source(id = "odysee", name = "Odysee").isYoutubeAccountImportSource())
    }

    private fun source(
        id: String,
        name: String,
        engineId: String = id,
    ) = SourceUiModel(
        id = id,
        name = name,
        description = "",
        accentColor = 0L,
        isEnabled = true,
        engineId = engineId,
    )
}
