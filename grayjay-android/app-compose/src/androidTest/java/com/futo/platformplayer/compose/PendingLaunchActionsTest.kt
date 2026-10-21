package com.futo.platformplayer.compose

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test

class PendingLaunchActionsTest {
    @Test fun consumedExternalVideoIsNotReopenedByActivityRecreation() {
        var intentReads = 0
        val original = PendingLaunchActions(contentUrl = "https://www.youtube.com/watch?v=fixture")
        val initial = launchActionsForCreation(null) { intentReads++; original }
        assertEquals(original, initial)
        val saved = Bundle()
        initial.copy(contentUrl = null).saveTo(saved)
        val restored = launchActionsForCreation(saved) { intentReads++; original }
        assertNull(restored.contentUrl)
        assertEquals(1, intentReads)
    }

    @Test fun unprocessedImportAndPairingActionsSurviveRecreation() {
        val pending = PendingLaunchActions(sourceUrl = "grayjay://plugin/source",
            importUri = "content://provider/export.zip", pairingUrl = "grayjoy://pc-pair?fixture")
        val saved = Bundle()
        pending.saveTo(saved)
        assertEquals(pending, launchActionsForCreation(saved) { error("Old launch Intent must not be repeated") })
    }

    @Test fun olderSavedStateDoesNotRepeatAlreadyConsumedLaunchIntent() {
        assertEquals(PendingLaunchActions(), launchActionsForCreation(Bundle()) { error("Already delivered") })
    }
}
