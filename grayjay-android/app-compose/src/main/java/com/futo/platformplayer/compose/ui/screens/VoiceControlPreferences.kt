package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.voice.VoiceAccess

@Composable
internal fun VoiceControlPreferences(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val installed = VoiceAccess.isInstalled(context)
    var enabled by remember { mutableStateOf(VoiceAccess.enabled(context)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.voice_control)) },
        text = { Column {
            if (!installed) Text(stringResource(R.string.voice_companion_missing))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.voice_control_enable))
                AdaptiveSwitch(checked = enabled, enabled = installed, onCheckedChange = {
                    VoiceAccess.setEnabled(context, it); enabled = it
                })
            }
        } }, confirmButton = {
            TextButton(enabled = installed, onClick = { VoiceAccess.open(context); onDismiss() }) {
                Text(stringResource(R.string.voice_control_open))
            }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
