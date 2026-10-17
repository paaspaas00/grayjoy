package com.futo.platformplayer.voice

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class VoiceListeningService : Service() {
    companion object {
        @Volatile internal var isListening = false; private set
        internal val lastHeard = kotlinx.coroutines.flow.MutableStateFlow("")
        internal val wakeDiagnostics = kotlinx.coroutines.flow.MutableStateFlow(WakeDiagnostics())
        internal val listeningStatus = kotlinx.coroutines.flow.MutableStateFlow("Disabilitato")
        internal val voicePreparation = kotlinx.coroutines.flow.MutableStateFlow("")
        internal val interpreterPreparation = kotlinx.coroutines.flow.MutableStateFlow("")
        internal val microphoneProcessing = kotlinx.coroutines.flow.MutableStateFlow("")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val active = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var runtime: OfflineVoiceRuntime? = null
    private var client: GrayjoyMcpClient? = null
    private var lastNotification = 0L
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }
        if (!active.compareAndSet(false, true)) return START_NOT_STICKY
        isListening = true
        listeningStatus.value = "Avvio offline…"
        lastHeard.value = ""
        wakeDiagnostics.value = WakeDiagnostics()
        voicePreparation.value = ""
        interpreterPreparation.value = ""
        startForeground(101, notification("Avvio offline…"))
        scope.launch {
            try { listen() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) {
                if (active.get() && scope.isActive) {
                    getSharedPreferences("voice", 0).edit().putString("last_error", error.message?.take(300) ?: error.javaClass.simpleName).apply()
                    notifyStatus("Errore: ${error.message?.take(100)}")
                }
            } finally { withContext(Dispatchers.Main) { stopSelf() } }
        }
        // A microphone foreground service cannot be silently recreated from the background.
        return START_NOT_STICKY
    }

    private suspend fun listen() {
        val prefs = getSharedPreferences("voice", 0)
        val language = prefs.getString("stt", "it").orEmpty()
        val required = listOf("wake-en", "vad", prefs.getString("stt_model", "whisper-base").orEmpty(), VoiceModels.selectedLlm(prefs), TtsVoices.selected(prefs).modelId) +
            if (language == "it") listOf("wake-it") else emptyList()
        check(required.all { VoiceModels.installed(this, it) }) { "Scarica prima i modelli selezionati" }
        val nextClient = GrayjoyMcpClient(this); client = nextClient
        nextClient.connect()
        val toolContext = nextClient.tools()
        // Initialize the host controller while speech resources prepare, not on the first command.
        val controllerReady = scope.async { nextClient.call("status") }
        val voice = OfflineVoiceRuntime(this, scope, toolContext) {
            if (active.get() && scope.isActive) voicePreparation.value = it
        }; runtime = voice
        voice.onInterpreterPreparation = { if (active.get() && scope.isActive) interpreterPreparation.value = it }
        voice.warm()
        voice.prepareInterpreter()
        Model(VoiceModels.directory(this, "wake-en").path).use { wakeModel ->
        val italianCommandModel = if (language == "it") Model(VoiceModels.directory(this, "wake-it").path) else null
        try {
        val commandModel = when (language) { "it" -> italianCommandModel; "en" -> wakeModel; else -> null }
        WakeWordListener(wakeModel).use { wakeListener ->
        CommandSpeechDetector(this).use { speechDetector ->
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Autorizza il microfono prima di attivare l’ascolto")
        val microphone = VoiceMicrophone.open()
        val input = microphone.record
        recorder = input
        microphoneProcessing.value = microphone.description
        val captureGain = VoiceInputGain(enabled = !microphone.gainControl)
        val chunk = ShortArray(1600)
        val command = FloatArray(16 * 16000)
        var count = 0
        var capturing = false
        var silenceFrames = 0
        var spokenFrames = 0
        var waitingFrames = 0
        var diagnosticUpdate = 0L
        var callModeCheckedAt = 0L
        val commandRecognizer = commandModel?.let { Recognizer(it, 16000f).apply { setWords(true) } }
        val confirmationRecognizer = createReplyRecognizer(commandModel, language)
        val replyEndpoint = ReplyEndpoint()
        val streamingText = StringBuilder()
        var confidence = 0.0
        var wordCount = 0
        var endOfSpeechMs = 0L
        fun collectTranscript(json: String) {
            val value = JSONObject(json)
            streamingText.append(value.optString("text")).append(' ')
            value.optJSONArray("result")?.let { words ->
                for (index in 0 until words.length()) { confidence += words.getJSONObject(index).optDouble("conf", 0.0); wordCount++ }
            }
        }
        fun startInput() {
            if (active.get() && input.recordingState != AudioRecord.RECORDSTATE_RECORDING) input.startRecording()
        }
        voice.onReplyCaptureReady = {
            // Capture immediately after the last presented speech frame. The native two-second
            // input buffer retains an early "sì" while the output's silent safety tail drains.
            startInput()
            notifyStatus("Attendo una risposta…")
        }
        try {
            controllerReady.await()
            startInput()
            notifyStatus("In ascolto · ok Grayjoy")
            while (currentCoroutineContext().isActive && active.get()) {
                val clock = SystemClock.elapsedRealtime()
                val mode = if (clock - callModeCheckedAt >= 1_000L) {
                    callModeCheckedAt = clock
                    getSystemService(AudioManager::class.java).mode
                } else AudioManager.MODE_NORMAL
                if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE) {
                    input.stop(); voice.clearFollowup(); voice.endCommand(); releaseDucking(); capturing = false
                    VoiceMessageListener.inbox.clear()
                    notifyStatus("Ascolto sospeso durante la chiamata")
                    while (active.get() && getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL) delay(300)
                    if (!active.get()) break
                    wakeListener.reset(); speechDetector.reset(); commandRecognizer?.reset()
                    startInput(); notifyStatus("In ascolto · ok Grayjoy")
                }
                if (!capturing) {
                    val message = VoiceMessageListener.inbox.poll()
                    if (message != null && VoiceMessageListener.enabled(this)) {
                        // The body is spoken only after a later explicit affirmative answer, never as a command.
                        nextClient.call("status") // Revalidate the current profile before announcing private data.
                        input.stop(); requestDucking()
                        commandRecognizer?.reset(); confirmationRecognizer?.reset(); replyEndpoint.reset()
                        voice.announce(message)
                        capturing = voice.needsFollowup
                        count = 0; silenceFrames = 0; spokenFrames = 0; waitingFrames = 0
                        wakeListener.reset(); speechDetector.reset(shortReply = voice.expectsConfirmation)
                        streamingText.clear(); confidence = 0.0; wordCount = 0
                        if (!capturing) releaseDucking()
                        startInput(); notifyStatus(if (capturing) "Attendo una risposta…" else "In ascolto · ok Grayjoy")
                    }
                }
                val read = input.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                check(read > 0) { "Microfono non disponibile ($read)" }
                captureGain.process(chunk, read)
                if (!capturing) {
                    val detected = wakeListener.accept(chunk, read)
                    val now = SystemClock.elapsedRealtime()
                    if (now - diagnosticUpdate >= 250 && prefs.getBoolean("wake_diagnostics", false)) {
                        val blocked = getSystemService(AudioManager::class.java).isMicrophoneMute ||
                            (Build.VERSION.SDK_INT >= 29 && input.activeRecordingConfiguration?.isClientSilenced == true)
                        val device = input.routedDevice
                        val inputName = when (device?.type) {
                            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfono del telefono"
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Microfono Bluetooth"
                            else -> "Microfono (${device?.type ?: 0})"
                        }
                        wakeDiagnostics.value = wakeListener.diagnostics.copy(microphoneBlocked = blocked, input = inputName)
                        diagnosticUpdate = now
                    }
                    if (detected) {
                        capturing = true; count = 0; silenceFrames = 0; spokenFrames = 0; waitingFrames = 0
                        wakeListener.reset()
                        speechDetector.reset()
                        commandRecognizer?.reset(); confirmationRecognizer?.reset(); replyEndpoint.reset()
                        streamingText.clear(); confidence = 0.0; wordCount = 0
                        voice.beginCommand()
                        requestDucking()
                        // The cue is not fed back into either recognizer.
                        input.stop()
                        playVoiceCue()
                        currentCoroutineContext().ensureActive()
                        if (!active.get()) { break }
                        startInput()
                        notifyStatus("Comando…")
                    }
                    continue
                }
                val samples = FloatArray(read) { chunk[it] / 32768f }
                val voiced = speechDetector.accept(samples)
                for (sample in samples) if (count < command.size) command[count++] = sample
                val recognizer = if (voice.expectsConfirmation) confirmationRecognizer ?: commandRecognizer else commandRecognizer
                recognizer?.let { if (it.acceptWaveForm(chunk, read)) collectTranscript(it.result) }
                waitingFrames++
                if (voiced) { spokenFrames++; silenceFrames = 0; endOfSpeechMs = SystemClock.elapsedRealtime() } else silenceFrames++
                val partial = recognizer?.let { JSONObject(it.partialResult).optString("partial") }.orEmpty()
                val currentText = cleanVoiceText("$streamingText $partial")
                val stableReply = replyEndpoint.accept(currentText, voice.expectsConfirmation)
                // For free-form speech, prepare fallback models before the user finishes.
                if (spokenFrames >= 3 && currentText.isNotBlank() && !ignorableVoiceTranscript(currentText) && fastVoiceCommand(currentText) == null && !voice.needsFollowup)
                    voice.prepareUnderstanding()
                val endpointFrames = if (fastVoiceCommand(currentText) != null || voice.needsFollowup) 3 else 6
                if (((speechDetector.heardSpeech || stableReply) && silenceFrames >= endpointFrames) || count >= command.size || (!speechDetector.heardSpeech && waitingFrames >= 50)) {
                    // Recording stops during inference/TTS: self-generated speech cannot trigger commands.
                    input.stop()
                    var acceptedQuickReply = false
                    try {
                        recognizer?.let { collectTranscript(it.finalResult) }
                        val transcript = streamingText.toString().trim()
                        acceptedQuickReply = wordCount > 0 && reliableQuickReply(transcript, confidence / wordCount, voice.expectsConfirmation, speechDetector.heardSpeech)
                        val direct = if (acceptedQuickReply || wordCount > 0 && canUseStreamingCommand(transcript, confidence / wordCount,
                            voice.needsFollowup)) transcript else ""
                        if (speechDetector.heardSpeech || acceptedQuickReply) voice.handle(command.copyOf(count), nextClient, toolContext, ::notifyStatus,
                            direct, endOfSpeechMs) { lastHeard.value = it }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { notifyStatus("Non ho eseguito il comando: ${error.message?.take(80)}") }
                    finally {
                        wakeListener.reset()
                        if (!speechDetector.heardSpeech && !acceptedQuickReply) voice.clearFollowup()
                        speechDetector.reset(shortReply = voice.expectsConfirmation); replyEndpoint.reset()
                        capturing = voice.needsFollowup
                        if (!capturing) voice.endCommand()
                        count = 0; silenceFrames = 0; spokenFrames = 0; waitingFrames = 0
                        if (!capturing) releaseDucking()
                        commandRecognizer?.reset(); confirmationRecognizer?.reset(); streamingText.clear(); confidence = 0.0; wordCount = 0
                        if (active.get() && currentCoroutineContext().isActive) {
                            startInput()
                            notifyStatus(if (capturing) "Attendo una risposta…" else "In ascolto · ok Grayjoy")
                        }
                    }
                }
            }
        } finally {
            voice.onReplyCaptureReady = null
            microphone.close(); recorder = null
            commandRecognizer?.close()
            confirmationRecognizer?.close()
            withContext(NonCancellable) {
                try { withTimeout(2_000L) { releaseDucking() } }
                finally { nextClient.close() }
            }
        }
        }
        }
        } finally { italianCommandModel?.close() }
        }
    }
    private suspend fun requestDucking() {
        val percent = getSharedPreferences("voice", 0).getInt("listening_volume_percent", 50).coerceIn(0, 100)
        client?.call("voice_volume", JSONObject().put("percent", percent))
    }
    private suspend fun releaseDucking() {
        try { client?.call("voice_volume", JSONObject().put("percent", -1)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { client?.close() } // Disconnect also releases the authenticated volume lease.
    }
    private fun notification(message: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("voice", "Controllo vocale", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceListeningService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, VoiceSettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "voice").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Grayjoy Voice").setContentText(message).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open).addAction(0, "Interrompi", stop).build()
    }
    private fun notifyStatus(message: String) {
        listeningStatus.value = message
        getSharedPreferences("voice", 0).edit().putString("status", message).apply()
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotification > 250L) { getSystemService(NotificationManager::class.java).notify(101, notification(message)); lastNotification = now }
    }
    override fun onDestroy() {
        isListening = false
        VoiceMessageListener.inbox.clear()
        listeningStatus.value = "Disabilitato"
        lastHeard.value = ""
        wakeDiagnostics.value = WakeDiagnostics()
        voicePreparation.value = ""
        interpreterPreparation.value = ""
        microphoneProcessing.value = ""
        active.set(false); runCatching { recorder?.stop() }; runtime?.close(); client?.close(); scope.cancel()
        getSharedPreferences("voice", 0).edit().putBoolean("running", false).putString("status", "Disabilitato").apply()
        super.onDestroy()
    }
}
