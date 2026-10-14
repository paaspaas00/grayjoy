package com.futo.platformplayer.voice

import org.json.JSONArray
import org.vosk.Model
import org.vosk.Recognizer

/** Reuses the same streaming acoustic model, with a short-answer vocabulary only while
 * awaiting confirmation. Unknown speech is deliberately retained for the selected full STT. */
internal fun createReplyRecognizer(model: Model?, language: String): Recognizer? {
    if (model == null || language !in setOf("it", "en")) return null
    val phrases = if (language == "it") listOf(
        // One canonical spelling: duplicate "si"/"sì" acoustic paths split word confidence.
        "sì", "sì leggilo", "sì leggi pure",
        "sì puoi leggerlo", "va bene", "ok", "certo", "leggilo", "leggi",
        "no", "no grazie", "non leggerlo", "sì non leggerlo",
        "qualunque cosa", "quello che ti pare", "annulla", "pausa", "stop",
        // The prompt itself must not be coerced into a positive answer if a route has an echo.
        "devo leggerlo"
    ) else listOf("yes", "yes please", "yes read it", "read it please", "sure", "okay",
        "no", "no thanks", "do not read it", "yes do not read it", "anything", "whatever",
        "cancel", "pause", "stop", "shall i read it")
    return Recognizer(model, 16000f, JSONArray(phrases + "[unk]").toString()).apply {
        setWords(true)
        setEndpointerDelays(5f, .3f, 16f)
    }
}
