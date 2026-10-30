package com.futo.platformplayer.compose.engine

import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.playback.LoudnessNormalizationAudioProcessor
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

class AudioNormalizationTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun disabledProcessorIsInactiveBitExactAndAllocatesNoDsp() {
        val processor = LoudnessNormalizationAudioProcessor()
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(AudioFormat.NOT_SET, processor.configure(format))
        processor.flush()
        assertFalse(processor.isActive)
        assertFalse(processor.isDspAllocated)
        processor.enabled = true
        processor.configure(format)
        processor.flush()
        val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        repeat(2048) { input.putShort((it % 32000).toShort()) }; input.flip()
        processor.queueInput(input)
        assertTrue(processor.isActive)
        assertTrue(processor.isDspAllocated)
        assertEquals(4096, processor.output.remaining())
        processor.enabled = false
        val bytes = ByteArray(4096) { it.toByte() }
        processor.queueInput(ByteBuffer.wrap(bytes)) // An old in-flight buffer must also bypass DSP.
        val actual = ByteArray(bytes.size); processor.output.get(actual)
        assertArrayEquals(bytes, actual)
        assertFalse(processor.isDspAllocated)
        assertEquals(AudioFormat.NOT_SET, processor.configure(format))
        processor.flush()
        assertFalse(processor.isActive)
        assertFalse(processor.isDspAllocated)
        processor.reset()
    }

    @Test fun preferenceDefaultsOffAndIsPersistedPerProfile() {
        activity.scenario.onActivity { context ->
            val id = "normalization-test-${UUID.randomUUID()}"
            try {
                val preferences = GrayjayPreferences(context, id)
                assertFalse(preferences.audioNormalizationEnabled)
                assertEquals(-32, preferences.audioNormalizationTargetLufs)
                preferences.audioNormalizationTargetLufs = -14
                assertEquals(-14, GrayjayPreferences(context, id).audioNormalizationTargetLufs)
                preferences.audioNormalizationTargetLufs = 999
                assertEquals(-14, GrayjayPreferences(context, id).audioNormalizationTargetLufs)
                preferences.audioNormalizationEnabled = true
                assertTrue(GrayjayPreferences(context, id).audioNormalizationEnabled)
                preferences.audioNormalizationEnabled = false
                assertFalse(GrayjayPreferences(context, id).audioNormalizationEnabled)
            } finally { context.deleteSharedPreferences("grayjay_compose_preferences_$id") }
        }
    }

    @Test fun floatPcmAndUnsupportedFormatChangesCannotLeaveActiveDspOrInvalidSamples() {
        val processor = LoudnessNormalizationAudioProcessor()
        processor.enabled = true
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
        for (sample in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -3f, 3f, 0f, 0f, .25f, -.25f)) input.putFloat(sample)
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(32, output.remaining())
        while (output.hasRemaining()) {
            val sample = output.float
            assertTrue(sample.isFinite() && kotlin.math.abs(sample) <= .795f)
        }
        assertTrue(processor.isDspAllocated)
        processor.flush() // A same-format seek keeps the accumulated recording gain.
        assertTrue(processor.isDspAllocated)
        assertEquals(AudioFormat.NOT_SET, processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_8BIT)))
        processor.flush()
        assertFalse(processor.isActive)
        assertFalse(processor.isDspAllocated)
        processor.reset()
    }

    @Test fun changingNormalizationReconfiguresPcmWithoutLosingQueuePositionOrPause() {
        lateinit var engine: AndroidGrayjayEngine
        lateinit var file: File
        lateinit var processor: LoudnessNormalizationAudioProcessor
        activity.scenario.onActivity { context ->
            val bytes = 16_000 * 2 * 30
            val wave = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
            wave.put("RIFF".toByteArray()).putInt(bytes + 36).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(bytes)
            repeat(bytes / 2) { wave.putShort((8000 * sin(2 * PI * 440 * it / 16_000)).toInt().toShort()) }
            file = File.createTempFile("normalization-", ".wav", context.cacheDir).apply { writeBytes(wave.array()) }
            engine = AndroidGrayjayEngine(context)
            engine.player.volume = 0f
            processor = AndroidGrayjayEngine::class.java.getDeclaredField("loudnessProcessor").let {
                it.isAccessible = true; it.get(engine) as LoudnessNormalizationAudioProcessor
            }
            val video = VideoUiModel("normalization-fixture", "Fixture", "Fixture", "", "0:30",
                sourceId = "fixture", playbackUrl = file.toURI().toString(), playbackMimeType = "audio/wav")
            engine.open(listOf(video, video.copy(id = "normalization-next")), video.id, true)
        }
        try {
            waitUntil { engine.player.playbackState == Player.STATE_READY && engine.player.isPlaying }
            activity.scenario.onActivity {
                assertFalse(processor.isActive)
                assertFalse(processor.isDspAllocated)
                engine.pausePlayback()
                engine.player.seekTo(5_000)
                engine.setAudioNormalizationEnabled(true)
            }
            waitUntil { engine.player.playbackState == Player.STATE_READY && processor.isActive }
            activity.scenario.onActivity {
                assertEquals(2, engine.player.mediaItemCount)
                assertEquals("normalization-fixture", engine.player.currentMediaItem?.mediaId)
                assertEquals(5_000.0, engine.player.currentPosition.toDouble(), 100.0)
                assertFalse(engine.player.playWhenReady)
                engine.resumePlayback()
            }
            waitUntil { processor.isDspAllocated && engine.player.isPlaying }
            activity.scenario.onActivity { engine.pausePlayback(); engine.setAudioNormalizationEnabled(false) }
            waitUntil { engine.player.playbackState == Player.STATE_READY && !processor.isActive }
            activity.scenario.onActivity {
                assertFalse(processor.isDspAllocated)
                assertFalse(engine.player.playWhenReady)
                assertEquals(2, engine.player.mediaItemCount)
                assertNull(engine.player.playerError)
            }
        } finally { activity.scenario.onActivity { engine.release(); file.delete() } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        var passed = false
        while (!passed && android.os.SystemClock.elapsedRealtime() < deadline) {
            activity.scenario.onActivity { passed = condition() }
            if (!passed) Thread.sleep(50)
        }
        assertTrue("Audio pipeline did not reach the expected state", passed)
    }
}
