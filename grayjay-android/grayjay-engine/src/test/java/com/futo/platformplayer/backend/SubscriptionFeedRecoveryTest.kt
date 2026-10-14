package com.futo.platformplayer.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SubscriptionFeedRecoveryTest {
    @Test fun fastSuccessDoesNotLoadTheFullChannel() = runBlocking {
        assertEquals(listOf("video"), recoverSubscriptionFeed({ listOf("video") }, { error("Unexpected full fetch") }))
    }

    @Test fun emptyOrFailedFastFeedFallsBack() = runBlocking {
        assertEquals(listOf("full"), recoverSubscriptionFeed({ emptyList<String>() }, { listOf("full") }))
        assertEquals(listOf("full"), recoverSubscriptionFeed({ throw IOException("offline") }, { listOf("full") }))
    }

    @Test fun failedRefreshCannotBecomeSuccessfulEmptyFeed() {
        val error = IOException("No connection")
        try {
            successfulSubscriptionFeeds<String>(listOf(Result.failure(error), Result.failure(error)))
            fail("A failed refresh must not erase cached videos")
        } catch (actual: IOException) { assertSame(error, actual) }
        assertEquals(listOf("ok"), successfulSubscriptionFeeds(listOf(Result.failure(error), Result.success(listOf("ok")))))
        assertEquals(emptyList<String>(), successfulSubscriptionFeeds(listOf(Result.success(emptyList<String>()))))
    }

    @Test fun cancellationNeverTriggersFallbackOrPartialSuccess() = runBlocking {
        val cancelled = CancellationException("Changed feed")
        try {
            recoverSubscriptionFeed<String>({ throw cancelled }, { error("Must not fallback") })
            fail("Must propagate cancellation")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
        try {
            successfulSubscriptionFeeds(listOf(Result.failure(cancelled), Result.success(listOf("old"))))
            fail("Must discard cancelled refresh")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
}
