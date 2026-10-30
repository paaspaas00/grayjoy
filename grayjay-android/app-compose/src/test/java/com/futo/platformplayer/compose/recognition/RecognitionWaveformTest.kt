package com.futo.platformplayer.compose.recognition

import org.junit.Assert.*
import org.junit.Test

class RecognitionWaveformTest {
    @Test fun levelsRespondInPlaceWithoutPropagatingToNeighbouringBars() {
        val model = RecognitionWaveModel()
        val signal = RecognitionAudioFrame(.9f, FloatArray(15) { if (it == 3) 1f else 0f })
        repeat(12) { model.advance(signal, 1f / 60) }
        assertTrue(model.bars[3] > .5f)
        repeat(24) { model.advance(RecognitionAudioFrame.Silence, 1f / 60) }
        assertTrue(model.bars.indices.filter { it != 3 }.all { model.bars[it] == 0f })
        assertEquals(.5f, model.blobX)
        assertEquals(0f, model.bars.first()); assertEquals(0f, model.bars.last())
    }
    @Test fun silenceReturnsToDotsAndStoppingSettlesTheCloud() {
        val model = RecognitionWaveModel()
        repeat(60) { model.advance(RecognitionAudioFrame(1f), 1f / 60) }
        assertTrue(model.blobEnergy > .5f)
        repeat(120) { model.advance(RecognitionAudioFrame.Silence, 1f / 60, listening = false) }
        assertTrue(model.bars.all { it == 0f }); assertTrue(model.blobEnergy < .01f)
    }
    @Test fun malformedLevelsAreBoundedAndPerceptualGainWorksForQuietMusic() {
        val model = RecognitionWaveModel()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, .5f, 5f).forEach { input ->
            repeat(50) { model.advance(RecognitionAudioFrame(input), .02f) }
            assertTrue(model.bars.all { it.isFinite() && it in 0f..1f })
            assertTrue(model.blobEnergy in 0f..1f && model.blobX in 0f..1f)
        }
        assertEquals(0f, recognitionDisplayLevel(0.0)); assertEquals(0f, recognitionDisplayLevel(Double.NaN))
        assertTrue(recognitionDisplayLevel(200.0) > .25f)
        assertTrue(recognitionDisplayLevel(2000.0) > recognitionDisplayLevel(200.0))
    }
}
