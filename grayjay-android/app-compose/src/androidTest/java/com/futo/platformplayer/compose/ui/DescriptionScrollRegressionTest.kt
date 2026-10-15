package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.theme.GrayjayTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DescriptionScrollRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var player: ExoPlayer
    @After fun release() { if (::player.isInitialized) rule.runOnUiThread { player.release() } }

    @Test fun everyPartOfTheCollapsedAndExpandedDescriptionScrollsThePage() {
        rule.runOnUiThread { player = ExoPlayer.Builder(rule.activity).build() }
        val video = VideoUiModel("description-fixture", "Title", "Channel", "", "1:00",
            description = "A sufficiently long description with multiple lines and links as ordinary text. ".repeat(80))
        val state = mutableStateOf(GrayjayUiState(
            nowPlaying = NowPlayingUiState(video = video, recommendations = (0..20).map { video.copy(id = "next-$it", description = "") }),
            videos = listOf(video), playback = PlaybackUiState(currentVideoId = video.id),
            externalNavigation = ExternalNavigationUiModel(1, ExternalNavigationKind.Video, video.id),
        ))
        val actions = GrayjayAppActions().apply {
            onExternalNavigationHandled = { state.value = state.value.copy(externalNavigation = null) }
        }
        rule.setContent { GrayjayTheme(dynamicColor = false) { GrayjayApp(state.value, player, actions) } }
        val list = rule.onNodeWithTag("video-detail-list")
        val description = rule.onNodeWithTag("video-description-card")
        fun position() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        for (expanded in listOf(false, true)) {
            if (expanded) {
                list.performScrollToNode(hasText(rule.activity.getString(R.string.show_more)))
                rule.onNodeWithText(rule.activity.getString(R.string.show_more)).performClick()
            }
            for (xFraction in listOf(.04f, .5f, .96f)) for (yFraction in listOf(.07f, .5f, .93f)) {
                list.performScrollToIndex(3)
                rule.waitForIdle()
                val viewport = list.fetchSemanticsNode().boundsInRoot
                val card = description.fetchSemanticsNode().boundsInRoot
                val left = maxOf(viewport.left, card.left)
                val right = minOf(viewport.right, card.right)
                val top = maxOf(viewport.top, card.top)
                val bottom = minOf(viewport.bottom, card.bottom)
                val start = Offset(left + (right-left)*xFraction - viewport.left,
                    top + (bottom-top)*yFraction - viewport.top)
                val distance = if (start.y < 150f) 120f else -120f
                val before = position()
                list.performTouchInput { swipe(start, start + Offset(0f, distance), durationMillis = 450) }
                rule.waitForIdle()
                assertTrue("Dead scroll area expanded=$expanded x=$xFraction y=$yFraction",
                    kotlin.math.abs(position() - before) > 10f)
            }
        }
    }
}
