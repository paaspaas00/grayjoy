package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.DatabaseImportFormat
import com.futo.platformplayer.compose.ui.DatabaseImportSelection
import com.futo.platformplayer.compose.ui.DatabaseImportUiState

@Composable
fun DatabaseImportDialogs(
    state: DatabaseImportUiState,
    onDismiss: () -> Unit,
    onPasswordSubmit: (String) -> Unit,
    onConfirm: (DatabaseImportSelection) -> Unit,
) {
    when {
        state.isBusy -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = { CircularProgressIndicator() },
            title = {
                Text(
                    stringResource(
                        if (state.preview == null && state.format == DatabaseImportFormat.NewPipe) {
                            R.string.reading_newpipe_backup
                        } else if (state.preview == null) R.string.reading_grayjay_backup
                        else R.string.importing_database,
                    ),
                )
            },
            text = { Text(state.fileName.ifBlank { stringResource(R.string.please_wait) }) },
        )
        state.passwordRequired -> PasswordDialog(
            fileName = state.fileName,
            errorMessage = state.errorMessage,
            onDismiss = onDismiss,
            onSubmit = onPasswordSubmit,
        )
        state.preview != null -> ImportPreviewDialog(
            state = state,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
        state.errorMessage != null -> MessageDialog(
            title = stringResource(R.string.import_failed),
            message = state.errorMessage,
            onDismiss = onDismiss,
        )
        state.resultMessage != null -> MessageDialog(
            title = stringResource(R.string.import_complete),
            message = state.resultMessage,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun PasswordDialog(
    fileName: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by remember(fileName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.encrypted_grayjay_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_password_prompt, fileName))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(32) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("database-import-password"),
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            Button(
                onClick = { onSubmit(password) },
                enabled = password.toByteArray().size in 4..32,
                modifier = Modifier.testTag("database-import-unlock"),
            ) { Text(stringResource(R.string.unlock)) }
        },
    )
}

@Composable
private fun ImportPreviewDialog(
    state: DatabaseImportUiState,
    onDismiss: () -> Unit,
    onConfirm: (DatabaseImportSelection) -> Unit,
) {
    val preview = requireNotNull(state.preview)
    var sources by remember(preview) { mutableStateOf(preview.sourceCount > 0) }
    var pluginSettings by remember(preview) { mutableStateOf(preview.pluginSettingsCount > 0) }
    var subscriptions by remember(preview) { mutableStateOf(preview.subscriptionCount > 0) }
    var watchLater by remember(preview) { mutableStateOf(preview.watchLaterCount > 0) }
    var playlists by remember(preview) { mutableStateOf(preview.playlistCount > 0) }
    var history by remember(preview) { mutableStateOf(preview.historyCount > 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("database-import-preview"),
        title = {
            Text(
                stringResource(
                    if (preview.format == DatabaseImportFormat.NewPipe) {
                        R.string.import_newpipe_database_question
                    } else {
                        R.string.import_grayjay_database_question
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preview.fileName, style = MaterialTheme.typography.titleSmall)
                if (preview.sourceCount > 0) ImportChoice(
                    stringResource(R.string.nav_sources),
                    pluralStringResource(
                        R.plurals.plugin_configurations,
                        preview.sourceCount,
                        preview.sourceCount,
                    ),
                    sources,
                    { sources = it }, "import-sources",
                )
                if (preview.pluginSettingsCount > 0) ImportChoice(
                    stringResource(R.string.source_settings),
                    pluralStringResource(
                        R.plurals.plugin_settings_records,
                        preview.pluginSettingsCount,
                        preview.pluginSettingsCount,
                    ),
                    pluginSettings,
                    { pluginSettings = it }, "import-plugin-settings",
                )
                if (preview.subscriptionCount > 0) ImportChoice(
                    stringResource(R.string.subscriptions),
                    pluralStringResource(
                        R.plurals.followed_creators,
                        preview.subscriptionCount,
                        preview.subscriptionCount,
                    ),
                    subscriptions,
                    { subscriptions = it }, "import-subscriptions",
                )
                if (preview.watchLaterCount > 0) ImportChoice(
                    stringResource(R.string.watch_later),
                    pluralStringResource(
                        R.plurals.video_count,
                        preview.watchLaterCount,
                        preview.watchLaterCount,
                    ),
                    watchLater,
                    { watchLater = it }, "import-watch-later",
                )
                if (preview.playlistCount > 0) ImportChoice(
                    stringResource(R.string.playlists),
                    pluralStringResource(
                        R.plurals.playlist_count,
                        preview.playlistCount,
                        preview.playlistCount,
                    ),
                    playlists,
                    { playlists = it }, "import-playlists",
                )
                if (preview.historyCount > 0) ImportChoice(
                    stringResource(R.string.history),
                    pluralStringResource(
                        R.plurals.watched_videos,
                        preview.historyCount,
                        preview.historyCount,
                    ),
                    history,
                    { history = it }, "import-history",
                )
                if (preview.hasLegacySettings) {
                    Text(
                        stringResource(R.string.legacy_settings_notice),
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(
                        if (preview.format == DatabaseImportFormat.NewPipe) {
                            R.string.newpipe_import_merge_notice
                        } else {
                            R.string.import_merge_notice
                        },
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        DatabaseImportSelection(
                            importSources = sources,
                            importPluginSettings = pluginSettings,
                            importSubscriptions = subscriptions,
                            importWatchLater = watchLater,
                            importPlaylists = playlists,
                            importHistory = history,
                        ),
                    )
                },
                enabled = sources || pluginSettings || subscriptions || watchLater || playlists || history,
                modifier = Modifier.testTag("database-import-confirm"),
            ) { Text(stringResource(R.string.import_action)) }
        },
    )
}

@Composable
private fun ImportChoice(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
