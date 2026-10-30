package com.futo.platformplayer.compose.ui

import com.futo.platformplayer.compose.ui.screens.centeredArtworkCrop
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.*
import org.junit.Test

class ArtworkMorphCropTest {
    @Test fun landscapeArtworkCropsToSquareWithoutStretching() {
        assertEquals(IntRect(280, 0, 1000, 720), centeredArtworkCrop(1280, 720, 160f, 160f))
    }
    @Test fun squareCoverCropsToVideoAspectWithoutStretching() {
        assertEquals(IntRect(0, 168, 768, 600), centeredArtworkCrop(768, 768, 1600f, 900f))
    }
    @Test fun matchingAspectKeepsTheEntireBitmap() {
        assertEquals(IntRect(0, 0, 1280, 720), centeredArtworkCrop(1280, 720, 640f, 360f))
    }
}
