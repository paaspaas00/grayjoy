package com.futo.platformplayer.compose.jobs

/** Services call this on the main thread so cancellation reaches the actual job owner. */
internal object RunningJobs {
    private var pauseOwner: (() -> Unit)? = null

    fun register(owner: () -> Unit) { pauseOwner = owner }
    fun unregister(owner: () -> Unit) {
        if (pauseOwner === owner) pauseOwner = null
    }
    fun pause() { pauseOwner?.invoke() }
}
