package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.futo.platformplayer.compose.ui.sourceStringResource as stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.data.YoutubePlaybackAccounts
import com.futo.platformplayer.compose.ui.LocalProfileNavigation
import com.futo.platformplayer.compose.ui.ProfileUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalYoutubePlaybackAccountChanged = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun YoutubePlaybackAccountPreference() {
    val context = LocalContext.current
    val profile = LocalProfileNavigation.current
    val accounts = remember(context) { YoutubePlaybackAccounts(context) }
    val changed = LocalYoutubePlaybackAccountChanged.current
    val scope = rememberCoroutineScope()
    var linked by remember(profile.current) { mutableStateOf<String?>(null) }
    var candidates by remember(profile.current) { mutableStateOf(emptyList<ProfileUiModel>()) }
    var visible by remember(profile.current) { mutableStateOf(false) }
    var draft by remember(profile.current) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(profile.current, profile.profiles, visible) {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) { accounts.linkedProfile(profile.current) to accounts.candidates(profile.current) }
            linked = result.first; candidates = result.second
            if (visible) draft = linked
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { candidates = emptyList(); error = true }
        finally { loading = false }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.youtube_playback_account)) },
        supportingContent = { Text(if (linked == null) stringResource(R.string.youtube_playback_account_current)
            else candidates.firstOrNull { it.id == linked }?.name ?: stringResource(R.string.youtube_playback_account_unavailable)) },
        leadingContent = { Icon(Icons.Outlined.ManageAccounts, null) },
        modifier = Modifier.fillMaxWidth().testTag("youtube-playback-account").clickable(enabled = !loading) { visible = true },
    )
    if (visible) AlertDialog(onDismissRequest = { if (!saving) visible = false },
        title = { Text(stringResource(R.string.youtube_playback_account)) },
        text = { LazyColumn(Modifier.heightIn(max = 440.dp)) {
            item { Text(stringResource(R.string.youtube_playback_account_description)) }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)) }
                item {
                    ListItem(headlineContent = { Text(stringResource(R.string.youtube_playback_account_current)) },
                        leadingContent = { RadioButton(selected = draft == null, onClick = { draft = null }) },
                        modifier = Modifier.padding(top = 8.dp, bottom = if (candidates.isNotEmpty()) 4.dp else 0.dp)
                            .dialogChoiceRow(0, candidates.size + 1).clickable { draft = null })
                }
                itemsIndexed(candidates, key = { _, profile -> profile.id }) { index, item ->
                    ListItem(headlineContent = { Text(item.name) },
                        leadingContent = { RadioButton(selected = draft == item.id, onClick = { draft = item.id }) },
                        modifier = Modifier.padding(bottom = if (index < candidates.lastIndex) 4.dp else 0.dp)
                            .dialogChoiceRow(index + 1, candidates.size + 1)
                            .testTag("youtube-account-profile-${item.id}").clickable { draft = item.id })
                }
            if (!loading && candidates.isEmpty()) item { Text(stringResource(R.string.youtube_playback_account_empty)) }
            if (error) item { Text(stringResource(R.string.profile_operation_failed), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !loading && !saving && (draft == null || candidates.any { it.id == draft }), onClick = {
            saving = true; error = false
            scope.launch {
                try { withContext(Dispatchers.IO) { accounts.link(profile.current, draft) }; linked = draft; changed(); visible = false }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
                finally { saving = false }
            }
        }, modifier = Modifier.testTag("youtube-account-confirm")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = { visible = false }) { Text(stringResource(R.string.cancel)) } })
}
