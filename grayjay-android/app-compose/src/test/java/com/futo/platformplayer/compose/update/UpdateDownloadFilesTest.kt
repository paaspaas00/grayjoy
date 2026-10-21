package com.futo.platformplayer.compose.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CancellationException

class UpdateDownloadFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun cleanupOfCancelledAttemptCannotDeleteItsReplacement() {
        val old = createUpdateDownloadFiles(folder.root, "2.0.1")
        val next = createUpdateDownloadFiles(folder.root, "2.0.1")
        assertEquals(old.destination, next.destination)
        assertTrue(old.destination.name.endsWith("-release.apk"))
        assertNotEquals(old.temporary, next.temporary)
        old.temporary.delete()
        assertTrue(next.temporary.exists())
    }

    @Test
    fun versionNamesCannotEscapeTheUpdateDirectory() {
        val files = createUpdateDownloadFiles(folder.root, "../../other\\location")
        assertEquals(folder.root.canonicalFile, files.destination.canonicalFile.parentFile)
        assertEquals(folder.root.canonicalFile, files.temporary.canonicalFile.parentFile)
    }

    @Test fun failedPublicationPreservesThePreviouslyDownloadedApk() {
        val files = createUpdateDownloadFiles(folder.root, "3.4.0")
        files.destination.writeText("previous complete apk")
        files.temporary.delete()
        assertThrows(java.io.IOException::class.java) { publishUpdateDownload(files) }
        assertEquals("previous complete apk", files.destination.readText())
    }

    @Test fun cancellationAtTheFinalCommitGateCannotReplaceTheOldApk() {
        val files = createUpdateDownloadFiles(folder.root, "3.4.0")
        files.destination.writeText("previous complete apk")
        files.temporary.writeText("replacement apk")
        var checks = 0
        assertThrows(CancellationException::class.java) {
            publishUpdateDownload(files) { if (++checks == 2) throw CancellationException("cancelled") }
        }
        assertEquals("previous complete apk", files.destination.readText())
        assertTrue(files.temporary.exists())
    }

    @Test fun completedPublicationReplacesTheApkAndConsumesOnlyItsOwnTemporaryFile() {
        val old = createUpdateDownloadFiles(folder.root, "3.4.0")
        val current = createUpdateDownloadFiles(folder.root, "3.4.0")
        old.destination.writeText("previous complete apk")
        old.temporary.writeText("unrelated old attempt")
        current.temporary.writeText("replacement apk")
        publishUpdateDownload(current)
        assertEquals("replacement apk", current.destination.readText())
        assertFalse(current.temporary.exists())
        assertEquals("unrelated old attempt", old.temporary.readText())
    }
}
