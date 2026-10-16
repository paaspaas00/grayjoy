package com.futo.platformplayer.voice

import org.json.JSONObject
import java.util.Locale

internal class NativeLlm {
    private val owner = owners.incrementAndGet()
    private external fun nativeLoad(path: String, owner: Long)
    private external fun nativePrime(prefix: String, threads: Int, owner: Long)
    private external fun nativeInterpret(prompt: String, grammar: String, threads: Int, owner: Long): String
    private external fun nativeCancel(owner: Long)
    private external fun nativeUnload(owner: Long)
    private external fun nativeParameterCount(owner: Long): Long
    fun load(path: String) = nativeLoad(path, owner)
    fun prime(prefix: String, threads: Int) = nativePrime(prefix, threads, owner)
    fun interpret(prompt: String, grammar: String, threads: Int) = nativeInterpret(prompt, grammar, threads, owner)
    fun cancel() = nativeCancel(owner)
    fun unload() = nativeUnload(owner)
    fun parameterCount(): Long = nativeParameterCount(owner)
    companion object {
        private val owners = java.util.concurrent.atomic.AtomicLong()
        init { System.loadLibrary("grayjoy-voice") }
    }
}

internal data class VoiceDecision(val tool: String, val argument: String = "", val number: Int = 0) {
    fun arguments(): JSONObject = JSONObject().apply {
        when (tool) {
            "volume" -> put("steps", number.coerceIn(-5, 5))
            "seek" -> put("seconds", number.coerceIn(-600, 600))
            "search_play", "play_named", "play_playlist", "call_contact", "play_artist" -> put("query", argument.take(200))
        }
    }
    companion object {
        val tools = setOf("play", "pause", "next", "previous", "volume", "seek", "play_playlist", "play_named", "search_play", "clarify", "ignore", "help", "list_playlists", "random_music", "call_contact", "reply_yes", "reply_no", "reply_anything", "play_artist", "play_same_artist")
        fun parse(raw: String): VoiceDecision {
            if (raw.trimStart().startsWith("[")) {
                val array = org.json.JSONArray(raw)
                require(array.length() == 2)
                val name = array.getString(0)
                require(name in tools)
                val argument = if (name in setOf("search_play", "play_named", "play_playlist", "call_contact", "play_artist")) array.getString(1).trim() else ""
                val number = if (name in setOf("volume", "seek")) array.getInt(1) else 0
                require(argument.length <= 200 && argument.none { it.code < 32 })
                if (name in setOf("search_play", "play_named", "play_playlist", "call_contact", "play_artist")) require(argument.isNotBlank())
                if (name == "volume") require(number in setOf(-1, 1))
                return VoiceDecision(name, argument, number)
            }
            val json = JSONObject(raw)
            val name = json.getString("tool")
            require(name in tools)
            val argument = json.optString("argument").trim()
            require(argument.length <= 200 && argument.none { it.code < 32 })
            if (name in setOf("search_play", "play_named", "play_playlist", "call_contact", "play_artist")) require(argument.isNotBlank())
            return VoiceDecision(name, argument, json.optInt("number", 0))
        }
    }
}

