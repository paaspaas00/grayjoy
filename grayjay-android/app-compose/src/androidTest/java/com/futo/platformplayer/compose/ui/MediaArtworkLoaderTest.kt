package com.futo.platformplayer.compose.ui

import android.media.MediaMetadata
import android.media.session.MediaController
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.compose.images.MediaArtworkLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MediaArtworkLoaderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun platformSessionReceivesBoundedCoverBitmap() {
        val resource = "android.resource://${InstrumentationRegistry.getInstrumentation().context.packageName}/drawable/audit_earth"
        lateinit var player: ExoPlayer
        lateinit var session: MediaSession
        lateinit var controller: MediaController
        val loader = MediaArtworkLoader(rule.activity)
        try {
            rule.runOnUiThread {
                player = ExoPlayer.Builder(rule.activity).build()
                session = MediaSession.Builder(rule.activity, player).setId("artwork-test").setBitmapLoader(loader).build()
                controller = MediaController(rule.activity, session.platformToken)
                player.setMediaItem(MediaItem.Builder().setUri("https://media.example/fixture.mp4")
                    .setMediaId("fixture").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Cover fixture").setArtist("Creator").setArtworkUri(Uri.parse(resource)).build()).build())
            }
            rule.waitUntil(10_000) { controller.metadata?.description?.iconBitmap != null }
            val bitmap = controller.metadata!!.description.iconBitmap!!
            assertTrue(bitmap.width in 1..256 && bitmap.height in 1..256)
            assertEquals("Cover fixture", controller.metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE))
        } finally {
            rule.runOnUiThread { runCatching { session.release() }; runCatching { player.release() } }
            loader.close()
        }
    }
}
