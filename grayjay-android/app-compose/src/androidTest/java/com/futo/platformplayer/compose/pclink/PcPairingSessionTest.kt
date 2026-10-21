package com.futo.platformplayer.compose.pclink

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.UUID

class PcPairingSessionTest {
    @Test fun rotatingPairingSecretDiscardsCommandsAndPlaybackFromTheOldSession() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "pc-pairing-fixture-${UUID.randomUUID()}"
        val stored = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences = stored
        }
        try {
            val manager = PcLinkManager(context, observeNetwork = false)
            val id = "computer-fixture"
            fun payload(seed: Byte, label: String = "PC"): String {
                val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { seed })
                return "grayjoy://pc-pair?v=1&id=$id&name=$label&secret=$secret"
            }
            assertTrue(manager.pair(payload(1)))
            assertTrue(manager.enqueueCommand(id, PcRemoteCommandType.Next))
            manager.updatePlayback(PcPlaybackState(id, "PC", true, PcMediaKind.Video, "Old session", "Old session",
                "https://www.youtube.com/watch?v=fixture", "", "", true, 0, 1000, System.currentTimeMillis()))
            assertTrue(manager.pair(payload(1, "Renamed")))
            assertEquals(1, manager.commandsAfter(id, 0).size)
            assertNotNull(manager.snapshot.value.activePlayback)
            assertTrue(manager.pair(payload(2)))
            assertTrue(manager.commandsAfter(id, 0).isEmpty())
            assertNull(manager.snapshot.value.activePlayback)
            manager.remove(id)
            assertFalse(manager.enqueueCommand(id, PcRemoteCommandType.Play))
            assertTrue(manager.commandsAfter(id, 0).isEmpty())
        } finally { app.deleteSharedPreferences(name) }
    }
}
