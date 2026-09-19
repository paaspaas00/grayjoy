package com.futo.platformplayer.compose.diagnostics

import android.content.Context
import android.content.ContentValues
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
import kotlin.system.exitProcess

internal object CrashLogStore {
    private const val PREFERENCES = "grayjoy_diagnostics"
    private const val KEY_ENABLED = "crash_logging_enabled"
    private const val DIRECTORY = "diagnostics"
    private const val MAX_LOG_FILES = 10
    private const val MAX_STACK_CHARS = 256_000
    private val installed = AtomicBoolean(false)

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                if (isEnabled(appContext)) writeCrash(appContext, thread, throwable)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
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
        val fileName = "grayjoy-crash-$formattedTimestamp.txt"
        val destination = File(directory, fileName)
        val stack = throwable.stackTraceToString().take(MAX_STACK_CHARS)
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
        runCatching { writePublicCrashLog(context, fileName, contents) }
        directory.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_LOG_FILES)
            .forEach(File::delete)
        return destination
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
