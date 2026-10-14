package com.futo.platformplayer.voice

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal fun answerText(text: String): String = Normalizer.normalize(cleanVoiceText(text).lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
internal fun affirmative(text: String): Boolean {
    val value = answerText(text)
    if (negative(value)) return false
    return value in setOf("si", "si grazie", "si certo", "si va bene", "va bene", "va benissimo", "ok", "okay", "certo", "certamente", "esatto", "confermo", "leggilo", "leggi", "fallo", "yes", "yes please", "sure", "go ahead", "please do", "yep", "yeah", "of course", "sounds good") ||
        Regex("^(?:si|certo|certamente|ok|okay|va bene|yes|sure)(?: (?:si|certo|leggilo|leggi|leggerlo|puoi|pure|il|messaggio|per|favore|grazie|read|it|please|go|ahead))+$").matches(value)
}
internal fun negative(text: String): Boolean {
    val answer = answerText(text)
    // A clearly spoken refusal must never become affirmative because a model was uncertain.
    return answer.split(' ').lastOrNull() in setOf("no", "nope") ||
        Regex("\\b(?:non (?:leggi|leggere|leggerlo|leggilo)|(?:do not|don t) read)\\b").containsMatchIn(answer) ||
        answer in setOf("no grazie", "non ora", "non adesso", "non leggerlo", "annulla", "lascia stare", "no thanks", "not now", "cancel", "never mind")
}
internal fun quickConfirmationAnswer(text: String): String? = when {
    negative(text) -> "no"
    affirmative(text) -> "yes"
    anything(text) -> "anything"
    else -> null
}
internal fun anything(text: String) = answerText(text) in setOf("qualunque cosa", "qualsiasi cosa", "quello che ti pare", "come vuoi", "scegli tu", "a caso", "qualcosa di casuale", "anything", "whatever", "you choose", "surprise me")
internal fun assistantShortcut(text: String): VoiceDecision? {
    val value = answerText(text)
    if (value in setOf("che cosa puoi fare", "cosa puoi fare", "cosa posso chiederti", "quali comandi conosci", "elenca i comandi", "lista comandi", "aiuto", "help", "what can you do", "what can i ask you", "list commands")) return VoiceDecision("help")
    if (value in setOf("elenca le playlist", "lista playlist", "quali playlist ho", "che playlist ho", "dimmi le playlist", "leggi le playlist", "mostra le playlist", "list playlists", "what playlists do i have", "my playlists")) return VoiceDecision("list_playlists")
    if (anything(text) || value in setOf("metti su qualcosa", "metti qualcosa", "riproduci qualcosa", "riproduci quello che ti pare", "metti quello che ti pare", "suona qualcosa", "play something", "play anything", "play something random")) return VoiceDecision("random_music")
    Regex("(?i)^(?:chiama|telefona a|fai una chiamata a|call|phone|dial)\\s+(.+)$")
        .matchEntire(cleanVoiceText(text))?.groupValues?.get(1)?.let { return VoiceDecision("call_contact", it.take(120)) }
    return null
}

internal data class VoiceContact(val id: String, val name: String, val number: String, val label: String = "")
internal interface VoicePersonalActions {
    fun callsAllowed(): Boolean
    fun messagesAllowed(app: String? = null): Boolean
    suspend fun contacts(query: String): List<VoiceContact>
    suspend fun call(contact: VoiceContact)
}
internal data class IncomingVoiceMessage(val key: String, val app: String, val sender: String, val text: String, val receivedAt: Long)

/** Session-only conversations. Message bodies/contact numbers never enter the LLM prompt. */
internal class VoiceAssistantActions(
    private val italian: Boolean,
    private val personal: VoicePersonalActions,
    private val speak: suspend (String) -> Unit,
    private val semanticAnswer: suspend (String) -> String = { "other" },
    private val speakSequence: suspend (List<String>) -> Unit = { parts -> parts.forEach { speak(it) } },
    private val askSequence: suspend (List<String>) -> Unit = speakSequence,
) {
    private sealed interface Pending {
        data object Offline : Pending
        data object Local : Pending
        data object Mood : Pending
        data object Artist : Pending
        data class Message(val message: IncomingVoiceMessage) : Pending
        data class Contacts(val contacts: List<VoiceContact>) : Pending
        data class ConfirmContact(val contact: VoiceContact) : Pending
        data class Playlists(val names: List<String>, val nextOffset: Int) : Pending
    }
    private var pending: Pending? = null
    private var attempts = 0
    val needsFollowup get() = pending != null
    val expectsConfirmation get() = pending != null && pending !is Pending.Local && pending !is Pending.Contacts && pending !is Pending.Artist
    fun clear() { pending = null; attempts = 0 }
    private suspend fun phrase(key: String) {
        val text = VoiceFixedPhrases.get(key, italian)
        if (key in setOf("read_message", "answer_yes_no", "offline_offer", "local_name", "random_mood", "mood_playlist", "more_playlists", "artist_name"))
            askSequence(listOf(text)) else speak(text)
    }

    suspend fun announce(message: IncomingVoiceMessage) {
        if (!personal.messagesAllowed(message.app)) return
        clear()
        pending = Pending.Message(message)
        val intro = if (kotlin.random.Random.nextBoolean()) {
            if (italian) "Nuovo messaggio da ${message.sender} su ${message.app}." else "New message from ${message.sender} on ${message.app}."
        } else if (italian) "${message.sender} ti ha scritto su ${message.app}." else "${message.sender} sent you a message on ${message.app}."
        askSequence(listOf(intro, VoiceFixedPhrases.get("read_message", italian)))
    }

    suspend fun followup(text: String, client: VoiceCommandClient): Boolean {
        val state = pending ?: return false
        val answer = when {
            negative(text) -> "no"
            affirmative(text) -> "yes"
            anything(text) -> "anything"
            state is Pending.Local || state is Pending.Contacts || state is Pending.Artist || fastVoiceCommand(text) != null -> "other"
            else -> try { semanticAnswer(text) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { "other" }
        }
        if (answer == "no") { clear(); acknowledge(); return true }
        if (state is Pending.Local && immediateVoiceCommand(text) != null) { clear(); return false }
        val continueListing = state is Pending.Playlists && answerText(text) in setOf("continua", "avanti", "altre", "continue", "more")
        if (state !is Pending.Local && !continueListing && !affirmative(text) && !anything(text) &&
            fastVoiceCommand(text)?.tool?.let { it != "random_music" } == true) { clear(); return false }
        when (state) {
            Pending.Artist -> {
                if (affirmative(text) || anything(text)) { phrase("artist_name"); return true }
                val artist = voiceArtistRequest(text)?.argument?.takeIf { it.isNotBlank() } ?: text
                clear()
                playbackResponse(client.call("play_artist", JSONObject().put("query", artist.take(120))), client)
            }
            Pending.Offline -> if (answer == "yes") { pending = Pending.Local; phrase("local_name") } else retry()
            Pending.Local -> {
                voiceArtistRequest(text)?.let { request ->
                    clear()
                    playbackResponse(client.call(request.tool, request.arguments().put("localOnly", true)), client)
                    return true
                }
                if (anything(text)) { clear(); playbackResponse(client.call("play_random_music", JSONObject().put("localOnly", true)), client); return true }
                val target = playbackRequest(text) ?: text
                val name = playlistRequest(target) ?: target
                if (name.isBlank() || affirmative(text)) phrase("local_name")
                else { clear(); playbackResponse(client.call("play_local", JSONObject().put("query", name.take(200))), client) }
            }
            Pending.Mood -> if (answer == "yes" || answer == "anything") {
                clear(); playbackResponse(client.call("play_random_music"), client)
            } else {
                // Do not pretend to infer mood from titles or search for an unrelated word.
                pending = Pending.Local
                phrase("mood_playlist")
            }
            is Pending.Message -> if (answer == "yes") {
                clear()
                if (personal.messagesAllowed(state.message.app) && System.currentTimeMillis() - state.message.receivedAt in 0..120_000)
                    speakSequence(listOf(
                        if (italian) "${state.message.sender} ti scrive:" else "${state.message.sender} writes:",
                        state.message.text))
                else phrase("message_expired")
            } else retry()
            is Pending.ConfirmContact -> if (answer == "yes") { clear(); placeCall(state.contact, client) } else retry()
            is Pending.Contacts -> {
                val index = when (answerText(text)) {
                    "uno", "primo", "il primo", "la prima", "1", "first", "the first" -> 0
                    "due", "secondo", "il secondo", "la seconda", "2", "second", "the second" -> 1
                    "tre", "terzo", "il terzo", "la terza", "3", "third", "the third" -> 2
                    else -> state.contacts.indexOfFirst { answerText(it.name) == answerText(text) }
                }
                val chosen = state.contacts.getOrNull(index)
                if (chosen == null) retry() else confirmContact(chosen)
            }
            is Pending.Playlists -> if (answer == "yes" || answerText(text) in setOf("continua", "avanti", "altre", "continue", "more")) {
                if (state.names.isNotEmpty()) readPlaylists(state.names, state.nextOffset)
                else listPlaylists(client, state.nextOffset)
            } else retry()
        }
        return true
    }

    suspend fun handle(decision: VoiceDecision, client: VoiceCommandClient, heard: String = ""): Boolean {
        when (decision.tool) {
            "help" -> { clear(); phrase("help"); if (personal.callsAllowed()) phrase("help_calls"); if (personal.messagesAllowed()) phrase("help_messages") }
            "list_playlists" -> { clear(); listPlaylists(client, 0) }
            "random_music" -> { clear(); pending = Pending.Mood; phrase("random_mood") }
            "call_contact" -> {
                clear()
                if (!personal.callsAllowed()) { phrase("calls_disabled"); return true }
                val matches = personal.contacts(decision.argument).take(3)
                when {
                    matches.isEmpty() -> phrase("contact_missing")
                    matches.size == 1 && answerText(matches.first().name) == answerText(decision.argument) &&
                        assistantShortcut(heard)?.let { it.tool == "call_contact" && answerText(it.argument) == answerText(decision.argument) } == true ->
                        placeCall(matches.first(), client)
                    matches.size == 1 -> confirmContact(matches.first())
                    else -> {
                        pending = Pending.Contacts(matches)
                        askSequence(listOf((if (italian) "Quale contatto? " else "Which contact? ") +
                            matches.mapIndexed { i, contact -> "${i + 1}: ${contact.name}, ${contact.label}" }.joinToString(". ")))
                    }
                }
            }
            else -> return false
        }
        return true
    }

    suspend fun playbackResponse(response: JSONObject, client: VoiceCommandClient): Boolean {
        when {
            response.optBoolean("networkUnavailable") -> { clear(); pending = Pending.Offline; phrase("offline_offer") }
            response.optBoolean("needsArtist") -> { clear(); pending = Pending.Artist; phrase("artist_name") }
            response.optBoolean("noOtherArtistTrack") -> { clear(); phrase("no_other_artist_track") }
            response.optBoolean("noLocalContent") -> { clear(); phrase("no_local_music") }
            response.optBoolean("noRecentMusic") -> { clear(); phrase("no_recent_music") }
            response.optBoolean("noResults") -> { clear(); phrase("no_results") }
            response.optBoolean("ok") -> { clear(); phrase("playing") }
            else -> return false
        }
        return true
    }
    suspend fun handlePlaybackProblem(response: JSONObject): Boolean {
        return when {
            response.optBoolean("networkUnavailable") -> { clear(); pending = Pending.Offline; phrase("offline_offer"); true }
            response.optBoolean("needsArtist") -> { clear(); pending = Pending.Artist; phrase("artist_name"); true }
            response.optBoolean("noOtherArtistTrack") -> { clear(); phrase("no_other_artist_track"); true }
            response.optBoolean("noLocalContent") -> { clear(); phrase("no_local_music"); true }
            response.optBoolean("noRecentMusic") -> { clear(); phrase("no_recent_music"); true }
            response.optBoolean("noResults") -> { clear(); phrase("no_results"); true }
            else -> false
        }
    }
    private suspend fun confirmContact(contact: VoiceContact) {
        pending = Pending.ConfirmContact(contact)
        askSequence(listOf((if (italian) "Vuoi chiamare " else "Do you want to call ") + contact.name + " " + contact.label + "?"))
    }
    private suspend fun placeCall(contact: VoiceContact, client: VoiceCommandClient) {
        if (!personal.callsAllowed()) { phrase("calls_disabled"); return }
        client.call("pause")
        phrase("calling")
        try { personal.call(contact) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { phrase("call_failed") }
    }
    private suspend fun listPlaylists(client: VoiceCommandClient, offset: Int) {
        val result = client.call("list_playlists", JSONObject().put("offset", offset.coerceAtLeast(0)))
        val names = result.getJSONArray("playlists").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("title").take(120) } }
        if (names.isEmpty()) { clear(); phrase("no_playlists") } else readPlaylists(names, result.optInt("nextOffset", -1))
    }
    private suspend fun readPlaylists(names: List<String>, nextOffset: Int) {
        names.take(5).forEach { speak(it) }
        if (names.size > 5 || nextOffset >= 0) { pending = Pending.Playlists(names.drop(5), nextOffset); phrase("more_playlists") }
        else clear()
    }
    private suspend fun retry() {
        if (++attempts >= 2) { clear(); return }
        phrase("answer_yes_no")
    }
    private suspend fun acknowledge() = speak((if (italian) listOf("Ok.", "Capito.", "Va bene.") else listOf("Okay.", "Understood.", "All right.")).random())
}
