package com.futo.platformplayer.compose.diagnostics

import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/** Never retain exception messages: they may contain credentials, titles or user-entered text. */
internal fun technicalStackTrace(error: Throwable, limit: Int = 12_000): String {
    val text = StringBuilder()
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    fun visit(value: Throwable, prefix: String, depth: Int) {
        if (depth > 6 || text.length >= limit || !seen.add(value)) return
        text.append(prefix).append(value.javaClass.name).append('\n')
        value.stackTrace.take(32).forEach { frame ->
            if (text.length < limit) text.append("  at ").append(frame.className).append('.').append(frame.methodName)
                .append('(').append(frame.fileName?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]+\\.(?:kt|java|js|cpp|c)")) } ?: "Unknown")
                .append(':').append(frame.lineNumber).append(")\n")
        }
        value.cause?.let { visit(it, "Caused by: ", depth + 1) }
        value.suppressed.take(2).forEach { visit(it, "Suppressed: ", depth + 1) }
    }
    visit(error, "", 0)
    return text.take(limit).toString()
}

/** Legacy reports are shared as stack frames only; arbitrary text and identifiers stay private. */
internal fun sanitizeLegacyDiagnostic(text: String): String = text.lineSequence().mapNotNull { line ->
    val trimmed = line.trim()
    when {
        trimmed.matches(Regex("(?:Version|Current installed version|Android): [0-9A-Za-z. ()_-]+")) -> trimmed
        trimmed.matches(Regex("at [A-Za-z0-9_.$<>-]+\\((?:[A-Za-z0-9_.-]+\\.(?:kt|java|js|cpp|c)|Unknown):[-0-9]+\\)")) -> trimmed
        else -> Regex("^(?:(?:Caused by|Suppressed): )?([A-Za-z0-9_.$]+(?:Exception|Error))(?:$|:)")
            .find(trimmed)?.groupValues?.get(1)
    }
}.take(400).joinToString("\n")

/** Append-only blocks cap disk I/O; the reader exposes exactly the latest 1,000 retained events. */
internal class DiagnosticEventRing(private val directory: File) {
    private val lock = Any()
    private var current: File? = null
    private var count = 0
    private fun blocks() = directory.listFiles { file -> file.isFile && file.name.matches(Regex("events-[0-9]+\\.jsonl")) }
        .orEmpty().sortedBy { it.name }
    fun append(record: String) = synchronized(lock) {
        directory.mkdirs()
        if (current == null) {
            current = blocks().lastOrNull()
            current?.takeIf { it.length() > 0 }?.let { file ->
                val complete = java.io.RandomAccessFile(file, "r").use { it.seek(file.length() - 1); it.read() == 10 }
                if (!complete) file.appendText("\n")
            }
            count = current?.bufferedReader()?.use { it.readDiagnosticText(1_600_000).lineSequence().filter(String::isNotBlank).take(100).count() } ?: 0
        }
        if (current == null || count >= 100) {
            val sequence = (blocks().lastOrNull()?.name?.removePrefix("events-")?.removeSuffix(".jsonl")?.toLongOrNull() ?: 0L) + 1
            current = File(directory, "events-${sequence.toString().padStart(16, '0')}.jsonl")
            count = 0
            blocks().dropLast(10).forEach(File::delete)
        }
        current!!.appendText(record.take(16_000).replace("\n", " ") + "\n")
        count++
    }
    fun forEachRecord(block: (String) -> Unit) = synchronized(lock) {
        val files = blocks().takeLast(11)
        val lastCount = files.lastOrNull()?.bufferedReader()?.use { it.readDiagnosticText(1_600_000).lineSequence().filter(String::isNotBlank).take(100).count() } ?: 0
        var skip = ((files.size - 1).coerceAtLeast(0) * 100 + lastCount - 1000).coerceAtLeast(0)
        var emitted = 0
        files.forEach { file -> file.bufferedReader().use { reader ->
            reader.readDiagnosticText(1_600_000).lineSequence().take(100)
                .filter { it.isNotBlank() && it.length <= 16_000 }.forEach {
                    if (skip > 0) skip-- else if (emitted++ < 1000) block(it)
                }
        } }
    }
    fun snapshot(): List<String> = buildList { forEachRecord(::add) }
}

/** Keep technical wording in private logs, with authentication material removed before either sink. */
internal object DiagnosticLog {
    private fun safeText(message: String?, error: Throwable?): String = runCatching { buildString {
        append(redactDiagnosticMessage(message))
        if (error != null) { append('\n'); append(privateExceptionMessages(error)); append('\n'); append(technicalStackTrace(error)) }
    } }.getOrDefault("[diagnostic unavailable]")
    fun d(tag: String, message: String?, error: Throwable? = null): Int {
        CrashLogStore.recordEvent(tag, "debug", error, message); return android.util.Log.d(tag, safeText(message, error))
    }
    fun i(tag: String, message: String?, error: Throwable? = null): Int {
        CrashLogStore.recordEvent(tag, "info", error, message); return android.util.Log.i(tag, safeText(message, error))
    }
    fun w(tag: String, message: String?, error: Throwable? = null): Int {
        CrashLogStore.recordEvent(tag, "warning", error, message); return android.util.Log.w(tag, safeText(message, error))
    }
    fun w(tag: String, error: Throwable): Int = w(tag, null, error)
    fun e(tag: String, message: String?, error: Throwable? = null): Int {
        CrashLogStore.recordEvent(tag, "error", error, message); return android.util.Log.e(tag, safeText(message, error))
    }
}
