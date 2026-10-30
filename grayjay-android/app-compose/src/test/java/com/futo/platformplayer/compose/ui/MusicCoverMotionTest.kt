package com.futo.platformplayer.compose.ui

import com.futo.platformplayer.compose.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class MusicCoverMotionTest {
    private val queue = listOf("a", "b", "c")
    @Test fun queueOrderDeterminesBothDirectionsIncludingExternalControls() {
        assertEquals(1, musicCoverDirection("a", "b", queue, queue))
        assertEquals(-1, musicCoverDirection("c", "b", queue, queue))
        assertEquals(0, musicCoverDirection("b", "b", queue, queue))
    }
    @Test fun aRollingQueueDoesNotForgetThePreviousOrderingBeforeTheItemChanges() {
        val motion = MusicCoverMotion("b", queue)
        motion.frame("b", "cover-b", null, listOf("a"))
        assertEquals(-1, motion.frame("a", "cover-a", null, listOf("a")).direction)
    }
    @Test fun anExplicitTargetRetainsDirectionAcrossQueueReordering() {
        val motion = MusicCoverMotion("b", queue)
        motion.request("a", -1)
        assertEquals(-1, motion.frame("a", "", null, listOf("b", "a", "c")).direction)
    }
    @Test fun restartingTheSameTrackDoesNotLeakABackwardIntentIntoAutoplay() {
        val motion = MusicCoverMotion("b", queue)
        motion.request("a", -1)
        assertEquals("b", motion.frame("b", "cover", null, queue).id)
        assertEquals(1, motion.frame("c", "next", null, queue).direction)
    }
    @Test fun metadataUpdatesKeepTheSameAnimationKeyButTracksSharingACoverRemainDistinct() {
        val motion = MusicCoverMotion("a", queue)
        val a = motion.frame("a", "same-cover", null, queue)
        val metadata = motion.frame("a", "better-cover", null, queue)
        assertEquals(a.id, metadata.id)
        val b = motion.frame("b", "same-cover", null, queue)
        assertEquals(a.url, b.url)
        assertNotEquals(a.id, b.id)
    }
    @Test fun anOldRestartIntentCannotReverseALaterReorderedQueueAdvance() {
        var now = 0L
        val motion = MusicCoverMotion("b", queue) { now }
        motion.request("a", -1)
        motion.frame("b", "", null, listOf("b", "a"))
        now = 60_000L
        assertEquals(1, motion.frame("a", "", null, listOf("b", "a")).direction)
    }
}
