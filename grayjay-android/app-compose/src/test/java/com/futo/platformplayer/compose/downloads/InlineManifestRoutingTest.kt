package com.futo.platformplayer.compose.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineManifestRoutingTest {
    @Test
    fun inlineManifestGetsDistinctIdentityFromMediaBaseUrl() {
        val media = "https://media.example/videoplayback?token=abc"
        val manifest = inlineManifestRequestUri(media, "request-id")

        assertNotEquals(media, manifest)
        assertEquals(media, manifest.substringBefore('#'))
        assertTrue(manifest.endsWith("#grayjoy-inline-manifest-request-id"))
    }

    @Test
    fun existingFragmentIsReplacedWithoutChangingMediaUrl() {
        val manifest = inlineManifestRequestUri(
            "https://media.example/path/video?token=abc#old-fragment",
            "request-id",
        )

        assertEquals(
            "https://media.example/path/video?token=abc#grayjoy-inline-manifest-request-id",
            manifest,
        )
    }

    @Test
    fun generatedDashAudioIsDetectedWithoutMatchingVideoOnlyManifest() {
        assertTrue(
            rawDashManifestContainsAudio(
                """<AdaptationSet mimeType="audio/mp4"><Representation /></AdaptationSet>""",
            ),
        )
        assertTrue(
            rawDashManifestContainsAudio(
                """<Representation mimeType = 'AUDIO/webm' />""",
            ),
        )
        assertFalse(
            rawDashManifestContainsAudio(
                """<AdaptationSet mimeType="video/mp4"><Representation /></AdaptationSet>""",
            ),
        )
    }

    @Test
    fun refreshedSignedUrlKeepsStableCacheIdentity() {
        assertEquals(
            "https://media.example/video/representation",
            stableCacheResourceUri(
                "https://media.example/video/representation?expire=1&signature=old",
            ),
        )
        assertEquals(
            stableCacheResourceUri("https://media.example/video?id=1&signature=old"),
            stableCacheResourceUri("https://media.example/video?signature=new&id=1"),
        )
    }

    @Test
    fun stableCacheIdentityPreservesInlineManifestFragment() {
        assertEquals(
            "https://media.example/video#grayjoy-inline-manifest-request-id",
            stableCacheResourceUri(
                "https://media.example/video?signature=old#grayjoy-inline-manifest-request-id",
            ),
        )
    }
}
