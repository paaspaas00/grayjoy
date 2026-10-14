package com.futo.platformplayer.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceArtistRequestTest {
    @Test fun namedArtistAndPronounsHaveDifferentTools() {
        listOf("metti su qualcosa dei Queen", "riproduci qualcosa di Queen", "riproduci qualcos'altro dei Queen", "dei Queen").forEach {
            assertEquals(it, VoiceDecision("play_artist", "Queen"), voiceArtistRequest(it))
        }
        listOf("riproduci qualcos'altro sempre loro", "qualcos’altro sempre suo", "metti su qualcos'altro di loro", "sempre suo").forEach {
            assertEquals(it, VoiceDecision("play_same_artist"), voiceArtistRequest(it))
        }
        assertNull(voiceArtistRequest("riproduci Bohemian Rhapsody"))
        assertEquals("play_artist", VoiceDecision.parse("[\"play_artist\",\"Queen\"]").tool)
        assertEquals("play_same_artist", VoiceDecision.parse("[\"play_same_artist\",\"\"]").tool)
    }
    @Test fun modelCannotInventAnArtistButCanInterpretFreePhrasing() {
        assertEquals(VoiceDecision("play_artist", "Muse"), validateVoiceDecision(VoiceDecision("play_artist", "Queen"), "metti qualcosa dei Muse", false))
        assertEquals(VoiceDecision("play_artist", "Muse"), validateVoiceDecision(VoiceDecision("random_music"), "metti qualcosa dei Muse", false))
        assertEquals("play_artist", validateVoiceDecision(VoiceDecision("play_artist", "Dire Straits"), "Mi andrebbe di sentire i Dire Straits", false).tool)
        assertEquals("ignore", validateVoiceDecision(VoiceDecision("play_artist", "invented"), "vorrei ascoltare del jazz", false).tool)
        assertEquals("ignore", validateVoiceDecision(VoiceDecision("play_artist", "Queen"), "Queen", false).tool)
    }
}
