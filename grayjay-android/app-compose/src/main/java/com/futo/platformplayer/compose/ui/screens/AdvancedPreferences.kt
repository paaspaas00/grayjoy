package com.futo.platformplayer.compose.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.BuildConfig
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.SourceUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal data class ExtraPreferences(
    val brainrotEnabled: Boolean = false,
    val onBrainrotChange: (Boolean) -> Unit = {},
    val rebuildingCaches: Boolean = false,
    val onRebuildCaches: () -> Unit = {},
)
internal val LocalExtraPreferences = compositionLocalOf { ExtraPreferences() }

@Composable
internal fun AdvancedPreferencesDialog(sources: List<SourceUiModel>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val options = LocalExtraPreferences.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val versions by produceState<Map<String, String>?>(null, sources) {
        value = withContext(Dispatchers.IO) { sources.associate { it.id to pluginVersion(context, it) } }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_settings)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 600.dp).testTag("advanced-options-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.rebuild_cache_description), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { confirmClear = true }, enabled = !options.rebuildingCaches, modifier = Modifier.testTag("rebuild-content-caches")) {
                        Text(stringResource(R.string.rebuild_content_caches))
                    }
                    if (options.rebuildingCaches) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                item {
                    Text(stringResource(R.string.engine_versions), style = MaterialTheme.typography.titleSmall)
                    Text("Grayjay · ${BuildConfig.GRAYJAY_ENGINE_REVISION.take(8)}${if (BuildConfig.GRAYJAY_ENGINE_REVISION.endsWith('+')) "+" else ""}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.engine_revision_explanation), style = MaterialTheme.typography.bodySmall)
                    Text("NewPipe ${BuildConfig.NEWPIPE_ENGINE_VERSION} · ${BuildConfig.NEWPIPE_ENGINE_REVISION.take(8)}", style = MaterialTheme.typography.bodySmall)
                }
                item { Text(stringResource(R.string.plugin_versions), style = MaterialTheme.typography.titleSmall) }
                if (versions == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else items(sources, key = { it.id }) { source ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(source.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(versions?.get(source.id).orEmpty().ifBlank { stringResource(R.string.version_unavailable) }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.rebuild_content_caches)) },
        text = { Text(stringResource(R.string.rebuild_cache_description)) },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button(onClick = { confirmClear = false; options.onRebuildCaches() }) { Text(stringResource(R.string.ok)) } },
    )
}

private fun pluginVersion(context: Context, source: SourceUiModel): String {
    val versions = mutableListOf<Int>()
    source.pluginConfigPath?.let { path -> runCatching {
        versions += context.assets.open(path).bufferedReader().use { JSONObject(it.readText()).getInt("version") }
    } }
    val root = File(context.filesDir, "grayjay-js-plugins").canonicalFile
    val directory = File(root, source.engineId).canonicalFile
    if (directory.parentFile == root) runCatching {
        val config = File(directory, "config.json")
        val json = if (config.isFile) {
            require(config.length() <= 1024 * 1024)
            JSONObject(config.readText())
        } else {
            val payload = File(directory, "payload.json")
            require(payload.length() <= 3 * 1024 * 1024)
            JSONObject(JSONObject(payload.readText()).getString("config"))
        }
        versions += json.getInt("version")
    }
    return versions.maxOrNull()?.toString().orEmpty()
}
