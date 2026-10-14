package com.futo.platformplayer.voice

import android.content.Context
import android.content.SharedPreferences
import com.k2fsa.sherpa.onnx.*
import java.io.File

internal data class TtsVoice(val id: String, val label: String, val modelId: String, val language: String,
    val speaker: Int = 0)

internal object TtsVoices {
    val all = listOf(
        TtsVoice("paola", "Piper · Paola", "tts-it", "it"),
        TtsVoice("riccardo", "Piper · Riccardo", "tts-riccardo", "it"),
        TtsVoice("lessac", "Piper · Lessac", "tts-en", "en"),
        // IDs are defined by the pinned v1.0 voices.bin, not by the English-only v1.1 pack.
        TtsVoice("kokoro-sara", "Kokoro · Sara", "tts-kokoro", "it", 35),
        TtsVoice("kokoro-nicola", "Kokoro · Nicola", "tts-kokoro", "it", 36),
        TtsVoice("kokoro-heart", "Kokoro · Heart (US)", "tts-kokoro", "en", 3),
        TtsVoice("kokoro-michael", "Kokoro · Michael (US)", "tts-kokoro", "en", 16),
        TtsVoice("kokoro-emma", "Kokoro · Emma (UK)", "tts-kokoro", "en", 21),
    ) + listOf("it", "en").flatMap { language ->
        // The released archive sorts F1..F5 followed by M1..M5.
        (0..9).map { speaker ->
            val name = if (speaker < 5) "F${speaker + 1}" else "M${speaker - 4}"
            TtsVoice("supertonic-$language-$name", "Supertonic 3 · $name", "tts-supertonic", language, speaker)
        }
    }
    fun forLanguage(language: String) = all.filter { it.language == language }
    fun resolve(language: String, id: String?): TtsVoice = forLanguage(language).firstOrNull { it.id == id }
        ?: all.first { it.id == if (language == "en") "lessac" else "paola" }
    fun selected(prefs: SharedPreferences): TtsVoice {
        val language = prefs.getString("tts", "it").orEmpty().takeIf { it == "en" } ?: "it"
        return resolve(language, prefs.getString("tts_voice_$language", null))
    }
}

/** Only the chosen engine is loaded. Speaker presets share a single verified model download. */
internal class LocalTts(context: Context, val preset: TtsVoice, threads: Int = 2) : AutoCloseable {
    private val model: OfflineTts
    init {
        check(VoiceModels.installed(context, preset.modelId)) { "Scarica prima ${preset.label}" }
        val dir = VoiceModels.directory(context, preset.modelId)
        fun path(name: String) = File(dir, name).path
        val config = when (preset.modelId) {
            "tts-supertonic" -> OfflineTtsModelConfig(supertonic = OfflineTtsSupertonicModelConfig(
                durationPredictor = path("duration_predictor.int8.onnx"), textEncoder = path("text_encoder.int8.onnx"),
                vectorEstimator = path("vector_estimator.int8.onnx"), vocoder = path("vocoder.int8.onnx"),
                ttsJson = path("tts.json"), unicodeIndexer = path("unicode_indexer.bin"), voiceStyle = path("voice.bin")),
                numThreads = threads)
            "tts-kokoro" -> OfflineTtsModelConfig(kokoro = OfflineTtsKokoroModelConfig(model = path("model.int8.onnx"),
                voices = path("voices.bin"), tokens = path("tokens.txt"), dataDir = path("espeak-ng-data"),
                lang = if (preset.language == "it") "it" else if (preset.id == "kokoro-emma") "en" else "en-us"),
                numThreads = threads)
            else -> {
                val onnx = VoiceModels.catalog.first { it.id == preset.modelId }.required.first { it.endsWith(".onnx") }
                OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(model = path(onnx),
                    tokens = path("tokens.txt"), dataDir = path("espeak-ng-data")), numThreads = 1)
            }
        }
        model = OfflineTts(config = OfflineTtsConfig(model = config))
        if (preset.speaker !in 0 until maxOf(1, model.numSpeakers())) {
            model.release(); error("Voce non disponibile nel modello")
        }
    }
    fun generate(text: String): GeneratedAudio {
        require(text.length <= 500)
        val audio = if (preset.modelId == "tts-supertonic") model.generateWithConfig(text,
            GenerationConfig(sid = preset.speaker, numSteps = 8, extra = mapOf("lang" to preset.language)))
        else model.generate(text, sid = preset.speaker)
        check(audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() }) { "Il motore non ha generato una voce valida" }
        return audio
    }
    override fun close() = model.release()
}
