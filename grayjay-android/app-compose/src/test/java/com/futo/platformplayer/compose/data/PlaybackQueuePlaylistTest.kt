package com.futo.platformplayer.compose.data

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueuePlaylistTest {
    private val templates = listOf("Played on %1\$s", "Riprodotti il %1\$s", "%1\$s で再生されました")
    private val locales = listOf(Locale.US, Locale.ITALIAN, Locale.JAPANESE)

    @Test fun oldGeneratedTitlesRemainQueuePlaylistsAfterUpgrade() {
        locales.forEachIndexed { index, locale ->
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(1_790_000_000_000L))
            val title = templates[index].replace("%1\$s", date)
            assertTrue(isLegacyPlaybackQueueTitle(title, templates, locales))
            assertTrue(isLegacyPlaybackQueueTitle("$title (2)", templates, locales))
            assertTrue(isLegacyPlaybackQueueTitle("$title (12)", templates, locales))
        }
    }

    @Test fun ordinaryTitlesAreNotTreatedAsAutoQueues() {
        listOf("Road trip", "Riprodotti il mare", "Played on piano", "Played on yesterday", "Played on 99/99/9999")
            .forEach { assertFalse(isLegacyPlaybackQueueTitle(it, templates, locales)) }
    }
}
