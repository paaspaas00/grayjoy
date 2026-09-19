package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.os.StatFs
import java.io.IOException
import kotlin.math.max

internal data class DownloadStorageStatus(
    val availableBytes: Long,
    val totalBytes: Long,
    val reserveBytes: Long,
    val requiredFreeBytes: Long,
    val knownRemainingBytes: Long,
    val isWarning: Boolean,
    val downloadsPaused: Boolean,
)

/**
 * Keeps a real free-space reserve instead of waiting for a write to fail at zero bytes. The
 * platform's own low-storage threshold is device-specific; the additional bounded reserve covers
 * data already buffered by concurrent download workers and filesystem/accounting delays.
 */
internal object DownloadStorageGuard {
    fun inspect(
        context: Context,
        knownRemainingBytes: Long = 0L,
        wasPaused: Boolean = false,
    ): DownloadStorageStatus {
        val directory = context.applicationContext.filesDir
        val stat = StatFs(directory.absolutePath)
        val available = stat.availableBytes.coerceAtLeast(0L)
        val total = stat.totalBytes.coerceAtLeast(1L)
        return assessDownloadStorage(
            availableBytes = available,
            totalBytes = total,
            // Media3's DEVICE_STORAGE_NOT_LOW requirement independently honors Android's
            // device-specific low-storage policy. This reserve is Grayjoy's earlier guard.
            platformLowBytes = 0L,
            knownRemainingBytes = knownRemainingBytes,
            wasPaused = wasPaused,
        )
    }

    fun requireCapacity(context: Context, additionalBytes: Long) {
        val status = inspect(
            context = context,
            knownRemainingBytes = additionalBytes.coerceAtLeast(0L),
        )
        if (status.downloadsPaused) throw InsufficientStorageException(status)
    }
}

internal class InsufficientStorageException(
    val storageStatus: DownloadStorageStatus,
) : IOException("Insufficient free storage for this job")

internal fun canResumeManagedDownloads(startupHeld: Boolean, storagePaused: Boolean): Boolean =
    !startupHeld && !storagePaused

internal fun assessDownloadStorage(
    availableBytes: Long,
    totalBytes: Long,
    platformLowBytes: Long,
    knownRemainingBytes: Long,
    wasPaused: Boolean,
): DownloadStorageStatus {
    val available = availableBytes.coerceAtLeast(0L)
    val total = totalBytes.coerceAtLeast(1L)
    val remaining = knownRemainingBytes.coerceAtLeast(0L)
    val dynamicReserve = (total / 50L).coerceIn(
        MIN_DOWNLOAD_STORAGE_RESERVE_BYTES,
        MAX_DOWNLOAD_STORAGE_RESERVE_BYTES,
    )
    val reserve = max(platformLowBytes.coerceAtLeast(0L), dynamicReserve)
    val required = saturatingAdd(reserve, remaining)
    val shouldPauseNow = available <= reserve || (remaining > 0L && available < required)
    val canResume = available >= saturatingAdd(required, DOWNLOAD_STORAGE_RESUME_HYSTERESIS_BYTES)
    val paused = if (wasPaused) !canResume else shouldPauseNow
    val warningThreshold = max(DOWNLOAD_STORAGE_WARNING_FLOOR_BYTES, saturatingAdd(reserve, reserve))
    return DownloadStorageStatus(
        availableBytes = available,
        totalBytes = total,
        reserveBytes = reserve,
        requiredFreeBytes = required,
        knownRemainingBytes = remaining,
        isWarning = paused || available <= warningThreshold,
        downloadsPaused = paused,
    )
}

private fun saturatingAdd(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

private const val MIN_DOWNLOAD_STORAGE_RESERVE_BYTES = 256L * 1024L * 1024L
private const val MAX_DOWNLOAD_STORAGE_RESERVE_BYTES = 1024L * 1024L * 1024L
private const val DOWNLOAD_STORAGE_WARNING_FLOOR_BYTES = 768L * 1024L * 1024L
private const val DOWNLOAD_STORAGE_RESUME_HYSTERESIS_BYTES = 128L * 1024L * 1024L
