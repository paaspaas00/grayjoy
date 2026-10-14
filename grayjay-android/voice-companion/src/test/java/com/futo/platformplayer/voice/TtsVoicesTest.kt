package com.futo.platformplayer.voice

import org.junit.Assert.*
import org.junit.Test

class TtsVoicesTest {
    @Test fun activationPrefixDoesNotEraseCommandOrPlaylistName() {
        assertEquals("metti in pausa", cleanVoiceText("Ok Grayjoy, metti in pausa"))
        assertEquals("pause", cleanVoiceText("Okay Grey Joy: pause"))
        assertEquals("riproduci playlist Grayjoy mix", cleanVoiceText("riproduci playlist Grayjoy mix"))
    }
    @Test fun eachLanguageOffersAllThreeEnginesAndUniqueVoices() {
        listOf("it", "en").forEach { language ->
            val voices = TtsVoices.forLanguage(language)
            assertEquals(voices.size, voices.map { it.id }.distinct().size)
            assertTrue(voices.any { it.modelId == "tts-kokoro" })
            assertEquals(10, voices.count { it.modelId == "tts-supertonic" })
            assertTrue(voices.any { it.modelId == "tts-$language" })
        }
        assertEquals(TtsVoices.all.size, TtsVoices.all.map { it.id }.distinct().size)
    }
    @Test fun invalidOrOtherLanguagePreferenceFallsBackToExistingPiper() {
        assertEquals("paola", TtsVoices.resolve("it", "lessac").id)
        assertEquals("lessac", TtsVoices.resolve("en", "unknown").id)
        assertEquals("kokoro-nicola", TtsVoices.resolve("it", "kokoro-nicola").id)
    }
    @Test fun downloadCatalogIsPinnedAndPresetsHaveValidModelFiles() {
        assertEquals(VoiceModels.catalog.size, VoiceModels.catalog.map { it.id }.distinct().size)
        VoiceModels.catalog.forEach { assertTrue(it.hash.matches(Regex("[a-f0-9]{64}"))); assertTrue(it.bytes > 0) }
        TtsVoices.all.forEach { voice ->
            val model = VoiceModels.catalog.first { it.id == voice.modelId }
            assertTrue(model.required.any { it.endsWith(".onnx") })
        }
    }
}
