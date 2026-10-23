package com.futo.platformplayer.compose.onboarding

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HelpGuideTest {
    @Test fun keyboardInsetIsNotAppliedTwiceToAResizedPopupWindow() {
        assertEquals(0, remainingHelpInset(2400, 1400, 1000))
        assertEquals(1000, remainingHelpInset(2400, 2400, 1000))
        assertEquals(50, remainingHelpInset(2400, 1450, 1000))
    }
    @Test fun startsFromFirstStepAndStopsWithoutRunningAnyAppAction() {
        val guide = HelpGuideState()
        guide.start(HelpTopic.Album)
        guide.next()
        guide.start(HelpTopic.Music)
        assertEquals(0, guide.step)
        repeat(HelpTopic.Music.steps.size) { guide.next() }
        assertNull(guide.topic)
    }
    @Test fun hiddenAndDisposedTargetsAreNotKept() {
        val guide = HelpGuideState()
        val owner = Any()
        guide.register(owner, "search", Rect(0f, 40f, 300f, 90f))
        assertNotNull(guide.bounds("search"))
        guide.register(owner, "search", Rect.Zero)
        assertNull(guide.bounds("search"))
        guide.register(owner, "search", Rect(0f, 40f, 300f, 90f))
        guide.remove(owner)
        assertNull(guide.bounds("search"))
    }
    @Test fun bubbleAvoidsTargetWhenThereIsSpace() {
        val target = Rect(10f, 700f, 300f, 760f)
        val position = helpBubblePosition(target, IntSize(400, 900), IntSize(320, 200), 24, 32, 12)
        assertTrue(position.y + 200 < target.top)
    }
    @Test fun searchTipLeavesTheResultTypeTabsUncovered() {
        val field = Rect(10f, 80f, 380f, 160f)
        val tabs = Rect(10f, 190f, 380f, 245f)
        val position = helpBubblePosition(field, IntSize(400, 900), IntSize(320, 260), 24, 32, 12, tabs)
        assertTrue(position.y >= tabs.bottom)
    }
    @Test fun bubbleNeverEscapesUsableWindowAcrossSizesAndKeyboardInsets() {
        val random = Random(836)
        repeat(2000) {
            val width = random.nextInt(280, 1601)
            val height = random.nextInt(500, 2601)
            val top = random.nextInt(0, 80)
            val bottom = random.nextInt(0, height / 2)
            val size = IntSize(random.nextInt(180, width - 23), random.nextInt(80, minOf(280, height - top - bottom - 24)))
            val y = random.nextInt(top, height - bottom - 30).toFloat()
            val target = Rect(20f, y, width - 20f, y + 30f)
            val position = helpBubblePosition(target, IntSize(width, height), size, top, bottom, 12)
            assertTrue(position.x >= 0 && position.x + size.width <= width)
            assertTrue(position.y >= top && position.y + size.height <= height - bottom)
        }
    }
}
