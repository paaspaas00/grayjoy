package com.futo.platformplayer.compose.engine

import android.content.Context
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.SourceAvailability
import com.futo.platformplayer.compose.ui.SourceUiModel
import org.json.JSONObject

internal class GrayjaySourceCatalog(private val context: Context) {
    fun load(fallback: List<SourceUiModel>): List<SourceUiModel> = runCatching {
        val registry = context.assets.open("plugin_config.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        val embedded = registry.getJSONObject("SOURCES_EMBEDDED")
        val defaultsJson = registry.getJSONArray("SOURCES_EMBEDDED_DEFAULT")
        val defaultEngineIds = buildSet {
            for (index in 0 until defaultsJson.length()) add(defaultsJson.getString(index))
        }
        val entries = buildList {
            val keys = embedded.keys()
            while (keys.hasNext()) {
                val engineId = keys.next()
                val configPath = embedded.getString(engineId)
                val alias = configPath.split('/').getOrNull(1) ?: engineId
                add(RegistryEntry(engineId, alias, configPath))
            }
        }
        entries
            .sortedWith(compareBy<RegistryEntry> { sourceOrder.indexOf(it.alias).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                .thenBy { sourceNames[it.alias] ?: it.alias })
            .map { entry ->
                val hasPluginPayload = runCatching {
                    context.assets.open(entry.configPath).use { }
                }.isSuccess
                val runtimePlugin = officialPluginEndpoints[entry.alias]
                val hasRuntimePlugin = runtimePlugin != null
                SourceUiModel(
                    id = entry.alias,
                    engineId = entry.engineId,
                    name = sourceNames[entry.alias] ?: entry.alias.toDisplayName(),
                    description = context.getString(
                        sourceDescriptionResources[entry.alias]
                            ?: R.string.source_description_default,
                    ),
                    accentColor = sourceColors[entry.alias] ?: 0xFF52647A,
                    isEnabled = entry.engineId in defaultEngineIds,
                    pluginConfigPath = entry.configPath,
                    pluginConfigUrl = runtimePlugin?.configUrl.orEmpty(),
                    iconUrl = runtimePlugin?.iconUrl.orEmpty(),
                    availability = when {
                        hasPluginPayload -> SourceAvailability.PluginAvailable
                        hasRuntimePlugin -> SourceAvailability.PluginAvailable
                        else -> SourceAvailability.MissingPlugin
                    },
                )
            }
    }.getOrElse {
        fallback.map { source ->
            source.copy(
                description = context.getString(
                    sourceDescriptionResources[source.id] ?: R.string.source_description_default,
                ),
            )
        }
    }

    private data class RegistryEntry(
        val engineId: String,
        val alias: String,
        val configPath: String,
    )

    private fun String.toDisplayName(): String =
        split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    companion object {
        private val sourceOrder = listOf(
            "youtube", "odysee", "peertube", "rumble", "twitch", "kick",
            "soundcloud", "nebula", "patreon", "dailymotion", "bitchute",
            "bilibili", "apple-podcasts", "tedtalks", "curiositystream",
            "crunchyroll", "mixcloud", "radiobrowser", "redbull-tv", "fosdem",
            "nasa-plus",
        )

        private val sourceNames = mapOf(
            "youtube" to "YouTube",
            "odysee" to "Odysee",
            "peertube" to "PeerTube",
            "rumble" to "Rumble",
            "twitch" to "Twitch",
            "kick" to "Kick",
            "soundcloud" to "SoundCloud",
            "nebula" to "Nebula",
            "patreon" to "Patreon",
            "bilibili" to "Bilibili",
            "dailymotion" to "Dailymotion",
            "bitchute" to "BitChute",
            "apple-podcasts" to "Apple Podcasts",
            "tedtalks" to "TED Talks",
            "curiositystream" to "CuriosityStream",
            "crunchyroll" to "Crunchyroll",
            "mixcloud" to "Mixcloud",
            "radiobrowser" to "Radio Browser",
            "redbull-tv" to "Red Bull TV",
            "fosdem" to "FOSDEM",
            "nasa-plus" to "NASA+",
        )

        private val sourceDescriptionResources: Map<String, Int> = mapOf(
            "youtube" to R.string.source_description_youtube,
            "odysee" to R.string.source_description_odysee,
            "peertube" to R.string.source_description_peertube,
            "rumble" to R.string.source_description_rumble,
            "twitch" to R.string.source_description_twitch,
            "kick" to R.string.source_description_kick,
            "soundcloud" to R.string.source_description_soundcloud,
            "nebula" to R.string.source_description_nebula,
            "patreon" to R.string.source_description_patreon,
            "bilibili" to R.string.source_description_bilibili,
            "dailymotion" to R.string.source_description_dailymotion,
            "bitchute" to R.string.source_description_bitchute,
            "apple-podcasts" to R.string.source_description_apple_podcasts,
            "tedtalks" to R.string.source_description_tedtalks,
            "curiositystream" to R.string.source_description_curiositystream,
            "crunchyroll" to R.string.source_description_crunchyroll,
            "mixcloud" to R.string.source_description_mixcloud,
            "radiobrowser" to R.string.source_description_radiobrowser,
            "redbull-tv" to R.string.source_description_redbull_tv,
            "fosdem" to R.string.source_description_fosdem,
            "nasa-plus" to R.string.source_description_nasa_plus,
        )

        private val sourceColors = mapOf(
            "youtube" to 0xFFE53935,
            "odysee" to 0xFF2F7CF6,
            "peertube" to 0xFFF1680D,
            "rumble" to 0xFF74CC1D,
            "twitch" to 0xFF9146FF,
            "kick" to 0xFF53FC18,
            "soundcloud" to 0xFFFF5500,
            "nebula" to 0xFF202A44,
            "patreon" to 0xFFFF424D,
            "bilibili" to 0xFF00A1D6,
            "dailymotion" to 0xFF0066DC,
            "bitchute" to 0xFFEF4135,
            "apple-podcasts" to 0xFF9933CC,
            "tedtalks" to 0xFFE62B1E,
            "curiositystream" to 0xFF1E8E74,
            "crunchyroll" to 0xFFF47521,
            "mixcloud" to 0xFF5000FF,
            "radiobrowser" to 0xFF3F6DB5,
            "redbull-tv" to 0xFFDB0A40,
            "fosdem" to 0xFF5B87A5,
            "nasa-plus" to 0xFF0B3D91,
        )
    }
}
