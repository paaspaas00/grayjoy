package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ChannelUiModel

@Composable
internal fun CompactChannelHeader(channel: ChannelUiModel, following: Boolean, onToggleFollow: () -> Unit, actions: List<PlaylistMenuAction>) {
    var showActions by rememberSaveable(channel.id) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChannelAvatarImage(channel.name, channel.thumbnailUrl, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.followerCount, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleFollow, modifier = Modifier.size(48.dp).testTag("channel-follow")) {
                Icon(if (following) Icons.Outlined.Check else Icons.Outlined.PersonAdd,
                    modifier = Modifier.size(20.dp), tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(if (following) R.string.following else R.string.follow))
            }
            IconButton(onClick = { showActions = true }, modifier = Modifier.testTag("channel-more-actions")) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.details))
            }
        }
    }
    if (showActions) CompactActionsSheet(
        title = channel.name,
        description = "${channel.source} • ${channel.followerCount}\n\n${channel.description}".trim(),
        actions = actions,
        onDismiss = { showActions = false },
    )
}
