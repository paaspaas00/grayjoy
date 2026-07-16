package com.futo.platformplayer.compose.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.SourceAvailability
import com.futo.platformplayer.compose.ui.SourceUiModel
import java.util.Locale

internal fun visibleSourcesForQuery(
    sources: List<SourceUiModel>,
    query: String,
): List<SourceUiModel> = sources
    .filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    .sortedWith(
        compareBy<SourceUiModel>(
            { if (it.id.equals("youtube", true) || it.name.equals("youtube", true)) 0 else 1 },
            { it.name.lowercase(Locale.ROOT) },
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    sources: List<SourceUiModel>,
    isOperationInProgress: Boolean,
    operationMessage: String?,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onInstallSource: (String) -> Unit,
    onScanSourceQr: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onClearSourceCache: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onLoginSource: (SourceUiModel) -> Unit,
    onLogoutSource: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSource = sources.firstOrNull { it.id == selectedSourceId }
    val visibleSources = visibleSourcesForQuery(sources, query)

    LazyColumn(
        modifier = Modifier.testTag("sources-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val activeSourceCount = sources.count {
                            it.isEnabled && it.availability != SourceAvailability.MissingPlugin
                        }
                        Text(
                            pluralStringResource(
                                R.plurals.active_sources,
                                activeSourceCount,
                                activeSourceCount,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.installed_sources,
                                sources.size,
                                sources.size,
                            ),
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAddDialog = true },
                        enabled = !isOperationInProgress,
                        modifier = Modifier
                            .widthIn(min = 132.dp)
                            .testTag("add-source"),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(stringResource(R.string.add))
                    }
                }
            }
        }
        if (isOperationInProgress || operationMessage != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isOperationInProgress) CircularProgressIndicator(Modifier.size(24.dp))
                    Text(
                        operationMessage ?: stringResource(R.string.installing_source),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                label = { Text(stringResource(R.string.find_source)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.clear_source_search),
                            )
                        }
                    }
                },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.engine_sources), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.engine_sources_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(visibleSources, key = { _, source -> source.id }) { index, source ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SourceCard(
                    source = source,
                    onClick = { selectedSourceId = source.id },
                    onEnabledChange = { enabled -> onSourceEnabledChange(source.id, enabled) },
                )
                if (
                    index < visibleSources.lastIndex &&
                    (source.id.equals("youtube", true) || source.name.equals("youtube", true))
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        if (visibleSources.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_sources_match, query),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            isBusy = isOperationInProgress,
            onDismiss = { showAddDialog = false },
            onInstall = { url ->
                showAddDialog = false
                onInstallSource(url)
            },
            onScanQr = {
                showAddDialog = false
                onScanSourceQr()
            },
        )
    }

    selectedSource?.let { source ->
        SourceOptionsSheet(
            source = source,
            isBusy = isOperationInProgress,
            onDismiss = { selectedSourceId = null },
            onEnabledChange = { onSourceEnabledChange(source.id, it) },
            onRefresh = { onRefreshSource(source.id) },
            onClearCache = { onClearSourceCache(source.id) },
            onLogin = { onLoginSource(source) },
            onLogout = { onLogoutSource(source.id) },
            onRemove = {
                selectedSourceId = null
                onRemoveSource(source.id)
            },
        )
    }
}

@Composable
private fun SourceCard(
    source: SourceUiModel,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val canEnable = source.availability != SourceAvailability.MissingPlugin
    val status = when (source.availability) {
        SourceAvailability.PluginAvailable -> stringResource(
            if (source.isEnabled) R.string.source_enabled else R.string.source_disabled,
        )
        SourceAvailability.LocalIndex -> stringResource(
            if (source.isEnabled) R.string.source_enabled_local else R.string.source_disabled_local,
        )
        SourceAvailability.MissingPlugin -> stringResource(R.string.plugin_payload_unavailable)
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SourceIconImage(
                name = source.name,
                iconUrl = source.iconUrl,
                accentColor = Color(source.accentColor),
                modifier = Modifier.size(50.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    source.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    status,
                    color = if (source.isEnabled && canEnable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Switch(
                checked = source.isEnabled && canEnable,
                onCheckedChange = onEnabledChange,
                enabled = canEnable,
                modifier = Modifier.testTag("source-${source.id}"),
            )
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.source_options, source.name),
            )
        }
    }
}

@Composable
private fun AddSourceDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onScanQr: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val isValid = url.trim().let {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("grayjay://plugin/", ignoreCase = true) ||
            it.startsWith("vfuto://", ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_grayjay_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.add_source_description),
                )
                OutlinedButton(
                    onClick = onScanQr,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan-source-qr"),
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text(
                        stringResource(R.string.scan_source_qr),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("source-url-field"),
                    label = { Text(stringResource(R.string.source_config_url)) },
                    placeholder = { Text(stringResource(R.string.source_config_url_hint)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onInstall(url.trim()) },
                enabled = isValid && !isBusy,
                modifier = Modifier.testTag("install-source"),
            ) {
                Text(stringResource(R.string.install))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceOptionsSheet(
    source: SourceUiModel,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.grayjay_source_clipboard_label)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceIconImage(
                    name = source.name,
                    iconUrl = source.iconUrl,
                    accentColor = Color(source.accentColor),
                    modifier = Modifier.size(56.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(source.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        source.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = source.isEnabled,
                    onCheckedChange = onEnabledChange,
                    enabled = source.availability != SourceAvailability.MissingPlugin && !isBusy,
                )
            }
            HorizontalDivider()
            SourceOption(
                title = stringResource(
                    if (source.isAuthenticated) R.string.sign_out else R.string.log_in_to_source,
                ),
                body = if (source.isAuthenticated) {
                    stringResource(R.string.sign_out_source_description)
                } else {
                    stringResource(R.string.log_in_source_description)
                },
                icon = {
                    Icon(
                        if (source.isAuthenticated) {
                            Icons.AutoMirrored.Outlined.Logout
                        } else {
                            Icons.AutoMirrored.Outlined.Login
                        },
                        contentDescription = null,
                    )
                },
                enabled = source.pluginConfigUrl.isNotBlank() && !isBusy,
                onClick = if (source.isAuthenticated) onLogout else onLogin,
            )
            SourceOption(
                title = stringResource(R.string.update_or_reinstall),
                body = stringResource(R.string.update_source_description),
                icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                enabled = source.pluginConfigUrl.isNotBlank() && !isBusy,
                onClick = onRefresh,
            )
            SourceOption(
                title = stringResource(R.string.clear_plugin_cache),
                body = stringResource(R.string.clear_plugin_cache_description),
                icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                enabled = !isBusy,
                onClick = onClearCache,
            )
            if (source.pluginConfigUrl.isNotBlank()) {
                SourceOption(
                    title = stringResource(R.string.copy_config_url),
                    body = source.pluginConfigUrl,
                    icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                clipboardLabel,
                                source.pluginConfigUrl,
                            ),
                        )
                    },
                )
            }
            if (source.isCustom) {
                SourceOption(
                    title = stringResource(R.string.remove_source),
                    body = stringResource(R.string.remove_source_description),
                    icon = {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    enabled = !isBusy,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun SourceOption(
    title: String,
    body: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = icon,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
    )
}
