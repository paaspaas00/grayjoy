package com.futo.platformplayer.compose.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackendFallbackLoggingTest {
    @Test fun attemptIsReportedBeforeTheOtherBackendRunsEvenWhenBothFail() = runBlocking {
        val calls = mutableListOf<String>()
        val first = IllegalStateException("LOGIN_REQUIRED")
        val failure = runCatching { resolveYoutubePlaybackWithFallback(youtubePlaybackResolverOrder(true),
            onFallback = { from, to, reason -> calls += "$from->$to"; assertSame(first, reason) }) { backend ->
                calls += backend.name
                if (backend == YoutubePlaybackResolver.NewPipe) throw first else throw IllegalArgumentException("other failure")
            } }.exceptionOrNull()
        assertEquals(listOf("NewPipe", "NewPipe->Grayjay", "Grayjay"), calls)
        assertSame(first, failure!!.cause)
        assertEquals(1, failure.suppressed.size)
    }
    @Test fun diagnosticCallbackFailureCannotPreventSuccessfulFallback() = runBlocking {
        val result = resolveYoutubePlaybackWithFallback(youtubePlaybackResolverOrder(true),
            onFallback = { _, _, _ -> error("diagnostic sink unavailable") }) {
            if (it == YoutubePlaybackResolver.NewPipe) error("primary failure") else "ok"
        }
        assertEquals("ok", result.value)
    }
}
