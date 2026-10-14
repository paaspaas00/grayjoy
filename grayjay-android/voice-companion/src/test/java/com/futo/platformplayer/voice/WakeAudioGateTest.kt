package com.futo.platformplayer.voice

import org.junit.Assert.*
import org.junit.Test

class WakeAudioGateTest {
    @Test fun quietSpeechAndNormalPausesAreNotDiscarded() {
        val gate = WakeAudioGate()
        val silence = ShortArray(1600)
        val quiet = ShortArray(1600) { if (it % 2 == 0) 80 else -80 }
        repeat(10) { assertFalse(gate.process(silence, silence.size).feed) }
        assertTrue(gate.process(quiet, quiet.size).start)
        repeat(8) { assertTrue(gate.process(silence, silence.size).feed) }
        assertTrue(gate.process(quiet, quiet.size).feed)
        repeat(11) { assertTrue(gate.process(silence, silence.size).feed) }
        assertTrue(gate.process(silence, silence.size).reset)
        repeat(10) { assertFalse(gate.process(silence, silence.size).feed) }
    }
}
