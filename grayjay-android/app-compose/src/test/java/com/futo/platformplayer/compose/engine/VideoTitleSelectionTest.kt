package com.futo.platformplayer.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTitleSelectionTest {
    @Test
    fun `original mode preserves youtube feed title`() {
        assertEquals(
            "FOMO parte 2: mille sessioni AI",
            resolvedVideoTitle(
                feedTitle = "FOMO parte 2: mille sessioni AI",
                sourceTitle = "FOMO part 2: a thousand AI sessions",
                sourceId = "youtube",
                preferOriginal = true,
            ),
        )
    }

    @Test
    fun `application language mode accepts resolved localized title`() {
        assertEquals(
            "FOMO part 2: a thousand AI sessions",
            resolvedVideoTitle(
                feedTitle = "FOMO parte 2: mille sessioni AI",
                sourceTitle = "FOMO part 2: a thousand AI sessions",
                sourceId = "youtube",
                preferOriginal = false,
            ),
        )
    }
}
