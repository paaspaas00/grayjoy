package com.futo.platformplayer.compose.diagnostics

import android.content.Context
import android.content.ContentValues
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import com.futo.platformplayer.compose.BuildConfig
import java.io.File
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

internal object CrashLogStore {
    private const val PREFERENCES = "grayjoy_diagnostics"
    private const val KEY_ENABLED = "crash_logging_enabled"
    private const val KEY_DOWNLOADS_COPY = "crash_logs_downloads_copy"
    private const val DIRECTORY = "diagnostics"
    private const val MAX_LOG_FILES = 10
    private const val MAX_STACK_CHARS = 256_000
    private val installed = AtomicBoolean(false)
    private val collectingExits = AtomicBoolean(false)
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class EventWrite(val record: String? = null, val flushed: CompletableDeferred<Unit>? = null)
    private val eventQueue = Channel<EventWrite>(256)
    private val droppedEvents = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var enabled = false
    @Volatile private var eventRing: DiagnosticEventRing? = null
    private var eventDirectory: File? = null
    private val pending = MutableStateFlow<String?>(null)
    val pendingCrash = pending.asStateFlow()
    private var previousLaunchAt = 0L

    fun recordEvent(component: String, level: String, error: Throwable? = null,
        message: String? = null, fields: Map<String, String> = emptyMap()) {
        if (!enabled) return
        runCatching {
            val event = JSONObject().put("time", Instant.now().toString())
                .put("component", component.filter { it.isLetterOrDigit() || it in "._-" }.take(80))
                .put("level", level.take(16))
            if (error != null) event.put("trace", technicalStackTrace(error, 6000))
            if (!message.isNullOrBlank()) event.put("message", redactDiagnosticMessage(message, 1000))
            if (error != null) event.put("details", privateExceptionMessages(error).take(2000))
            safeBackendEventFields(fields).forEach { (key, value) -> event.put(key, value) }
            if (event.toString().length > 15_000) {
                event.put("message", event.optString("message").take(500))
                event.put("details", event.optString("details").take(1000))
                event.put("trace", event.optString("trace").take(3000))
            }
            if (eventQueue.trySend(EventWrite(record = event.toString())).isFailure) droppedEvents.incrementAndGet()
        }
    }

    private fun markCrash(context: Context, id: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("pending_crash", id.take(160)).commit()
    }

