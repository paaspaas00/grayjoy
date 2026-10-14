package com.futo.platformplayer.voice

/** A stable, recognisable answer can rescue a monosyllable that neural VAD missed.
 * Final ASR confidence is still checked before accepting it as consent. */
internal class ReplyEndpoint {
    private var last = ""
    private var stableFrames = 0
    fun reset() { last = ""; stableFrames = 0 }
    fun accept(text: String, confirmation: Boolean): Boolean {
        val normalized = answerText(text)
        stableFrames = if (normalized == last && normalized.isNotBlank()) stableFrames + 1 else 0
        last = normalized
        return confirmation && stableFrames >= 3 && quickConfirmationAnswer(normalized) != null
    }
}

internal fun reliableQuickReply(text: String, confidence: Double, confirmation: Boolean, heardSpeech: Boolean = false): Boolean =
    confirmation && confidence.isFinite() && confidence >= (if (heardSpeech) .60 else .80) && quickConfirmationAnswer(text) != null

internal fun voicePresentationEndNanos(frames: Long, timestampFrame: Long, timestampNanos: Long, rate: Int): Long =
    timestampNanos + (frames - timestampFrame) * 1_000_000_000L / rate

/** Locate only very quiet trailing model padding; do not trim or modify the played audio. */
internal fun voiceReplyHandoffFrame(samples: FloatArray, rate: Int): Int {
    val lastSound = samples.indexOfLast { kotlin.math.abs(it) > .00015f }
    return if (lastSound < 0) samples.size else minOf(samples.size, lastSound + 1 + rate / 50)
}

internal fun isVoicePromptEcho(text: String, prompt: String): Boolean =
    prompt.isNotBlank() && answerText(text) == answerText(prompt)

/** Keep a small margin around all audible samples; leave the original cached audio intact. */
internal fun trimVoicePadding(samples: FloatArray, rate: Int): FloatArray {
    val peak = samples.maxOfOrNull { kotlin.math.abs(it) } ?: return samples
    if (peak <= 0f) return samples
    val threshold = minOf(.00015f, peak * .001f)
    val first = samples.indexOfFirst { kotlin.math.abs(it) > threshold }
    val last = samples.indexOfLast { kotlin.math.abs(it) > threshold }
    if (first < 0) return samples
    val margin = rate / 50
    val start = (first - margin).coerceAtLeast(0).takeIf { it >= rate * 80 / 1000 } ?: 0
    val end = (last + 1 + margin).coerceAtMost(samples.size).takeIf { samples.size - it >= rate * 80 / 1000 } ?: samples.size
    return if (start == 0 && end == samples.size) samples else samples.copyOfRange(start, end)
}
