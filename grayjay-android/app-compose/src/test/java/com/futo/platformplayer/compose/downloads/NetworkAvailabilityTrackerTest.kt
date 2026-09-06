package com.futo.platformplayer.compose.downloads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAvailabilityTrackerTest {
    @Test
    fun recoveryUsesCallbackCapabilitiesAndWaitsForValidation() {
        val state = NetworkAvailabilityTracker<String>()
        state.onAvailable("mobile")
        assertFalse(state.available.value)
        state.onCapabilitiesChanged("mobile", true)
        assertTrue(state.available.value)
        state.onCapabilitiesChanged("mobile", false)
        assertFalse(state.available.value)
        state.onCapabilitiesChanged("mobile", true)
        assertTrue(state.available.value)
        state.onLost("mobile")
        assertFalse(state.available.value)
    }

    @Test
    fun previousNetworkEventsCannotOverwriteRecoveredDefaultNetwork() {
        val state = NetworkAvailabilityTracker("wifi", true)
        state.onAvailable("mobile")
        state.onCapabilitiesChanged("mobile", true)
        state.onLost("wifi")
        state.onCapabilitiesChanged("wifi", false)
        state.onBlockedStatusChanged("wifi", true)
        assertTrue(state.available.value)
    }

    @Test
    fun blockedNetworkDoesNotResumeUntilUnblockedAndValidated() {
        val state = NetworkAvailabilityTracker("wifi", true)
        state.onBlockedStatusChanged("wifi", true)
        state.onCapabilitiesChanged("wifi", true)
        assertFalse(state.available.value)
        state.onBlockedStatusChanged("wifi", false)
        assertTrue(state.available.value)
    }
}
