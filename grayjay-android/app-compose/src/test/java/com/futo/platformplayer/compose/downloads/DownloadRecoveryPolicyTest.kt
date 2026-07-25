package com.futo.platformplayer.compose.downloads

import com.futo.platformplayer.compose.ui.DownloadMediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecoveryPolicyTest {
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
