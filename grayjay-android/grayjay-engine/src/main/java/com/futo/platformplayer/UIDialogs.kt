package com.futo.platformplayer

object UIDialogs {
    fun toast(message: String, long: Boolean = true) { com.futo.platformplayer.backend.BackendLog.i("SourceNotice", message) }
    fun appToast(message: String) { com.futo.platformplayer.backend.BackendLog.i("SourceNotice", message) }
}
