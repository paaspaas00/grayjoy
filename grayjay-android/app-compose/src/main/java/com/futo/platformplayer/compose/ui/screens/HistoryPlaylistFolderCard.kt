package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourcePluralStringResource as pluralStringResource
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

@Composable
internal fun HistoryPlaylistFolderCard(
    entry: HistoryListEntry.Folder,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onOpenPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = compactUi()
    val width = videoCardThumbnailWidth(LocalConfiguration.current.screenWidthDp, compact).dp
    val height = width * 9f / 16f
    val label = stringResource(if (entry.expanded) R.string.collapse_history_playlist else R.string.expand_history_playlist)
    val shape = RoundedCornerShape(if (compact) 16.dp else 22.dp)
    Row(modifier.fillMaxWidth().clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
        .combinedClickable(role = Role.Button, onClickLabel = label, onClick = onToggle, onLongClick = onLongClick)
        .testTag(entry.key)
        .padding(if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(width).height(height + 10.dp)) {
            for (depth in (minOf(3, entry.videos.size) - 1) downTo 0) {
                val video = entry.videos[depth]
                val thumbShape = RoundedCornerShape(12.dp)
                Box(Modifier.offset(x = (depth * 4).dp, y = (depth * 4).dp)
                    .width(width - 8.dp).height(height)
                    .clip(thumbShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape),
                    contentAlignment = Alignment.Center) {
                    if (video.thumbnailUrl.isNotBlank()) RemoteBitmapImage(
                        video.thumbnailUrl, MaterialTheme.colorScheme.surfaceContainerHighest,
                        Modifier.fillMaxSize(), requestHeaders = video.thumbnailRequestHeaders,
                        fallbackUrl = youtubeThumbnailFallbackUrl(video.sourceId, video.id, video.thumbnailUrl),
                    ) else Icon(Icons.Outlined.Folder, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    if (depth == 0) {
                        IconButton(onClick = onToggle,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = .38f)).testTag("toggle-${entry.key}")) {
                            Icon(if (entry.expanded) Icons.Outlined.Remove else Icons.Outlined.Add,
                                label, Modifier.size(30.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (entry.collectionGrouping != null) entry.origin.title else pluralStringResource(R.plurals.history_playlist_video_count, entry.videos.size,
                entry.videos.size, entry.origin.title.ifBlank { stringResource(R.string.playlist) }),
                style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (entry.collectionGrouping != null) Text(pluralStringResource(R.plurals.video_count, entry.videos.size, entry.videos.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.collectionGrouping != LibraryGrouping.Channel) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = onOpenPlaylist, onLongClick = onLongClick, role = Role.Button)
                    .testTag("open-${entry.key}")) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).heightIn(min = 24.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.go_to_playlist), style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            }
        }
    }
}
