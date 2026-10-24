package com.futo.platformplayer.compose.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal suspend fun shareDiagnostics(context: Context) {
    val archive = CrashLogStore.createShareArchive(context)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archive)
    val send = Intent(Intent.ACTION_SEND).setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri("Diagnostics", uri) }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_diagnostics)))
}

@Composable
internal fun ShareDiagnosticsButton(onShared: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    OutlinedButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try { shareDiagnostics(context); onShared() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { android.widget.Toast.makeText(context, R.string.diagnostics_share_failed, android.widget.Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }, modifier = Modifier.testTag("share-diagnostics")) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Outlined.Share, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.share_diagnostics))
    }
}

@Composable
internal fun PreviousCrashPrompt() {
    val context = LocalContext.current
    val crash by CrashLogStore.pendingCrash.collectAsState()
    val id = crash ?: return
    PreviousCrashDialog(id, CrashLogStore.isEnabled(context)) { CrashLogStore.acknowledgeCrash(context, id) }
}

@Composable
internal fun PreviousCrashDialog(id: String, loggingEnabled: Boolean, onHandled: () -> Unit) {
    var confirmSkip by remember(id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { confirmSkip = !confirmSkip },
        icon = { Icon(Icons.Outlined.BugReport, null, Modifier.size(36.dp)) },
        title = { Text(stringResource(if (confirmSkip) R.string.skip_crash_report_title else R.string.previous_crash_title)) },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (confirmSkip) R.string.skip_crash_report_body else R.string.previous_crash_body))
            if (!confirmSkip && !loggingEnabled) Text(stringResource(R.string.previous_crash_logging_off))
        } },
        confirmButton = {
            if (confirmSkip) TextButton(onClick = onHandled) { Text(stringResource(R.string.skip_crash_report_action)) }
            else ShareDiagnosticsButton(onShared = onHandled)
        },
        dismissButton = { TextButton(onClick = { confirmSkip = !confirmSkip }) {
            Text(stringResource(if (confirmSkip) R.string.back else R.string.skip_crash_report_action))
        } },
        modifier = Modifier.testTag(if (confirmSkip) "skip-crash-confirmation" else "previous-crash-prompt"))
}
