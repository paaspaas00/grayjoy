package com.futo.platformplayer.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockCategory
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockRule
import com.futo.platformplayer.compose.ui.screens.SponsorBlockRuleEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SponsorBlockControlsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inheritedVideoRuleCanBeCustomizedWithoutChangingItsParent() {
        val inherited = SponsorBlockRule(
            enabled = true,
            categories = setOf(SponsorBlockCategory.Sponsor),
        )
        var storedOverride: SponsorBlockRule? = null
        compose.setContent {
            MaterialTheme {
                SponsorBlockRuleEditor(
                    rule = storedOverride ?: inherited,
                    inherited = storedOverride == null,
                    inheritedRule = inherited,
                    onCustomize = { storedOverride = inherited },
                    onUseInherited = { storedOverride = null },
                    onRuleChange = { storedOverride = it },
                )
            }
        }

        compose.onNodeWithTag("sponsorblock-customize").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(inherited, storedOverride) }
    }
}
