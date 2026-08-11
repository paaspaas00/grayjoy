package com.futo.platformplayer.compose

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

internal data class UiLanguageOption(
    val tag: String,
    val nativeName: String,
)

internal object AppLanguageManager {
    const val SYSTEM_LANGUAGE_TAG = ""

    val supportedLanguages: List<UiLanguageOption> = listOf(
        UiLanguageOption("en", "English"),
        UiLanguageOption("ar", "العربية"),
        UiLanguageOption("de", "Deutsch"),
        UiLanguageOption("es", "Español"),
        UiLanguageOption("fr", "Français"),
        UiLanguageOption("it", "Italiano"),
        UiLanguageOption("ja", "日本語"),
        UiLanguageOption("ko", "한국어"),
        UiLanguageOption("pt", "Português"),
        UiLanguageOption("ru", "Русский"),
        UiLanguageOption("tr", "Türkçe"),
        UiLanguageOption("zh", "中文"),
    )

    fun selectedLanguageTag(context: Context): String = normalizeLanguageTag(
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, SYSTEM_LANGUAGE_TAG),
    )

    fun setSelectedLanguageTag(context: Context, languageTag: String) {
        val normalized = normalizeLanguageTag(languageTag)
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, normalized)
            .apply()
        applyToApplicationResources(context.applicationContext, normalized)
    }

    fun localizedContext(base: Context): Context {
        val languageTag = selectedLanguageTag(base)
        if (languageTag == SYSTEM_LANGUAGE_TAG) return base
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    fun displayName(languageTag: String, systemDefaultLabel: String): String =
        supportedLanguages.firstOrNull { it.tag == normalizeLanguageTag(languageTag) }
            ?.nativeName
            ?: systemDefaultLabel

    fun effectiveLanguageTag(context: Context): String {
        val selected = selectedLanguageTag(context)
        if (selected.isNotBlank()) return selected
        return context.resources.configuration.locales.get(0)?.toLanguageTag()
            ?.takeIf(String::isNotBlank)
            ?: Locale.getDefault().toLanguageTag()
    }

    internal fun normalizeLanguageTag(languageTag: String?): String {
        val requested = languageTag.orEmpty().trim()
        if (requested.isEmpty()) return SYSTEM_LANGUAGE_TAG
        val language = Locale.forLanguageTag(requested).language
        return supportedLanguages.firstOrNull { it.tag == language }?.tag
            ?: SYSTEM_LANGUAGE_TAG
    }

    @Suppress("DEPRECATION")
    private fun applyToApplicationResources(context: Context, languageTag: String) {
        val configuration = Configuration(context.resources.configuration)
        if (languageTag == SYSTEM_LANGUAGE_TAG) {
            configuration.setLocales(Resources.getSystem().configuration.locales)
        } else {
            val locale = Locale.forLanguageTag(languageTag)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
        }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    private const val PREFERENCES_FILE = "grayjoy_global_preferences"
    private const val KEY_LANGUAGE_TAG = "ui_language_tag"
}
