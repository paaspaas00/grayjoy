package com.futo.platformplayer.compose

import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSwitchJobCancellationTest {
    @Test
    fun cancellationCallbacksCanMutateTheJobRegistry() {
        val jobs = mutableMapOf<String, Job>()
        val first = Job()
        val second = Job()
        jobs["first"] = first
        jobs["second"] = second
        first.invokeOnCompletion { jobs.remove("first") }
        second.invokeOnCompletion { jobs.remove("second") }

        jobs.cancelAndClearJobs()

        assertTrue(jobs.isEmpty())
        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
    }
}
