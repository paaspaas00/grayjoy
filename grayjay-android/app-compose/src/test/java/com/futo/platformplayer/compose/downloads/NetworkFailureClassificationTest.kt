package com.futo.platformplayer.compose.downloads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkFailureClassificationTest {
    @Test
    fun socketAndDnsFailuresAreRecoverable() {
        assertTrue(UnknownHostException("media.example").isRecoverableConnectivityFailure())
        assertTrue(SocketTimeoutException("read timed out").isRecoverableConnectivityFailure())
    }

    @Test
    fun pluginWrappedConnectionAbortIsRecoverable() {
        assertTrue(
            IllegalStateException("[Youtube] P:Software caused connection abort")
                .isRecoverableConnectivityFailure(),
        )
    }

    @Test
    fun sourceAndHttpResponseErrorsRemainFailures() {
        assertFalse(
            IllegalStateException("The plugin returned no supported video stream.")
                .isRecoverableConnectivityFailure(),
        )
        assertFalse(IllegalStateException("HTTP 403 Forbidden").isRecoverableConnectivityFailure())
    }
}
