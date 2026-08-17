package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.CastProtocolUi
import com.futo.platformplayer.compose.ui.ChromecastUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChromecastSheet(
    state: ChromecastUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val selectedProtocol = if (selectedTab == 0) CastProtocolUi.Chromecast else CastProtocolUi.FCast
    val protocolName = if (selectedProtocol == CastProtocolUi.Chromecast) "Chromecast" else "FCast"
    val visibleDevices = state.devices.filter { it.protocol == selectedProtocol }

    LaunchedEffect(state.activeProtocol) {
        state.activeProtocol?.let { selectedTab = if (it == CastProtocolUi.Chromecast) 0 else 1 }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { grayjoySheetInsets() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.cast_to_device),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Chromecast") },
                    modifier = Modifier.testTag("cast-tab-chromecast"),
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("FCast") },
                    modifier = Modifier.testTag("cast-tab-fcast"),
                )
            }
            if (state.isConnected || state.isConnecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CastConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.activeDeviceName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                if (state.isConnected) R.string.chromecast_connected
                                else R.string.chromecast_connecting,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.isConnecting) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier.testTag("chromecast-disconnect"),
                        ) {
                            Text(stringResource(R.string.disconnect))
                        }
                    }
                }
                HorizontalDivider()
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (visibleDevices.isEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.looking_for_cast_devices, protocolName),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.cast_same_wifi, protocolName),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.available_devices),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                visibleDevices.forEach { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        leadingContent = {
                            Icon(
                                if (device.id == state.activeDeviceId) Icons.Outlined.CastConnected
                                else Icons.Outlined.Cast,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.isConnecting) { onConnect(device.id) }
                            .testTag("chromecast-device-${device.id}"),
                    )
                }
            }
        }
    }
}
