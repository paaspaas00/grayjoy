package com.futo.platformplayer.voice

import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.sqrt

internal data class WakeGateDecision(val feed: Boolean, val start: Boolean, val reset: Boolean,
    val rms: Double, val threshold: Double)

/** Keep quiet speech and brief word gaps, but do no decoder work during sustained silence. */
internal class WakeAudioGate {
    private var noise = 8.0
    private var remaining = 0
    fun process(samples: ShortArray, count: Int): WakeGateDecision {
        require(count in 1..samples.size)
        val rms = sqrt((0 until count).sumOf { samples[it].toDouble() * samples[it] } / count)
        // A slowly rising minimum tracks the background without treating the first word as noise.
        noise = minOf(rms, noise * 1.003 + .75).coerceIn(4.0, 140.0)
        val threshold = (noise * 2.4).coerceIn(35.0, 350.0)
        val wasActive = remaining > 0
        if (rms >= threshold) remaining = 19_200 else remaining = (remaining - count).coerceAtLeast(0)
        return WakeGateDecision(remaining > 0, !wasActive && remaining > 0, wasActive && remaining == 0, rms, threshold)
    }
    fun reset() { remaining = 0 }
}

internal data class WakeDiagnostics(val rms: Double = 0.0, val threshold: Double = 0.0,
    val text: String = "", val decodedFrames: Long = 0, val microphoneBlocked: Boolean = false,
    val input: String = "")

/** This is the exact streaming path used by both the real microphone and regression tests. */
internal class WakeWordListener(model: Model) : AutoCloseable {
    val phrases = listOf("ok gray joy", "okay gray joy", "ok grey joy", "okay grey joy")
    private val grammar = (phrases + phrases.flatMap { it.split(' ') } + "[unk]").distinct()
    private val recognizer = Recognizer(model, 16_000f, JSONArray(grammar).toString()).apply {
        setWords(true)
        setEndpointerDelays(5f, .8f, 15f)
    }
    private val gate = WakeAudioGate()
    private val preroll = java.util.ArrayDeque<ShortArray>()
    private var windowSamples = 0
    private var decodedFrames = 0L
    private var elapsedSamples = 0L
    private var recentPrefix = ""
    private var prefixAtSample = 0L
    var diagnostics = WakeDiagnostics(); private set

    fun accept(samples: ShortArray, count: Int): Boolean {
        val activity = gate.process(samples, count)
        elapsedSamples += count
        if (elapsedSamples - prefixAtSample > 40_000) recentPrefix = ""
        var hit = false
        if (activity.feed) {
            if (activity.start) {
                // Preserve weak initial consonants that preceded the energy threshold crossing.
                while (preroll.isNotEmpty()) { val lead = preroll.removeFirst(); hit = decode(lead, lead.size) || hit }
            }
            hit = decode(samples, count) || hit
        } else {
            if (activity.reset) {
                hit = inspect(recognizer.finalResult, "text", completed = true)
                recognizer.reset(); windowSamples = 0
            }
            preroll.addLast(samples.copyOf(count))
            while (preroll.sumOf { it.size } > 4_800) preroll.removeFirst()
        }
        diagnostics = diagnostics.copy(rms = activity.rms, threshold = activity.threshold, decodedFrames = decodedFrames)
        if (hit) reset()
        return hit
    }
    private fun decode(samples: ShortArray, count: Int): Boolean {
        if (windowSamples >= 16 * 16_000) { recognizer.reset(); windowSamples = 0; recentPrefix = "" }
        windowSamples += count; decodedFrames++
        val complete = recognizer.acceptWaveForm(samples, count)
        return inspect(if (complete) recognizer.result else recognizer.partialResult, if (complete) "text" else "partial", complete)
    }
    private fun inspect(json: String, key: String, completed: Boolean): Boolean {
        val text = JSONObject(json).optString(key).trim()
        if (text.isNotBlank()) diagnostics = diagnostics.copy(text = text.take(100))
        if (text.contains("[unk]")) recentPrefix = ""
        val combined = "$recentPrefix $text".trim().lowercase(java.util.Locale.ROOT)
        if (phrases.any { combined.contains(it) }) {
            diagnostics = diagnostics.copy(text = combined.take(100)); return true
        }
        if (completed && text.isNotBlank()) {
            // Vosk can finalize "ok" during a natural pause. Carry only a valid wake prefix,
            // not a transcript of surrounding conversation, into the next bounded utterance.
            recentPrefix = phrases.flatMap { phrase ->
                val words = phrase.split(' ')
                (1 until words.size).map { words.take(it).joinToString(" ") }
            }.filter { combined == it || combined.endsWith(" $it") }.maxByOrNull { it.length }.orEmpty()
            prefixAtSample = elapsedSamples
        }
        return false
    }
    fun reset() { recognizer.reset(); gate.reset(); preroll.clear(); windowSamples = 0; recentPrefix = "" }
    override fun close() = recognizer.close()
}
