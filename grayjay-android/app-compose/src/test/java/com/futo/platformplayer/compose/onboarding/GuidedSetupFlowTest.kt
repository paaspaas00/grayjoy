package com.futo.platformplayer.compose.onboarding

import org.junit.Assert.*
import org.junit.Test

class GuidedSetupFlowTest {
    @Test fun `version 6 guide is required once only for upgrades from version 5 or older`() {
        assertTrue(needsVersion6UpgradeGuide(6000, 5000, true))
        assertTrue(needsVersion6UpgradeGuide(6000, 100, true))
        assertTrue(needsVersion6UpgradeGuide(6000, 0, true))
        assertFalse(needsVersion6UpgradeGuide(6000, 6000, true))
        assertFalse(needsVersion6UpgradeGuide(6000, 0, false))
        assertFalse(needsVersion6UpgradeGuide(6000, 5001, true))
        assertFalse(needsVersion6UpgradeGuide(6001, 5000, true))
    }
    @Test fun noImportOmitsDetailsWhileEitherImportSourceIncludesThem() {
        for (youtube in listOf(false, true)) for (files in listOf(false, true)) {
            val steps = guidedSetupSteps(youtube, files)
            assertEquals(youtube || files, SetupStep.ImportDetails in steps)
            assertEquals(SetupStep.Welcome, steps.first())
            assertEquals(SetupStep.Done, steps.last())
            assertEquals(steps.size, steps.distinct().size)
            assertTrue(SetupStep.PlayerLayout in steps)
            assertTrue(SetupStep.AudioNormalization in steps)
            assertTrue(SetupStep.Diagnostics in steps)
            assertTrue(steps.indexOf(SetupStep.Diagnostics) < steps.indexOf(SetupStep.Done))
            assertTrue(steps.indexOf(SetupStep.PlayerLayout) < steps.indexOf(SetupStep.Done))
            val choice = steps.indexOf(SetupStep.ImportChoice)
            assertEquals(if (youtube || files) SetupStep.ImportDetails else SetupStep.Shorts, steps[choice + 1])
        }
    }
}
