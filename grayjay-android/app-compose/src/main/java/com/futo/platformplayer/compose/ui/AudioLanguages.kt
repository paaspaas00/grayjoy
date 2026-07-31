package com.futo.platformplayer.compose.ui

import java.util.Locale

/**
 * Keep this list aligned with the language choices exposed by legacy Grayjay.
 * A source may still return another BCP-47 tag; the player sheet displays those too.
 */
internal val supportedAudioLanguageCodes = listOf(
    "en",
    "es",
    "de",
    "fr",
    "ja",
    "ko",
    "th",
    "vi",
    "id",
    "hi",
    "ar",
    "tr",
    "ru",
    "pt",
    "zh",
    "it",
)

internal fun audioLanguageDisplayName(
    language: String,
    displayLocale: Locale = Locale.getDefault(),
): String {
    val normalized = language.trim().replace('_', '-')
    if (normalized.isBlank()) return language
    val locale = Locale.forLanguageTag(normalized)
    val displayName = locale.getDisplayName(displayLocale).ifBlank { normalized.uppercase(displayLocale) }
    return displayName.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(displayLocale) else character.toString()
    }
}
