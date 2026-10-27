package com.futo.platformplayer.compose.onboarding

import org.junit.Assert.*
import org.junit.Test

class GuidedSetupFlowTest {
    @Test fun noImportOmitsDetailsWhileEitherImportSourceIncludesThem() {
        for (youtube in listOf(false, true)) for (files in listOf(false, true)) {
            val steps = guidedSetupSteps(youtube, files)
            assertEquals(youtube || files, SetupStep.ImportDetails in steps)
            assertEquals(SetupStep.Welcome, steps.first())
            assertEquals(SetupStep.Done, steps.last())
            assertEquals(steps.size, steps.distinct().size)
            assertTrue(SetupStep.PlayerLayout in steps)
            assertTrue(steps.indexOf(SetupStep.PlayerLayout) < steps.indexOf(SetupStep.Done))
            val choice = steps.indexOf(SetupStep.ImportChoice)
            assertEquals(if (youtube || files) SetupStep.ImportDetails else SetupStep.Shorts, steps[choice + 1])
        }
    }
}
