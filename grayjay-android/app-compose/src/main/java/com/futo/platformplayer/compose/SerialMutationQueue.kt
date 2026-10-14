package com.futo.platformplayer.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Ordered UI requests, all invalidated together when the owning profile goes away. */
internal class SerialMutationQueue(private val scope: CoroutineScope) {
    private val jobs = linkedSetOf<Job>()
    private var tail: Job? = null
    private var generation = 0L

    fun launch(block: suspend () -> Unit) {
        val previous = tail
        val token = generation
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.join()
                if (token == generation) block()
            } finally {
                jobs.remove(job)
                if (tail === job) tail = null
            }
        }
        jobs += job
        tail = job
        if (!job.start()) {
            jobs.remove(job)
            if (tail === job) tail = null
        }
    }

    fun cancel() {
        generation++
        val cancelled = jobs.toList()
        jobs.clear()
        tail = null
        cancelled.forEach(Job::cancel)
    }
}
