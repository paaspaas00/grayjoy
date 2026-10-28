package com.futo.platformplayer.compose

import org.junit.Assert.*
import org.junit.Test

class DevicePerformanceTest {
    @Test fun old64BitThreeGbPhoneGetsTheConstrainedPath() {
        assertTrue(isConstrainedDevice(false, 384, false, 2_842_144L * 1024, 28))
        assertTrue(isConstrainedDevice(false, 512, false, 3_850_000_000, 29))
    }
    @Test fun pixelsKeepExistingRenderingAndTypography() {
        assertFalse(isConstrainedDevice(false, 384, false, 7_500_000_000, 36))
        assertFalse(isConstrainedDevice(false, 256, false, 5_500_000_000, 34))
        assertFalse(useSmallPhoneTypography(false, 411, 1f))
        assertFalse(useSmallPhoneTypography(false, 360, 1f))
    }
    @Test fun accessibilityFontScalingIsNeverCancelledAndTabletsKeepTheirLayout() {
        assertTrue(useSmallPhoneTypography(true, 360, 1f))
        assertFalse(useSmallPhoneTypography(true, 360, 1.3f))
        assertFalse(useSmallPhoneTypography(true, 600, 1f))
        assertFalse(isConstrainedDevice(false, 384, false, 0, 28))
        assertTrue(isConstrainedDevice(true, 512, false, 4_000_000_000, 36))
    }
}
