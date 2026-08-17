package com.futo.platformplayer.compose

import android.app.Application
import android.content.Context
import com.futo.platformplayer.compose.diagnostics.CrashLogStore

class GrayjoyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.localizedContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
    }
}
