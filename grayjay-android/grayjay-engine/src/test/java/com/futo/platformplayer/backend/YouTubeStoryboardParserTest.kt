package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeStoryboardParserTest {
    private val spec =
        "https://i.ytimg.com/sb/video/storyboard3_L\$L/\$N.jpg?sqp=token" +
            "|48#27#100#10#10#0#default#signature0" +
            "|106#45#191#10#10#10000#M\$M#signature1" +
            "|213#90#191#5#5#10000#M\$M#signature2" +
            "|427#180#191#3#3#10000#M\$M#signature3"

    @Test
    fun parsesEveryLevelAndBuildsSignedSheetTemplate() {
        val storyboard = YouTubeStoryboardParser.parseSpec(spec, 1_910)
        assertNotNull(storyboard)

        assertEquals(4, storyboard!!.levels.size)
        assertEquals(19_100L, storyboard.levels.first().intervalMs)
        assertEquals(
            "https://i.ytimg.com/sb/video/storyboard3_L3/M\$M.jpg?sqp=token&sigh=signature3",
            storyboard.levels.last().sheetUrlTemplate,
        )
    }

    @Test
    fun extractsJsonEscapesFromWatchResponse() {
        val escapedSpec = spec
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("&", "\\u0026")
        val html = "prefix \"playerStoryboardSpecRenderer\":{\"spec\":" +
            "\"$escapedSpec\"} suffix"

        assertEquals(4, YouTubeStoryboardParser.parseWatchHtml(html, 1_910)?.levels?.size)
    }

    @Test
    fun malformedOrAbsentSpecsUseFallback() {
        assertNull(YouTubeStoryboardParser.parseWatchHtml("no player data", 100))
        assertNull(YouTubeStoryboardParser.parseSpec("https://example.com/base", 100))
    }
}
