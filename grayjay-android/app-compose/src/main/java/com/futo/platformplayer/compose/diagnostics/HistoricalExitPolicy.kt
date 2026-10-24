package com.futo.platformplayer.compose.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo

internal fun shouldPromptForHistoricalCrash(reason: Int, timestamp: Long, previousLaunch: Long, acknowledgedAt: Long): Boolean =
    previousLaunch > 0 && timestamp >= previousLaunch && timestamp > acknowledgedAt && reason in setOf(
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)

/** Routine cached-process reclamation and explicit user stops are not app crashes. */
internal fun shouldRecordHistoricalExit(reason: Int, importance: Int, processName: String, packageName: String): Boolean {
    if (processName != packageName && !processName.startsWith("$packageName:")) return false
    return when (reason) {
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
        ApplicationExitInfo.REASON_SIGNALED -> importance in
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND..
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
        else -> false
    }
}
