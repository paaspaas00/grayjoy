package com.futo.platformplayer.backend

import kotlinx.coroutines.CancellationException

/** An unavailable lightweight feed is not proof that a channel has no uploads. */
internal suspend fun <T> recoverSubscriptionFeed(
    fast: (suspend () -> List<T>)?,
    complete: suspend () -> List<T>,
): List<T> {
    if (fast != null) {
        try {
            fast().takeIf { it.isNotEmpty() }?.let { return it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Full extraction also handles channel handles which have no canonical Atom URL.
        }
    }
    return complete()
}

internal fun <T> successfulSubscriptionFeeds(outcomes: List<Result<List<T>>>): List<T> {
    outcomes.firstNotNullOfOrNull { it.exceptionOrNull() as? CancellationException }?.let { throw it }
    if (outcomes.isNotEmpty() && outcomes.all { it.isFailure }) {
        throw outcomes.first().exceptionOrNull()!!
    }
    return outcomes.flatMap { it.getOrNull().orEmpty() }
}
