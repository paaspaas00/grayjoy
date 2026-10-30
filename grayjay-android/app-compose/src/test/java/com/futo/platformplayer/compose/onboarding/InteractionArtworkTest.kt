package com.futo.platformplayer.compose.onboarding

import org.junit.Assert.*
import org.junit.Test

class InteractionArtworkTest {
    @Test fun normalizedLevelsSettleAndResetWithinBounds() {
        assertEquals(0f, setupNormalizationMix(0f), 0f)
        assertEquals(1f, setupNormalizationMix(.6f), 0f)
        assertEquals(0f, setupNormalizationMix(1f), 0f)
        for (p in (-100..1100).map { it / 1000f } + Float.NaN) {
            assertTrue(setupNormalizationMix(p) in 0f..1f)
            assertTrue(setupShortcutReveal(p) in 0f..1f)
        }
    }
    @Test fun autoplayAlternatesScenesWithoutAnArtworkJumpAtCycleBoundaries() {
        val previous = setupAutoplayFrame(.49999f)
        val next = setupAutoplayFrame(.5f)
        assertEquals(1f, previous.slide, .001f)
        assertEquals(1 - previous.scene, next.scene)
        assertEquals(0f, next.slide, .001f)
        assertEquals(setupAutoplayFrame(0f), setupAutoplayFrame(1f))
    }
    @Test fun shortcutMenuAppearsAfterLongPressAndReturnsBehindNavbar() {
        assertEquals(0f, setupShortcutReveal(.1f), 0f)
        assertEquals(1f, setupShortcutReveal(.6f), 0f)
        assertEquals(0f, setupShortcutReveal(1f), 0f)
    }
    @Test fun profilesSeparateAndLoopWithoutAnEndpointJump() {
        assertEquals(0f, setupProfileSeparation(0f), 0f)
        assertEquals(1f, setupProfileSeparation(.4f), 0f)
        assertEquals(0f, setupProfileSeparation(1f), 0f)
    }
    @Test fun insectReversesOnlyAtTheTrackEnds() {
        assertTrue(setupBugFrame(.2f).walking)
        assertEquals(0f, setupBugFrame(.2f).heading, 0f)
        assertFalse(setupBugFrame(.46f).walking)
        assertEquals(1f, setupBugFrame(.46f).position, 0f)
        assertEquals(180f, setupBugFrame(.7f).heading, 0f)
        assertEquals(0f, setupBugFrame(.96f).position, 0f)
        assertEquals(setupBugFrame(0f).position, setupBugFrame(1f).position, 0f)
    }
    @Test fun insectAndProfileGeometryStaysBoundedForAllFrames() {
        val phases = (-100..1100).map { it / 1000f } + listOf(Float.NaN, Float.POSITIVE_INFINITY)
        phases.forEach {
            assertTrue(setupProfileSeparation(it) in 0f..1f)
            val bug = setupBugFrame(it)
            assertTrue(bug.position in 0f..1f)
            assertTrue(bug.heading in 0f..360f)
        }
    }
    @Test fun controlsMirrorExactlyAcrossThePhone() {
        assertEquals(160f, setupHandControlX(true) + setupHandControlX(false), 0f)
        assertTrue(setupHandControlX(true) < 80f)
        assertTrue(setupHandControlX(false) > 80f)
    }
    @Test fun recognitionSequenceListensThenShowsAResultAndResetsContinuously() {
        assertEquals(0f, setupRecognitionReveal(.2f), 0f)
        assertEquals(1f, setupRecognitionReveal(.75f), .0001f)
        assertEquals(0f, setupRecognitionReveal(0f), 0f)
        assertEquals(0f, setupRecognitionReveal(1f), 0f)
        assertTrue(setupRecognitionReveal(.58f) in .1f.. .9f)
    }
    @Test fun malformedAndOutOfRangePhasesRemainSafe() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 2f)) {
            assertTrue(setupRecognitionReveal(value) in 0f..1f)
        }
    }
}
