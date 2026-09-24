package com.futo.platformplayer.compose.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** One monitor per profile, including independent foreground and worker repository instances. */
internal fun profileDataLock(context: Context, profileId: String): Any =
    profileDataLocks.getOrPut(context.applicationContext.filesDir.absolutePath to profileId) { Any() }

private val profileDataLocks = ConcurrentHashMap<Pair<String, String>, Any>()
