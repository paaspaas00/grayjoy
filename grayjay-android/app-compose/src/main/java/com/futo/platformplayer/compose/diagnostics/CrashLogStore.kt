package com.futo.platformplayer.compose.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import com.futo.platformplayer.compose.BuildConfig
import java.io.File
import java.time.Instant
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
        val destination = File(directory, "crash-$timestamp.log")
        val stack = throwable.stackTraceToString().take(MAX_STACK_CHARS)
        destination.writeText(
            buildString {
                appendLine("Timestamp: ${Instant.ofEpochMilli(timestamp)}")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(stack)
            },
        )
        directory.listFiles { file -> file.isFile && file.name.endsWith(".log") }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_LOG_FILES)
            .forEach(File::delete)
        return destination
    }
}
