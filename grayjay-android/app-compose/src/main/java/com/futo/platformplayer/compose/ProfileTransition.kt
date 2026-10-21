package com.futo.platformplayer.compose

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Publishing profile-bound repositories is safe only after both engine and catalog agree. */
internal suspend fun commitProfileTransition(
    previous: String,
    target: String,
    switchEngine: suspend (String) -> Unit,
    persistTarget: (String) -> Unit,
    rollbackFailed: (Throwable) -> Unit = {},
) {
    try {
        switchEngine(target)
        persistTarget(target)
    } catch (error: Exception) {
        withContext(NonCancellable) {
            try { switchEngine(previous) }
            catch (rollback: Exception) { rollbackFailed(rollback) }
        }
        throw error
    }
}
