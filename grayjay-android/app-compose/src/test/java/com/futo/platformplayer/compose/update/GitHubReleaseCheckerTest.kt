package com.futo.platformplayer.compose.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals

class GitHubReleaseCheckerTest {
    @Test fun choosesHighestStableVersionEvenIfAnOldReleaseWasRepublished() {
        val releases = org.json.JSONArray("""[
            {"tag_name":"v1.0.0"},
            {"tag_name":"v4.0.0","prerelease":true},
            {"tag_name":"v5.0.0","draft":true},
            {"tag_name":"v2.3.1"},
            {"tag_name":"v2.4.0"},
            {"tag_name":"not-999-a-version"}
        ]""")
        assertEquals("2.4.0", latestInstallableRelease(releases, "2.3.0", emptyList())?.versionName)
    }

    @Test fun malformedOrOverflowingVersionsCannotDropTheirMajorNumber() {
        assertFalse(isNewerVersion("garbage999", "2.3.0"))
        assertFalse(isNewerVersion("9999999999999999999999.4.0", "2.3.0"))
        assertFalse(isNewerVersion("1.2.3.4", "2.3.0"))
    }

    @Test fun fuzzVersionOrderingIsAntisymmetricAndMatchesNumericTuples() {
        val random = kotlin.random.Random(0x564552)
        repeat(20_000) {
            val a = List(3) { random.nextInt(1000) }
            val b = List(3) { random.nextInt(1000) }
            val left = a.joinToString(".")
            val right = b.joinToString(".")
            val differing = (0..2).firstOrNull { a[it] != b[it] }
            val expected = differing != null && a[differing] > b[differing]
            assertEquals(expected, isNewerVersion(left, right))
            assertFalse(isNewerVersion(left, right) && isNewerVersion(right, left))
        }
    }
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
    fun `release asset selection follows device ABI and falls back to universal`() {
        val assets = listOf(
            GitHubReleaseAsset("Grayjoy-v1.4.0-universal-debug.apk", "https://example/universal"),
            GitHubReleaseAsset("Grayjoy-v1.4.0-arm64-v8a-debug.apk", "https://example/arm64"),
            GitHubReleaseAsset("Grayjoy-v1.4.0-universal-release.apk", "https://example/universal-release"),
            GitHubReleaseAsset("Grayjoy-v1.4.0-arm64-v8a-release.apk", "https://example/arm64-release"),
            GitHubReleaseAsset("Grayjoy-v1.4.0-arm64-v8a-release-unsigned.apk", "https://example/unsigned"),
        )
        assertEquals("https://example/arm64-release", selectReleaseApkUrl(assets, listOf("arm64-v8a")))
        assertEquals("https://example/universal-release", selectReleaseApkUrl(assets, listOf("riscv64")))
    }

    @Test
    fun `debug and unsigned assets are never selected for an in app update`() {
        val assets = listOf(
            GitHubReleaseAsset("Grayjoy-v2.2.0-arm64-v8a-debug.apk", "https://example/debug"),
            GitHubReleaseAsset("Grayjoy-v2.2.0-arm64-v8a-release-unsigned.apk", "https://example/unsigned"),
        )
        assertEquals(null, selectReleaseApkUrl(assets, listOf("arm64-v8a")))
    }
}
