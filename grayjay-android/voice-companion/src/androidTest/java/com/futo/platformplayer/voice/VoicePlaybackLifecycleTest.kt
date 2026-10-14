package com.futo.platformplayer.voice

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoicePlaybackLifecycleTest {
    @Test fun localPlaylistStartedInBackgroundUsesTheVisiblePlaybackSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = GrayjoyMcpClient(context)
        if (!VoiceModels.installed(context, "vad")) VoiceModels.request(context, "vad")
        withTimeout(60_000) { while (!VoiceModels.installed(context, "vad")) delay(200) }
        val activity = ActivityScenario.launch(VoiceSettingsActivity::class.java)
        try {
            activity.onActivity { it.startForegroundService(Intent(it, VoiceListeningService::class.java)) }
            withTimeout(30_000) { while (!VoiceListeningService.listeningStatus.value.startsWith("In ascolto")) delay(100) }
            activity.onActivity { it.moveTaskToBack(true) }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 3").close()
            delay(20_000) // Expire the Activity's temporary foreground-start exemption.
            client.connect()
            val playlists = client.call("list_playlists").getJSONArray("playlists")
            val chosen = (0 until playlists.length()).map { playlists.getJSONObject(it) }
                .firstOrNull { it.getString("title").equals("songs", true) }
                ?: error("The isolated test library needs its Songs playlist")
            val response = client.call("play_playlist", org.json.JSONObject().put("query", chosen.getString("id")))
            assertTrue(response.toString(), response.optBoolean("ok"))
            val started = withTimeout(45_000) {
                var state = client.call("status")
                while (!state.optBoolean("playing")) { delay(250); state = client.call("status") }
                state
            }
            android.util.Log.i("VoicePlaybackTest", "Background playback: $started")
            assertTrue(started.getString("title").isNotBlank())
            assertEquals(started.getString("currentVideoId"), started.getString("nowPlayingId"))
            assertEquals(started.getString("title"), started.getString("mediaTitle"))
            assertTrue("Playback must have its foreground notification", started.getBoolean("notificationActive"))
            delay(1500)
        } finally {
            runCatching { client.call("pause") }
            client.close()
            context.stopService(Intent(context, VoiceListeningService::class.java))
            activity.close()
        }
    }
}
