package com.futo.platformplayer.compose.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun cleanupOfCancelledAttemptCannotDeleteItsReplacement() {
        val old = createUpdateDownloadFiles(folder.root, "2.0.1")
        val next = createUpdateDownloadFiles(folder.root, "2.0.1")
        assertEquals(old.destination, next.destination)
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
}
