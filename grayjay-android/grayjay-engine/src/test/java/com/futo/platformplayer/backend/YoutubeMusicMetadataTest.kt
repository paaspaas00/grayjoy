package com.futo.platformplayer.backend

import org.junit.Assert.*
import org.junit.Test

class YoutubeMusicMetadataTest {
    @Test fun onlyKnownCategoryMetadataDeterminesClassification() {
        assertEquals(true, youtubeMusicClassification("Music"))
        assertEquals(true, youtubeMusicClassification(" music "))
        assertEquals(false, youtubeMusicClassification("Science & Technology"))
        assertEquals(false, youtubeMusicClassification("Gaming"))
        assertNull(youtubeMusicClassification(null))
        assertNull(youtubeMusicClassification(""))
        assertNull(youtubeMusicClassification("A Music channel"))
        assertNull(youtubeMusicClassification("Unrecognized category"))
    }

    @Test fun watchHeadGenreAndPlayerMicroformatProduceSameResult() {
        assertEquals(true, youtubeMusicClassificationFromWatchPage(
            "<html><head><meta itemprop='genre' content='Music'></head><body></body></html>",
        ))
        assertEquals(false, youtubeMusicClassificationFromWatchPage(
            "<head><meta itemprop='genre' content='News &amp; Politics'></head>",
        ))
        assertEquals(true, youtubeMusicClassificationFromWatchPage(
            "<script>var player={\"microformat\":{\"playerMicroformatRenderer\":{\"category\":\"Music\"}}};</script>",
        ))
    }

    @Test fun titlesAndUnrelatedPageCategoriesNeverCountAsEvidence() {
        assertNull(youtubeMusicClassificationFromWatchPage("<head><title>Music Video Official Song</title></head>"))
        assertNull(youtubeMusicClassificationFromWatchPage("<script>var recommendations={\"category\":\"Music\"};</script>"))
        assertNull(youtubeMusicClassificationFromWatchPage("<head><meta itemprop='genre' content=''></head>"))
    }

    @Test fun onlyValidYoutubeVideoReferencesAreFetched() {
        val id = "abcdefghijk"
        assertEquals(id, youtubeMusicVideoId(id))
        assertEquals(id, youtubeMusicVideoId("https://www.youtube.com/watch?v=$id&list=other"))
        assertEquals(id, youtubeMusicVideoId("https://youtu.be/$id"))
        assertEquals(id, youtubeMusicVideoId("https://youtube.com/shorts/$id"))
        assertNull(youtubeMusicVideoId("https://youtube.com.attacker.test/watch?v=$id"))
        assertNull(youtubeMusicVideoId("https://example.test/?v=$id"))
        assertNull(youtubeMusicVideoId("https://youtube.com/watch?v=%Q"))
        assertNull(youtubeMusicVideoId("file://youtube.com/watch?v=$id"))
        assertNull(youtubeMusicVideoId("https://youtube.com/@creator"))
    }
}
