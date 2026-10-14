package com.futo.platformplayer.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecord
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VoiceArtistAndMicrophoneTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun selectedModelUnderstandsArtistAndReferences() {
        val modelId = VoiceModels.selectedLlm(context.getSharedPreferences("voice", 0))
        val llm = NativeLlm()
        try {
            llm.load(File(VoiceModels.directory(context, modelId), "model.gguf").path)
            llm.prime(commandPrefix("", modelId), 4)
            for ((phrase, expected) in listOf(
                "Metti su qualcosa dei Queen" to VoiceDecision("play_artist", "Queen"),
                "Riproduci qualcosa di Elisa" to VoiceDecision("play_artist", "Elisa"),
                "Riproduci qualcos'altro sempre loro" to VoiceDecision("play_same_artist"),
                "Qualcos'altro sempre suo" to VoiceDecision("play_same_artist"),
                "Mi andrebbe di ascoltare una canzone dei Radiohead" to VoiceDecision("play_artist", "Radiohead"))) {
                val result = VoiceDecision.parse(llm.interpret(commandPrompt(phrase, "", modelId = modelId), COMMAND_GRAMMAR, 4))
                val checked = validateVoiceDecision(result, phrase, false)
                android.util.Log.i("VoiceArtistTest", "expected=${expected.tool} actual=${checked.tool}")
                assertEquals(phrase, expected.tool, checked.tool)
                if (expected.argument.isNotBlank()) assertEquals(answerText(expected.argument), answerText(checked.argument))
            }
        } finally { llm.unload() }
    }

    @Test fun callTunedMicrophoneDoesNotChangeThePhoneAudioMode() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            error("Microphone permission is required for this device test")
        ActivityScenario.launch(VoiceSettingsActivity::class.java).use {
            val audio = context.getSystemService(AudioManager::class.java)
            val beforeMode = audio.mode
            val beforeVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            VoiceMicrophone.open().use { mic ->
                mic.record.startRecording()
                val buffer = ShortArray(1600)
                val count = mic.record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                assertTrue("Capture must supply audio frames", count > 0)
                assertEquals(beforeMode, audio.mode)
                assertEquals(beforeVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                android.util.Log.i("VoiceArtistTest", "microphone source=${mic.source} AGC=${mic.gainControl} NS=${mic.noiseSuppression} AEC=${mic.echoCancellation} wide=${mic.wideField} mode=${audio.mode}")
            }
            assertEquals(beforeMode, audio.mode)
        }
    }

    @Test fun artistPlaybackAndSameArtistUseDifferentTracksOverMcp(): Unit = runBlocking {
        val client = GrayjoyMcpClient(context)
        ActivityScenario.launch(VoiceSettingsActivity::class.java).use { activity ->
            try {
                activity.onActivity { it.startForegroundService(Intent(it, VoiceListeningService::class.java)) }
                withTimeout(30_000) { while (!VoiceListeningService.listeningStatus.value.startsWith("In ascolto")) delay(100) }
                client.connect()
                android.util.Log.i("VoiceArtistTest", "MCP connected")
                val first = client.call("play_artist", JSONObject().put("query", "Queen"))
                android.util.Log.i("VoiceArtistTest", "First artist request completed")
                assertTrue(first.toString(), first.optBoolean("ok"))
                val second = client.call("play_same_artist")
                android.util.Log.i("VoiceArtistTest", "Second artist request completed")
                assertTrue(second.toString(), second.optBoolean("ok"))
                assertEquals("Queen", second.getString("artist"))
                assertNotEquals(first.getString("videoId"), second.getString("videoId"))
                android.util.Log.i("VoiceArtistTest", "Artist MCP selected distinct recordings")
                Unit
            } finally {
                runCatching { client.call("pause") }; client.close()
                context.stopService(Intent(context, VoiceListeningService::class.java))
            }
        }
    }
}
