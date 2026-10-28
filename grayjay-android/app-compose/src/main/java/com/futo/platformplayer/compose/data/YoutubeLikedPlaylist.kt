package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.ui.PlaylistUiModel
import java.net.URI

internal const val YOUTUBE_LIKED_LOCAL_ID = "account:https://www.youtube.com/playlist?list=LL"
internal fun isYoutubeLikedPlaylistKey(id: String): Boolean = runCatching {
    val uri = URI(id.removePrefix("account:"))
    uri.host?.lowercase()?.let { it == "youtube.com" || it.endsWith(".youtube.com") } == true &&
        uri.rawQuery.orEmpty().split('&').any { it == "list=LL" }
}.getOrDefault(false)

internal fun isYoutubeLikedPlaylist(playlist: PlaylistUiModel, localizedTitle: String): Boolean =
    isYoutubeLikedPlaylistKey(playlist.id) || (playlist.sourceId.isBlank() &&
        listOf(localizedTitle, "Liked videos", "Video piaciuti", "Polubione filmy").any { playlist.title.equals(it, true) })