internal fun fastVoiceCommand(text: String): VoiceDecision? {
    val cleaned = normalizeVoiceCommandTranscript(text, italian = true, followup = false)
    val normalized = cleaned.lowercase(Locale.ROOT)
    voiceArtistRequest(cleaned)?.let { return it }
    assistantShortcut(cleaned)?.let { return it }
    if (!looksLikeVoiceCommand(cleaned)) return null
    // Negation must never turn into an affirmative fast-path operation.
    if (isNegatedVoiceRequest(cleaned)) return null
    if (Regex("(?i)^(?:metti(?:mi)? in pausa|stoppa)(?:\\s+(?:la musica|il video|la riproduzione|tutto|un attimo|per favore))*$").matches(cleaned))
        return VoiceDecision("pause")
    when (normalized) {
        "pausa", "metti in pausa", "stoppa", "stoppa la musica", "pause", "stop", "pause playback", "stop the music" -> return VoiceDecision("pause")
        "riprendi", "riproduci", "continua", "riprendi riproduzione", "play", "resume", "resume playback" -> return VoiceDecision("play")
        "successivo", "prossimo", "next", "play next", "next track", "next song" -> return VoiceDecision("next")
        "precedente", "previous", "play previous", "previous track", "previous song" -> return VoiceDecision("previous")
    }
    val playRequest = playbackRequest(cleaned)
    if (playRequest != null) {
        if (Regex("(?i)^(?:metti|mettimi)\\b").containsMatchIn(cleaned) &&
            !Regex("(?i)^metti(?:mi)? in (?:play|riproduzione)\\b").containsMatchIn(cleaned) &&
            Regex("(?i)\\b(?:pausa|silenzio|volume|più piano|meno forte)\\b").containsMatchIn(playRequest)) return null
        val localPlaylist = playlistRequest(playRequest)
        if (localPlaylist != null) return if (localPlaylist.isBlank()) VoiceDecision("clarify")
            else VoiceDecision("play_playlist", localPlaylist.take(200))
        when (playRequest.lowercase(Locale.ROOT)) {
            "il prossimo brano", "il video successivo", "la prossima canzone", "next", "next track", "next song" -> return VoiceDecision("next")
            "il brano precedente", "il video precedente", "la canzone precedente", "previous", "previous track" -> return VoiceDecision("previous")
            "la musica", "music", "playback" -> return VoiceDecision("play")
            "un video", "a video", "video", "qualcosa", "something" -> return VoiceDecision("clarify")
        }
        val explicitSearch = Regex("(?i)^(?:cerca|cercami|trova|trovami|search|find)\\b").containsMatchIn(cleaned)
        return VoiceDecision(if (explicitSearch) "search_play" else "play_named", playRequest.take(200))
    }
    if (!Regex("\\b(non|don't|do not)\\b").containsMatchIn(normalized)) {
        if (!normalized.startsWith("riproduci ") && !normalized.startsWith("play ") &&
            Regex("\\b(ferm[aei]|interromp\\w*|pause|stop)\\b.*\\b(riproduzione|music\\w*|playback|audio)\\b").containsMatchIn(normalized))
            return VoiceDecision("pause")
        if (Regex("\\b(abbass\\w*|riduc\\w*|diminuis\\w*|quieter|lower|decrease|a bas[seai]*)(?:\\s|').*\\b(volum\\w*|music\\w*|audio|sound)\\b").containsMatchIn(normalized))
            return VoiceDecision("volume", number = -1)
        if (Regex("\\b(alz\\w*|aument\\w*|louder|raise|increase)(?:\\s|').*\\b(volum\\w*|music\\w*|audio|sound)\\b").containsMatchIn(normalized))
            return VoiceDecision("volume", number = 1)
        if (Regex("\\b(?:make|turn)\\b.*\\b(?:music|audio|sound|volume|it)\\b.*\\b(?:quieter|lower|down)\\b").containsMatchIn(normalized))
            return VoiceDecision("volume", number = -1)
        if (Regex("\\b(?:make|turn)\\b.*\\b(?:music|audio|sound|volume|it)\\b.*\\b(?:louder|higher|up)\\b").containsMatchIn(normalized))
            return VoiceDecision("volume", number = 1)
        if (Regex("\\b(?:prossim\\w*|successiv\\w*|next)\\b.*\\b(?:brano|video|canzone|track|song)\\b").containsMatchIn(normalized))
            return VoiceDecision("next")
        if (Regex("\\b(?:precedente|previous)\\b.*\\b(?:brano|video|canzone|track|song)\\b").containsMatchIn(normalized))
            return VoiceDecision("previous")
    }
    return when (normalized) {
        "pausa", "metti in pausa", "pause", "pause playback", "stop the music" -> VoiceDecision("pause")
        "riprendi", "continua", "riprendi riproduzione", "play", "resume", "resume playback" -> VoiceDecision("play")
        "successivo", "prossimo", "next", "play next", "next track", "next song" -> VoiceDecision("next")
        "precedente", "previous", "play previous", "previous track", "previous song" -> VoiceDecision("previous")
        "alza il volume", "volume su", "volume up", "turn it up" -> VoiceDecision("volume", number = 1)
        "abbassa il volume", "volume giù", "volume down", "turn it down" -> VoiceDecision("volume", number = -1)
        else -> null
    }
}

