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
    R.string.watch_later -> R.string.music_favorites
    R.string.add_watch_later -> R.string.add_music_favorite
    R.string.remove_watch_later -> R.string.remove_music_favorite
    else -> id
}
