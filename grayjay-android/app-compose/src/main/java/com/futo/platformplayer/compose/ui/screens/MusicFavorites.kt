package com.futo.platformplayer.compose.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.futo.platformplayer.compose.R

internal val LocalVideoLike = staticCompositionLocalOf<(String) -> Unit> { {} }
@Composable internal fun watchLaterIcon(selected: Boolean = false): ImageVector =
    if (LocalMusicPresentation.current) { if (selected) Icons.Outlined.Star else Icons.Outlined.StarBorder }
    else Icons.Outlined.BookmarkBorder

internal fun musicTerminologyResource(id: Int, music: Boolean): Int = if (!music) id else when (id) {
    R.string.videos -> R.string.music_tracks
    R.string.creators, R.string.channels_label -> R.string.music_artists
    R.string.creator, R.string.channel_label -> R.string.music_artist
    R.string.watch_later -> R.string.music_favorites
    R.string.add_watch_later -> R.string.add_music_favorite
    R.string.remove_watch_later -> R.string.remove_music_favorite
    R.string.clear_video_search -> R.string.clear_search
    R.string.search_channel_videos -> R.string.music_artist_tracks_search
    R.string.no_channel_videos_match -> R.string.music_artist_tracks_no_match
    R.string.video_title_language -> R.string.music_track_title_language
    R.string.channel_playback_speed_description -> R.string.music_artist_playback_speed_description
    R.string.per_channel_playback_speed_description -> R.string.music_per_artist_playback_speed_description
    R.string.search_hint, R.string.search_channels_hint -> R.string.music_search_hint
    else -> id
}
