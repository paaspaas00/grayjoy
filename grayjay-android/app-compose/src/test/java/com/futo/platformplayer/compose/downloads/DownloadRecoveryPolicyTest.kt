package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.ui.DownloadMediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecoveryPolicyTest {
    @Test fun offlineSecondaryBackendPausesInsteadOfFailingDownloadPermanently() {
        val error = IllegalStateException("Both engines failed", IllegalStateException("No supported stream"))
        error.addSuppressed(java.net.UnknownHostException("offline"))
        assertTrue(error.isRecoverableConnectivityFailure())
    }
    @Test fun cyclicNonNetworkErrorsDoNotRetryForever() {
        val first = IllegalStateException("deleted")
        val second = IllegalStateException("no stream", first)
        first.addSuppressed(second)
        assertFalse(first.isRecoverableConnectivityFailure())
    }
    @Test
    fun rehydratingTransferDoesNotWaitForItself() {
        assertTrue(
            isExcludedRecoveryTransfer(
                transferVideoId = "video",
                transferMediaType = DownloadMediaType.Video,
                excludingVideoId = "video",
                excludingMediaType = DownloadMediaType.Video,
            ),
        )
    }

    @Test
    fun anotherMediaTypeStillSerializesBehindRecovery() {
        assertFalse(
            isExcludedRecoveryTransfer(
                transferVideoId = "video",
                transferMediaType = DownloadMediaType.Audio,
                excludingVideoId = "video",
                excludingMediaType = DownloadMediaType.Video,
            ),
        )
    }
}