/** Italian acoustic models often render the English transport word "play" as the phonetically
 * close pronoun "lei". Correct only a complete, one-word command after activation; never rewrite
 * the same word inside a sentence or while the assistant is waiting for a follow-up answer. */
internal fun normalizeVoiceCommandTranscript(text: String, italian: Boolean, followup: Boolean): String {
    val cleaned = cleanVoiceText(text)
    if (!italian || followup) return cleaned
    val phonetic = java.text.Normalizer.normalize(cleaned.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z]+"), "")
    return if (phonetic in setOf("lei", "plei", "pley", "plai", "play")) "play" else cleaned
}

internal fun canUseStreamingCommand(text: String, confidence: Double, followup: Boolean): Boolean {
    if (text.contains("[unk]")) return false
    if (!confidence.isFinite()) return false
    val decision = fastVoiceCommand(cleanVoiceText(text))
    val threshold = if (decision?.tool in setOf("play", "pause", "next", "previous", "volume")) .65 else .75
    return confidence >= threshold && (decision != null || (followup && text.isNotBlank())) ||
        confidence >= .90 && text.isNotBlank() && !looksLikeVoiceCommand(text)
}

/** Only unambiguous transport/information requests bypass semantic tool selection. */
internal fun immediateVoiceCommand(text: String): VoiceDecision? = fastVoiceCommand(text)?.takeIf {
    it.tool in setOf("play", "pause", "next", "previous", "volume", "seek", "help", "list_playlists")
}

internal fun looksLikeVoiceCommand(text: String): Boolean {
    val cleaned = cleanVoiceText(text)
    if (voiceArtistRequest(cleaned) != null) return true
    if (assistantShortcut(cleaned) != null) return true
    if (cleaned.equals("stoppa", true) || cleaned.startsWith("stoppa ", true)) return true
    if (playbackRequest(cleaned) == null && Regex("(?i)\\b(?:sapere|spiegami|spiegare|cosa significa|come funziona|tell me about|explain|do you know|how does)\\b").containsMatchIn(cleaned))
        return false
    return Regex("(?i)^(?:mi\\s+)?(?:riproduc[ieao]\\b|riprodurre\\b|avvi\\w*|fammi\\b|fai\\b|fa\\b|mett\\w*|ascolta(?:re)?\\b|suona\\b|cerc\\w*|trova\\w*|pausa\\b|riprendi\\b|continua\\b|ferm\\w*|interromp\\w*|sospend\\w*|alz\\w*|aument\\w*|abbass\\w*|riduc\\w*|diminuis\\w*|volume\\b|successivo\\b|prossimo\\b|precedente\\b|playlist\\b|play\\b|pause\\b|stop\\b|resume\\b|next\\b|previous\\b|turn\\b|make\\b|lower\\b|raise\\b|increase\\b|decrease\\b|put on\\b|start\\b|listen\\b|search\\b|find\\b|vorrei\\b|voglio\\b|i (?:want|would like)\\b|i'd like\\b|sposta\\w*|vai\\b|salta\\b|seek\\b|skip\\b)")
        .containsMatchIn(cleaned)
}

internal fun hasContentPlaybackIntent(text: String): Boolean = playbackRequest(text) != null ||
    Regex("(?i)^(?:(?:mi|vorrei|voglio|i would like|i want|i'd like)\\s+){0,2}(?:(?:che\\s+)?(?:mi\\s+)?(?:faresti|facessi)\\s+)?(?:riproduc[ieao]|riprodurre|ascolta(?:re)?|sentire|play|listen|find|search|cerca\\w*)\\b")
        .containsMatchIn(cleanVoiceText(text))

