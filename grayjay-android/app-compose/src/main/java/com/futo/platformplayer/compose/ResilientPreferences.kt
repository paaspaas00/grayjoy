package com.futo.platformplayer.compose

import android.content.SharedPreferences

/** Reading a damaged setting uses its default without rewriting the recoverable source value. */
internal class ResilientPreferences(
    private val source: SharedPreferences,
    private val failClosedBooleanKeys: Set<String> = emptySet(),
) : SharedPreferences by source {
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        try { source.getBoolean(key, defValue) }
        catch (_: ClassCastException) { (key != null && key in failClosedBooleanKeys) || defValue }

    override fun getInt(key: String?, defValue: Int): Int =
        try { source.getInt(key, defValue) } catch (_: ClassCastException) { defValue }

    override fun getLong(key: String?, defValue: Long): Long =
        try { source.getLong(key, defValue) } catch (_: ClassCastException) { defValue }

    override fun getFloat(key: String?, defValue: Float): Float =
        try { source.getFloat(key, defValue) } catch (_: ClassCastException) { defValue }

    override fun getString(key: String?, defValue: String?): String? =
        try { source.getString(key, defValue) } catch (_: ClassCastException) { defValue }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        try { source.getStringSet(key, defValues) } catch (_: ClassCastException) { defValues }
}
