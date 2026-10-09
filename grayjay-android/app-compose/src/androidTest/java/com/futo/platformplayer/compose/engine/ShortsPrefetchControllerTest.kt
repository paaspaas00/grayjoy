package com.futo.platformplayer.compose.engine

import androidx.media3.datasource.DefaultHttpDataSource
import com.futo.platformplayer.compose.shorts.PrefixCacheHttpDataSource
import com.futo.platformplayer.compose.shorts.ShortsPrefetchController
import com.futo.platformplayer.compose.ui.VideoUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ShortsPrefetchControllerTest {
    @Test fun foregroundPlaybackJoinsTheSamePrefetchAndBackSwipeReusesItsPreviousDescriptor() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val ready = MutableStateFlow<String?>("a")
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<VideoUiModel>()
        val calls = AtomicInteger()
        val first = fixture("a")
        val next = fixture("b")
        val controller = ShortsPrefetchController(
            scope = scope,
            resolve = {
                calls.incrementAndGet()
                assertEquals("b", it.id)
                started.complete(Unit)
                response.await()
            },
            playbackReady = { ready.value == it }, playbackChanges = ready,
            onRetainedSources = {}, preferredHeight = { 720 },
        )
        try {
            withContext(Dispatchers.Main.immediate) {
                controller.update("profile", listOf(first, next))
                controller.rememberPlayed("profile", first)
            }
            withTimeout(5_000) { started.await() }
            val foreground = withContext(Dispatchers.Main.immediate) {
                controller.update("profile", listOf(next, first))
                ready.value = "b"
                scope.async { controller.playback("profile", "b") }
            }
            assertFalse(foreground.isCompleted)
            response.complete(next)
            assertEquals("b", withTimeout(5_000) { foreground.await() }?.id)
            assertEquals(1, calls.get())

            val restored = withContext(Dispatchers.Main.immediate) {
                controller.update("profile", listOf(first, next))
                ready.value = "a"
                controller.playback("profile", "a")
            }
            assertEquals("a", restored?.id)
            assertEquals(first.title, restored?.title)
            assertEquals(1, calls.get())
        } finally {
            withContext(Dispatchers.Main.immediate) { controller.clear() }
            scope.cancel()
            response.cancel()
        }
    }

    @Test fun profileChangeRejectsALateNonCancellableResolutionAndItsSourceFactories() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val ready = MutableStateFlow<String?>("a")
        val started = CompletableDeferred<Unit>()
        val finishOldRequest = CompletableDeferred<Unit>()
        val oldRequestReturned = CompletableDeferred<Unit>()
        val oldFactory = DefaultHttpDataSource.Factory()
        val retained = mutableListOf<List<VideoUiModel>>()
        val first = fixture("a")
        val next = fixture("b")
        val controller = ShortsPrefetchController(
            scope = scope,
            resolve = {
                try {
                    withContext(NonCancellable) {
                        started.complete(Unit)
                        finishOldRequest.await()
                        it.copy(title = "Old profile result", playbackDataSourceFactory = oldFactory)
                    }
                } finally { oldRequestReturned.complete(Unit) }
            },
            playbackReady = { ready.value == it }, playbackChanges = ready,
            onRetainedSources = { retained += it.toList() }, preferredHeight = { 720 },
        )
        try {
            withContext(Dispatchers.Main.immediate) {
                controller.update("old-profile", listOf(first, next))
                controller.rememberPlayed("old-profile", first)
            }
            withTimeout(5_000) { started.await() }
            val callbacksAfterSwitch = withContext(Dispatchers.Main.immediate) {
                controller.clear()
                ready.value = null // New playback is loading; no new speculative resolution.
                controller.update("new-profile", listOf(first, next))
                controller.rememberPlayed("new-profile", first.copy(title = "New profile result"))
                retained.size
            }
            finishOldRequest.complete(Unit)
            withTimeout(5_000) { oldRequestReturned.await() }
            withContext(Dispatchers.Main.immediate) {
                assertNull(controller.playback("old-profile", "b"))
                assertNull(controller.playback("new-profile", "b"))
                assertEquals("New profile result", controller.playback("new-profile", "a")?.title)
                assertEquals(callbacksAfterSwitch, retained.size)
                assertTrue(retained.flatten().none {
                    (it.playbackDataSourceFactory as? PrefixCacheHttpDataSource.Factory)?.originalFactory === oldFactory
                })
            }
        } finally {
            finishOldRequest.complete(Unit)
            withContext(Dispatchers.Main.immediate) { controller.clear() }
            scope.cancel()
        }
    }

    private fun fixture(id: String) = VideoUiModel(
        id = id, title = "Fixture $id", creator = "Fixture", metadata = "", duration = "",
        sourceId = "youtube", playbackUrl = "fixture://$id",
    )
}
