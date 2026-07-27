package com.futo.platformplayer.compose.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.PcLinkUiState
import com.futo.platformplayer.compose.ui.PcPlaybackUiModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
internal fun PcPlaybackBanner(
    playback: PcPlaybackUiModel,
    onPlayHere: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(playback.computerId) { mutableStateOf(false) }
    var scrubPositionMs by remember(
        playback.computerId,
        playback.videoTitle,
        playback.durationMs,
    ) {
        mutableStateOf<Long?>(null)
    }
    var pendingSeekMs by remember(
        playback.computerId,
        playback.videoTitle,
        playback.durationMs,
    ) {
        mutableStateOf<Long?>(null)
    }
    var pendingSeekStartedAtMs by remember { mutableLongStateOf(0L) }
    var clockNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(expanded, playback.isPlaying, playback.receivedAtMs) {
        do {
            clockNowMs = System.currentTimeMillis()
            if (!expanded || !playback.isPlaying) break
            delay(PC_POSITION_TICK_MS)
        } while (true)
    }
    LaunchedEffect(
        pendingSeekMs,
        playback.positionMs,
        playback.receivedAtMs,
        playback.isPlaying,
    ) {
        val requested = pendingSeekMs ?: return@LaunchedEffect
        while (pendingSeekMs == requested) {
            val now = System.currentTimeMillis()
            if (
                pcSeekConfirmed(
                    requestedPositionMs = requested,
                    requestedAtMs = pendingSeekStartedAtMs,
                    pcPositionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    isPlaying = playback.isPlaying,
                    pcReceivedAtMs = playback.receivedAtMs,
                    nowMs = now,
                )
            ) {
                pendingSeekMs = null
                break
            }
            delay(PC_POSITION_TICK_MS)
        }
    }

    val remotePositionMs = estimatedPcPlaybackPosition(
        positionMs = playback.positionMs,
        durationMs = playback.durationMs,
        isPlaying = playback.isPlaying,
        receivedAtMs = playback.receivedAtMs,
        nowMs = clockNowMs,
    )
    val displayedPositionMs = (
        scrubPositionMs ?: pendingSeekMs ?: remotePositionMs
        ).coerceIn(0L, playback.durationMs.coerceAtLeast(0L))

    Card(
        onClick = { expanded = !expanded },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
            .testTag("pc-playback-banner"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Computer, contentDescription = null)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.on_pc),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        playback.videoTitle.ifBlank { playback.title },
                        maxLines = 2,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        playback.computerName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!expanded) {
                    PcRemoteTransportControls(
                        isPlaylist = playback.isPlaylist,
                        isPlaying = playback.isPlaying,
                        onTogglePlayback = onTogglePlayback,
                        onPrevious = onPrevious,
                        onNext = onNext,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier.testTag("pc-playback-expanded-controls"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (playback.durationMs > 0L) {
                        Slider(
                            value = displayedPositionMs.toFloat(),
                            onValueChange = {
                                scrubPositionMs = it.roundToLong()
                                    .coerceIn(0L, playback.durationMs)
                            },
                            onValueChangeFinished = {
                                val target = scrubPositionMs ?: displayedPositionMs
                                pendingSeekMs = target
                                pendingSeekStartedAtMs = System.currentTimeMillis()
                                scrubPositionMs = null
                                onSeek(target)
                            },
                            valueRange = 0f..playback.durationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.22f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pc-remote-seek"),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                formatPcPlaybackTime(displayedPositionMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                formatPcPlaybackTime(playback.durationMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    PcRemoteTransportControls(
                        isPlaylist = playback.isPlaylist,
                        isPlaying = playback.isPlaying,
                        onTogglePlayback = onTogglePlayback,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onPlayHere = onPlayHere,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    )
                }
            }
        }
    }
}

@Composable
private fun PcRemoteTransportControls(
    isPlaylist: Boolean,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayHere: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaylist) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.testTag("pc-remote-previous"),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_video),
                )
            }
        }
        IconButton(
            onClick = onTogglePlayback,
            modifier = Modifier.testTag("pc-remote-toggle"),
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.pause else R.string.play,
                ),
            )
        }
        if (isPlaylist) {
            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("pc-remote-next"),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.next_video),
                )
            }
        }
        onPlayHere?.let { playHere ->
            IconButton(
                onClick = playHere,
                modifier = Modifier.testTag("pc-play-here"),
            ) {
                Icon(
                    Icons.Outlined.PhoneAndroid,
                    contentDescription = stringResource(R.string.play_here),
                )
            }
        }
    }
}

internal fun estimatedPcPlaybackPosition(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    receivedAtMs: Long,
    nowMs: Long,
): Long {
    val base = positionMs.coerceAtLeast(0L)
    val elapsed = if (isPlaying) (nowMs - receivedAtMs).coerceAtLeast(0L) else 0L
    val estimated = base + elapsed
    return if (durationMs > 0L) estimated.coerceIn(0L, durationMs) else estimated
}

internal fun pcSeekConfirmed(
    requestedPositionMs: Long,
    requestedAtMs: Long,
    pcPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    pcReceivedAtMs: Long,
    nowMs: Long,
): Boolean {
    if (nowMs - requestedAtMs >= PC_SEEK_CONFIRM_TIMEOUT_MS) return true
    if (pcReceivedAtMs < requestedAtMs) return false
    val confirmedPosition = estimatedPcPlaybackPosition(
        positionMs = pcPositionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        receivedAtMs = pcReceivedAtMs,
        nowMs = nowMs,
    )
    return abs(confirmedPosition - requestedPositionMs) <= PC_SEEK_CONFIRM_TOLERANCE_MS
}

private fun formatPcPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val PC_POSITION_TICK_MS = 250L
private const val PC_SEEK_CONFIRM_TOLERANCE_MS = 2_000L
private const val PC_SEEK_CONFIRM_TIMEOUT_MS = 6_000L

@Composable
internal fun PairedComputersDialog(
    pcLink: PcLinkUiState,
    onScanQr: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Computer, contentDescription = null) },
        title = { Text(stringResource(R.string.paired_computers)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.paired_computers_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onScanQr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan-pc-pairing-qr"),
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text(
                        stringResource(R.string.scan_pairing_qr),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.phone_lan_address),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (pcLink.serverAddresses.isEmpty()) {
                            Text(
                                stringResource(R.string.connect_phone_to_wifi),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            pcLink.serverAddresses.forEach { address ->
                                Text(
                                    address,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (pcLink.pairedComputers.isEmpty()) {
                    Text(
                        stringResource(R.string.no_paired_computers),
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .testTag("paired-computers-list"),
                    ) {
                        items(pcLink.pairedComputers, key = { it.id }) { computer ->
                            ListItem(
                                headlineContent = { Text(computer.name) },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            if (computer.isConnected) {
                                                R.string.connected
                                            } else {
                                                R.string.not_connected
                                            },
                                        ),
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Outlined.Computer, contentDescription = null)
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { onRemove(computer.id) },
                                        modifier = Modifier.testTag("remove-paired-computer-${computer.id}"),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                R.string.remove_paired_computer,
                                                computer.name,
                                            ),
                                        )
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
