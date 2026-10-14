package com.futo.platformplayer.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** Runs only after activation. Road noise must not become an utterance merely because it is loud. */
internal class CommandSpeechDetector(context: Context) : AutoCloseable {
    private val modelPath = File(VoiceModels.directory(context, "vad"), "silero_vad.onnx").path
    private fun create(shortReply: Boolean) = Vad(config = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = modelPath,
            threshold = if (shortReply) .42f else .5f, minSpeechDuration = if (shortReply) .06f else .1f,
            minSilenceDuration = .1f, maxSpeechDuration = 16f),
        sampleRate = 16_000, numThreads = 1))
    private val commands = create(false)
    private val replies = create(true)
    private var detector = commands
    var heardSpeech = false; private set
    fun accept(samples: FloatArray): Boolean {
        detector.acceptWaveform(samples)
        val speech = detector.isSpeechDetected()
        heardSpeech = heardSpeech || speech || !detector.empty()
        while (!detector.empty()) detector.pop()
        return speech
    }
    fun reset(shortReply: Boolean = false) {
        detector.reset()
        detector = if (shortReply) replies else commands
        detector.reset(); heardSpeech = false
    }
    override fun close() { commands.release(); replies.release() }
}
