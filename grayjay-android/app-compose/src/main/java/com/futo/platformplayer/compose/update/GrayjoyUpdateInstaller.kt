package com.futo.platformplayer.compose.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal class GrayjoyUpdateInstaller(private val context: Context) {
    suspend fun download(
        versionName: String,
        downloadUrl: String,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(downloadUrl.startsWith("https://github.com/paaspaas00/grayjoy/releases/")) {
            "Unsupported update host"
        }
        require(downloadUrl.substringBefore('?').endsWith("-release.apk")) {
            "The selected release asset is not a signed release APK"
        }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val files = createUpdateDownloadFiles(directory, versionName)
        val destination = files.destination
        val temporary = files.temporary
        var current = URL(downloadUrl)
        var connection: HttpURLConnection? = null
        var completed = false
        try {
            repeat(6) {
                currentCoroutineContext().ensureActive()
                connection?.disconnect()
                connection = current.openConnection() as HttpURLConnection
                connection!!.instanceFollowRedirects = false
                connection!!.connectTimeout = 12_000
                connection!!.readTimeout = 30_000
                connection!!.setRequestProperty("User-Agent", "Grayjoy/$versionName")
                val code = connection!!.responseCode
                if (code in 300..399) {
                    current = URL(current,
                        connection!!.getHeaderField("Location") ?: error("Missing redirect"),
                    )
                    require(current.protocol == "https") { "Update redirect must use HTTPS" }
                } else {
                    require(code in 200..299) { "Update download failed with HTTP $code" }
                    val expected = connection!!.contentLengthLong
                    require(expected <= MAX_APK_BYTES) { "Update is too large" }
                    val totalBytes = expected.takeIf { it > 0L }
                    onProgress(0L, totalBytes)
                    var copied = 0L
                    var lastProgressAt = 0L
                    connection!!.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                require(copied <= MAX_APK_BYTES) { "Update is too large" }
                                output.write(buffer, 0, count)
                                val now = System.currentTimeMillis()
                                if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                                    onProgress(copied, totalBytes)
                                    lastProgressAt = now
                                }
                            }
                        }
                    }
                    require(copied > 0L && (expected <= 0L || copied == expected)) {
                        "Incomplete update download"
                    }
                    onProgress(copied, totalBytes ?: copied)
                    if (destination.exists()) destination.delete()
                    require(temporary.renameTo(destination)) { "Could not finish update download" }
                    completed = true
                    return@withContext destination
                }
            }
            error("Too many update redirects")
        } finally {
            connection?.disconnect()
            if (!completed) temporary.delete()
        }
    }

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
        const val PROGRESS_INTERVAL_MS = 150L
    }
}

internal data class UpdateDownloadFiles(val destination: File, val temporary: File)

internal fun createUpdateDownloadFiles(directory: File, versionName: String): UpdateDownloadFiles {
    val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    require(directory.isDirectory || directory.mkdirs()) { "Could not create update directory" }
    return UpdateDownloadFiles(
        File(directory, "Grayjoy-$safeVersion-release.apk"),
        // A cancelled request may finish its cleanup after the next attempt has begun.
        File.createTempFile("Grayjoy-$safeVersion-", ".part", directory),
    )
}
