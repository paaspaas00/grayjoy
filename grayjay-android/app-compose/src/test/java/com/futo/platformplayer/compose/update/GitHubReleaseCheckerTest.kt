package com.futo.platformplayer.compose.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals

class GitHubReleaseCheckerTest {
    @Test
    fun `semantic release comparison handles v prefixes and multi digit parts`() {
        assertTrue(isNewerVersion("v0.9.9", "0.9.8"))
        assertTrue(isNewerVersion("0.10.0", "0.9.99"))
        assertTrue(isNewerVersion("1.0.0-beta", "0.9.8"))
        assertTrue(isNewerVersion("v1.0.0", "v0.9.14"))
    }

    @Test
    fun `same and older versions do not show an update`() {
        assertFalse(isNewerVersion("v0.9.8", "0.9.8"))
        assertFalse(isNewerVersion("0.9.7", "0.9.8"))
        assertFalse(isNewerVersion("not-a-version", "0.9.8"))
    }

    @Test
    fun `debug asset selection follows device ABI and falls back to universal`() {
        val assets = listOf(
            GitHubReleaseAsset("Grayjoy-v1.4.0-universal-debug.apk", "https://example/universal"),
            GitHubReleaseAsset("Grayjoy-v1.4.0-arm64-v8a-debug.apk", "https://example/arm64"),
            GitHubReleaseAsset("Grayjoy-v1.4.0-arm64-v8a-release.apk", "https://example/release"),
        )
        assertEquals("https://example/arm64", selectDebugApkUrl(assets, listOf("arm64-v8a")))
        assertEquals("https://example/universal", selectDebugApkUrl(assets, listOf("riscv64")))
    }
}
