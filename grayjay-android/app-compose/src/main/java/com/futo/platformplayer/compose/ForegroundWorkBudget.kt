package com.futo.platformplayer.compose

/** Optional metadata maintenance yields to interaction; explicit user operations never use this gate. */
internal object ForegroundWorkBudget {
    @Volatile private var constrained = false
    @Volatile private var foreground = false
    @Volatile private var lastInputAt = Long.MIN_VALUE
    fun enter(lowEnd: Boolean) {
        constrained = lowEnd
        foreground = true
        if (lowEnd) lastInputAt = android.os.SystemClock.elapsedRealtime() + 5_000L
    }
    fun leave() { foreground = false }
    fun touch() { if (constrained && foreground) lastInputAt = android.os.SystemClock.elapsedRealtime() }
    fun shouldYield(): Boolean = constrained && foreground &&
        android.os.SystemClock.elapsedRealtime() - lastInputAt < 1_500L
}
