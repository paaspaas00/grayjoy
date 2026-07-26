package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.PcLinkUiState
import com.futo.platformplayer.compose.ui.PcPlaybackUiModel

@Composable
internal fun PcPlaybackBanner(
    playback: PcPlaybackUiModel,
    onPlayHere: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pc-playback-banner"),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
            FilledTonalButton(
                onClick = onPlayHere,
                modifier = Modifier.testTag("pc-play-here"),
            ) {
                Text(stringResource(R.string.play_here))
            }
        }
    }
}

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
