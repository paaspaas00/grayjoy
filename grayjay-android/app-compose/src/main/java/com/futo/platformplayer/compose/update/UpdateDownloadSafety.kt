package com.futo.platformplayer.compose.update

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Disconnect blocking I/O as soon as its owner cancels, even while that owner is in read(). */
internal suspend fun <T> withUpdateConnectionCleanup(
    disconnect: () -> Unit,
    action: suspend () -> T,
): T = coroutineScope {
    val cleanup = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { runCatching(disconnect) }
    }
    try { action() } finally {
        withContext(NonCancellable) { cleanup.cancelAndJoin() }
    }
}

/** Failed/cancelled replacement must leave the previous complete APK intact. */
internal fun publishUpdateDownload(files: UpdateDownloadFiles, checkActive: () -> Unit = {}) {
    checkActive()
    if (!files.temporary.isFile || files.temporary.length() <= 0L) throw IOException("Incomplete update download")
    require(files.temporary.canonicalFile.parentFile == files.destination.canonicalFile.parentFile) {
        "Update publication must stay in its download directory"
    }
    checkActive()
    Files.move(files.temporary.toPath(), files.destination.toPath(),
        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
