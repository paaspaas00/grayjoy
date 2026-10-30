package com.futo.platformplayer.compose.recognition

import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RecognitionTransitionTest {
    @Test fun morphBoundsInterpolateWithoutOvershootOrAspectStretchAtTheEndpoints() {
        val source = androidx.compose.ui.geometry.Rect(100f, 500f, 260f, 660f)
        val target = androidx.compose.ui.geometry.Rect(20f, 100f, 380f, 460f)
        assertEquals(source, recognitionMorphRect(source, target, 0f))
        assertEquals(target, recognitionMorphRect(source, target, 1f))
        var previousWidth = source.width
        repeat(101) {
            val bounds = recognitionMorphRect(source, target, it / 100f)
            assertTrue(bounds.width >= previousWidth && bounds.width <= target.width)
            assertEquals(bounds.width, bounds.height, .01f)
            previousWidth = bounds.width
        }
    }
    @Test fun motionRequiresBothMeasuredDestinationsAndFreezesThemAtLaunch() = runBlocking {
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined), { video }, { _, _ -> })
        controller.prepare(song)
        val source = RecognitionMorphNode(androidx.compose.ui.geometry.Rect(0f, 300f, 400f, 800f))
        val target = RecognitionMorphNode(androidx.compose.ui.geometry.Rect(0f, 0f, 400f, 900f))
        controller.capture("surface", source, true); controller.capture("art", source, true)
        controller.openResult(false)
        controller.capture("surface", target, false)
        assertFalse(controller.beginMotion())
        controller.capture("art", target, false)
        assertTrue(controller.beginMotion())
        controller.capture("art", source, false)
        assertEquals(target, controller.morph!!.target["art"])
    }
    private val song = RecognizedSong("Track", "Artist", "")
    private val video = VideoUiModel("recognized-fixture", "Track", "Artist", "", "3:00")
    @Test fun theKnownCoverRemainsAnArtworkFallbackAfterPreviewCleanup() = runBlocking {
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined), { video }, { _, _ -> })
        controller.prepare(song.copy(artwork = "https://example.test/known-cover.jpg"))
        controller.openResult(false); controller.complete(); controller.reset()
        assertEquals(video.id to "https://example.test/known-cover.jpg", controller.artworkFallback)
    }
    @Test fun preparationDoesNotPlayAndSwipeCommitsExactlyOnceInPause() = runBlocking {
        val opened = mutableListOf<Pair<String, Boolean>>(); var lookups = 0
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { lookups++; video }, { id, play -> opened.add(id to play) })
        controller.prepare(song); controller.prepare(song)
        assertEquals(1, lookups); assertTrue(opened.isEmpty())
        controller.openResult(false); controller.openResult(true)
        assertEquals(listOf(video.id to false), opened)
        assertEquals(RecognitionOpenPhase.Opening, controller.phase)
    }
    @Test fun thePlayButtonStillStartsPlayback() = runBlocking {
        var play: Boolean? = null
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { video }, { _, start -> play = start })
        controller.prepare(song); controller.openResult(true)
        assertEquals(true, play)
    }
    @Test fun dismissalCancelsPendingResolutionWithoutOpeningLate() = runBlocking {
        val result = CompletableDeferred<VideoUiModel>(); var opens = 0
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { result.await() }, { _, _ -> opens++ })
        controller.prepare(song); controller.openResult(false)
        assertTrue(controller.requested)
        controller.reset(); result.complete(video); yield()
        assertEquals(0, opens); assertNull(controller.video); assertFalse(controller.requested)
    }
    @Test fun completionAfterTheWindowClosesCannotCommit() = runBlocking {
        val result = CompletableDeferred<VideoUiModel>(); var visible = true; var opens = 0
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { result.await() }, { _, _ -> opens++ }, allowed = { visible })
        controller.prepare(song); controller.openResult(false); visible = false
        result.complete(video); yield()
        assertEquals(0, opens); assertFalse(controller.requested)
    }
    @Test fun failedPreloadIsSilentAndAnExplicitRetryIsReportedOnce() = runBlocking {
        var attempts = 0
        val controller = RecognitionTransitionController(CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { attempts++; throw java.io.IOException("fixture failure") }, { _, _ -> fail() }, report = { _, _ -> })
        controller.prepare(song); assertEquals(0, controller.failures)
        controller.openResult(false)
        assertEquals(2, attempts); assertEquals(1, controller.failures); assertFalse(controller.requested)
    }
}
