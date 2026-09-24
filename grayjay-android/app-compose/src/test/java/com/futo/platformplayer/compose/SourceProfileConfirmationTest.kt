package com.futo.platformplayer.compose

import org.junit.Assert.*
import org.junit.Test

class SourceProfileConfirmationTest {
    @Test fun loginAndProfileChooserMustNotConfirmAnUnselectedProfile() {
        assertFalse(isSourceProfileConfirmationPage("https://sso.crunchyroll.com/login"))
        assertFalse(isSourceProfileConfirmationPage("https://www.crunchyroll.com/select-profile"))
        assertFalse(isSourceProfileConfirmationPage("https://www.crunchyroll.com.attacker.test/"))
        assertFalse(isSourceProfileConfirmationPage("http://www.crunchyroll.com/"))
        assertTrue(isSourceProfileConfirmationPage("https://www.crunchyroll.com/it/"))
        assertTrue(isSourceProfileConfirmationPage("https://www.crunchyroll.com/discover"))
    }
}
