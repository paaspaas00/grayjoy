package com.futo.platformplayer.voice

/** Ground explicit artist/reference phrases; free paraphrases still go through the language model. */
internal fun voiceArtistRequest(text: String): VoiceDecision? {
    val clean = cleanVoiceText(text).replace('’', '\'')
    if (anything(clean)) return null
    val start = "(?:metti(?:mi)?(?: su| in play)?|riproduci|avvia|fammi (?:sentire|ascoltare)|play|put on)"
    val song = "(?:qualcos[' ]?altro|qualcosa(?: di diverso)?|un[' ]?altr[oa] (?:brano|canzone)|una canzone|un brano|something(?: else)?|another (?:song|track))"
    Regex("(?i)^(?:$start\\s+)?$song\\s+(?:sempre\\s+)?(?:di|dei|del|della|degli|delle|dell'|by|from)\\s*(.+)$")
        .matchEntire(clean)?.groupValues?.get(1)?.trim()?.let { artist ->
            if (artist.lowercase() in setOf("casuale", "diverso", "nuovo", "rilassante", "allegro", "tranquillo") &&
                clean.contains(" di ", ignoreCase = true)) return null
            if (artist.lowercase() in setOf("loro", "lui", "lei", "suo", "sua", "them", "him", "her", "the same artist"))
                return VoiceDecision("play_same_artist")
            if (artist.isNotBlank()) return VoiceDecision("play_artist", artist.take(120))
        }
    if (Regex("(?i)^(?:$start\\s+)?(?:$song\\s+)?(?:sempre (?:loro|suo|sua|lui|lei)|ancora (?:loro|lui|lei)|(?:by|from) the same artist)$")
        .matches(clean)) return VoiceDecision("play_same_artist")
    Regex("(?i)^(?:di|dei|del|della|degli|delle)\\s+(.+)$").matchEntire(clean)?.groupValues?.get(1)?.trim()?.let {
        if (it.isNotBlank()) return VoiceDecision("play_artist", it.take(120))
    }
    return null
}
