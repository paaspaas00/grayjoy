package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R

@Composable
internal fun AudioNormalizationLevelDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var pending by remember(current) { mutableIntStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.audio_normalization_level)) },
        text = {
            Column(Modifier.selectableGroup().testTag("audio-normalization-level-dialog")) {
                listOf(-32, -14).forEachIndexed { index, level ->
                    Row(Modifier.fillMaxWidth().dialogChoiceRow(index, 2).selectable(pending == level, role = Role.RadioButton,
                        onClick = { pending = level }).padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("normalization-level-$level"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(pending == level, onClick = null)
                        Column {
                            Text("$level LUFS", style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (level == -32) R.string.normalization_quieter else R.string.normalization_louder),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pending) }, modifier = Modifier.testTag("normalization-level-confirm")) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
