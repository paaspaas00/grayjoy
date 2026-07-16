package com.futo.platformplayer.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelBackNavigationTest {
    @Test
    fun bottomNavigationDestinationsReturnHome() {
        GrayjayDestination.entries
            .filter { it.showInCompactNavigation && it != GrayjayDestination.Home }
            .forEach { destination ->
                assertEquals(GrayjayDestination.Home, topLevelBackDestination(destination))
            }
    }

    @Test
    fun homeDelegatesBackToAndroid() {
        assertNull(topLevelBackDestination(GrayjayDestination.Home))
    }

    @Test
    fun nestedOnlyDestinationDoesNotMasqueradeAsTopLevel() {
        assertNull(topLevelBackDestination(GrayjayDestination.Sources))
    }
}
