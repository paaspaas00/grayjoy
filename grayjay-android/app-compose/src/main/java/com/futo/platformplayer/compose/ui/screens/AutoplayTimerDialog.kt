package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource

@Composable
internal fun AutoplayTimerDialog(initial: Int, save: (Int) -> Unit, dismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val seconds = text.toIntOrNull()?.takeIf { it in 0..120 }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.youtube_autoplay_timer)) },
        text = { Column {
            Text(stringResource(R.string.youtube_autoplay_timer_description))
            OutlinedTextField(text, { text = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth().testTag("autoplay-timer-input"), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = seconds == null)
        } },
        confirmButton = { TextButton(enabled = seconds != null, onClick = { seconds?.let(save); dismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
