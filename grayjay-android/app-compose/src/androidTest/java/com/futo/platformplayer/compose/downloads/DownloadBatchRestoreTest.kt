package com.futo.platformplayer.compose.downloads

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DownloadBatchRestoreTest {
    @Test fun restoredQueueKeepsTheOriginalTotalBeforePreparationRestarts() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "batch-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val pending = (1..4).map { DownloadUiModel("p", "$it", DownloadMediaType.Audio, DownloadStatus.Queued) }
            DownloadBatchProgressStore(context).update("p", pending, emptyList())
            val queue = (2..4).map { QueuedDownload("p", "$it", DownloadMediaType.Audio, DownloadStatus.Paused, 1) }
            val restored = DownloadBatchProgressStore(context).update("p",
                listOf(pending[0].copy(status = DownloadStatus.Completed)), queue)
            assertEquals(4, restored.total)
            assertEquals(1, restored.completed)
            assertEquals(.25f, restored.fraction, .0001f)
        } finally { base.deleteSharedPreferences(name) }
    }
}
