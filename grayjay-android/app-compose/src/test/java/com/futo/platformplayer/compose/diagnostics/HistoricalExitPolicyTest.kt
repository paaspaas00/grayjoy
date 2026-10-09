package com.futo.platformplayer.compose.diagnostics

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import org.junit.Assert.*
import org.junit.Test

class HistoricalExitPolicyTest {
    @Test fun appFailuresAreCollectedWhileRoutineProcessReclamationIsExcluded() {
        val app = "com.example.player"
        val foreground = RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
        val cached = RunningAppProcessInfo.IMPORTANCE_CACHED
        assertTrue(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_ANR, cached, app, app))
        assertTrue(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_CRASH_NATIVE, foreground, "$app:worker", app))
        assertTrue(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_SIGNALED, foreground, app, app))
        assertFalse(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_SIGNALED, cached, app, app))
        assertFalse(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_LOW_MEMORY, cached, app, app))
        assertFalse(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_USER_REQUESTED, foreground, app, app))
        assertFalse(shouldRecordHistoricalExit(ApplicationExitInfo.REASON_CRASH, foreground, "$app.fake", app))
    }
}