    fun acknowledgeCrash(context: Context, id: String) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val saved = runCatching { preferences.getString("pending_crash", null) }.getOrNull()
        if (saved == id || saved == null) {
            preferences.edit().remove("pending_crash").putLong("acknowledged_crash_at", System.currentTimeMillis()).apply()
            pending.value = null
        }
    }
    @Volatile
    private var beforeCrashHook: (() -> Unit)? = null

    fun isEnabled(context: Context): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    } catch (_: ClassCastException) {
        // Called synchronously at startup and again from the uncaught-exception handler.
        false
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        this.enabled = enabled
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) collectHistoricalExits(context.applicationContext)
    }

    fun isDownloadsCopyEnabled(context: Context): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_DOWNLOADS_COPY, false)
    }.getOrDefault(false)

    fun setDownloadsCopyEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DOWNLOADS_COPY, enabled).apply()
    }

    fun install(context: Context) {
        com.futo.platformplayer.backend.BackendLog.sink = { level, tag, message, error ->
            when (level) {
                "error" -> DiagnosticLog.e(tag, message, error)
                "warning" -> DiagnosticLog.w(tag, message, error)
                "info" -> DiagnosticLog.i(tag, message, error)
                else -> DiagnosticLog.d(tag, message, error)
            }
        }
        val appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        com.futo.platformplayer.logging.Logger.setLogConsumers(listOf(object : com.futo.platformplayer.logging.ILogConsumer {
            override fun willConsume(level: com.futo.platformplayer.logging.LogLevel, tag: String): Boolean =
                level == com.futo.platformplayer.logging.LogLevel.ERROR || level == com.futo.platformplayer.logging.LogLevel.WARNING
            override fun consume(level: com.futo.platformplayer.logging.LogLevel, tag: String, text: String?, e: Throwable?) {
                when {
                    level == com.futo.platformplayer.logging.LogLevel.ERROR -> DiagnosticLog.e("SourceRuntime", text, e)
                    level == com.futo.platformplayer.logging.LogLevel.WARNING -> DiagnosticLog.w("SourceRuntime", text, e)
                    text?.take(512)?.contains(Regex("error|fail|exception|fallback|retry|login.required", RegexOption.IGNORE_CASE)) == true -> DiagnosticLog.i("SourceRuntime", text, e)
                }
            }
        }))
        enabled = isEnabled(appContext)
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        previousLaunchAt = runCatching { preferences.getLong("last_launch", 0L) }.getOrDefault(0L)
        preferences.edit().putLong("last_launch", System.currentTimeMillis()).apply()
        pending.value = runCatching { preferences.getString("pending_crash", null) }.getOrNull()?.takeIf { it.isNotBlank() }
        eventDirectory = File(appContext.filesDir, "$DIRECTORY/events")
        eventRing = DiagnosticEventRing(eventDirectory!!)
        diagnosticsScope.launch {
            for (event in eventQueue) {
                if (enabled && event.record != null) runCatching { eventRing?.append(event.record) }
                    .onFailure { droppedEvents.incrementAndGet() }
                event.flushed?.complete(Unit)
            }
        }
        recordEvent("Application", "start")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Keep the diagnostic before invoking application code, which may itself be blocked
            // on the lock involved in the crash. Restart recovery already holds managed jobs.
            runCatching {
                markCrash(appContext, "${System.currentTimeMillis()}:${throwable.javaClass.name}")
                if (isEnabled(appContext)) writeCrash(appContext, thread, throwable)
            }
            beforeCrashHook?.let { hook -> runCatching { runCrashHookWithDeadline(hook) } }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        collectHistoricalExits(appContext)
    }

    fun setBeforeCrashHook(hook: (() -> Unit)?) {
        beforeCrashHook = hook
    }

    internal fun writeCrash(context: Context, thread: Thread, throwable: Throwable): File {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val formattedTimestamp = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss-SSS",
            Locale.ROOT,
        ).format(Date(timestamp))
        // MediaStore normalizes text/plain files to .txt on several Android builds. Use that
        // extension up front so the public name exactly matches the internal diagnostic record.
        val destination = File.createTempFile("grayjoy-crash-$formattedTimestamp-", ".txt", directory)
        val stack = technicalStackTrace(throwable)
        val contents = buildString {
            appendLine("Timestamp: ${Instant.ofEpochMilli(timestamp)}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${if (thread === android.os.Looper.getMainLooper().thread) "main" else "background"}")
            appendLine()
            appendLine("Private details: " + JSONObject.quote(privateExceptionMessages(throwable)))
            appendLine()
            append(stack)
        }
        destination.writeText(contents)
        if (isDownloadsCopyEnabled(context)) runCatching { writePublicCrashLog(context, destination.name, sanitizeLegacyDiagnostic(contents)) }
        trimPrivateLogs(directory)
        return destination
    }

    private fun trimPrivateLogs(directory: File) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_LOG_FILES)
            .forEach(File::delete)
    }

    private fun collectHistoricalExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !isEnabled(context) ||
            !collectingExits.compareAndSet(false, true)) return
        diagnosticsScope.launch {
            try {
                recordHistoricalExits(context)
            } catch (error: Exception) {
                android.util.Log.w("CrashLogStore", "Could not collect historical process exits", error)
            } finally {
                collectingExits.set(false)
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun recordHistoricalExits(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val seen = preferences.getStringSet("recorded_process_exits", emptySet()).orEmpty().toMutableSet()
        val earliest = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val records = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { it.timestamp >= earliest }
            .sortedBy { it.timestamp }
        for (record in records) {
            if (!isEnabled(context)) return
            val key = "${record.timestamp}:${record.pid}:${record.reason}"
            if (key in seen || !shouldRecordHistoricalExit(record.reason, record.importance,
                    record.processName, context.packageName)) continue
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val exportedAt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT).format(Date())
            val output = File(directory, "grayjoy-exit-$exportedAt-${record.timestamp}-${record.pid}.txt")
            val contents = buildString {
                appendLine("Recovered process-exit diagnostic")
                appendLine("Exit timestamp: ${Instant.ofEpochMilli(record.timestamp)}")
                appendLine("Export timestamp: ${Instant.now()}")
                appendLine("Current installed version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Process: ${record.processName}; PID: ${record.pid}")
                appendLine("Reason: ${record.reason}; status: ${record.status}; importance: ${record.importance}")
                appendLine("Description: ${record.description.orEmpty().take(4096)}")
                appendLine("PSS: ${record.pss} KiB; RSS: ${record.rss} KiB")
                if (record.reason == ApplicationExitInfo.REASON_SIGNALED) {
                    appendLine("This records a foreground process termination, not proof of an application crash.")
                }
                // Native crash traces are protobuf on newer Android versions, not plain text.
                if (record.reason == ApplicationExitInfo.REASON_ANR) {
                    runCatching {
                        record.traceInputStream?.bufferedReader()?.use { it.readDiagnosticText(MAX_STACK_CHARS) }
                    }.getOrNull()?.let { appendLine(); append(it) }
                }
            }
            output.writeText(contents)
            if (shouldPromptForHistoricalCrash(record.reason, record.timestamp, previousLaunchAt,
                    runCatching { preferences.getLong("acknowledged_crash_at", 0L) }.getOrDefault(0L)) &&
                pending.value == null) {
                val id = "${record.timestamp}:exit-${record.reason}"
                markCrash(context, id)
                pending.value = id
            }
            if (isDownloadsCopyEnabled(context)) runCatching { writePublicCrashLog(context, output.name, contents) }
            trimPrivateLogs(directory)
            seen += key
            // Keep only identifiers Android can still return; no accumulating per-session list.
            val currentKeys = records.mapTo(mutableSetOf()) { "${it.timestamp}:${it.pid}:${it.reason}" }
            seen.retainAll(currentKeys)
            preferences.edit().putStringSet("recorded_process_exits", seen.toSet()).apply()
        }
    }

    suspend fun createShareArchive(context: Context): File = withContext(Dispatchers.IO) {
        withTimeoutOrNull(2_000L) {
            val flushed = CompletableDeferred<Unit>()
            eventQueue.send(EventWrite(flushed = flushed)); flushed.await()
        }
        val outputDirectory = File(context.cacheDir, "diagnostic-exports").apply { mkdirs() }
        val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT).format(Date())
        val output = File.createTempFile("Grayjoy-diagnostics-$name-", ".zip", outputDirectory)
        try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                entry("report.txt", "Grayjoy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid API ${Build.VERSION.SDK_INT}\nVariant: ${BuildConfig.BUILD_TYPE}\n" +
                    "Diagnostics enabled: ${isEnabled(context)}\nDropped events during overload: ${droppedEvents.get()}\n" +
                    "Last failure type: ${pending.value?.substringAfter(':')?.filter { it.isLetterOrDigit() || it in "._-" }.orEmpty()}\n" +
                    "Exception messages, account data, URLs and content titles are excluded.\n")
                val logs = File(context.filesDir, DIRECTORY).listFiles { file -> file.isFile && file.name.endsWith(".txt") }
                    .orEmpty().sortedByDescending(File::lastModified).take(MAX_LOG_FILES)
                logs.forEachIndexed { index, file ->
                    val safe = file.bufferedReader().use { sanitizeLegacyDiagnostic(it.readDiagnosticText(MAX_STACK_CHARS)) }
                    entry("crash-${index + 1}.txt", safe)
                }
                zip.putNextEntry(ZipEntry("events.jsonl"))
                val requestedEvents = File(context.filesDir, "$DIRECTORY/events")
                val ring = if (requestedEvents == eventDirectory) eventRing else DiagnosticEventRing(requestedEvents)
                ring?.forEachRecord { line -> runCatching {
                    val json = JSONObject(line)
                    JSONObject().put("time", json.optString("time").filter { it in "0123456789TZ:.-+" }.take(40))
                        .put("component", json.optString("component").filter { it.isLetterOrDigit() || it in "._-" }.take(80))
                        .put("level", json.optString("level").filter(Char::isLetter).take(16))
                        .put("trace", sanitizeLegacyDiagnostic(json.optString("trace"))).apply {
                            safeBackendEventFields(listOf("operation", "from", "to", "phase", "reason")
                                .associateWith { json.optString(it) }).forEach { (key, value) -> put(key, value) }
                        }.toString()
                }.getOrNull()?.let { zip.write((it + "\n").toByteArray(Charsets.UTF_8)) } }
                zip.closeEntry()
            }
            outputDirectory.listFiles { file -> file.isFile && file != output && file.lastModified() < System.currentTimeMillis() - 86_400_000L }
                .orEmpty().forEach(File::delete)
            output
        } catch (error: Throwable) { output.delete(); throw error }
    }

    private fun writePublicCrashLog(context: Context, fileName: String, contents: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Grayjoy",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return
            try {
                resolver.openOutputStream(uri, "w")?.bufferedWriter()?.use {
                    it.write(contents)
                } ?: error("Could not open the public crash-log destination.")
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS,
            ).apply { mkdirs() }
            File(downloads, fileName).writeText(contents)
        }
    }
}
