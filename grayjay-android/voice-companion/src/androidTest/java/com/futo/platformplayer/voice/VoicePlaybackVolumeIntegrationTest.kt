package com.futo.platformplayer.voice

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VoicePlaybackVolumeIntegrationTest {
    @Test fun playlistStartsAndChangesRestoreVolumeEvenWhileSpokenFeedbackIsRunning() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = GrayjoyMcpClient(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tools = client.tools()
        val runtime = OfflineVoiceRuntime(context, scope, tools)
        try {
            val playlists = client.call("list_playlists").getJSONArray("playlists")
            val names = (0 until playlists.length()).map { playlists.getJSONObject(it).getString("title") }
            val preferred = listOf("songs", "movin").mapNotNull { expected -> names.firstOrNull { it.equals(expected, true) } }
            val selected = (preferred + names).distinct().take(2)
            org.junit.Assume.assumeTrue("Requires two local test playlists", selected.size == 2)
            for (command in selected.map { "riproduci la playlist $it" } + "play") {
                val dispatched = CompletableDeferred<String>()
                val monitored = object : VoiceCommandClient {
                    override suspend fun call(name: String, arguments: JSONObject): JSONObject {
                        val result = client.call(name, arguments)
                        if (name in setOf("play_playlist", "play_named", "play") && result.optBoolean("ok")) dispatched.complete(name)
                        return result
                    }
                }
                client.call("voice_volume", JSONObject().put("percent", 50))
                delay(250)
                assertEquals(.5, client.call("status").getDouble("playerVolume"), .001)
                val job = scope.async { runtime.handle(FloatArray(0), monitored, tools, {}, command) }
                val tool = withTimeout(60_000L) { dispatched.await() }
                repeat(6) {
                    delay(250)
                    val status = client.call("status")
                    assertEquals("Volume remains attenuated after $tool", 1.0, status.getDouble("playerVolume"), .001)
                }
                withTimeout(90_000L) { job.await() }
                assertFalse("A successful playback command must not leave a follow-up open", runtime.needsFollowup)
                delay(1_200L)
                assertEquals("Spoken feedback must not reapply attenuation", 1.0, client.call("status").getDouble("playerVolume"), .001)
                android.util.Log.i("VoiceVolumeTest", "tool=$tool listening=0.5 after-dispatch=1.0 after-feedback=1.0")
            }
        } finally {
            runCatching { client.call("voice_volume", JSONObject().put("percent", -1)) }
            runCatching { client.call("pause") }
            runtime.releaseHeavyModels()
            scope.cancel()
            client.close()
        }
    }

    @Test fun startingPlaybackReleasesTheAuthenticatedListeningAttenuation() = runBlocking {
        val client = GrayjoyMcpClient(InstrumentationRegistry.getInstrumentation().targetContext)
        var wasPlaying = false
        try {
            wasPlaying = client.call("status").optBoolean("playing")
            client.call("voice_volume", JSONObject().put("percent", 50))
            delay(250)
            assertEquals(.5, client.call("status").getDouble("playerVolume"), .001)
            assertTrue(client.call("play").optBoolean("ok"))
            delay(250)
            assertEquals(1.0, client.call("status").getDouble("playerVolume"), .001)
        } finally {
            runCatching { client.call("voice_volume", JSONObject().put("percent", -1)) }
            if (!wasPlaying) runCatching { client.call("pause") }
            client.close()
        }
    }
}
