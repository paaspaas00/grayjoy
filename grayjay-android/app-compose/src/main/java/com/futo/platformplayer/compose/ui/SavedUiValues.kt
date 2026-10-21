package com.futo.platformplayer.compose.ui

/** Enum names may have changed since Android last saved an older version of the app. */
internal inline fun <reified T : Enum<T>> restoredEnumValue(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

/** Never restore a truncated bulk action: oversize transient selections are dismissed as a whole. */
internal fun savedBulkSelection(values: List<*>): List<String> {
    if (values.size > 1_000) return emptyList()
    val ids = values.mapNotNull { it as? String }
    if (ids.size != values.size || ids.sumOf(String::length) > 32_768) return emptyList()
    return ids.filter(String::isNotBlank).distinct()
}
