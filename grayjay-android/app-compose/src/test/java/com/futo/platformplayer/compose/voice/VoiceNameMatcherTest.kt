package com.futo.platformplayer.compose.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VoiceNameMatcherTest {
    private fun names(vararg titles: String) = titles.mapIndexed { i, title -> VoiceNameCandidate(i.toString(), title) }
    @Test fun phoneticsHandleItalianEnglishAndInventedSpellings() {
        listOf("mudi" to "Moody's", "modis" to "Moody's", "nuova" to "Nueva", "compiuters" to "computerss", "name" to "N4ME", "boiemian rapsodi" to "Bohemian Rhapsody").forEach { (heard, expected) ->
            val ranked = VoiceNameMatcher.rank(heard, names("Sport", expected, "Jazz"))
            assertEquals(heard, expected, ranked.first().candidate.title)
            assertTrue("$heard: $ranked", ranked.first().score > .65)
        }
    }
    @Test fun numbersAmbiguityAndMissingNamesAreNotGuessed() {
        val ranked = VoiceNameMatcher.rank("Stagione diciassette", names("Stagione 18", "Stagione 17"))
        assertEquals("Stagione 17", ranked.first().candidate.title)
        assertEquals("Stagione 17", VoiceNameMatcher.automatic(ranked)?.title)
        assertNull(VoiceNameMatcher.automatic(VoiceNameMatcher.rank("mudi", names("Moody", "Moodie"))))
        assertTrue(VoiceNameMatcher.rank("   ", names("Songs")).isEmpty())
        assertEquals("positiva", VoiceNameMatcher.normalized("Positiva :)"))
    }
    @Test fun youtubeTitlesCanContainExtraArtistAndFormatWords() {
        val ranked = VoiceNameMatcher.rank("boiemian rapsodi", names("Bohemian cats", "Queen – Bohemian Rhapsody (Official Video)"), true)
        assertEquals("Queen – Bohemian Rhapsody (Official Video)", ranked.first().candidate.title)
    }
    @Test fun randomizedNamesHaveFiniteBoundedScores() {
        val random = Random(905)
        repeat(500) {
            val query = (0 until random.nextInt(0, 400)).joinToString("") { random.nextInt(32, 0x300).toChar().toString() }
            VoiceNameMatcher.rank(query, names(query, "Songs", "Nueva")).forEach { assertTrue(it.score.isFinite() && it.score in 0.0..1.0) }
        }
    }
}
