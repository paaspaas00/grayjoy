package com.futo.platformplayer.compose.engine

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.backend.YoutubeSubscriptionFetchMode
import com.futo.platformplayer.compose.data.ProfileRepository
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress

/** Opt-in network diagnostic. Does not change accounts, VPN, system volume or the user's library. */
class CurrentNetworkPlaybackProbe {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    @Test fun playbackOnTheCurrentlySelectedAndroidNetwork(): Unit = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONObject()
        val dns = JSONArray()
        for (host in listOf("www.youtube.com", "youtubei.googleapis.com")) {
            val started = SystemClock.elapsedRealtime()
            val status = runCatching { withContext(Dispatchers.IO) { InetAddress.getAllByName(host).isNotEmpty() } }
            dns.put(JSONObject().put("host", host).put("ok", status.getOrDefault(false))
                .put("ms", SystemClock.elapsedRealtime() - started).put("error", status.exceptionOrNull()?.javaClass?.simpleName.orEmpty()))
        }
        report.put("dns", dns)
        val trials = JSONArray()
        for (newPipe in listOf(true, false)) {
            lateinit var engine: AndroidGrayjayEngine
            activity.scenario.onActivity {
                engine = AndroidGrayjayEngine(it)
                engine.player.volume = 0f
                engine.setProfile(ProfileRepository(context).activeProfileId())
                engine.configureYoutubeBackend(newPipe, YoutubeSubscriptionFetchMode.Fast)
            }
            val trial = JSONObject().put("preferNewPipe", newPipe)
            val positions = JSONArray()
            trial.put("progress", positions)
            val started = SystemClock.elapsedRealtime()
            try {
                val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                val source = withTimeout(75_000) { engine.resolve(
                    VideoUiModel(url, "Network playback verification", "", "", "", sourceId = "youtube", contentUrl = url, shareUrl = url),
                    "it", true) }
                trial.put("resolvedMs", SystemClock.elapsedRealtime() - started)
                val resolvers = AndroidGrayjayEngine::class.java.getDeclaredField("youtubeResolverByVideoId").apply { isAccessible = true }
                trial.put("resolver", (resolvers.get(engine) as Map<*, *>)[source.id]?.toString().orEmpty())
                withContext(Dispatchers.Main) { engine.open(listOf(source), source.id, true); engine.setVideoQuality(720) }
                for (seek in listOf(0L, 90_000L, 150_000L)) {
                    trial.put("requestedSeek", seek)
                    if (seek > 0L) withContext(Dispatchers.Main) { engine.player.seekTo(seek) }
                    withTimeout(35_000) {
                        while (!withContext(Dispatchers.Main) { engine.player.isPlaying }) {
                            engine.playback.value.errorMessage?.let { error(it) }
                            delay(100)
                        }
                    }
                    val before = withContext(Dispatchers.Main) { engine.player.currentPosition }
                    val progressStarted = SystemClock.elapsedRealtime()
                    val after = withTimeout(20_000) {
                        delay(5000)
                        while (withContext(Dispatchers.Main) { engine.player.currentPosition } - before <= 2000) {
                            engine.playback.value.errorMessage?.let { error(it) }
                            delay(100)
                        }
                        withContext(Dispatchers.Main) { engine.player.currentPosition }
                    }
                    positions.put(JSONObject().put("seek", seek).put("progressMs", after - before)
                        .put("elapsedMs", SystemClock.elapsedRealtime() - progressStarted))
                }
                trial.put("ok", true).put("progress", positions)
            } catch (error: Exception) {
                trial.put("ok", false).put("error", error.javaClass.simpleName)
                    .put("kind", com.futo.platformplayer.compose.errors.classifyBackendFailure(error, "youtube").name)
                    .put("detail", com.futo.platformplayer.compose.diagnostics.privateExceptionMessages(error).take(1000))
            } finally {
                trial.put("totalMs", SystemClock.elapsedRealtime() - started)
                withContext(Dispatchers.Main) { engine.release() }
                trials.put(trial)
            }
        }
        report.put("trials", trials)
        File(context.getExternalFilesDir(null), "current-network-playback-probe.json").writeText(report.toString(2))
    }
}
