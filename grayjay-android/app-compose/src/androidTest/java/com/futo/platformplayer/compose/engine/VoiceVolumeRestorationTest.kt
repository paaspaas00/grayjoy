package com.futo.platformplayer.compose.engine

import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceVolumeRestorationTest {
    @Test fun endingListeningRestoresTheActualPlayerAndStaleDisconnectDoesNotLowerIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: ExoPlayer
        lateinit var controller: OtherAudioDuckingController
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(instrumentation.targetContext).build()
            controller = OtherAudioDuckingController(instrumentation.targetContext, player)
            controller.setVoiceVolume("listening", 50)
        }
        try {
            android.os.SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                assertEquals(.5f, player.volume, .001f)
                controller.setVoiceVolume("listening", null)
            }
            android.os.SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                assertEquals(1f, player.volume, .001f)
                controller.setVoiceVolume("listening", null)
                assertEquals(1f, player.volume, .001f)
            }
        } finally { instrumentation.runOnMainSync { controller.release(); player.release() } }
    }
}
