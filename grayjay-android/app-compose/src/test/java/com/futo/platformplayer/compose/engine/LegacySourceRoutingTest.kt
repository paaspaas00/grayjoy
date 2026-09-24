package com.futo.platformplayer.compose.engine

import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Test

class LegacySourceRoutingTest {
    private val legacyUri = "lbry://example#0123456789abcdef"
    private val webUrl = "https://odysee.com/@creator:a/example:0"
    private fun video() = VideoUiModel(
        id = legacyUri, title = "Example", creator = "Creator", metadata = "", duration = "",
        sourceId = "youtube", sourceName = "YouTube", sourceIconUrl = "old-icon",
        contentUrl = legacyUri, shareUrl = webUrl,
    )

    @Test fun repairsAlreadySavedDefaultSourceWithoutChangingPlaylistIdentity() {
        val repaired = repairLegacyVideoSource(video())
        assertEquals(legacyUri, repaired.id)
        assertEquals("odysee", repaired.sourceId)
        assertEquals("Odysee", repaired.sourceName)
        assertEquals(webUrl, repaired.contentUrl)
        assertEquals("", repaired.sourceIconUrl)
        assertFalse(isYoutubeContentReference(repaired.contentUrl))
    }

    @Test fun preservesNativeUriWhenNoSameSourceWebLinkExists() {
        val repaired = repairLegacyVideoSource(video().copy(shareUrl = "https://youtube.com/watch?v=abcdefghijk"))
        assertEquals("odysee", repaired.sourceId)
        assertEquals(legacyUri, repaired.contentUrl)
    }

    @Test fun customSourceChoicesRemainUnchanged() {
        val custom = video().copy(sourceId = "custom-source")
        assertSame(custom, repairLegacyVideoSource(custom))
    }

    @Test fun routingMatchesDomainsAndSchemesNotStringsInsideOtherUrls() {
        assertEquals("odysee", sourceIdHintForUrl(legacyUri))
        assertEquals("youtube", sourceIdHintForUrl("HTTPS://WWW.YOUTUBE.COM/watch?v=abcdefghijk"))
        assertNull(sourceIdHintForUrl("https://example.test/?next=https://youtube.com/watch"))
        assertNull(sourceIdHintForUrl("https://youtube.com.example.test/video"))
        assertNull(sourceIdHintForUrl("ftp://youtube.com/watch"))
        assertFalse(isYoutubeContentReference(legacyUri))
        assertFalse(isYoutubeContentReference("https://unknown.test/video"))
        assertTrue(isYoutubeContentReference("abcdefghijk"))
        assertTrue(isYoutubeContentReference("https://youtu.be/abcdefghijk"))
    }
}
