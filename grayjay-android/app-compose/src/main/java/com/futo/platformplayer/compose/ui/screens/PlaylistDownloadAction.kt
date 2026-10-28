package com.futo.platformplayer.compose.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.futo.platformplayer.compose.R

@Composable
internal fun playlistDownloadAction(active: Boolean, start: () -> Unit): () -> Unit {
    val context = LocalContext.current
    return { if (active) Toast.makeText(context, R.string.playlist_already_downloading, Toast.LENGTH_SHORT).show() else start() }
}
