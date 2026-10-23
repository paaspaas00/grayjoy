package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.GrayjayPreferences
import com.futo.platformplayer.compose.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import com.futo.platformplayer.backend.GrayjayPluginAuthStore
import com.futo.platformplayer.api.media.platforms.js.SourceAuth

class SourceProfileSplitTest {
    private fun fixture(test: (Context) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "source-split-test-${UUID.randomUUID()}"
        val names = mutableSetOf<String>()
        val files = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = files
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val key = "$prefix-$name"
                synchronized(names) { names += key }
                return base.getSharedPreferences(key, mode)
            }
        }
        try { test(context) } finally { names.forEach(base::deleteSharedPreferences); files.deleteRecursively() }
    }
    private val sources = listOf("youtube", "spotify", "crunchyroll").map {
        SourceUiModel(it, it, "", 0xFF336699, true)
    }
    private fun video(id: String, source: String) = VideoUiModel(id, id, "Channel", "", "1:00",
        sourceId = source, isWatchLater = true, isLiked = true, lastWatchedAt = 12345, watchProgress = .6f)

    private fun auth(label: String) = SourceAuth(
        cookieMap = hashMapOf(".youtube.com" to hashMapOf("SID" to label, "PREF" to "account")),
        headers = mapOf(".youtube.com" to mapOf("X-Goog-AuthUser" to "2", "X-Goog-PageId" to "selected-channel")),
        userAgent = "Test browser",
    )

    @Test fun splitCopiesWholeCurrentSessionAndIdentityWithoutModifyingDonorOrExistingTarget() = fixture { context ->
        val catalog = ProfileRepository(context)
        val current = catalog.createProfile("Current", null)
        val locked = catalog.createProfile("Protected", "1234")
        val pluginId = "35ae969a-a7db-11ed-afa1-0242ac120002"
        val sourceList = sources.take(2).map { if (it.id == "youtube") it.copy(engineId = pluginId) else it }
        val session = auth("current")
        GrayjayPluginAuthStore.save(context, "main", pluginId, auth("main"))
        GrayjayPluginAuthStore.save(context, locked.id, pluginId, auth("protected"))
        GrayjayPluginAuthStore.save(context, "private", pluginId, auth("private"))
        GrayjayPluginAuthStore.save(context, current.id, pluginId, session)
        assertTrue(GrayjayPluginAuthStore.saveAccountName(context, current.id, pluginId, session, "My account"))
        val state = GrayjayUiState(activeProfileId = current.id)
        val targets = runBlocking { SourceProfileSplit(context).prepare(sourceList, state, { _, _ -> }, {}) }
        val target = targets.getValue("youtube")
        assertEquals(session, GrayjayPluginAuthStore.load(context, target, pluginId))
        assertEquals("My account", GrayjayPluginAuthStore.accountName(context, target, pluginId))
        assertEquals(session, GrayjayPluginAuthStore.load(context, current.id, pluginId))
        assertEquals(auth("main"), GrayjayPluginAuthStore.load(context, "main", pluginId))
        assertFalse(GrayjayPluginAuthStore.has(context, targets.getValue("spotify"), pluginId))
        GrayjayPluginAuthStore.save(context, target, pluginId, auth("different target account"))
        runBlocking { SourceProfileSplit(context).prepare(sourceList, state, { _, _ -> }, {}) }
        assertEquals(auth("different target account"), GrayjayPluginAuthStore.load(context, target, pluginId))
    }

    @Test fun protectedAndPrivateSessionsAreNeverCopiedEvenIfCurrent() = fixture { context ->
        val catalog = ProfileRepository(context)
        val locked = catalog.createProfile("Protected", "1234")
        GrayjayPluginAuthStore.save(context, locked.id, "youtube", auth("protected"))
        GrayjayPluginAuthStore.save(context, "private", "youtube", auth("private"))
        val targets = runBlocking { SourceProfileSplit(context).prepare(sources.take(2),
            GrayjayUiState(activeProfileId = locked.id), { _, _ -> }, {}) }
        assertFalse(GrayjayPluginAuthStore.has(context, targets.getValue("youtube"), "youtube"))
    }

    @Test fun staleIdentityCannotRestoreLogoutOrOverwriteAnotherSession() = fixture { context ->
        val session = auth("one")
        GrayjayPluginAuthStore.save(context, "main", "youtube", session)
        assertTrue(GrayjayPluginAuthStore.saveAccountName(context, "main", "youtube", session, "One"))
        GrayjayPluginAuthStore.clear(context, "main", "youtube")
        assertFalse(GrayjayPluginAuthStore.saveAccountName(context, "main", "youtube", session, "Late"))
        assertNull(GrayjayPluginAuthStore.accountName(context, "main", "youtube"))
        GrayjayPluginAuthStore.save(context, "main", "youtube", auth("two"))
        assertFalse(GrayjayPluginAuthStore.saveAccountName(context, "main", "youtube", session, "Wrong"))
        assertNull(GrayjayPluginAuthStore.accountName(context, "main", "youtube"))
    }

    @Test fun legacyMainSessionAlsoTransfersWithoutChangingLegacyData() = fixture { context ->
        val prefs = context.getSharedPreferences("grayjay-js-plugin-auth", Context.MODE_PRIVATE)
        val legacy = """{"cookies":{".youtube.com":{"SID":"legacy"}},"headers":{},"userAgent":"Legacy browser"}"""
        prefs.edit().putString("youtube", legacy).commit()
        assertTrue(GrayjayPluginAuthStore.copyIfAbsent(context, "main", "target", "youtube"))
        assertEquals(GrayjayPluginAuthStore.load(context, "main", "youtube"), GrayjayPluginAuthStore.load(context, "target", "youtube"))
        assertEquals(legacy, prefs.getString("youtube", null))
        assertFalse(GrayjayPluginAuthStore.copyIfAbsent(context, "missing", "target", "youtube"))
    }

    @Test fun migrationIsAdditiveSourceSpecificPrivateSafeAndIdempotent() = fixture { context ->
        val catalog = ProfileRepository(context)
        val locked = catalog.createProfile("Protected", "1234")
        val original = SharedPreferencesLibraryRepository(context)
        val items = listOf(video("yt", "youtube"), video("sp", "spotify"), video("cr", "crunchyroll"))
        original.mergeImportedData(items, listOf(PlaylistUiModel("mix", "Mixed playlist", "", items.map { it.id })))
        SharedPreferencesLibraryRepository(context, locked.id).saveVideo(video("secret", "youtube"))
        SharedPreferencesLibraryRepository(context, "private").saveVideo(video("private-secret", "spotify"))
        val channel = ChannelUiModel("https://www.youtube.com/channel/test", "Channel", "youtube", "", 0, "", "", "")
        GrayjayPreferences(context).mergeImportedSubscriptions(listOf(channel))
        val before = original.createImportSnapshot()
        val copies = mutableListOf<SourceProfileCopy>()
        val split = SourceProfileSplit(context)
        val targets = runBlocking { split.prepare(sources, GrayjayUiState(), { _, _ -> }, { copies += it }) }
        assertFalse(catalog.sourceProfilesEnabled())
        assertFalse(catalog.profiles().any { it.sourceId.isNotBlank() })
        assertEquals(before, original.createImportSnapshot())
        targets.forEach { (source, id) ->
            val copied = SharedPreferencesLibraryRepository(context, id).loadSavedVideos()
            assertEquals(listOf(source), copied.map { it.sourceId }.distinct())
            assertEquals(1, copied.size)
            assertTrue(copied.single().isLiked && copied.single().isWatchLater)
            assertEquals(.6f, copied.single().watchProgress, .001f)
        }
        assertTrue(GrayjayPreferences(context, targets.getValue("youtube")).isCreatorFollowed(channel.id))
        assertFalse(copies.any { it.sourceProfileId in setOf("private", locked.id) })
        val second = runBlocking { split.prepare(sources, GrayjayUiState(), { _, _ -> }, { copies += it }) }
        assertEquals(targets, second)
        catalog.activateSourceProfiles(targets.values.toSet())
        assertTrue(catalog.sourceProfilesEnabled())
        assertFalse(catalog.profiles().any { it.id == "main" })
        assertTrue(catalog.profiles().any { it.id == locked.id })
        assertFalse(catalog.deleteProfile(targets.getValue("youtube")))
        assertFalse(catalog.deleteProfile(targets.getValue("spotify")))
        val yt = SharedPreferencesSourceRepository(context, targets.getValue("youtube"))
        yt.setEnabled("youtube", false); yt.setEnabled("spotify", true)
        assertEquals(setOf("youtube"), yt.loadEnabledSourceIds(sources))
        assertEquals(before, original.createImportSnapshot())
    }
    @Test fun interruptedPreparationLeavesMainVisibleAndRetryDoesNotDuplicatePlaylists() = fixture { context ->
        val catalog = ProfileRepository(context)
        val original = SharedPreferencesLibraryRepository(context)
        original.mergeImportedData(listOf(video("yt", "youtube")), listOf(PlaylistUiModel("p", "Songs", "", listOf("yt"))))
        val before = original.createImportSnapshot()
        val failed = runCatching { runBlocking { SourceProfileSplit(context).prepare(sources.take(2), GrayjayUiState(),
            { done, _ -> if (done == 1) throw IOException("Test interruption") }, {}) } }
        assertTrue(failed.isFailure)
        assertTrue(catalog.profiles().any { it.id == "main" })
        assertFalse(catalog.profiles().any { it.sourceId.isNotBlank() })
        val targets = runBlocking { SourceProfileSplit(context).prepare(sources.take(2), GrayjayUiState(), { _, _ -> }, {}) }
        assertEquals(1, SharedPreferencesLibraryRepository(context, targets.getValue("youtube")).loadPlaylists().size)
        assertEquals(before, original.createImportSnapshot())
    }
    @Test fun optingOutDoesNotChangeProfileCatalogOrSourceSelection() = fixture { context ->
        val catalog = ProfileRepository(context)
        val repository = SharedPreferencesSourceRepository(context)
        repository.setEnabled("youtube", true)
        repository.setEnabled("spotify", true)
        assertFalse(catalog.sourceProfilesEnabled())
        assertEquals(setOf("youtube", "spotify"), repository.loadEnabledSourceIds(sources))
        assertEquals(setOf("main", "private"), catalog.profiles().map { it.id }.toSet())
    }
    @Test fun offlineGrantsKeepMediaTypesSeparateAndNeverModifyTheOriginalOwner() = fixture { context ->
        val links = OfflineProfileLinks(context)
        links.add("target", "origin", setOf("video"), mapOf("video" to setOf(DownloadMediaType.Video, DownloadMediaType.Audio)))
        assertEquals("origin", links.owners("target")["video"])
        assertTrue(links.owners("origin").isEmpty())
        assertTrue(links.remove("target", "video", DownloadMediaType.Audio))
        assertEquals(setOf(DownloadMediaType.Video), links.allowedTypes("target", "video"))
        assertEquals("origin", links.owners("target")["video"])
        assertTrue(links.remove("target", "video", DownloadMediaType.Video))
        assertTrue(links.owners("target").isEmpty())
        assertTrue(links.owners("origin").isEmpty())
    }
}
