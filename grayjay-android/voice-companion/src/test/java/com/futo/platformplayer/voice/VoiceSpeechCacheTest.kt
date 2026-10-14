package com.futo.platformplayer.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceSpeechCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val audio = CachedVoiceAudio(FloatArray(800) { .2f }, 16_000)
    @Test fun staticSpeechRoundTripsAndModelOrVoiceChangesInvalidateIt() {
        val root = temporary.newFolder()
        val cache = VoiceSpeechCache(root, "model-v1-voice-a", setOf("Riproduco."), reserve = 0)
        cache.write("Riproduco.", audio)
        assertArrayEquals(audio.samples, cache.read("Riproduco.")!!.samples, 0f)
        assertNull(VoiceSpeechCache(root, "model-v2-voice-a", setOf("Riproduco.")).read("Riproduco."))
        assertNull(VoiceSpeechCache(root, "model-v1-voice-b", setOf("Riproduco.")).read("Riproduco."))
    }
    @Test fun dynamicNamesAndMicrophoneSpeechAreNeverStored() {
        val root = temporary.newFolder()
        val cache = VoiceSpeechCache(root, "voice", setOf("Riproduco."), reserve = 0)
        cache.write("A private playlist name", audio)
        assertTrue(root.walkTopDown().none { it.isFile })
        assertNull(cache.read("A private playlist name"))
    }
    @Test fun corruptOrPartialCacheIsDiscardedWithoutAllocationFromUntrustedLength() {
        val root = temporary.newFolder()
        val cache = VoiceSpeechCache(root, "voice", setOf("Riproduco."), reserve = 0)
        cache.write("Riproduco.", audio)
        val file = root.walkTopDown().first { it.extension == "gja" }
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertNull(cache.read("Riproduco."))
        assertFalse(file.exists())
        cache.write("Riproduco.", audio)
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(cache.read("Riproduco."))
    }
    @Test fun cacheHasGlobalSizeCapAndRejectsInvalidAudio() {
        val root = temporary.newFolder()
        val cache = VoiceSpeechCache(root, "voice", setOf("a", "b", "c", "bad"), budget = 6500, reserve = 0)
        listOf("a", "b", "c").forEach { cache.write(it, audio) }
        assertTrue(root.walkTopDown().filter { it.isFile }.sumOf(File::length) <= 6500)
        cache.write("bad", CachedVoiceAudio(floatArrayOf(Float.NaN), Int.MAX_VALUE))
        assertNull(cache.read("bad"))
        assertTrue(root.walkTopDown().filter { it.isFile }.all { it.name.matches(Regex("[a-f0-9]{64}\\.gja")) })
    }
    @Test fun allFixedRepliesAndFiveRetryVariantsArePrepared() {
        listOf(true, false).forEach { italian ->
            val phrases = VoiceFixedPhrases.all(italian)
            val retries = if (italian) MisunderstandingReplies.italianReplies else MisunderstandingReplies.englishReplies
            assertTrue(phrases.size > 18)
            assertEquals(phrases.size, phrases.distinct().size)
            assertTrue(phrases.all { it.length <= 240 })
            assertTrue(phrases.containsAll(spokenChunks(VoiceFixedPhrases.get("help", italian))))
            assertTrue(VoiceFixedPhrases.get("offline_offer", italian) in phrases)
            assertTrue(VoiceFixedPhrases.get("read_message", italian) in phrases)
            assertTrue(phrases.containsAll(retries))
            assertTrue(phrases.containsAll(retries.map { it + VoiceFixedPhrases.restartSuffix(italian) }))
            assertTrue(VoiceFixedPhrases.get("playing", italian) in phrases)
        }
    }
}
