package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.futo.platformplayer.compose.ui.screens.PlayerViewTargets
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PlayerViewTargetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun repeatedPipHandoffsDetachOldTargetAndIgnoreItsLateRelease() {
        rule.runOnUiThread {
            val player = ExoPlayer.Builder(rule.activity).build()
            val normal = PlayerView(rule.activity)
            val pip = PlayerView(rule.activity)
            try {
                repeat(10) {
                    PlayerViewTargets.attach(normal, player)
                    assertSame(player, normal.player)
                    assertNull(pip.player)
                    PlayerViewTargets.attach(pip, player)
                    assertNull(normal.player)
                    assertSame(player, pip.player)
                    PlayerViewTargets.detach(normal)
                    assertSame(player, pip.player)
                    PlayerViewTargets.attach(normal, player)
                    assertNull(pip.player)
                    PlayerViewTargets.detach(pip)
                    assertSame(player, normal.player)
                }
            } finally {
                PlayerViewTargets.detach(normal)
                PlayerViewTargets.detach(pip)
                player.release()
            }
        }
    }
}
