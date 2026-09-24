package com.futo.platformplayer.backend

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PluginClientEpochTest {
    @Test fun staleInitializationCannotPublishAfterProfileOrAuthenticationChanges() {
        val epoch = PluginClientEpoch()
        val initialized = CountDownLatch(1)
        val publish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var storedClient = "new-account"
        try {
            val oldLoad = executor.submit<Boolean> {
                val generation = epoch.snapshot()
                initialized.countDown()
                assertTrue(publish.await(5, TimeUnit.SECONDS))
                try {
                    epoch.current(generation) { storedClient = "old-account" }
                    false
                } catch (_: SourceSessionChangedException) { true }
            }
            assertTrue(initialized.await(5, TimeUnit.SECONDS))
            epoch.invalidate { storedClient = "new-account" }
            publish.countDown()
            assertTrue(oldLoad.get(5, TimeUnit.SECONDS))
            assertEquals("new-account", storedClient)
        } finally {
            publish.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun repeatedInvalidationsRejectAllPriorEpochsButAllowCurrentOne() {
        val epoch = PluginClientEpoch()
        repeat(10000) {
            val stale = epoch.snapshot()
            epoch.invalidate { }
            var staleActionRan = false
            try {
                epoch.current(stale) { staleActionRan = true }
                fail("Stale source load was accepted")
            } catch (_: SourceSessionChangedException) { }
            assertFalse(staleActionRan)
            assertEquals(it, epoch.current(epoch.snapshot()) { it })
        }
    }
}
