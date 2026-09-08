package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

internal data class PlaylistMenuAction(
    val title: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val subtitle: String? = null,
    val tag: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactPlaylistHeader(
    title: String,
    description: String,
    playEnabled: Boolean,
    playTag: String,
    onPlayAll: () -> Unit,
    actions: List<PlaylistMenuAction>,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPlayAll, enabled = playEnabled, modifier = Modifier.weight(1f).testTag(playTag)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(stringResource(R.string.play_all), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
        }
        FilledTonalIconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp).testTag("playlist-more-actions")) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.details))
        }
    }
    if (expanded) {
        CompactActionsSheet(title, description, actions, onDismiss = { expanded = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactActionsSheet(title: String, description: String, actions: List<PlaylistMenuAction>, onDismiss: () -> Unit) {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { grayjoySheetInsets() }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                actions.forEach { action ->
                    ListItem(
                        modifier = Modifier.testTag(action.tag).clickable(enabled = action.enabled) {
                            onDismiss()
                            action.onClick()
                        },
                        headlineContent = { Text(action.title) },
                        supportingContent = action.subtitle?.let { subtitle -> { Text(subtitle) } },
                        leadingContent = { Icon(action.icon, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                    )
                }
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
}
