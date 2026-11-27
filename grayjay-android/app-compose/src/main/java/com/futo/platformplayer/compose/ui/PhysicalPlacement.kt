package com.futo.platformplayer.compose.ui

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset

/**
 * Places content at a physical (left, top) position inside a parent that fills the measured area.
 *
 * Bounds captured with boundsInRoot()/positionInRoot() are physical pixels. Modifier.offset {} and
 * a Box's default TopStart alignment are both mirrored in right-to-left layouts, which moved
 * overlays built from such bounds to the wrong side of the screen.
 */
internal fun Modifier.physicalOffset(offset: Density.() -> IntOffset): Modifier =
    fillMaxSize().wrapContentSize(AbsoluteAlignment.TopLeft).absoluteOffset(offset)