internal fun cleanVoiceText(text: String): String = text.take(400).trim()
    .replace(Regex("(?i)^(?:ok(?:ay)?[, .:]+)?gr[ae]y\\s?joy\\b[, .:]*"), "")
    .trim().trim('.', '!', '?').trim()
    .replace(Regex("(?i)^(?:(?:per favore|please|puoi|potresti|can you|could you)[\\s,:.]+)+"), "")

internal fun isNegatedVoiceRequest(text: String): Boolean {
    val cleaned = cleanVoiceText(text)
    if (Regex("(?i)^(?:riproduci|avvia|play|metti in play)\\s+").containsMatchIn(cleaned)) return false
    return Regex("(?i)\\b(?:non|don't|do not|never)\\s+(?:mi\\s+)?(?:riprodu\\w*|riprodur\\w*|avvi\\w*|mettere|play|pause|stop|start|alz\\w*|abbass\\w*|aument\\w*|riduc\\w*|chiam\\w*|telefon\\w*|call|phone|dial)\\b")
        .containsMatchIn(cleaned)
}

internal fun playbackRequest(text: String): String? =
    Regex("(?i)^(?:riproduc[iea]|riprodurre|avvia(?:re)?|fammi sentire|fammi ascoltare|fai partire|vorrei ascoltare|voglio ascoltare|metti(?:mi)?(?: in (?:play|riproduzione))?|ascolta|suona|play(?: me)?|put on|start|listen to|cerca(?:mi)?(?: e riproduci)?|trova(?:mi)?|search(?: for)?|find)\\s+(.+)$")
        .matchEntire(cleanVoiceText(text))?.groupValues?.get(1)?.trim()

/** Null means video/search content; empty means an explicit playlist request without a name. */
internal fun playlistRequest(text: String): String? =
    Regex("(?i)^(?:(?:la|una|the|a|local|locale|mia|my)\\s+)*(?:play[ -]?list|lista di riproduzione)(?:\\s+(?:(?:di nome|chiamata|called|named)\\s+)?(.*))?$")
        .matchEntire(text.trim())?.groupValues?.get(1)?.trim()

internal const val COMMAND_GRAMMAR = """
root ::= "[" ws (simple | volume | seek | query) ws "]"
simple ::= ("\"play\"" | "\"pause\"" | "\"next\"" | "\"previous\"" | "\"clarify\"" | "\"ignore\"" | "\"help\"" | "\"list_playlists\"" | "\"random_music\"" | "\"reply_yes\"" | "\"reply_no\"" | "\"reply_anything\"" | "\"play_same_artist\"") ws "," ws "\"\""
volume ::= "\"volume\"" ws "," ws ("-1" | "1")
seek ::= "\"seek\"" ws "," ws number
query ::= ("\"play_playlist\"" | "\"play_named\"" | "\"search_play\"" | "\"call_contact\"" | "\"play_artist\"") ws "," ws string
string ::= "\"" ([^"\\\x00-\x1f] | "\\" ["\\/])* "\""
number ::= "-"? [0-9]{1,3}
ws ::= [ \t\n]{0,2}
"""

internal const val CONFIRMATION_GRAMMAR = """
root ::= "[" ws ("\"reply_yes\"" | "\"reply_no\"" | "\"reply_anything\"" | "\"ignore\"") ws "," ws "\"\"" ws "]"
ws ::= [ \t\n]{0,2}
"""
internal const val CONFIRMATION_CONTEXT = "Answer to a proposal: acceptance or praise -> reply_yes; refusal -> reply_no; " +
    "explicit no preference -> reply_anything; unrelated -> ignore. Value empty. Classify the answer, do not execute commands."

