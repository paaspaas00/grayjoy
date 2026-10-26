package com.futo.platformplayer.compose.data

import java.text.DateFormat
import java.text.ParsePosition
import java.util.Locale
import com.futo.platformplayer.compose.ui.PlaylistUiModel

/** A UI-only queue descriptor. Callers must explicitly save a normal playlist to persist it. */
internal fun temporaryPlaybackQueue(previous: PlaylistUiModel?, videoIds: List<String>, title: String, sessionId: String): PlaylistUiModel {
    val ids = videoIds.distinct()
    if (previous?.videoIds == ids && previous.title == title) return previous
    return PlaylistUiModel(previous?.id ?: sessionId, title, "", ids, isPlaybackQueue = true)
}

/** Older backups had no queue marker. Match the complete generated date, not just the prefix. */
internal fun isLegacyPlaybackQueueTitle(title: String, templates: List<String>, locales: List<Locale>): Boolean {
    val base = title.replace(Regex(" \\((?:[2-9]|[1-9][0-9]+)\\)$"), "")
    return templates.any { template ->
        val pieces = template.split("%1\$s")
        if (pieces.size != 2 || !base.startsWith(pieces[0]) || !base.endsWith(pieces[1])) return@any false
        val dateText = base.substring(pieces[0].length, base.length - pieces[1].length)
        locales.any { locale ->
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                .apply { isLenient = false }
            val position = ParsePosition(0)
            val date = format.parse(dateText, position)
            date != null && position.index == dateText.length && format.format(date) == dateText
        }
    }
}
