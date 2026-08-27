package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.SourceFilterGroupUiModel
import com.futo.platformplayer.compose.ui.SourceFilterOptionUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel
import org.json.JSONArray
import org.json.JSONObject

interface SourceRepository {
    fun loadEnabledSourceIds(sources: List<SourceUiModel>): Set<String>
    fun setEnabled(sourceId: String, enabled: Boolean)
    fun loadCustomSources(): List<SourceUiModel>
    fun saveCustomSource(source: SourceUiModel)
    fun removeCustomSource(sourceId: String)
}

internal class SharedPreferencesSourceRepository(
    context: Context,
    profileId: String = "main",
) : SourceRepository {
    private val preferences = context.getSharedPreferences(
        if (profileId == "main") FILE_NAME else "${FILE_NAME}_$profileId",
        Context.MODE_PRIVATE,
    )

    override fun loadEnabledSourceIds(sources: List<SourceUiModel>): Set<String> {
        if (!preferences.contains(KEY_ENABLED_SOURCE_IDS)) {
            preferences.edit().putStringSet(
                KEY_ENABLED_SOURCE_IDS,
                sources.filter(SourceUiModel::isEnabled).mapTo(mutableSetOf(), SourceUiModel::id),
            ).apply()
        }
        return preferences.getStringSet(KEY_ENABLED_SOURCE_IDS, emptySet()).orEmpty().toSet()
    }

    override fun setEnabled(sourceId: String, enabled: Boolean) {
        val updated = preferences
            .getStringSet(KEY_ENABLED_SOURCE_IDS, emptySet())
            .orEmpty()
            .toMutableSet()
        if (enabled) updated += sourceId else updated -= sourceId
        preferences.edit().putStringSet(KEY_ENABLED_SOURCE_IDS, updated).apply()
    }

    override fun loadCustomSources(): List<SourceUiModel> = runCatching {
        val array = JSONArray(preferences.getString(KEY_CUSTOM_SOURCES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString("id")
                val engineId = json.optString("engineId")
                val configUrl = json.optString("pluginConfigUrl")
                if (id.isBlank() || engineId.isBlank() || configUrl.isBlank()) continue
                add(
                    SourceUiModel(
                        id = id,
                        engineId = engineId,
                        name = json.optString("name", id),
                        description = json.optString("description"),
                        accentColor = json.optLong("accentColor", 0xFF52647A),
                        isEnabled = false,
                        pluginConfigUrl = configUrl,
                        iconUrl = json.optString("iconUrl"),
                        isCustom = true,
                        filterGroups = json.optJSONArray("filterGroups").toSourceFilterGroups(),
                        imageRequestHeaders = json.optJSONObject("imageRequestHeaders")
                            .toStringMap(),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    override fun saveCustomSource(source: SourceUiModel) {
        val sources = loadCustomSources()
            .filterNot { it.id == source.id || it.engineId == source.engineId }
            .plus(source.copy(isCustom = true))
        writeCustomSources(sources)
    }

    override fun removeCustomSource(sourceId: String) {
        writeCustomSources(loadCustomSources().filterNot { it.id == sourceId })
        setEnabled(sourceId, false)
    }

    private fun writeCustomSources(sources: List<SourceUiModel>) {
        val array = JSONArray().apply {
            sources.forEach { source ->
                put(
                    JSONObject().apply {
                        put("id", source.id)
                        put("engineId", source.engineId)
                        put("name", source.name)
                        put("description", source.description)
                        put("accentColor", source.accentColor)
                        put("pluginConfigUrl", source.pluginConfigUrl)
                        put("iconUrl", source.iconUrl)
                        put("filterGroups", source.filterGroups.toJsonArray())
                        put(
                            "imageRequestHeaders",
                            JSONObject().apply {
                                source.imageRequestHeaders.forEach { (name, value) -> put(name, value) }
                            },
                        )
                    },
                )
            }
        }
        preferences.edit().putString(KEY_CUSTOM_SOURCES, array.toString()).apply()
    }

    private companion object {
        const val FILE_NAME = "grayjay_compose_sources"
        const val KEY_ENABLED_SOURCE_IDS = "enabled_source_ids"
        const val KEY_CUSTOM_SOURCES = "custom_sources"
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    val json = this ?: return emptyMap()
    return json.keys().asSequence().associateWith(json::optString)
}

private fun JSONArray?.toSourceFilterGroups(): List<SourceFilterGroupUiModel> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val group = array.optJSONObject(index) ?: continue
            val options = group.optJSONArray("options")?.let { optionArray ->
                buildList {
                    for (optionIndex in 0 until optionArray.length()) {
                        val option = optionArray.optJSONObject(optionIndex) ?: continue
                        add(
                            SourceFilterOptionUiModel(
                                id = option.optString("id"),
                                label = option.optString("label"),
                                value = option.optString("value"),
                            ),
                        )
                    }
                }
            }.orEmpty()
            add(
                SourceFilterGroupUiModel(
                    id = group.optString("id"),
                    label = group.optString("label"),
                    scopes = group.optJSONArray("scopes")?.let { scopeArray ->
                        buildSet {
                            for (scopeIndex in 0 until scopeArray.length()) {
                                scopeArray.optString(scopeIndex).takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }.orEmpty(),
                    defaultValue = group.optString("defaultValue"),
                    options = options,
                ),
            )
        }
    }
}

private fun List<SourceFilterGroupUiModel>.toJsonArray() = JSONArray().apply {
    forEach { group ->
        put(
            JSONObject().apply {
                put("id", group.id)
                put("label", group.label)
                put("scopes", JSONArray(group.scopes.toList()))
                put("defaultValue", group.defaultValue)
                put(
                    "options",
                    JSONArray().apply {
                        group.options.forEach { option ->
                            put(
                                JSONObject().apply {
                                    put("id", option.id)
                                    put("label", option.label)
                                    put("value", option.value)
                                },
                            )
                        }
                    },
                )
            },
        )
    }
}

data class VisibleContent(
    val videos: List<VideoUiModel>,
    val channels: List<ChannelUiModel>,
    val playlists: List<PlaylistUiModel>,
)

internal fun visibleContentForSources(
    videos: List<VideoUiModel>,
    channels: List<ChannelUiModel>,
    playlists: List<PlaylistUiModel>,
    enabledSourceIds: Set<String>,
): VisibleContent {
    val visibleVideos = videos.filter { it.sourceId in enabledSourceIds }
    val visibleVideoIds = visibleVideos.mapTo(mutableSetOf(), VideoUiModel::id)
    return VisibleContent(
        videos = visibleVideos,
        channels = channels.filter { it.sourceId in enabledSourceIds },
        playlists = playlists.mapNotNull { playlist ->
            val visibleIds = playlist.videoIds.filter { it in visibleVideoIds }
            playlist.copy(videoIds = visibleIds).takeIf { visibleIds.isNotEmpty() }
        },
    )
}
