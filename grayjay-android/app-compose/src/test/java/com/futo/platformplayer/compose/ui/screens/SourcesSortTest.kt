package com.futo.platformplayer.compose.ui.screens

import com.futo.platformplayer.compose.ui.SourceUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class SourcesSortTest {
    @Test
    fun youtubeIsPinnedThenRemainingSourcesAreAlphabetical() {
        val sources = listOf(source("YouTube"), source("bilibili"), source("Odysee"))

        assertEquals(
            listOf("YouTube", "bilibili", "Odysee"),
            visibleSourcesForQuery(sources, "").map(SourceUiModel::name),
        )
    }

    private fun source(name: String) = SourceUiModel(
        id = name.lowercase(),
        name = name,
        description = "Video source",
        accentColor = 0L,
        isEnabled = true,
    )
}
