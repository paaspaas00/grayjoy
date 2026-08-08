package com.futo.platformplayer.compose

import android.app.Application
import android.content.Context

class GrayjoyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.localizedContext(base))
    }
}
