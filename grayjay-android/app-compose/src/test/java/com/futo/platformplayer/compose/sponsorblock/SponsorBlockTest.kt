package com.futo.platformplayer.compose.sponsorblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockTest {
    @Test
    fun videoOverrideWinsOverChannelAndGlobal() {
        val global = SponsorBlockRule(enabled = true, categories = setOf(SponsorBlockCategory.Sponsor))
        val channel = SponsorBlockRule(enabled = false, categories = setOf(SponsorBlockCategory.Intro))
        val video = SponsorBlockRule(enabled = true, categories = setOf(SponsorBlockCategory.Outro))

        assertEquals(video, effectiveSponsorBlockRule(global, channel, video))
        assertEquals(channel, effectiveSponsorBlockRule(global, channel, null))
        assertEquals(global, effectiveSponsorBlockRule(global, null, null))
    }

    @Test
    fun parserKeepsOnlyRequestedValidSkipSegments() {
        val result = parseSponsorBlockSegments(
            """[
                {"UUID":"a","actionType":"skip","category":"sponsor","segment":[1.25,3.5]},
                {"UUID":"b","actionType":"skip","category":"intro","segment":[4,8]},
                {"UUID":"c","actionType":"mute","category":"sponsor","segment":[9,10]},
                {"UUID":"d","actionType":"skip","category":"sponsor","segment":[12,11]}
            ]""".trimIndent(),
            setOf(SponsorBlockCategory.Sponsor),
        )

        assertEquals(1, result.size)
        assertEquals(1_250L, result.single().startMs)
        assertEquals(3_500L, result.single().endMs)
    }

    @Test
    fun manualSeekMakesOnlyThatSegmentPlayable() {
        val first = SponsorBlockSegment("first", 1_000, 5_000, SponsorBlockCategory.Sponsor)
        val second = SponsorBlockSegment("second", 8_000, 10_000, SponsorBlockCategory.Intro)

        assertNull(sponsorSegmentToSkip(listOf(first, second), 2_000, 20_000, setOf("first"), null))
        assertEquals(second, sponsorSegmentToSkip(listOf(first, second), 8_100, 20_000, setOf("first"), null))
    }

    @Test
    fun extractsYoutubeIdsFromSupportedUrls() {
        assertEquals("DpJTzdBp09c", youtubeVideoId("https://www.youtube.com/watch?v=DpJTzdBp09c&list=RDMM"))
        assertEquals("DpJTzdBp09c", youtubeVideoId("https://youtu.be/DpJTzdBp09c"))
        assertEquals("DpJTzdBp09c", youtubeVideoId("https://www.youtube.com/shorts/DpJTzdBp09c"))
        assertTrue(youtubeVideoId("https://example.com/watch?v=DpJTzdBp09c") == null)
    }
}
