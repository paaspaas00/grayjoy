package com.futo.platformplayer.voice

import android.content.Context
import android.media.*
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OfflineVoiceRuntime(private val context: Context, private val scope: CoroutineScope, private val tools: String,
    private val onVoicePreparation: (String) -> Unit = {}) : AutoCloseable {
    private val lock = Mutex()
    private val speechLock = Mutex()
    private val llm = NativeLlm()
    private val dialogue = VoiceDialogue()
    private val misunderstandings = MisunderstandingReplies()
    private var voiceJob: Deferred<LocalTts>? = null
    private var asrJob: Deferred<OfflineRecognizer>? = null
    private var languageJob: Deferred<Unit>? = null
    private var loadedVoice: LocalTts? = null
    private var loadedAsr: OfflineRecognizer? = null
    private var closing = false
    private var expiry: Job? = null
    private var prefill: Job? = null
    @Volatile private var commandActive = false
    private val prefs = context.getSharedPreferences("voice", 0)
    private val llmModelId = VoiceModels.selectedLlm(prefs)
    var onInterpreterPreparation: ((String) -> Unit)? = null
    var onReplyCaptureReady: (() -> Unit)? = null
    private var lastQuestion = ""
    private val italian get() = selectedVoice.language == "it"
    private val selectedVoice = TtsVoices.selected(prefs)
    private val assistant = VoiceAssistantActions(selectedVoice.language == "it", AndroidVoicePersonalActions(context),
        speak = { speak(it) }, semanticAnswer = { text ->
            prepareInterpreter(); languageJob!!.await()
            val decision = VoiceDecision.parse(llm.interpret(commandPrompt(text, tools,
                CONFIRMATION_CONTEXT, llmModelId),
                CONFIRMATION_GRAMMAR, 4))
            decision.tool.removePrefix("reply_").takeIf { decision.tool.startsWith("reply_") } ?: "other"
        }, speakSequence = { speakSequence(it) }, askSequence = { speakSequence(it, listenAfter = true) })
    private val stockReplies = (listOf("read_message", "answer_yes_no", "playing", "cancelled")
        .map { VoiceFixedPhrases.get(it, italian) } + VoiceFixedPhrases.all(selectedVoice.language == "it")).distinct()
    private val speechCache = VoiceSpeechCache(File(context.noBackupFilesDir, "speech-cache"),
        "speech-v1-steps8:${selectedVoice.id}:${VoiceModels.catalog.first { it.id == selectedVoice.modelId }.hash}", stockReplies.toSet())
    private val replies = LinkedHashMap<String, GeneratedAudio>(32, .75f, true)
    val needsFollowup get() = dialogue.needsFollowup || assistant.needsFollowup
    val expectsConfirmation get() = dialogue.expectsConfirmation || assistant.expectsConfirmation
    fun clearFollowup() { dialogue.clear(); assistant.clear(); lastQuestion = "" }
    suspend fun announce(message: IncomingVoiceMessage) = lock.withLock {
        beginCommand()
        prepareAdvanced() // Hide fallback STT setup behind the announcement, not after a short answer.
        dialogue.clear()
        assistant.announce(message)
        if (!needsFollowup) endCommand()
    }
    fun beginCommand() { commandActive = true; warm() }
    fun endCommand() { commandActive = false; if (!needsFollowup) lastQuestion = "" }

    /** Speech feedback is prepared early; clear commands never load Whisper or the LLM. */
    @Synchronized fun warm() {
        check(!closing) { "Voice session closed" }
        expiry?.cancel()
        if (voiceJob != null) return
        voiceJob = scope.async(Dispatchers.Default) {
            val model = LocalTts(context, selectedVoice)
            synchronized(this@OfflineVoiceRuntime) {
                if (closing) { model.close(); throw CancellationException("Voice session closed") }
                loadedVoice = model
            }
            model
        }
        val preparedVoice = voiceJob!!
        prefill = scope.launch(Dispatchers.Default) {
            try {
                val model = preparedVoice.await()
                stockReplies.forEachIndexed { index, text ->
                    while (commandActive) { delay(100); currentCoroutineContext().ensureActive() }
                    var prepared = false
                    while (!prepared) {
                        speechLock.withLock {
                            if (!commandActive) { cachedSpeech(model, text); prepared = true }
                        }
                        if (!prepared) delay(100)
                    }
                    onVoicePreparation(if (index == stockReplies.lastIndex) "Risposte comuni pronte"
                        else "Preparazione risposte vocali · ${index + 1}/${stockReplies.size}")
                    delay(100)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onVoicePreparation("Risposte generate al bisogno") }
        }
    }

    private fun rememberSpeech(text: String, audio: GeneratedAudio): GeneratedAudio {
        replies[text] = audio
        while (replies.size > 32 || replies.values.sumOf { it.samples.size * 4L } > 16L * 1024 * 1024)
            replies.remove(replies.keys.first())
        return audio
    }
    private fun cachedSpeech(model: LocalTts, text: String): GeneratedAudio {
        replies[text]?.let { return it }
        val audio = speechCache.read(text)?.let { GeneratedAudio(it.samples, it.sampleRate) } ?: model.generate(text).also {
            speechCache.write(text, CachedVoiceAudio(it.samples, it.sampleRate))
        }
        return rememberSpeech(text, audio)
    }

    @Synchronized private fun prepareAdvanced() {
        warm()
        if (asrJob == null) asrJob = scope.async(Dispatchers.Default) {
            val model = prefs.getString("stt_model", "whisper-base").orEmpty()
            val prefix = when (model) { "whisper-small" -> "small"; "whisper-base" -> "base"; else -> "tiny" }
            val dir = VoiceModels.directory(context, model)
            val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(encoder = File(dir, "$prefix-encoder.int8.onnx").path,
                    decoder = File(dir, "$prefix-decoder.int8.onnx").path,
                    language = prefs.getString("stt", "it").orEmpty().takeUnless { it == "auto" }.orEmpty()),
                tokens = File(dir, "$prefix-tokens.txt").path, numThreads = 2)))
            synchronized(this@OfflineVoiceRuntime) {
                if (closing) { recognizer.release(); throw CancellationException("Voice session closed") }
                loadedAsr = recognizer
            }
            recognizer
        }
        prepareInterpreter()
    }

    @Synchronized fun prepareInterpreter() {
        check(!closing) { "Voice session closed" }
        if (languageJob?.isCancelled == true) languageJob = null
        if (languageJob == null) languageJob = scope.async(Dispatchers.Default) {
            onInterpreterPreparation?.invoke("Preparazione del modello comandi…")
            try {
                llm.load(File(VoiceModels.directory(context, llmModelId), "model.gguf").path)
                currentCoroutineContext().ensureActive()
                llm.prime(commandPrefix(tools, llmModelId), 4)
                currentCoroutineContext().ensureActive()
                onInterpreterPreparation?.invoke("Modello comandi pronto")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                onInterpreterPreparation?.invoke("Preparazione non riuscita. Riprova o scegli un modello più piccolo.")
                throw error
            }
        }
    }

    fun prepareUnderstanding() = prepareAdvanced()

    suspend fun handle(samples: FloatArray, client: VoiceCommandClient, toolContext: String, status: (String) -> Unit,
        streamingTranscript: String = "", endOfSpeechMs: Long = SystemClock.elapsedRealtime(),
        onTranscript: (String) -> Unit = {}) {
        beginCommand()
        try {
            lock.withLock {
                warm()
                val streamed = cleanVoiceText(streamingTranscript)
                if (samples.isEmpty() && streamed.isBlank()) { clearFollowup(); return@withLock }
                val canUseStream = streamed.isNotBlank()
                val rawHeard = if (canUseStream) streamed else {
                    status(if (italian) "Trascrizione offline…" else "Transcribing offline…")
                    prepareAdvanced()
                    val recognizer = asrJob!!.await()
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(samples, 16_000); recognizer.decode(stream)
                        cleanVoiceText(recognizer.getResult(stream).text)
                    } finally { stream.release() }
                }
                val heard = normalizeVoiceCommandTranscript(rawHeard, italian, needsFollowup)
                if (ignorableVoiceTranscript(heard) && !dialogue.acceptsBareTarget && !assistant.needsFollowup) { clearFollowup(); return@withLock }
                if (heard.isBlank()) { clearFollowup(); return@withLock }
                if (expectsConfirmation && isVoicePromptEcho(heard, lastQuestion)) return@withLock
                onTranscript(heard)
                if (assistant.followup(heard, client)) return@withLock
                if (isNegatedVoiceRequest(heard)) { dialogue.clear(); return@withLock }
                val resolved = if (dialogue.needsFollowup) dialogue.resolve(heard) else null
                when (resolved) {
                    DialogueReply.Cancelled -> { speak(VoiceFixedPhrases.get("cancelled", italian)); return@withLock }
                    is DialogueReply.Ask -> { ask(resolved); return@withLock }
                    else -> Unit
                }
                var decision = if (resolved is DialogueReply.Execute) resolved.decision else {
                    immediateVoiceCommand(heard) ?: run {
                        status(if (italian) "Interpretazione offline…" else "Interpreting offline…")
                        prepareInterpreter(); languageJob!!.await()
                        try {
                            validateVoiceDecision(VoiceDecision.parse(llm.interpret(
                                commandPrompt(heard, toolContext, dialogue.context, llmModelId), COMMAND_GRAMMAR, 4)), heard, dialogue.acceptsBareTarget)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            if (looksLikeVoiceCommand(heard) || dialogue.needsFollowup) ask(dialogue.misunderstood()) else dialogue.clear()
                            return@withLock
                        }
                    }
                }
                if (decision.tool == "ignore" || decision.tool.startsWith("reply_")) { dialogue.clear(); return@withLock }
                if (assistant.handle(decision, client, heard)) { dialogue.clear(); return@withLock }
                if (decision.tool == "clarify") {
                    when (val clarification = dialogue.clarify(heard)) {
                        is DialogueReply.Execute -> decision = clarification.decision
                        is DialogueReply.Ask -> { ask(clarification); return@withLock }
                        else -> return@withLock
                    }
                }
                // The earcon acknowledges recognition. Dispatch does not wait for spoken feedback.
                val cue = scope.async { playVoiceCue(confirmed = true) }
                val response = try { client.call(decision.tool, decision.arguments()) }
                    catch (cancelled: CancellationException) { cue.cancel(); throw cancelled }
                    catch (error: Exception) {
                        cue.await()
                        speak(VoiceFixedPhrases.get("failed", italian))
                        status(error.message?.take(120).orEmpty()); return@withLock
                    }
                val actionMs = SystemClock.elapsedRealtime() - endOfSpeechMs
                // Timings contain no audio, transcript, playlist title or search query.
                Log.i("VoiceLatency", "action=${decision.tool} end_to_action_ms=$actionMs streaming=$canUseStream")
                prefs.edit().putLong("last_action_ms", actionMs).putBoolean("last_streaming", canUseStream).apply()
                cue.await()
                if (assistant.handlePlaybackProblem(response)) { dialogue.clear(); return@withLock }
                if (response.optBoolean("needsClarification")) {
                    val choices = response.optJSONArray("choices")?.let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    }.orEmpty()
                    ask(if (response.optString("kind") == "content") dialogue.missingContent()
                        else dialogue.playlistProblem(decision.argument, choices, response.optString("kind"),
                            response.optJSONArray("choiceIds")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } }.orEmpty()))
                } else {
                    dialogue.clear()
                    // Simple controls have audible confirmation already; avoid speaking over music.
                    if (decision.tool in setOf("play_playlist", "play_named", "search_play", "play_artist", "play_same_artist") && prefs.getBoolean("spoken_feedback", true))
                        speak(VoiceFixedPhrases.get("playing", italian), endOfSpeechMs)
                }
                status((if (italian) "Pronto" else "Ready") + " · $actionMs ms")
            }
        } finally {
            if (!needsFollowup) endCommand()
            expiry?.cancel()
            expiry = scope.launch { delay(120_000); releaseHeavyModels(keepLanguageModel = true) }
        }
    }

    private suspend fun ask(reply: DialogueReply.Ask) = speakSequence(listOf(when (reply.kind) {
        "playlist_name", "video_name", "play_name", "missing_playlist", "empty_playlist" -> VoiceFixedPhrases.get(reply.kind, italian)
        "playlist_choice" -> (if (italian) "Quale playlist? " else "Which playlist? ") + reply.choices.take(3).joinToString(", ") { it.take(56) }
        "playlist_confirm" -> (if (italian) "Intendi la playlist " else "Did you mean the playlist ") + reply.choices.first().take(80) + "?"
        "video_confirm" -> (if (italian) "Vuoi riprodurre " else "Do you want to play ") + reply.query.take(80) + "?"
        "repeat_end" -> misunderstandings.next(italian) + VoiceFixedPhrases.restartSuffix(italian)
        else -> misunderstandings.next(italian)
    }), listenAfter = dialogue.needsFollowup)

    private suspend fun speak(text: String, endOfSpeechMs: Long? = null) {
        speakSequence(listOf(text), endOfSpeechMs = endOfSpeechMs)
    }

    private suspend fun prepareSpeech(text: String): GeneratedAudio {
        val saved = speechLock.withLock {
            replies[text] ?: speechCache.read(text)?.let { rememberSpeech(text, GeneratedAudio(it.samples, it.sampleRate)) }
        }
        val audio = saved ?: voiceJob!!.await().let { voice -> speechLock.withLock { cachedSpeech(voice, text) } }
        return GeneratedAudio(trimVoicePadding(audio.samples, audio.sampleRate), audio.sampleRate)
    }

    private suspend fun speakSequence(parts: List<String>, listenAfter: Boolean = false, endOfSpeechMs: Long? = null) {
        if (listenAfter) prepareAdvanced()
        val chunks = parts.flatMap(::spokenChunks)
        if (listenAfter) lastQuestion = chunks.lastOrNull().orEmpty()
        var first = true
        playPreparedSpeech(chunks, ::prepareSpeech) { audio, last ->
            playVoiceAudio(audio.samples, audio.sampleRate, tailMillis = if (last) 500 else 40,
                onSpeechEnd = if (last && listenAfter) ({ if (!closing && needsFollowup) onReplyCaptureReady?.invoke() }) else null) {
                if (first) endOfSpeechMs?.let { start ->
                    val elapsed = SystemClock.elapsedRealtime() - start
                    Log.i("VoiceLatency", "end_to_feedback_start_ms=$elapsed")
                    prefs.edit().putLong("last_feedback_ms", elapsed).apply()
                }
                first = false
            }
        }
    }

    suspend fun releaseHeavyModels(keepLanguageModel: Boolean = false) = withContext(Dispatchers.Default) { lock.withLock {
        prefill?.cancelAndJoin(); prefill = null
        val voice = voiceJob; val asr = asrJob; val language = languageJob.takeUnless { keepLanguageModel }
        listOfNotNull(voice, asr, language).forEach { it.join() }
        synchronized(this@OfflineVoiceRuntime) {
            loadedVoice?.close(); loadedVoice = null; loadedAsr?.release(); loadedAsr = null
            voiceJob = null; asrJob = null
            if (!keepLanguageModel) { llm.unload(); languageJob = null }
            replies.clear()
        }
    } }
    override fun close() {
        synchronized(this) { if (closing) return; closing = true }
        clearFollowup()
        llm.cancel(); expiry?.cancel(); prefill?.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { releaseHeavyModels(); cancel() }
    }
}

