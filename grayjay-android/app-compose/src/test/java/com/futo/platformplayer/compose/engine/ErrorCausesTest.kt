package com.futo.platformplayer.compose.engine

import org.junit.Assert.*
import org.junit.Test

class ErrorCausesTest {
    @Test fun cyclicPluginExceptionsTerminateAndKeepBothCauses() {
        val first = IllegalStateException("outer")
        val second = IllegalArgumentException("inner")
        first.initCause(second); second.initCause(first)
        assertEquals(listOf(first, second), first.boundedCauses().toList())
    }
    @Test fun pathologicalDepthIsBoundedWithoutRecursion() {
        var error: Throwable = IllegalArgumentException("root")
        repeat(10_000) { error = IllegalStateException("wrapper", error) }
        assertEquals(32, error.boundedCauses().count())
    }
    @Test fun normalRootCauseIsPreserved() {
        val root = java.net.UnknownHostException("offline")
        assertSame(root, IllegalStateException("outer", root).boundedCauses().last())
    }
}
