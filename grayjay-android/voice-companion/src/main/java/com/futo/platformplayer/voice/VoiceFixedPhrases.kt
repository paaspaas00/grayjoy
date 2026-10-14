package com.futo.platformplayer.voice

internal object VoiceFixedPhrases {
    private val italian = mapOf(
        "artist_name" to "Di quale artista vuoi ascoltare un brano?",
        "no_other_artist_track" to "Non trovo un altro brano di questo artista.",
        "help" to "Puoi chiedermi una canzone, una playlist o qualcosa di un artista, e poi qualcos’altro sempre suo. Posso elencare le playlist, scegliere musica recente a caso, mettere in pausa, riprendere, cambiare brano, spostare la riproduzione e regolare il volume.",
        "help_calls" to "Puoi anche dirmi: chiama, seguito dal nome del contatto.",
        "help_messages" to "Ti avviso dei messaggi in arrivo e li leggo soltanto se mi dici di sì.",
        "offline_offer" to "Non c’è Internet, mi spiace. Metto su qualcosa in locale?",
        "local_name" to "Quale playlist o canzone scaricata vuoi ascoltare?",
        "random_mood" to "Certo, come ti senti? Va bene qualunque cosa?",
        "mood_playlist" to "Dimmi una playlist o una canzone adatta al tuo umore, oppure dimmi: qualunque cosa.",
        "read_message" to "Devo leggerlo?", "answer_yes_no" to "Dimmi sì oppure no.",
        "message_expired" to "Il messaggio non è più disponibile.",
        "calling" to "Certo, chiamo.", "call_failed" to "Non riesco ad avviare la chiamata. Controlla le autorizzazioni e la SIM.",
        "calls_disabled" to "Abilita le chiamate vocali e autorizza contatti e telefono nelle impostazioni del controllo vocale.",
        "contact_missing" to "Non trovo quel contatto nella rubrica.",
        "no_playlists" to "Non ci sono playlist.", "more_playlists" to "Vuoi sentire le altre?",
        "no_local_music" to "Non trovo musica scaricata adatta alla richiesta.",
        "no_recent_music" to "Non trovo brani musicali riconosciuti nella cronologia recente.",
        "no_results" to "Non ho trovato un risultato adatto su YouTube.",
        "playing" to "Riproduco.", "cancelled" to "Annullato.",
        "playlist_name" to "Come si chiama la playlist?", "video_name" to "Quale video vuoi ascoltare?",
        "play_name" to "Non c'è nulla da riprendere. Dimmi il titolo da riprodurre.",
        "missing_playlist" to "Non trovo una playlist simile. Ripeti il nome, oppure annulla.",
        "empty_playlist" to "La playlist è vuota.", "failed" to "Non ho potuto eseguire il comando.")
    private val english = mapOf(
        "artist_name" to "Which artist would you like to hear?",
        "no_other_artist_track" to "I could not find another track by this artist.",
        "help" to "You can ask for a song, a playlist or something by an artist, then another song by the same artist. I can list playlists, choose recent music at random, pause, resume, change tracks, seek and adjust the volume.",
        "help_calls" to "You can also say: call, followed by a contact name.",
        "help_messages" to "I announce incoming messages and read them only when you say yes.",
        "offline_offer" to "There is no Internet connection, sorry. Shall I play something offline?",
        "local_name" to "Which downloaded song or playlist would you like?",
        "random_mood" to "Sure, how are you feeling? Is anything okay?",
        "mood_playlist" to "Name a song or playlist for your mood, or say: anything.",
        "read_message" to "Shall I read it?", "answer_yes_no" to "Please say yes or no.",
        "message_expired" to "That message is no longer available.",
        "calling" to "Sure, calling.", "call_failed" to "I could not start the call. Check permissions and your SIM.",
        "calls_disabled" to "Enable voice calls and allow contacts and phone access in voice control settings.",
        "contact_missing" to "I could not find that contact.",
        "no_playlists" to "There are no playlists.", "more_playlists" to "Would you like to hear more?",
        "no_local_music" to "I could not find suitable downloaded music.",
        "no_recent_music" to "I could not find identified music in your recent history.",
        "no_results" to "I could not find a suitable YouTube result.",
        "playing" to "Playing.", "cancelled" to "Cancelled.",
        "playlist_name" to "What's the playlist name?", "video_name" to "Which video?",
        "play_name" to "Nothing to resume. Say the title to play.",
        "missing_playlist" to "No similar playlist found. Repeat the name, or cancel.",
        "empty_playlist" to "The playlist is empty.", "failed" to "I couldn't execute the command.")
    fun get(key: String, isItalian: Boolean) = (if (isItalian) italian else english).getValue(key)
    fun restartSuffix(isItalian: Boolean) = if (isItalian) " Dì di nuovo ok Grayjoy." else " Say ok Grayjoy again."
    fun all(isItalian: Boolean): List<String> {
        val clarification = if (isItalian) MisunderstandingReplies.italianReplies else MisunderstandingReplies.englishReplies
        return ((if (isItalian) italian else english).values + clarification + clarification.map { it + restartSuffix(isItalian) })
            .flatMap(::spokenChunks).distinct()
    }
}

internal fun spokenChunks(text: String): List<String> {
    val result = ArrayList<String>()
    var remaining = text.take(2000).trim()
    while (remaining.isNotBlank()) {
        val end = if (remaining.length <= 240) remaining.length else remaining.lastIndexOf(' ', 240).takeIf { it >= 100 } ?: 240
        result += remaining.take(end)
        remaining = remaining.drop(end).trim()
    }
    return result
}