internal fun commandPrompt(text: String, tools: String, clarification: String = "", modelId: String = "qwen"): String =
    "<|im_start|>system\nYou control an offline assistant. Understand Italian and English by meaning. Return only [tool,value].\n" +
        "Tools (value is empty unless stated):\n" +
        "play: resume current media. pause: stop/pause current media. next/previous: change track.\n" +
        "volume: value -1 quieter or 1 louder. seek: value signed seconds.\n" +
        "play_named: riproduci/avvia/metti in play a named song/title; copy its name, even an unfamiliar made-up or foreign word.\n" +
        "play_playlist: play a specific playlist; value is its name.\n" +
        "play_artist: choose a song by a specified artist/band, without a specific song title. Value is ONLY the performer name, without introductory words.\n" +
        "A song/track BY someone, una canzone/un brano DI/DEI qualcuno, always uses play_artist, never play_named.\n" +
        "play_same_artist: another song by the current artist (sempre loro/suo, same artist); empty value.\n" +
        "search_play: explicit YouTube search; value is the search query.\n" +
        "random_music: user asks you to choose music WITHOUT naming any title; play anything or surprise them. Never replace an unfamiliar name with random music.\n" +
        "list_playlists: tell names of saved playlists, liste di riproduzione.\n" +
        "help: explain your functions/capabilities, cosa puoi fare.\n" +
        "call_contact: telephone a person; value is their contact name, never search for them.\n" +
        "clarify: incomplete request. ignore: unrelated conversation, silence or a bare name.\n" +
        "Followup only: reply_yes=agreement, reply_no=refusal, reply_anything=no preference.\n" +
        "Copy names from the request; never invent them. Pronouns refer to current media.\n" +
        "Examples: Metti Queen->[\"play_named\",\"Queen\"]; Avvia Plinplon->[\"play_named\",\"Plinplon\"]; Avvia la playlist Rock->[\"play_playlist\",\"Rock\"]; " +
        "Qualcosa dei Beatles->[\"play_artist\",\"Beatles\"]; Qualcos'altro sempre loro->[\"play_same_artist\",\"\"]; " +
        "Vorrei un brano di Mina->[\"play_artist\",\"Mina\"]; " +
        "Metti un brano a tua scelta->[\"random_music\",\"\"]; Telefona a Giulia->[\"call_contact\",\"Giulia\"]; " +
        "Che liste ho?->[\"list_playlists\",\"\"]; Che sai fare?->[\"help\",\"\"].\n<|im_end|>\n" +
        "<|im_start|>user\n" +
        (if (clarification.isBlank()) "" else "Previous dialogue context: ${promptData(clarification)}\nCurrent request: ") +
        "${promptData(text)}${commandTargetHint(text)}${if (modelId == "qwen-3") "" else " /no_think"}<|im_end|>\n<|im_start|>assistant\n" +
        if (modelId == "qwen-3") "" else "<think>\n\n</think>\n"

internal fun commandPrefix(tools: String, modelId: String = "qwen") = commandPrompt("", tools, modelId = modelId).substringBeforeLast("<|im_start|>user\n") + "<|im_start|>user\n"

private fun promptData(value: String): String = value.take(400).replace("<|", "‹|").replace("|>", "|›")

private fun commandTargetHint(text: String): String {
    val candidate = fastVoiceCommand(text)?.takeIf { it.tool in setOf("play_named", "play_playlist", "search_play", "call_contact", "play_artist") }
        ?.argument?.takeIf { it.isNotBlank() } ?: return ""
    // Entity grounding only: the model still chooses the operation. Foreign names are not synonyms for "anything".
    return "\nName/query literally present in the request: \"" + promptData(candidate).replace("\"", "'") + "\"."
}

