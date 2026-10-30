package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.data.playlistTitleExists
import com.futo.platformplayer.compose.ui.PlaylistUiModel

internal data class TemporaryQueueActions(
    val queue: PlaylistUiModel? = null,
    val savedNames: List<String> = emptyList(),
    val onSave: (String, List<String>) -> Unit = { _, _ -> },
    val onPlay: ((String) -> Unit)? = null,
)
internal val LocalTemporaryQueue = compositionLocalOf { TemporaryQueueActions() }

@Composable
internal fun QueueSectionHeader(modifier: Modifier = Modifier) {
    val actions = LocalTemporaryQueue.current
    val queue = actions.queue
    var showSave by rememberSaveable(queue?.id) { mutableStateOf(false) }
    var name by rememberSaveable(queue?.id) { mutableStateOf("") }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (queue != null) R.string.temporary_queue_title else R.string.queue),
            Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (queue != null) TextButton(onClick = { showSave = true }, modifier = Modifier.testTag("save-playback-queue")) {
            Text(stringResource(R.string.save_as_playlist))
        }
    }
    if (showSave && queue != null) {
        val duplicate = playlistTitleExists(name, actions.savedNames)
        AlertDialog(onDismissRequest = { showSave = false },
            title = { Text(stringResource(R.string.save_as_playlist)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.temporary_queue_description))
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) }, isError = duplicate,
                    supportingText = if (duplicate) {{ Text(stringResource(R.string.playlist_name_already_exists)) }} else null,
                    modifier = Modifier.fillMaxWidth().testTag("save-queue-name"))
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank() && !duplicate,
                onClick = { actions.onSave(name.trim(), queue.videoIds); showSave = false },
                modifier = Modifier.testTag("confirm-save-queue")) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
