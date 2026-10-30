package com.futo.platformplayer.backend

/** The host supplies its private, redacting diagnostic sink; standalone consumers keep Logcat. */
object BackendLog {
    @Volatile var sink: ((String, String, String?, Throwable?) -> Int)? = null
    fun d(tag: String, message: String?, error: Throwable? = null): Int = sink?.invoke("debug", tag, message, error) ?: android.util.Log.d(tag, message, error)
    fun i(tag: String, message: String?, error: Throwable? = null): Int = sink?.invoke("info", tag, message, error) ?: android.util.Log.i(tag, message, error)
    fun w(tag: String, message: String?, error: Throwable? = null): Int = sink?.invoke("warning", tag, message, error) ?: android.util.Log.w(tag, message, error)
    fun w(tag: String, error: Throwable): Int = w(tag, null, error)
    fun e(tag: String, message: String?, error: Throwable? = null): Int = sink?.invoke("error", tag, message, error) ?: android.util.Log.e(tag, message, error)
}
