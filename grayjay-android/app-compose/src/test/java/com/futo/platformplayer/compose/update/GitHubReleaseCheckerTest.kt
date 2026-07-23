package com.futo.platformplayer.compose.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun `semantic release comparison handles v prefixes and multi digit parts`() {
        assertTrue(isNewerVersion("v0.9.9", "0.9.8"))
        assertTrue(isNewerVersion("0.10.0", "0.9.99"))
        assertTrue(isNewerVersion("1.0.0-beta", "0.9.8"))
    }

    @Test
    fun `same and older versions do not show an update`() {
        assertFalse(isNewerVersion("v0.9.8", "0.9.8"))
        assertFalse(isNewerVersion("0.9.7", "0.9.8"))
        assertFalse(isNewerVersion("not-a-version", "0.9.8"))
    }
}
