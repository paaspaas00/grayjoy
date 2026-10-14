package com.futo.platformplayer.voice

internal sealed interface DialogueReply {
    data class Execute(val decision: VoiceDecision) : DialogueReply
    data class Ask(val kind: String, val choices: List<String> = emptyList(), val query: String = "") : DialogueReply
    data object Cancelled : DialogueReply
}

/** Slot filling is explicit: a one-word answer cannot erase the subject of the first request. */
internal class VoiceDialogue {
    private enum class Slot { Playlist, Video, Choice, ConfirmPlaylist, ConfirmVideo, Command }
    private var slot: Slot? = null
    private var subject = ""
    private var choices = emptyList<String>()
    private var choiceIds = emptyList<String>()
    private var attempts = 0
    val needsFollowup get() = slot != null
    val expectsConfirmation get() = slot in setOf(Slot.ConfirmPlaylist, Slot.ConfirmVideo)
    val acceptsBareTarget get() = slot in setOf(Slot.Playlist, Slot.Video, Slot.Choice, Slot.ConfirmPlaylist, Slot.ConfirmVideo)
    val context get() = if (slot == null) "" else "Original subject: $subject. Awaiting $slot. Choices: ${choices.joinToString()}."

    fun clear() { slot = null; subject = ""; choices = emptyList(); choiceIds = emptyList(); attempts = 0 }

    fun resolve(raw: String): DialogueReply? {
        val text = cleanVoiceText(raw)
        if (slot in setOf(Slot.ConfirmPlaylist, Slot.ConfirmVideo) &&
            text.lowercase() in setOf("no", "nope", "sbagliato", "non quella", "not that")) {
            slot = if (slot == Slot.ConfirmPlaylist) Slot.Playlist else Slot.Video
            return DialogueReply.Ask(if (slot == Slot.Playlist) "playlist_name" else "video_name")
        }
        if (isNegatedVoiceRequest(text)) return misunderstood()
        if (text.lowercase() in setOf("annulla", "lascia stare", "cancel", "never mind", "nevermind") ||
            (needsFollowup && text.equals("stop", true))) {
            clear(); return DialogueReply.Cancelled
        }
        // A new complete command always wins, even while waiting for a clarification.
        fastVoiceCommand(text)?.takeUnless { it.tool == "clarify" }?.let {
            clear(); return DialogueReply.Execute(it)
        }
        val pending = slot ?: return null
        when (pending) {
            Slot.Playlist -> {
                val query = playlistRequest(text) ?: text
                return if (query.isNotBlank()) execute("play_playlist", query) else retry("playlist_name")
            }
            Slot.Video -> {
                val playlist = playlistRequest(text)
                if (playlist != null) {
                    slot = Slot.Playlist
                    return if (playlist.isBlank()) retry("playlist_name") else execute("play_playlist", playlist)
                }
                subject = text; slot = Slot.ConfirmVideo
                return DialogueReply.Ask("video_confirm", query = text)
            }
            Slot.Choice -> {
                val ordinal = when (text.lowercase()) {
                    "uno", "prima", "la prima", "first", "the first", "1" -> 0
                    "due", "seconda", "la seconda", "second", "the second", "2" -> 1
                    "tre", "terza", "la terza", "third", "the third", "3" -> 2
                    else -> -1
                }
                val query = choiceIds.getOrNull(ordinal) ?: choices.getOrNull(ordinal) ?: playlistRequest(text) ?: text
                return if (query.isNotBlank()) execute("play_playlist", query) else DialogueReply.Ask("playlist_name")
            }
            Slot.ConfirmPlaylist, Slot.ConfirmVideo -> {
                when (text.lowercase()) {
                    "si", "sì", "yes", "esatto", "corretto", "confermo", "ok", "okay", "certo", "yeah", "correct" -> {
                        return execute(if (pending == Slot.ConfirmPlaylist) "play_playlist" else "search_play",
                            if (pending == Slot.ConfirmPlaylist) choiceIds.firstOrNull() ?: choices.first() else subject)
                    }
                    "no", "nope", "sbagliato", "non quella", "not that" -> {
                        slot = if (pending == Slot.ConfirmPlaylist) Slot.Playlist else Slot.Video
                        return DialogueReply.Ask(if (pending == Slot.ConfirmPlaylist) "playlist_name" else "video_name")
                    }
                    else -> return retry("repeat")
                }
            }
            Slot.Command -> return null
        }
    }

    private fun retry(kind: String): DialogueReply.Ask {
        if (++attempts > 2) { clear(); return DialogueReply.Ask("repeat_end") }
        return DialogueReply.Ask(kind)
    }

    fun clarify(raw: String): DialogueReply {
        if (!looksLikeVoiceCommand(raw)) return misunderstood()
        if (!needsFollowup) attempts = 0
        val request = playbackRequest(raw).orEmpty()
        val playlist = playlistRequest(request.ifBlank { cleanVoiceText(raw) })
        subject = playlist ?: request
        if (subject.isNotBlank() && subject.lowercase() !in setOf("qualcosa", "something", "un video", "a video", "video"))
            return execute(if (playlist != null) "play_playlist" else "play_named", subject)
        if (playlist != null) { slot = Slot.Playlist; return DialogueReply.Ask("playlist_name") }
        if (subject.lowercase() in setOf("un video", "a video", "video")) {
            slot = Slot.Video; return DialogueReply.Ask("video_name")
        }
        return misunderstood()
    }

    fun missingContent(): DialogueReply.Ask { clear(); slot = Slot.Video; return DialogueReply.Ask("play_name") }

    fun playlistProblem(query: String, available: List<String>, kind: String, ids: List<String> = emptyList()): DialogueReply.Ask {
        subject = query; choices = available.take(5)
        choiceIds = ids.take(choices.size)
        if (kind == "empty") { clear(); return DialogueReply.Ask("empty_playlist", query = query) }
        if (++attempts > 2) { clear(); return DialogueReply.Ask("repeat_end") }
        slot = when { choices.isEmpty() -> Slot.Playlist; kind == "confirm" && choices.size == 1 -> Slot.ConfirmPlaylist; else -> Slot.Choice }
        return DialogueReply.Ask(when (slot) { Slot.Playlist -> "missing_playlist"; Slot.ConfirmPlaylist -> "playlist_confirm"; else -> "playlist_choice" }, choices, query)
    }

    fun misunderstood(): DialogueReply.Ask {
        subject = ""; choices = emptyList(); choiceIds = emptyList()
        slot = if (++attempts <= 2) Slot.Command else null
        return DialogueReply.Ask(if (slot == null) "repeat_end" else "repeat")
    }

    private fun execute(tool: String, query: String): DialogueReply.Execute {
        // Keep the retry budget across a slot answer, but discard stale content/choices.
        slot = null; subject = ""; choices = emptyList(); choiceIds = emptyList()
        return DialogueReply.Execute(VoiceDecision(tool, query.take(200)))
    }
}
