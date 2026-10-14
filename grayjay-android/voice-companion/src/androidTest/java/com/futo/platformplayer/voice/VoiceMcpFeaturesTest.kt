package com.futo.platformplayer.voice

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VoiceMcpFeaturesTest {
    @Test fun networkAndPlaylistPagingAreAvailableOverAuthenticatedMcp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = GrayjoyMcpClient(context)
        try {
            client.connect()
            assertTrue(client.call("network_status").get("available") is Boolean)
            val tools = client.tools()
            assertTrue(tools.contains("download_audio")); assertTrue(tools.contains("play_local"))
            val ids = HashSet<String>(); var offset = 0; var total = -1
            do {
                val page = client.call("list_playlists", JSONObject().put("offset", offset))
                total = page.getInt("total")
                val entries = page.getJSONArray("playlists")
                assertTrue(entries.length() <= 20)
                repeat(entries.length()) { assertTrue(ids.add(entries.getJSONObject(it).getString("id"))) }
                offset = page.getInt("nextOffset")
            } while (offset >= 0)
            assertEquals(total, ids.size)
            val result = runCatching { client.call("download_audio", JSONObject().put("videoId", "https://invalid.example/unregistered")) }
            assertTrue("A model cannot download arbitrary URLs", result.isFailure)
        } finally { client.close() }
    }
}
