package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.futo.platformplayer.compose.ui.ChannelContentTab
import com.futo.platformplayer.compose.ui.PlaylistUiModel

internal fun channelSearchUsesListingPager(tab: ChannelContentTab, query: String) = tab.isPlaylistTab || query.isBlank()

internal fun filterChannelPlaylists(playlists: List<PlaylistUiModel>, query: String): List<PlaylistUiModel> {
    val term = query.trim()
    return if (term.isEmpty()) playlists else playlists.filter {
        it.title.contains(term, ignoreCase = true) || it.description.contains(term, ignoreCase = true)
    }
}

internal data class ChannelTabAnchor(val index: Int, val offset: Int)
internal fun channelTabAnchor(firstIndex: Int, firstOffset: Int, contentPadding: Int = 0): ChannelTabAnchor = when {
    firstIndex == 0 -> ChannelTabAnchor(0, firstOffset.coerceAtLeast(0))
    firstIndex == 1 -> ChannelTabAnchor(1, firstOffset.coerceIn(0, contentPadding.coerceAtLeast(0)))
    else -> ChannelTabAnchor(1, contentPadding.coerceAtLeast(0))
}

/** A bounded short result set occupies the remaining viewport even when empty. Large sets keep
 * their original lazy items; there is no permanent blank footer or nested scrolling list. */
internal fun LazyListScope.channelResultsPane(
    bounded: Boolean,
    minimumHeight: Dp,
    spacing: Dp,
    content: LazyListScope.() -> Unit,
) {
    if (!bounded) { content(); return }
    data class Entry(val key: Any?, val content: @Composable LazyItemScope.() -> Unit)
    val entries = ArrayList<Entry>()
    val base = this
    val scope = object : LazyListScope by base {
        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
            entries.add(Entry(key, content))
        }
        override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, itemContent: @Composable LazyItemScope.(Int) -> Unit) {
            repeat(count) { index -> entries.add(Entry(key?.invoke(index)) { itemContent(index) }) }
        }
    }
    scope.content()
    item(key = "channel-results-pane", contentType = "short-channel-results") {
        val itemScope = this
        Column(Modifier.fillMaxWidth().heightIn(min = minimumHeight).testTag("channel-results-pane"),
            verticalArrangement = Arrangement.spacedBy(spacing)) {
            entries.forEachIndexed { index, entry -> key(entry.key ?: index) { entry.content(itemScope) } }
        }
    }
}
