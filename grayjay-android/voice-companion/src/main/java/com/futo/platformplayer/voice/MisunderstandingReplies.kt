package com.futo.platformplayer.voice

import kotlin.random.Random

internal class MisunderstandingReplies(private val random: Random = Random.Default) {
    private var last = -1
    fun next(italian: Boolean): String {
        val index = (0..4).filter { it != last }.random(random)
        last = index
        return (if (italian) italianReplies else englishReplies)[index]
    }
    companion object {
        val italianReplies = listOf("Cosa intendi? Ripeti, per favore.", "Non ho capito. Puoi ripetere?",
            "Mi sfugge il comando. Riprova.", "Puoi dirlo di nuovo?", "Non mi è chiaro cosa vuoi fare. Ripeti.")
        val englishReplies = listOf("What do you mean? Please repeat.", "I didn't understand. Can you repeat?",
            "I missed the command. Try again.", "Could you say that again?", "I'm not sure what you want to do. Please repeat.")
    }
}