/** Drain actual playback, including a silent tail, rather than stop on a wall-clock estimate. */
internal suspend fun playVoiceAudio(samples: FloatArray, sampleRate: Int, tailMillis: Int = 500,
    onSpeechEnd: (() -> Unit)? = null, onStart: () -> Unit = {}) {
    require(sampleRate in 8_000..96_000 && samples.size <= sampleRate * 30 && tailMillis in 0..1000)
    val pcm = ShortArray(samples.size + sampleRate * tailMillis / 1000) { index ->
        if (index < samples.size) (samples[index].coerceIn(-1f, 1f) * 32767).toInt().toShort() else 0
    }
    val speechFrames = if (onSpeechEnd != null) voiceReplyHandoffFrame(samples, sampleRate) else samples.size
    val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2).build()
    try {
        check(track.state != AudioTrack.STATE_UNINITIALIZED)
        check(track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) == pcm.size)
        check(track.state == AudioTrack.STATE_INITIALIZED)
        track.play(); onStart()
        var armed = false
        var presentationEnd: Long? = null
        val timestamp = AudioTimestamp()
        withTimeout(pcm.size * 1000L / sampleRate + 5_000L) {
            while (true) {
                val head = Integer.toUnsignedLong(track.playbackHeadPosition)
                if (head >= speechFrames) {
                    if (presentationEnd == null) {
                        presentationEnd = if (track.getTimestamp(timestamp)) {
                            voicePresentationEndNanos(speechFrames.toLong(), timestamp.framePosition, timestamp.nanoTime, sampleRate) + 60_000_000L
                        } else {
                            val wireless = track.routedDevice?.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
                            // An unsupported Bluetooth timestamp cannot safely justify shortening its drain.
                            System.nanoTime() + if (wireless) 500_000_000L else 60_000_000L
                        }
                    }
                    if (!armed && onSpeechEnd != null && System.nanoTime() >= presentationEnd!!) { armed = true; onSpeechEnd() }
                }
                if (head >= pcm.size) break
                delay(10); currentCoroutineContext().ensureActive()
            }
            // Keep the output alive until the last speech frame is actually presented, including
            // intermediate chunks with a short tail. This avoids clipping Bluetooth output.
            presentationEnd?.let { deadline -> if (deadline > System.nanoTime()) delay(((deadline - System.nanoTime()) / 1_000_000).coerceIn(0, 500)) }
            if (!armed) onSpeechEnd?.invoke()
        }
    } finally { runCatching { track.stop() }; track.release() }
}

internal suspend fun playVoiceCue(confirmed: Boolean = false) {
    val rate = 16_000
    val length = if (confirmed) 210 else 90
    val audio = FloatArray(rate * length / 1000) { index ->
        val timeMs = index * 1000f / rate
        val offset = if (confirmed && timeMs >= 130) timeMs - 130 else timeMs
        if (offset >= 80) 0f else {
            val envelope = minOf(offset / 8, (80 - offset) / 8, 1f).coerceAtLeast(0f)
            (.14 * envelope * kotlin.math.sin(2 * Math.PI * (if (confirmed) 1040 else 880) * index / rate)).toFloat()
        }
    }
    playVoiceAudio(audio, rate, tailMillis = 40)
}
