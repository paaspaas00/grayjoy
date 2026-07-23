package com.futo.platformplayer.compose.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

internal data class GitHubRelease(
    val versionName: String,
    val releaseUrl: String,
)

internal class GitHubReleaseChecker(
    private val releasesUrl: String = RELEASES_URL,
) {
    fun latestUpdate(
        currentVersionName: String,
    ): GitHubRelease? {
        val connection = URL(releasesUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "Grayjoy/$currentVersionName")
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("GitHub release check failed with HTTP $responseCode")
            }
            val releases = connection.inputStream.bufferedReader().use { reader ->
                JSONArray(reader.readText())
            }
            val release = (0 until releases.length())
                .asSequence()
                .map(releases::getJSONObject)
                .firstOrNull { !it.optBoolean("draft", false) }
                ?: return null
            val versionName = release.optString("tag_name").removePrefix("v")
            if (!isNewerVersion(versionName, currentVersionName)) return null
            val releaseUrl = release.optString("html_url")
            return GitHubRelease(
                versionName = versionName,
                releaseUrl = releaseUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/paaspaas00/grayjoy/releases?per_page=10"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000
    }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = semanticVersionParts(candidate)
    val currentParts = semanticVersionParts(current)
    if (candidateParts.isEmpty() || currentParts.isEmpty()) return false
    val width = maxOf(candidateParts.size, currentParts.size)
    repeat(width) { index ->
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun semanticVersionParts(value: String): List<Int> =
    Regex("""\d+""").findAll(value.substringBefore('-'))
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
