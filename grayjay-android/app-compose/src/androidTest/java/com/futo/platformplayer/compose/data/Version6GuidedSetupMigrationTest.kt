package com.futo.platformplayer.compose.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.futo.platformplayer.compose.onboarding.GuidedSetupPreferences
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Version6GuidedSetupMigrationTest {
    @Test fun upgradeGuideIsPersistedUntilPresentedAndThenNeverRepeated() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "version-6-guide-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences("$prefix-$name", mode)
        }
        val stored = context.getSharedPreferences("grayjoy_guided_setup", Context.MODE_PRIVATE)
        try {
            assertTrue(stored.edit().putBoolean("show_on_startup", true)
                .putInt("last_launched_version", 5000).commit())
            val firstLaunch = GuidedSetupPreferences(context)
            assertTrue(firstLaunch.requiresVersion6UpgradeGuide())
            // Recreating the host before the dialog appears preserves the pending migration.
            assertTrue(GuidedSetupPreferences(context).requiresVersion6UpgradeGuide())
            firstLaunch.markVersion6UpgradeGuidePresented()
            assertFalse(GuidedSetupPreferences(context).requiresVersion6UpgradeGuide())
            assertFalse(GuidedSetupPreferences(context).showOnStartup)
        } finally {
            base.deleteSharedPreferences("$prefix-grayjoy_guided_setup")
        }
    }
}
