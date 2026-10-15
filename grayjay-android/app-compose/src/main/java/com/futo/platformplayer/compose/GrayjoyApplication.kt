package com.futo.platformplayer.compose

import android.app.Application
import android.content.Context
import com.futo.platformplayer.compose.diagnostics.CrashLogStore
import com.futo.platformplayer.compose.downloads.GrayjoyDownloadStore

class GrayjoyApplication : Application() {
    // Playback and authenticated companion commands share one owner, even without an Activity.
    val playbackViewModel: GrayjayViewModel by lazy { GrayjayViewModel(this) }
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.localizedContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
        val (images, pool) = com.futo.platformplayer.compose.images.artworkMemoryBudget(Runtime.getRuntime().maxMemory())
        com.bumptech.glide.Glide.init(this, com.bumptech.glide.GlideBuilder()
            .setMemoryCache(com.bumptech.glide.load.engine.cache.LruResourceCache(images))
            .setBitmapPool(com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool(pool)))
        // A process can disappear without any lifecycle callback (force-stop, native crash, or
        // process kill). Hold persisted Media3 transfers before any service can recreate the
        // download manager. The ViewModel releases this hold only after it has restored the
        // queue and the plugin-backed data sources required by unfinished downloads.
        GrayjoyDownloadStore.holdUntilAppRestore(this)
    }
}
