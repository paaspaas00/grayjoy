package com.futo.platformplayer.compose.data

import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.engine.EngineUserImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val accountImportMutex = Mutex()

/**
 * Applies an account import as one logical transaction across the library and profile settings.
 * Import commits are serialized through rollback. Repository mutations cannot interleave between
 * the snapshot and rollback (for example a history checkpoint or a just-created playlist).
 */
internal suspend fun applyAccountImportTransaction(
    repository: LibraryRepository,
    preferences: GrayjayPreferences,
    result: EngineUserImportResult,
    repairSyntheticHistoryDates: Boolean,
) = accountImportMutex.withLock {
    withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        synchronized(repository.transactionLock) {
            synchronized(preferences.accountImportLock) {
                coroutine.ensureActive()
                val librarySnapshot = repository.createImportSnapshot()
                val preferenceSnapshot = preferences.createAccountImportSnapshot()
                val journal = (repository as? SharedPreferencesLibraryRepository)?.importJournal
                journal?.begin()
                try {
                    repository.mergeImportedData(
                        videos = result.videos,
                        playlists = result.playlists,
                        repairSyntheticHistoryDates = repairSyntheticHistoryDates,
                    )
                    coroutine.ensureActive()
                    preferences.mergeImportedSubscriptions(result.subscriptions)
                    coroutine.ensureActive()
                    journal?.complete()
                } catch (error: Throwable) {
                    // Synchronous rollback has no cancellation points and keeps both locks until
                    // complete. Try both stores even if one write fails; preserve the root error.
                    val libraryRollback = runCatching { repository.restoreImportSnapshot(librarySnapshot) }
                    val preferenceRollback = runCatching { preferences.restoreAccountImportSnapshot(preferenceSnapshot) }
                    libraryRollback.exceptionOrNull()?.let(error::addSuppressed)
                    preferenceRollback.exceptionOrNull()?.let(error::addSuppressed)
                    if (libraryRollback.isSuccess && preferenceRollback.isSuccess) {
                        runCatching { journal?.complete() }.exceptionOrNull()?.let(error::addSuppressed)
                    }
                    journal?.abandonForRecovery()
                    throw error
                }
            }
        }
    }
}
