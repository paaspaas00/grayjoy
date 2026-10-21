package com.futo.platformplayer.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Response
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellation must close the call while its response body is still being read. */
internal suspend fun <T> withCancellableVoiceDownload(call: Call, consume: suspend (Response) -> T): T =
    coroutineScope {
        suspendCancellableCoroutine { continuation ->
            val worker = launch(Dispatchers.IO) {
                try {
                    val result = call.execute().use { consume(it) }
                    continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation { call.cancel(); worker.cancel() }
        }
    }

private val modelInstallLock = Any()

/** A process killed between the two directory renames must leave the previous model usable. */
internal fun recoverVoiceModelDirectory(target: File): File = synchronized(modelInstallLock) {
    val previous = File(target.parentFile, "${target.name}.previous")
    // Checking installed models also happens on the settings screen: a read-only/full volume
    // must report the model unavailable instead of throwing from composition.
    if (!target.exists() && previous.exists()) previous.renameTo(target)
    target
}

internal fun installVoiceModelDirectory(staging: File, target: File,
    rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }) = synchronized(modelInstallLock) {
    require(staging.isDirectory && staging.canonicalFile != target.canonicalFile &&
        staging.canonicalFile.parentFile == target.canonicalFile.parentFile)
    recoverVoiceModelDirectory(target)
    val previous = File(target.parentFile, "${target.name}.previous")
    check(target.exists() || !previous.exists()) { "Could not recover the previous model" }
    check(!previous.exists() || previous.deleteRecursively()) { "Could not clear the previous model backup" }
    if (target.exists()) check(rename(target, previous)) { "Could not preserve the previous model" }
    try {
        check(rename(staging, target)) { "Could not install the verified model" }
    } catch (error: Exception) {
        if (!target.exists() && previous.exists()) check(rename(previous, target)) { "Could not restore the previous model" }
        throw error
    }
    previous.deleteRecursively()
    Unit
}
