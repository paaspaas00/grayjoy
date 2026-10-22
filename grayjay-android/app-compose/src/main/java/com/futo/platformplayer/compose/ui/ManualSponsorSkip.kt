package com.futo.platformplayer.compose.ui

import androidx.compose.runtime.compositionLocalOf
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockSegment

internal data class ManualSponsorSkip(val segment: SponsorBlockSegment? = null, val skip: () -> Unit = {})
internal val LocalManualSponsorSkip = compositionLocalOf { ManualSponsorSkip() }
