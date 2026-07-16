package com.futo.platformplayer.api.media.platforms.js

/** Runtime portion of Grayjay's descriptor. Form annotations live in the legacy UI. */
class SourcePluginDescriptor(
    val config: SourcePluginConfig,
    private var auth: SourceAuth? = null,
    private var captcha: SourceCaptchaData? = null,
    val flags: List<String> = emptyList(),
    var settings: HashMap<String, String?> = hashMapOf(),
) {
    var appSettings: AppPluginSettings = AppPluginSettings()

    fun getSettingsWithDefaults(): HashMap<String, String?> = HashMap(settings).apply {
        config.settings.forEach { field ->
            if (this[field.variableOrName] == null) this[field.variableOrName] = field.default
        }
    }

    fun updateCaptcha(value: SourceCaptchaData?) { captcha = value }
    fun getCaptchaData(): SourceCaptchaData? = captcha
    fun updateAuth(value: SourceAuth?) { auth = value }
    fun getAuth(): SourceAuth? = auth

    class AppPluginSettings {
        var checkForUpdates: Boolean = true
        var automaticUpdate: Boolean = true
        var tabEnabled = TabEnabled()
        var sync = Sync()
        var rateLimit = RateLimit()
        var allowDeveloperSubmit: Boolean = false

        class TabEnabled {
            var enableHome: Boolean? = null
            var enableSearch: Boolean? = null
            var enableShorts: Boolean? = null
        }

        class Sync { var enableHistorySync: Boolean? = null }

        class RateLimit {
            var rateLimitSubs: Int = 0
            fun getSubRateLimit(): Int = listOf(-1, 25, 50, 75, 100, 125, 150, 200)
                .getOrElse(rateLimitSubs) { -1 }
        }

        fun loadDefaults(config: SourcePluginConfig) {
            if (tabEnabled.enableHome == null) tabEnabled.enableHome = config.enableInHome
            if (tabEnabled.enableSearch == null) tabEnabled.enableSearch = config.enableInSearch
            if (tabEnabled.enableShorts == null) tabEnabled.enableShorts = config.enableInShorts
        }
    }

    companion object { const val FLAG_EMBEDDED = "EMBEDDED" }
}
