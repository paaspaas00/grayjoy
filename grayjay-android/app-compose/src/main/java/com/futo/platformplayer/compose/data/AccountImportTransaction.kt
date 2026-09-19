package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.engine.EngineUserImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Applies an account import as one logical transaction across the library and profile settings.
 * SharedPreferences cannot transact across files, so a cancellation or write failure restores
 * both snapshots from a non-cancellable context before control returns to the caller.
 */
internal suspend fun applyAccountImportTransaction(
    repository: LibraryRepository,
    preferences: GrayjayPreferences,
    result: EngineUserImportResult,
    repairSyntheticHistoryDates: Boolean,
) {
    val librarySnapshot = withContext(Dispatchers.IO) { repository.createImportSnapshot() }
    val preferenceSnapshot = withContext(Dispatchers.IO) {
        preferences.createAccountImportSnapshot()
    }
    try {
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO) {
            repository.mergeImportedData(
                videos = result.videos,
                playlists = result.playlists,
                repairSyntheticHistoryDates = repairSyntheticHistoryDates,
            )
            preferences.mergeImportedSubscriptions(result.subscriptions)
        }
        currentCoroutineContext().ensureActive()
    } catch (error: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) {
            repository.restoreImportSnapshot(librarySnapshot)
            preferences.restoreAccountImportSnapshot(preferenceSnapshot)
        }
        throw error
    }
}
