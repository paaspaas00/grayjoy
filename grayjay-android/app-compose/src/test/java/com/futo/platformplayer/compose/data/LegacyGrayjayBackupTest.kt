package com.futo.platformplayer.compose.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class LegacyGrayjayBackupTest {
    @Test
    fun parsesLegacyExportAndMaterializesLibraryData() {
        val backup = LegacyGrayjayBackupParser.parse(fixtureZip())

        assertEquals(1, backup.pluginConfigUrls.size)
        assertEquals(1, backup.pluginSettings.size)
        assertEquals(listOf(CHANNEL_URL), backup.subscriptionUrls)
        assertEquals(listOf(VIDEO_URL), backup.watchLaterUrls)
        assertEquals("Research", backup.playlists.single().title)
        assertEquals("Fixture video", backup.cachedVideos.getValue(VIDEO_URL).video.title)
        assertEquals(CHANNEL_URL, backup.cachedChannels.getValue(CHANNEL_URL).id)

        val (videos, playlists) = backup.buildImportLibrary(
            includeWatchLater = true,
            includePlaylists = true,
            includeHistory = true,
        )
        val video = videos.single()
        assertTrue(video.isWatchLater)
        assertEquals(0.5f, video.watchProgress)
        assertEquals(1_700_000_000_000L, video.lastWatchedAt)
        assertEquals(listOf(VIDEO_URL), playlists.single().videoIds)
    }

    @Test
    fun parsesUnencryptedAutomaticBackupHeader() {
        val ezip = byteArrayOf(0x11, 0x22, 0x33, 0x44, 'G'.code.toByte(), 'J'.code.toByte(), 1, 0) +
            fixtureZip()

        val backup = LegacyGrayjayBackupParser.parse(ezip)

        assertEquals(1, backup.history.size)
    }

    @Test
    fun parsesManagedStoresUsingTheCasingWrittenByGrayjay() {
        val gson = Gson()
        val backup = LegacyGrayjayBackupParser.parse(
            zipOf(
                mapOf(
                    "exportInfo" to gson.toJson(mapOf("version" to "1")),
                    "stores/Subscriptions" to gson.toJson(listOf(CHANNEL_URL)),
                    "stores/Watch_later" to gson.toJson(listOf(VIDEO_URL)),
                    "stores/Playlists" to gson.toJson(listOf("Research:::playlist-id\n$VIDEO_URL")),
                ),
            ),
        )

        assertEquals(listOf(CHANNEL_URL), backup.subscriptionUrls)
        assertEquals(listOf(VIDEO_URL), backup.watchLaterUrls)
        assertEquals("Research", backup.playlists.single().title)
    }

    @Test
    fun materializesOnlyTheStoresSelectedByTheUser() {
        val backup = LegacyGrayjayBackupParser.parse(fixtureZip())

        val (videos, playlists) = backup.buildImportLibrary(
            includeWatchLater = false,
            includePlaylists = true,
            includeHistory = false,
        )

        assertEquals(1, playlists.size)
        assertEquals(1, videos.size)
        assertFalse(videos.single().isWatchLater)
        assertEquals(0L, videos.single().lastWatchedAt)
        assertEquals(0f, videos.single().watchProgress)
    }

    @Test
    fun decryptsLegacyV1AutomaticBackupAndRejectsWrongPassword() {
        val encrypted = encryptedV1(fixtureZip(), "correct-password")

        assertThrows(LegacyBackupPasswordRequiredException::class.java) {
            LegacyGrayjayBackupParser.parse(encrypted)
        }
        assertThrows(LegacyBackupInvalidPasswordException::class.java) {
            LegacyGrayjayBackupParser.parse(encrypted, "wrong-password")
        }
        val backup = LegacyGrayjayBackupParser.parse(encrypted, "correct-password")
        assertEquals("Research", backup.playlists.single().title)
    }

    @Test
    fun rejectsUnrelatedZipWithoutMutatingAnything() {
        val zip = zipOf(mapOf("random.json" to "{}"))

        val error = assertThrows(LegacyBackupException::class.java) {
            LegacyGrayjayBackupParser.parse(zip)
        }

        assertFalse(error.message.isNullOrBlank())
    }

    private fun fixtureZip(): ByteArray {
        val gson = Gson()
        val videoCache = """
            [{
              "contentType":"MEDIA",
              "id":{"platform":"YouTube","value":"fixture","pluginId":"$YOUTUBE_PLUGIN_ID"},
              "name":"Fixture video",
              "thumbnails":{"sources":[
                {"url":"https://example.com/low.jpg","quality":1},
                {"url":"https://example.com/high.jpg","quality":9}
              ]},
              "author":{
                "id":{"platform":"YouTube","value":"creator","pluginId":"$YOUTUBE_PLUGIN_ID"},
                "name":"Fixture creator",
                "url":"$CHANNEL_URL",
                "thumbnail":"https://example.com/avatar.jpg",
                "subscribers":1200
              },
              "url":"$VIDEO_URL",
              "shareUrl":"$VIDEO_URL",
              "duration":120,
              "viewCount":4567
            }]
        """.trimIndent()
        val channelCache = """
            [{
              "id":{"platform":"YouTube","value":"creator","pluginId":"$YOUTUBE_PLUGIN_ID"},
              "name":"Fixture creator",
              "thumbnail":"https://example.com/avatar.jpg",
              "subscribers":1200,
              "description":"A creator from the old database",
              "url":"$CHANNEL_URL",
              "links":{},
              "urlAlternatives":[]
            }]
        """.trimIndent()
        return zipOf(
            mapOf(
                "exportInfo" to gson.toJson(mapOf("version" to "1")),
                "settings" to "{}",
                "plugins" to gson.toJson(
                    mapOf(YOUTUBE_PLUGIN_ID to "https://plugins.grayjay.app/Youtube/YoutubeConfig.json"),
                ),
                "plugin_settings" to gson.toJson(
                    mapOf(YOUTUBE_PLUGIN_ID to mapOf("region" to "US")),
                ),
                "stores/subscriptions" to gson.toJson(listOf(CHANNEL_URL)),
                "stores/watch_later" to gson.toJson(listOf(VIDEO_URL)),
                "stores/playlists" to gson.toJson(listOf("Research:::playlist-id\n$VIDEO_URL")),
                "stores/history" to gson.toJson(
                    listOf("$VIDEO_URL|||1700000000|||60|||Fixture video"),
                ),
                "cache_videos" to videoCache,
                "cache_channels" to channelCache,
            ),
        )
    }

    private fun encryptedV1(zip: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 21).toByte() }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 131_072, 256)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            doFinal(zip)
        }
        return byteArrayOf(0x11, 0x22, 0x33, 0x44, 1) + salt + iv + ciphertext
    }

    private fun zipOf(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }

    private companion object {
        const val YOUTUBE_PLUGIN_ID = "35ae969a-a7db-11ed-afa1-0242ac120002"
        const val VIDEO_URL = "https://www.youtube.com/watch?v=fixture"
        const val CHANNEL_URL = "https://www.youtube.com/@fixture"
    }
}
