package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.SourceTrustRequestUiModel

@Composable
fun SourceTrustDialog(
    request: SourceTrustRequestUiModel,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        modifier = Modifier.testTag("source-signature-dialog"),
        title = { Text(stringResource(R.string.signature_mismatch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.signature_mismatch_description, request.pluginName),
                )
                Text(
                    stringResource(
                        R.string.publisher_label,
                        request.publisher.ifBlank { stringResource(R.string.unknown_publisher) },
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (request.publicKeyFingerprint.isNotBlank()) {
                    Text(
                        stringResource(R.string.signing_key_label, request.publicKeyFingerprint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.trust_publisher_question),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onReject,
                modifier = Modifier.testTag("reject-unverified-source"),
            ) { Text(stringResource(R.string.no)) }
        },
        confirmButton = {
            Button(
                onClick = onTrust,
                modifier = Modifier.testTag("trust-unverified-source"),
            ) { Text(stringResource(R.string.yes)) }
        },
    )
}
