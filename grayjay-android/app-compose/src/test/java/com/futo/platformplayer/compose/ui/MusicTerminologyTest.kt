package com.futo.platformplayer.compose.ui

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class MusicTerminologyTest {
    private fun it(text: String, key: String = "label", quantity: Int? = null, vararg args: Any) =
        MusicTerminology.template(text, Locale.ITALIAN, key, quantity = quantity, args = args)

    @Test fun italianArtistArticlesAndPrepositionsRemainGrammatical() {
        val cases = mapOf("Impostazioni del canale" to "Impostazioni dell’artista",
            "Velocità del canale" to "Velocità dell’artista", "Vai al canale" to "Vai all’artista",
            "Cerca nei canali" to "Cerca negli artisti", "I canali selezionati" to "Gli artisti selezionati",
            "I tuoi canali" to "I tuoi artisti", "Da tutti i canali" to "Da tutti gli artisti",
            "Per canale o artista" to "Per artista")
        cases.forEach { (source, expected) -> assertEquals(source, expected, it(source)) }
        assertEquals("Non segui ancora alcun artista", it("Non segui ancora alcun creator", "no_followed_creators"))
        assertEquals("Contenuti originali di artisti indipendenti", it("Contenuti originali di creator indipendenti", "source_description_nebula"))
    }
    @Test fun italianTracksUseSingularPluralAndCompoundLabelsCorrectly() {
        assertEquals("Brani", it("Video", "videos"))
        assertEquals("Brano", it("Video", "video"))
        assertEquals("I brani dell’artista", it("I video del canale"))
        assertEquals("Nessun brano in coda", it("Nessun video in coda"))
        assertEquals("Brani piaciuti", it("Video piaciuti", "liked_videos"))
        assertEquals("Sposta questo brano subito dopo quello corrente.", it("Sposta questo video subito dopo quello corrente."))
        assertEquals("Lingua dei titoli dei brani", it("Lingua dei titoli video"))
        assertEquals("Qualità di riproduzione preferita", it("Qualità video preferita"))
        assertEquals("Ordina brani", it("Ordina video", "sort_videos"))
        assertEquals("tra brani", it("tra video e brani", "setup_audio_normalization_body"))
    }
    @Test fun templatesAreChangedBeforeFormattingSoContentNamesAreNeverEdited() {
        val title = "Video killed the radio star - Il canale"
        val template = it("Apri il video “%1\$s” del canale “%2\$s”", args = arrayOf(title, "My video channel"))
        assertEquals("Apri il brano “$title” dell’artista “My video channel”", String.format(Locale.ITALIAN, template, title, "My video channel"))
        assertEquals("%d brano", it("%d video", "video_count", 1, 1))
        assertEquals("%d brani", it("%d video", "video_count", 2, 2))
        assertEquals("%d artista seguito", it("%d creator seguito", "followed_creators", 1, 1))
        assertEquals("%d artisti seguiti", it("%d creator seguiti", "followed_creators", 2, 2))
        assertEquals("%1\$d brano importato", it("%1\$d video importato", "summary", null, 1))
        assertEquals("%1\$d brani importati", it("%1\$d video importati", "summary", null, 12))
    }
    @Test fun englishAndOtherSupportedLocalesUseMusicNouns() {
        assertEquals("Follow an artist and play tracks", MusicTerminology.template("Follow a channel and play videos", Locale.ENGLISH, "label"))
        val cases = listOf(Triple("de", "Videos des Kanals", "Musikstücke des Künstlers"),
            Triple("es", "Vídeos del canal", "Temas del artista"), Triple("fr", "Vidéos de la chaîne", "Pistes de l’artiste"),
            Triple("pt", "Vídeos do canal", "Temas do artista"), Triple("pl", "Filmy kanału", "Utwory artysty"),
            Triple("tr", "Kanal videoları", "Sanatçı parçaları"), Triple("ja", "チャンネルの動画", "アーティストの曲"),
            Triple("zh", "频道视频", "艺人曲目"), Triple("ko", "채널의 동영상", "아티스트의 트랙"),
            Triple("ar", "الفيديو من القناة", "المقطع من الفنان"))
        cases.forEach { (tag, source, expected) -> assertEquals(tag, expected, MusicTerminology.template(source, Locale.forLanguageTag(tag), "label")) }
    }
    @Test fun textWithoutContentTermsIsReturnedWithoutAnAllocationOrRewrite() {
        val source = "This string has no content-type labels %1\$s"
        assertSame(source, MusicTerminology.template(source, Locale.ITALIAN, "label"))
    }
}
