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
import kotlin.system.exitProcess

internal object CrashLogStore {
    private const val PREFERENCES = "grayjoy_diagnostics"
    private const val KEY_ENABLED = "crash_logging_enabled"
    private const val DIRECTORY = "diagnostics"
    private const val MAX_LOG_FILES = 10
    private const val MAX_STACK_CHARS = 256_000
    private val installed = AtomicBoolean(false)
    private val collectingExits = AtomicBoolean(false)
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var beforeCrashHook: (() -> Unit)? = null

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) collectHistoricalExits(context.applicationContext)
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Keep the diagnostic before invoking application code, which may itself be blocked
            // on the lock involved in the crash. Restart recovery already holds managed jobs.
            runCatching {
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
        val stack = boundedStackTrace(throwable, MAX_STACK_CHARS)
        val contents = buildString {
            appendLine("Timestamp: ${Instant.ofEpochMilli(timestamp)}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }
        destination.writeText(contents)
        runCatching { writePublicCrashLog(context, destination.name, contents) }
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
            runCatching { writePublicCrashLog(context, output.name, contents) }
            trimPrivateLogs(directory)
            seen += key
            // Keep only identifiers Android can still return; no accumulating per-session list.
            val currentKeys = records.mapTo(mutableSetOf()) { "${it.timestamp}:${it.pid}:${it.reason}" }
            seen.retainAll(currentKeys)
            preferences.edit().putStringSet("recorded_process_exits", seen.toSet()).apply()
        }
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
