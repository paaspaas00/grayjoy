package com.futo.platformplayer

import android.os.Looper
import com.futo.platformplayer.logging.Logger
import java.io.IOException

fun warnIfMainThread(context: String) {
    if (BuildConfig.DEBUG && Looper.myLooper() == Looper.getMainLooper()) {
        Logger.w("V8Plugin", "JAVASCRIPT ON MAIN THREAD at $context")
    }
}

fun ensureNotMainThread() {
    if (runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(false)) {
        throw IllegalStateException("Cannot run on main thread")
    }
}

fun String.decodeUnicode(): String {
    val result = StringBuilder()
    var index = 0
    while (index < length) {
        var character = this[index]
        if (character == '\\' && index + 1 < length) {
            character = this[++index]
            when (character) {
                '\\' -> result.append('\\')
                't' -> result.append('\t')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                'f' -> result.append('\u000C')
                'b' -> result.append('\b')
                '"' -> result.append('"')
                '\'' -> result.append('\'')
                'u' -> {
                    if (index + 4 >= length) throw IOException("Incomplete Unicode sequence")
                    val unicode = substring(index + 1, index + 5)
                    result.append(unicode.toIntOrNull(16)?.toChar()
                        ?: throw IOException("Invalid Unicode sequence: $unicode"))
                    index += 4
                }
                in '0'..'7' -> {
                    val octal = substring(index, (index + 3).coerceAtMost(length))
                        .takeWhile { it in '0'..'7' }
                    result.append(octal.toIntOrNull(8)?.toChar()
                        ?: throw IOException("Invalid Octal sequence: $octal"))
                    index += octal.length - 1
                }
                else -> result.append(character)
            }
        } else {
            result.append(character)
        }
        index++
    }
    return result.toString()
}
