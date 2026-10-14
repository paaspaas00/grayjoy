package com.futo.platformplayer.voice

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VoiceReplyTimingTest {
    @Test fun affirmativeCompoundsAreImmediateAndNegationWins() {
        listOf("sì", "Sì, leggilo", "sì, leggilo pure", "certo, leggi il messaggio", "sì puoi leggerlo", "yes, read it please").forEach {
            assertEquals(it, "yes", quickConfirmationAnswer(it))
        }
        listOf("no", "sì, non leggerlo", "yes, do not read it").forEach { assertEquals(it, "no", quickConfirmationAnswer(it)) }
        assertNull(quickConfirmationAnswer("devo leggerlo"))
        assertNull(quickConfirmationAnswer("sì ma forse più tardi"))
        assertTrue(isVoicePromptEcho("devo leggerlo", "Devo leggerlo?"))
        assertFalse(isVoicePromptEcho("sì, leggilo", "Devo leggerlo?"))
    }
    @Test fun briefRepliesNeedContextStableTextAndFinalConfidence() {
        val endpoint = ReplyEndpoint()
        repeat(3) { assertFalse(endpoint.accept("sì", true)) }
        assertTrue(endpoint.accept("sì", true))
        endpoint.reset()
        repeat(10) { assertFalse(endpoint.accept("sì", false)) }
        assertTrue(reliableQuickReply("sì leggilo", .8, true))
        assertFalse(reliableQuickReply("sì", .4, true))
        assertFalse(reliableQuickReply("sì", Double.NaN, true))
        assertFalse(reliableQuickReply("sì", 1.0, false))
        assertFalse(reliableQuickReply("sì", .62, true))
        assertTrue(reliableQuickReply("sì", .62, true, heardSpeech = true))
        assertFalse(reliableQuickReply("devo leggerlo", 1.0, true))
    }
    @Test fun timestampsAccountForCommittedButNotYetPresentedFrames() {
        assertEquals(11_000_000_000L, voicePresentationEndNanos(16_000, 0, 10_000_000_000L, 16000))
        assertEquals(9_500_000_000L, voicePresentationEndNanos(16_000, 24_000, 10_000_000_000L, 16000))
        val audio = FloatArray(16000) { if (it < 8000) .1f else 0f }
        assertEquals(8320, voiceReplyHandoffFrame(audio, 16000))
        assertEquals(16000, audio.size)
        assertEquals(16000, voiceReplyHandoffFrame(FloatArray(16000), 16000))
    }
    @Test fun followingSpeechIsPreparedWhilePreviousPartPlays() = runBlocking {
        val secondPrepared = CompletableDeferred<Unit>()
        val played = mutableListOf<Pair<String, Boolean>>()
        playPreparedSpeech(listOf("intro", "question"), prepare = {
            if (it == "question") secondPrepared.complete(Unit)
            it
        }, play = { audio, last ->
            if (audio == "intro") withTimeout(1000) { secondPrepared.await() }
            played += audio to last
        })
        assertEquals(listOf("intro" to false, "question" to true), played)
    }
    @Test fun paddingRemovalPreservesAudibleSamplesAndTheCachedOriginal() {
        val samples = FloatArray(16000) { if (it in 4000..7999) .1f else 0f }
        val trimmed = trimVoicePadding(samples, 16000)
        assertEquals(4640, trimmed.size)
        assertEquals(4000, trimmed.count { it == .1f })
        assertEquals(16000, samples.size)
        assertEquals(0f, samples.first())
    }
}
