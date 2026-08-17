package com.futo.platformplayer.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class YouTubeScheduleParserTest {
    @Test
    fun `parses upcoming live start timestamp`() {
        val html = """
            {"videoDetails":{"isLiveContent":true},"isUpcoming":true,
             "liveBroadcastDetails":{"startTimestamp":"2026-08-18T16:30:00+00:00"}}
        """.trimIndent()

        assertEquals(
            OffsetDateTime.parse("2026-08-18T16:30:00+00:00").toInstant().toEpochMilli(),
            parseYouTubeScheduledStartMs(html),
        )
    }

    @Test
    fun `does not classify ordinary or completed live video as scheduled`() {
        assertNull(
            parseYouTubeScheduledStartMs(
                """{"isUpcoming":false,"startTimestamp":"2026-08-18T16:30:00+00:00"}""",
            ),
        )
        assertNull(parseYouTubeScheduledStartMs("""{"isLiveContent":false}"""))
    }
}
