package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import com.futo.platformplayer.backend.YoutubeSubscriptionFetchMode
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReportedYoutubePlaybackTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun reportedVideosPlayAndSeekWithBothBackends(): Unit = runBlocking {
        for (newPipe in listOf(true, false)) for (id in listOf("1BZB4c792-0", "u8oTH71vBeA")) {
            lateinit var engine: AndroidGrayjayEngine
            activity.scenario.onActivity { context ->
                engine = AndroidGrayjayEngine(context)
                engine.player.volume = 0f
                engine.configureYoutubeBackend(newPipe, YoutubeSubscriptionFetchMode.Fast)
            }
            try {
                val source = withTimeout(75_000) { engine.resolve(video(id), "it", true) }
                withContext(Dispatchers.Main) { engine.open(listOf(source), source.id, true); engine.setVideoQuality(2160) }
                ready(engine)
                for (position in listOf(60_000L, 360_000L, 660_000L)) {
                    withContext(Dispatchers.Main) { engine.player.seekTo(position) }
                    ready(engine)
                    val before = withContext(Dispatchers.Main) { engine.player.currentPosition }
                    delay(3_000)
                    val after = withContext(Dispatchers.Main) { engine.player.currentPosition }
                    assertTrue("No progress for $id backend=$newPipe at $position: $before->$after", after > before + 1_000)
                    android.util.Log.i("ReportedPlaybackTest", "id=$id newPipe=$newPipe seek=$position progress=${after-before}ms")
                }
            } finally { withContext(Dispatchers.Main) { engine.release() } }
        }
    }

    @Test fun transportFailureStartsFallbackPromptlyAndPreservesSeek(): Unit = runBlocking {
        lateinit var engine: AndroidGrayjayEngine
        activity.scenario.onActivity { context -> engine = AndroidGrayjayEngine(context); engine.player.volume = 0f }
        val server = ServerSocket(0).apply { soTimeout = 20_000 }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val source = withTimeout(75_000) { engine.resolve(video("u8oTH71vBeA"), "it", true) }
            withContext(Dispatchers.Main) { engine.open(listOf(source), source.id, true) }
            ready(engine)
            executor.submit {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n".toByteArray())
                    socket.getOutputStream().flush()
                }
            }
            val fallback = async(start = CoroutineStart.UNDISPATCHED) { engine.backendNotices.first { it.operation == "playback" } }
            val started = android.os.SystemClock.elapsedRealtime()
            withContext(Dispatchers.Main) {
                engine.replaceCurrent(source.copy(playbackUrl = "http://127.0.0.1:${server.localPort}/broken.wav",
                    playbackMimeType = MimeTypes.AUDIO_WAV, playbackManifest = "", audioUrl = "",
                    playbackDataSourceFactory = DefaultHttpDataSource.Factory().setReadTimeoutMs(1000),
                    qualityVariants = emptyList(), audioQualityVariants = emptyList(), subtitleTracks = emptyList()), 120_000L, true)
            }
            withTimeout(5_000) { fallback.await() }
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            assertTrue("Fallback startup was delayed: $elapsed ms", elapsed < 3_000)
            ready(engine)
            assertTrue(withContext(Dispatchers.Main) { engine.player.currentPosition >= 120_000L })
            android.util.Log.i("ReportedPlaybackTest", "fallback-start=${elapsed}ms, resumed=${android.os.SystemClock.elapsedRealtime()-started}ms, position-preserved=true")
        } finally {
            server.close(); executor.shutdownNow()
            withContext(Dispatchers.Main) { engine.release() }
        }
    }

    private suspend fun ready(engine: AndroidGrayjayEngine) = withTimeout(45_000) {
        while (!withContext(Dispatchers.Main) { engine.player.playbackState == Player.STATE_READY && engine.player.isPlaying }) {
            engine.playback.value.errorMessage?.let { error(it) }
            delay(100)
        }
    }
    private fun video(id: String): VideoUiModel {
        val url = "https://www.youtube.com/watch?v=$id"
        return VideoUiModel(url, "Playback verification", "", "", "", sourceId = "youtube", contentUrl = url, shareUrl = url)
    }
}