internal fun validateVoiceDecision(decision: VoiceDecision, heard: String, clarification: Boolean): VoiceDecision {
    if (isNegatedVoiceRequest(heard)) return VoiceDecision("ignore")
    val artistHint = voiceArtistRequest(heard)
    if (artistHint != null && decision.tool in setOf("play_artist", "play_same_artist", "play_named", "search_play", "random_music", "next"))
        return artistHint
    if (decision.tool == "play_artist") return if (voiceArgumentGrounded(decision.argument, heard) &&
        (clarification || !decision.argument.equals(cleanVoiceText(heard), true))) decision else VoiceDecision("ignore")
    if (decision.tool == "play_same_artist") return decision
    if (decision.tool == "random_music") {
        val target = fastVoiceCommand(heard)?.takeIf { it.tool in setOf("play_named", "play_playlist", "search_play") }
        // Keep an explicitly supplied name. Tiny models can mistake unfamiliar names for a request
        // to choose something; they must not discard the user's entity and start random playback.
        if (target != null && !Regex("(?i)\\b(?:qualcosa|qualunque|qualsiasi|caso|casuale|scegli|scelta|brano|canzone|musica|anything|something|whatever|random|choose|song|music)\\b")
                .containsMatchIn(target.argument)) return target
    }
    if (decision.tool == "call_contact" && Regex("(?i)\\b(?:non|not|never|avoid|evita|don't)\\b").containsMatchIn(heard))
        return VoiceDecision("ignore")
    if (decision.tool == "call_contact" && (!Regex("(?i)\\b(?:chiam\\w*|telefon\\w*|telephon\\w*|call|phone|dial|ring)\\b").containsMatchIn(heard) ||
        !voiceArgumentGrounded(decision.argument, heard))) return VoiceDecision("ignore")
    if (decision.tool in setOf("help", "list_playlists", "random_music", "call_contact")) return decision
    if (!clarification && decision.tool in setOf("play", "pause", "next", "previous", "volume", "seek") &&
        !hasMediaRequestEvidence(heard)) return VoiceDecision("ignore")
    val checked = if (isNegatedVoiceRequest(heard)) VoiceDecision("clarify")
    else if (!clarification && decision.tool in setOf("search_play", "play_named", "play_playlist") &&
        decision.argument.equals(cleanVoiceText(heard), true)) VoiceDecision("ignore")
    else if (decision.tool in setOf("search_play", "play_named", "play_playlist") && !clarification && !voiceArgumentGrounded(decision.argument, heard)) VoiceDecision("clarify")
    else if (decision.tool in setOf("search_play", "play_named") && fastVoiceCommand(heard)?.tool in setOf("pause", "play", "volume", "next", "previous")) VoiceDecision("clarify")
    else if (decision.tool == "play_playlist" && !clarification &&
        !Regex("(?i)\\b(playlist|playlists|lista|liste)\\b").containsMatchIn(heard))
        decision.copy(tool = "play_named")
    else if (decision.tool == "search_play" &&
        !Regex("(?i)\\b(?:cerc\\w*|trov\\w*|search|find|youtube)\\b").containsMatchIn(heard))
        decision.copy(tool = "play_named")
    else decision
    // An uncertain model response is not a reason to speak after an accidental activation.
    // This only suppresses unsolicited questions; semantic commands still accept new phrasings.
    return if (checked.tool == "clarify" && !clarification && !looksLikeVoiceCommand(heard))
        VoiceDecision("ignore") else checked
}

/** Broad intent evidence, not a list of accepted commands: it stops a model treating unrelated
 * conversation as a transport action, while letting the model resolve syntax and paraphrases. */
internal fun hasMediaRequestEvidence(text: String): Boolean = looksLikeVoiceCommand(text) ||
    Regex("(?i)\\b(?:audio|music\\w*|volum\\w*|video|playlist|brano|canzon\\w*|song|track|playback|ascolt\\w*|sentire|listening|playing|paus\\w*|ferm\\w*|sospend\\w*|interromp\\w*|quieter|louder|mute|silenzio|would|could|vorrei|andrebbe|starebbe|piacerebbe|gimme|dammi|need)\\b")
        .containsMatchIn(text)

internal fun voiceArgumentGrounded(argument: String, heard: String): Boolean {
    fun words(value: String) = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
    val query = words(argument)
    val spoken = words(heard).toSet()
    return query.isNotEmpty() && query.count { it in spoken } >= (query.size + 1) / 2
}

internal fun ignorableVoiceTranscript(text: String): Boolean {
    val normalized = cleanVoiceText(text).lowercase(Locale.ROOT)
    return normalized.isBlank() || normalized in setOf("eh", "ehm", "uh", "um", "boh", "ok", "okay", "grayjoy", "gray joy",
        "grazie", "grazie per la visione", "grazie per l'ascolto", "thanks for watching", "thank you", "[music]", "[musica]", "[silence]")
}
