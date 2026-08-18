package com.futo.platformplayer.compose.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubePlaybackResolverTest {
    @Test
    fun `preference only changes primary resolver and always keeps fallback`() {
        assertEquals(
            listOf(YoutubePlaybackResolver.Grayjay, YoutubePlaybackResolver.NewPipe),
            youtubePlaybackResolverOrder(preferNewPipe = false),
        )
        assertEquals(
            listOf(YoutubePlaybackResolver.NewPipe, YoutubePlaybackResolver.Grayjay),
            youtubePlaybackResolverOrder(preferNewPipe = true),
        )
    }

    @Test
    fun `successful primary resolver does not invoke fallback`() = runBlocking {
        val calls = mutableListOf<YoutubePlaybackResolver>()
        val result = resolveYoutubePlaybackWithFallback(
            youtubePlaybackResolverOrder(preferNewPipe = true),
        ) { resolver ->
            calls += resolver
            "stream"
        }

        assertEquals(listOf(YoutubePlaybackResolver.NewPipe), calls)
        assertEquals(YoutubePlaybackResolver.NewPipe, result.resolver)
        assertEquals("stream", result.value)
        assertNull(result.primaryError)
    }

    @Test
    fun `secondary resolver takes over after primary failure`() = runBlocking {
        val primaryFailure = IllegalStateException("primary failed")
        val calls = mutableListOf<YoutubePlaybackResolver>()
        val result = resolveYoutubePlaybackWithFallback(
            youtubePlaybackResolverOrder(preferNewPipe = false),
        ) { resolver ->
            calls += resolver
            if (resolver == YoutubePlaybackResolver.Grayjay) throw primaryFailure
            "fallback stream"
        }

        assertEquals(YoutubePlaybackResolver.NewPipe, result.resolver)
        assertEquals("fallback stream", result.value)
        assertSame(primaryFailure, result.primaryError)
        assertEquals(
            listOf(YoutubePlaybackResolver.Grayjay, YoutubePlaybackResolver.NewPipe),
            calls,
        )
    }

    @Test
    fun `both failures retain primary cause and fallback detail`() = runBlocking {
        val primaryFailure = IllegalStateException("first")
        val fallbackFailure = IllegalArgumentException("second")
        val thrown = try {
            resolveYoutubePlaybackWithFallback(
                youtubePlaybackResolverOrder(preferNewPipe = false),
            ) { resolver ->
                if (resolver == YoutubePlaybackResolver.Grayjay) throw primaryFailure
                throw fallbackFailure
            }
            error("Expected both resolvers to fail")
        } catch (error: IllegalStateException) {
            error
        }

        assertSame(primaryFailure, thrown.cause)
        assertTrue(thrown.suppressed.contains(fallbackFailure))
        assertTrue(thrown.message.orEmpty().contains("first"))
        assertTrue(thrown.message.orEmpty().contains("second"))
    }

    @Test
    fun `cancellation never triggers the fallback resolver`() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val calls = mutableListOf<YoutubePlaybackResolver>()
        val thrown = try {
            resolveYoutubePlaybackWithFallback(
                youtubePlaybackResolverOrder(preferNewPipe = true),
            ) { resolver ->
                calls += resolver
                throw cancellation
            }
            error("Expected cancellation")
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(YoutubePlaybackResolver.NewPipe), calls)
    }
}
