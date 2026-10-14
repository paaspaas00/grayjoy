package com.futo.platformplayer.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class VoiceSettingsActivity : ComponentActivity() {
    private val phonePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        getSharedPreferences("voice", 0).edit().putBoolean("voice_calls", granted).apply()
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) startListening()
    }
    private fun startListening() {
        getSharedPreferences("voice", 0).edit().putBoolean("running", true).putString("last_error", "").apply()
        runCatching { startForegroundService(Intent(this, VoiceListeningService::class.java)) }
            .onFailure { getSharedPreferences("voice", 0).edit().putBoolean("running", false).putString("last_error", it.message).apply() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = if (resources.configuration.uiMode and 0x30 == 0x20) darkColorScheme() else lightColorScheme()) {
            Surface { Settings() }
        } }
    }
    @Composable private fun Settings() {
        val preferences = remember { getSharedPreferences("voice", 0) }
        var stt by remember { mutableStateOf(preferences.getString("stt", "it").orEmpty()) }
        var sttModel by remember { mutableStateOf(preferences.getString("stt_model", "whisper-base").orEmpty()) }
        var llmModel by remember { mutableStateOf(VoiceModels.selectedLlm(preferences)) }
        var tts by remember { mutableStateOf(preferences.getString("tts", "it").orEmpty()) }
        var selectedVoice by remember { mutableStateOf(TtsVoices.selected(preferences)) }
        var listeningVolume by remember { mutableFloatStateOf(preferences.getInt("listening_volume_percent", 50).coerceIn(0, 100).toFloat()) }
        var voicePicker by remember { mutableStateOf(false) }
        var previewing by remember { mutableStateOf(false) }
        var previewStatus by remember { mutableStateOf("") }
        val modelManager = remember { WorkManager.getInstance(this) }
        val selectedDownloads by modelManager.getWorkInfosForUniqueWorkFlow("voice-model-${selectedVoice.modelId}")
            .collectAsState(initial = emptyList())
        val selectedInstalled = remember(selectedVoice.modelId, selectedDownloads) { VoiceModels.installed(this, selectedVoice.modelId) }
        val selectedDownload = selectedDownloads.lastOrNull()?.takeIf { !it.state.isFinished }
        var spokenFeedback by remember { mutableStateOf(preferences.getBoolean("spoken_feedback", true)) }
        var diagnosticsEnabled by remember { mutableStateOf(preferences.getBoolean("wake_diagnostics", false)) }
        var announceMessages by remember { mutableStateOf(preferences.getBoolean("message_announcements", false)) }
        var voiceCalls by remember { mutableStateOf(preferences.getBoolean("voice_calls", false)) }
        val status by VoiceListeningService.listeningStatus.collectAsState()
        val preparation by VoiceListeningService.voicePreparation.collectAsState()
        val interpreterPreparation by VoiceListeningService.interpreterPreparation.collectAsState()
        var error by remember { mutableStateOf(preferences.getString("last_error", "").orEmpty()) }
        val heard by VoiceListeningService.lastHeard.collectAsState()
        val diagnostics by VoiceListeningService.wakeDiagnostics.collectAsState()
        val microphoneProcessing by VoiceListeningService.microphoneProcessing.collectAsState()
        val changed = remember { android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "last_error") error = preferences.getString("last_error", "").orEmpty()
            if (key == "voice_calls") voiceCalls = preferences.getBoolean("voice_calls", false)
        } }
        DisposableEffect(Unit) { preferences.registerOnSharedPreferenceChangeListener(changed); onDispose { preferences.unregisterOnSharedPreferenceChangeListener(changed) } }
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Controllo vocale", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp)) }
            item { Text("Tutto offline: attivazione, trascrizione, comprensione e risposta. Solo la ricerca YouTube usa Internet. Nessun account o telemetria.") }
            item { Text(status, color = MaterialTheme.colorScheme.primary); if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) }
            if (preparation.isNotBlank()) item { Text(preparation, style = MaterialTheme.typography.bodySmall) }
            if (interpreterPreparation.isNotBlank()) item { Text(interpreterPreparation, style = MaterialTheme.typography.bodySmall) }
            if (heard.isNotBlank()) item { Text("Ho capito: $heard", maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
                    else permission.launch(arrayOf(Manifest.permission.RECORD_AUDIO) + if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
                }) { Text("Attiva") }
                OutlinedButton(onClick = { stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java)) }) { Text("Disattiva") }
            } }
            item {
                TextButton(onClick = {
                    diagnosticsEnabled = !diagnosticsEnabled
                    preferences.edit().putBoolean("wake_diagnostics", diagnosticsEnabled).apply()
                }) { Text(if (diagnosticsEnabled) "Nascondi diagnostica ascolto" else "Diagnostica ascolto") }
                if (diagnosticsEnabled) {
                    val level = ((20 * kotlin.math.log10(maxOf(diagnostics.rms, 1.0) / 32768) + 65) / 45).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
                    Text(if (diagnostics.microphoneBlocked) "Android sta bloccando il microfono" else diagnostics.input)
                    if (microphoneProcessing.isNotBlank()) Text(microphoneProcessing, style = MaterialTheme.typography.bodySmall)
                    Text("Livello ${diagnostics.rms.toInt()} · soglia ${diagnostics.threshold.toInt()} · frame ${diagnostics.decodedFrames}")
                    Text("Frase rilevata: ${diagnostics.text.ifBlank { "—" }}", maxLines = 2)
                    Text("Questi dati restano soltanto in memoria: nessuna registrazione o trascrizione viene salvata.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("Abilita anche “Consenti i comandi vocali” nelle Prefs di Grayjoy. Per sicurezza i profili protetti e le sessioni private non accettano comandi vocali.") }
            item {
                Text("Messaggi e chiamate", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Annuncia i messaggi in arrivo", Modifier.weight(1f))
                    Switch(announceMessages, {
                        announceMessages = it; preferences.edit().putBoolean("message_announcements", it).apply()
                        if (!it) VoiceMessageListener.inbox.clear()
                    })
                }
                Text("Solo mentre l’ascolto è attivo. Annuncia mittente e app; legge il testo soltanto dopo il tuo sì, anche a schermo bloccato. Nessun messaggio viene salvato.", style = MaterialTheme.typography.bodySmall)
                if (announceMessages) {
                    TextButton(onClick = { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("Autorizza accesso alle notifiche")
                    }
                    listOf("whatsapp" to "WhatsApp", "telegram" to "Telegram").forEach { (id, label) ->
                        var checked by remember(id) { mutableStateOf(preferences.getBoolean("messages_$id", true)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, Modifier.weight(1f)); Switch(checked, { checked = it; preferences.edit().putBoolean("messages_$id", it).apply(); VoiceMessageListener.inbox.clear() })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chiama i contatti con la voce", Modifier.weight(1f))
                    Switch(voiceCalls, {
                        if (it) phonePermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                        else { voiceCalls = false; preferences.edit().putBoolean("voice_calls", false).apply() }
                    })
                }
                Text("“Chiama Mario” avvia una chiamata telefonica al contatto riconosciuto. Nomi simili o più numeri richiedono conferma. Puoi disabilitarlo qui in ogni momento.", style = MaterialTheme.typography.bodySmall)
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Conferme vocali", style = MaterialTheme.typography.titleMedium)
                    Text("Blip all’ascolto, doppio blip al comando. La voce interviene per conferme utili o chiarimenti specifici.")
                }
                Switch(spokenFeedback, { spokenFeedback = it; preferences.edit().putBoolean("spoken_feedback", it).apply() })
            } }
            item { Text("Frase di attivazione", style = MaterialTheme.typography.titleMedium)
                Text("Dì “ok Grayjoy”, attendi il blip e pronuncia il comando.")
            }
            item { Text("Volume durante il comando: ${listeningVolume.toInt()}%", style = MaterialTheme.typography.titleMedium)
                Text("Percentuale del volume attuale di Grayjoy. Al termine viene ripristinato; non cambia il volume Android.", style = MaterialTheme.typography.bodySmall)
                Slider(value = listeningVolume, onValueChange = { listeningVolume = it }, valueRange = 0f..100f, steps = 19,
                    onValueChangeFinished = { preferences.edit().putInt("listening_volume_percent", listeningVolume.toInt()).apply() })
            }
            item { Text("Lingua della trascrizione (STT)", style = MaterialTheme.typography.titleMedium)
                LanguageRow(stt, listOf("it" to "Italiano", "en" to "English", "auto" to "Auto", "fr" to "Français", "de" to "Deutsch", "es" to "Español")) { stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java)); stt = it; preferences.edit().putString("stt", it).apply() }
            }
            item { Text("Lingua della voce (TTS)", style = MaterialTheme.typography.titleMedium)
                LanguageRow(tts, listOf("it" to "Italiano", "en" to "English")) {
                    stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java)); tts = it
                    preferences.edit().putString("tts", it).apply(); selectedVoice = TtsVoices.selected(preferences)
                }
            }
            item { Text("Motore e voce", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { voicePicker = true }, enabled = !previewing) { Text(selectedVoice.label) }
                Text("Piper è il più leggero. Kokoro e Supertonic sono alternative neurali offline; scarica solo il motore che vuoi usare.", style = MaterialTheme.typography.bodySmall)
                Text("Dopo un cambio di lingua o voce, premi Attiva per riprendere l’ascolto.", style = MaterialTheme.typography.bodySmall)
                if (selectedVoice.modelId == "tts-kokoro") Text("Kokoro può richiedere più tempo per le risposte vocali. L’esecuzione dei comandi non attende la voce.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java))
                    previewing = true; previewStatus = "Preparazione della voce…"
                    val preset = selectedVoice
                    lifecycleScope.launch {
                        var speech: LocalTts? = null
                        try {
                            val started = android.os.SystemClock.elapsedRealtime()
                            val audio = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                speech = LocalTts(this@VoiceSettingsActivity, preset)
                                speech!!.generate(if (preset.language == "it") "Ciao, sono la voce di Grayjoy. Sono pronta ad ascoltare i tuoi comandi." else "Hello, this is Grayjoy. I'm ready to listen to your commands.")
                            }
                            previewStatus = "Voce pronta in ${android.os.SystemClock.elapsedRealtime() - started} ms (incluso caricamento)."
                            playVoiceAudio(audio.samples, audio.sampleRate)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { previewStatus = failure.message.orEmpty() }
                        finally {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Default) { speech?.close() }
                            previewing = false
                        }
                    }
                }, enabled = !previewing && selectedInstalled) { Text(if (previewing) "Riproduzione…" else "Prova voce") }
                if (previewStatus.isNotBlank()) Text(previewStatus, style = MaterialTheme.typography.bodySmall)
                if (!selectedInstalled) {
                    Text(VoiceModels.catalog.first { it.id == selectedVoice.modelId }.label)
                    if (selectedDownload != null) LinearProgressIndicator(progress = { selectedDownload.progress.getFloat("progress", 0f) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { VoiceModels.request(this@VoiceSettingsActivity, selectedVoice.modelId) }, enabled = selectedDownload == null) { Text("Scarica il motore selezionato") }
                    selectedDownloads.lastOrNull()?.takeIf { it.state == WorkInfo.State.FAILED }?.let {
                        Text(it.outputData.getString("error").orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Text("Modello STT", style = MaterialTheme.typography.titleMedium)
                LanguageRow(sttModel, listOf("whisper-base" to "Base · equilibrato", "whisper" to "Tiny · più leggero", "whisper-small" to "Small int8 · più accurato")) {
                    stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java)); sttModel = it
                    preferences.edit().putString("stt_model", it).apply()
                }
                Text("Small int8 richiede più RAM e tempo di trascrizione. Non cambia il modello leggero che ascolta “ok Grayjoy”.", style = MaterialTheme.typography.bodySmall)
            }
            item { Text("Modelli", style = MaterialTheme.typography.titleLarge) }
            item {
                Text("Comprensione dei comandi", style = MaterialTheme.typography.titleMedium)
                LanguageRow(llmModel, listOf("qwen" to "Qwen 3 · 0,6B", "qwen-17" to "Qwen 3 · 1,7B", "qwen-3" to "Qwen 2.5 · 3B")) {
                    stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java))
                    llmModel = it; preferences.edit().putString("llm_model", it).apply()
                }
                Text("I modelli più grandi richiedono più memoria e tempo. Si preparano quando attivi l’ascolto; attendi “Modello comandi pronto” per le richieste complesse. I controlli semplici restano immediati.", style = MaterialTheme.typography.bodySmall)
            }
            items(VoiceModels.catalog, key = VoiceModel::id) { ModelCard(it) }
            item { Text("Esempi: “abbassa un po’ il volume”, “metti in pausa”, “avvia la playlist Songs”, “riproduci Bohemian Rhapsody”, “next song”. La notifica permette di interrompere il microfono. Dopo un riavvio o arresto forzato riattiva l’ascolto da qui.", modifier = Modifier.padding(bottom = 20.dp)) }
        }
        if (voicePicker) AlertDialog(onDismissRequest = { voicePicker = false }, title = { Text("Scegli motore e voce") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(TtsVoices.forLanguage(tts), key = TtsVoice::id) { preset ->
                    TextButton(onClick = {
                        stopService(Intent(this@VoiceSettingsActivity, VoiceListeningService::class.java))
                        selectedVoice = preset; preferences.edit().putString("tts_voice_$tts", preset.id).apply(); voicePicker = false
                        previewStatus = ""
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selectedVoice.id == preset.id) "✓ " else "") + preset.label, modifier = Modifier.weight(1f))
                        if (!VoiceModels.installed(this@VoiceSettingsActivity, preset.modelId)) Text("↓")
                    }
                }
            } }, confirmButton = { TextButton(onClick = { voicePicker = false }) { Text("Chiudi") } })
    }
    @Composable private fun LanguageRow(selected: String, languages: List<Pair<String, String>>, choose: (String) -> Unit) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(languages) { (code, label) -> FilterChip(selected == code, { choose(code) }, label = { Text(label) }) }
        }
    }
    @Composable private fun ModelCard(model: VoiceModel) {
        val manager = remember { WorkManager.getInstance(this) }
        val info by manager.getWorkInfosForUniqueWorkFlow("voice-model-${model.id}").collectAsState(initial = emptyList())
        val work = info.lastOrNull()
        val working = work != null && !work.state.isFinished
        val installed = VoiceModels.installed(this, model.id)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.label)
            if (model.attribution.isNotBlank()) Text(model.attribution, style = MaterialTheme.typography.bodySmall)
            if (working) LinearProgressIndicator(progress = { work!!.progress.getFloat("progress", 0f) }, modifier = Modifier.fillMaxWidth())
            if (working && work!!.progress.getString("stage") in setOf("verify", "unpack"))
                Text(if (work.progress.getString("stage") == "verify") "Verifica del modello…" else "Preparazione del modello…")
            if (work?.state == WorkInfo.State.FAILED) Text(work.outputData.getString("error").orEmpty(), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (installed) Text("Scaricato")
                else TextButton(onClick = { VoiceModels.request(this@VoiceSettingsActivity, model.id) }, enabled = !working) { Text("Scarica") }
                if (working) TextButton(onClick = { manager.cancelUniqueWork("voice-model-${model.id}") }) { Text("Annulla") }
                if (model.licenseUrl.isNotBlank()) TextButton(onClick = {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(model.licenseUrl))) }
                }) { Text("Licenza") }
            }
        } }
    }
}
