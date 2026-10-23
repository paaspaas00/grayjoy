package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.ui.YoutubeImportUiState

@Composable
internal fun YoutubeImportProgress(state: YoutubeImportUiState) {
    Column(Modifier.fillMaxWidth().testTag("youtube-import-progress"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.progressLabel(), style = MaterialTheme.typography.titleMedium)
        val progress = state.progressFraction()
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}
