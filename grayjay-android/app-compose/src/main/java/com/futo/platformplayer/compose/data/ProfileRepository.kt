package com.futo.platformplayer.compose.data

import android.content.Context
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ProfileProtection
import com.futo.platformplayer.compose.ui.ProfileUiModel
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

internal class ProfileRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val catalogLock = profileDataLock(context, "__profile_catalog")

    init {
        synchronized(catalogLock) {
        if (!preferences.contains(KEY_PROFILES) && runCatching { readDeletedProfileIds(appContext) }.isSuccess) {
            writeProfiles(
                listOf(
                    StoredProfile(MAIN_ID, "Main", ProfileProtection.None, builtIn = true),
                    StoredProfile(PRIVATE_ID, "Private", ProfileProtection.DeviceCredential, builtIn = true),
                ),
            )
            preferences.edit().putString(KEY_ACTIVE_PROFILE, MAIN_ID).apply()
        }
        }
    }

    fun profiles(): List<ProfileUiModel> = readProfiles().filterNot { it.hidden }.map(::toUiModel)
    fun allProfiles(): List<ProfileUiModel> = readProfiles().map(::toUiModel)
    fun boundSourceId(profileId: String): String? = readProfiles().firstOrNull { it.id == profileId }
        ?.sourceId?.takeIf(String::isNotBlank)
    fun sourceProfilesEnabled(): Boolean = runCatching {
        preferences.getBoolean("source_profiles_enabled", false)
    }.getOrDefault(false)

    /** Staged profiles are never shown until all data copies have completed. */
    fun stageSourceProfile(source: com.futo.platformplayer.compose.ui.SourceUiModel): ProfileUiModel = synchronized(catalogLock) {
        ensureCatalogReadable()
        val existing = readProfiles()
        existing.firstOrNull { it.sourceId == source.id }?.let { return@synchronized toUiModel(it) }
        val created = StoredProfile(UUID.randomUUID().toString(), source.name, ProfileProtection.None,
            builtIn = source.id in setOf("youtube", "spotify"), sourceId = source.id, iconUrl = source.iconUrl,
            accentColor = source.accentColor, hidden = true)
        check(preferences.edit().putString(KEY_PROFILES, encodeProfiles(existing + created)).commit())
        toUiModel(created)
    }

    fun activateSourceProfiles(profileIds: Set<String>) = synchronized(catalogLock) {
        ensureCatalogReadable()
        val current = readProfiles()
        require(current.any { it.id in profileIds && it.sourceId == "youtube" })
        require(current.any { it.id in profileIds && it.sourceId == "spotify" })
        val updated = current.map { profile -> when {
            profile.id == MAIN_ID && profile.protection == ProfileProtection.None -> profile.copy(hidden = true)
            profile.id in profileIds -> profile.copy(hidden = false)
            else -> profile
        } }
        check(preferences.edit().putString(KEY_PROFILES, encodeProfiles(updated))
            .putBoolean("source_profiles_enabled", true).commit())
    }

    fun activeProfileId(): String {
        val profiles = readProfiles()
        val requested = runCatching { preferences.getString(KEY_ACTIVE_PROFILE, MAIN_ID) }.getOrDefault(MAIN_ID)
        return requested?.takeIf { id -> profiles.any { it.id == id && !it.hidden } }
            ?: profiles.firstOrNull { !it.hidden && it.sourceId == "youtube" }?.id
            ?: MAIN_ID
    }

    fun requireAvailableProfile(profileId: String) = synchronized(catalogLock) {
        if (wasProfileDeleted(appContext, profileId)) throw DeletedProfileException()
        val catalog = readCatalog()
        if (catalog.profiles.none { it.id == profileId }) {
            catalog.error?.let { throw ProfileDataUnavailableException(it) }
            throw IllegalArgumentException(appContext.getString(R.string.unknown_profile))
        }
    }

    fun setActiveProfile(profileId: String): Unit = synchronized(catalogLock) {
        requireAvailableProfile(profileId)
        preferences.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
    }

    fun createProfile(name: String, pin: String? = null): ProfileUiModel = synchronized(catalogLock) {
        ensureCatalogReadable()
        val normalizedName = name.trim().take(40)
        require(normalizedName.isNotBlank()) { appContext.getString(R.string.enter_profile_name) }
        val normalizedPin = pin.orEmpty()
        if (normalizedPin.isNotEmpty()) {
            require(normalizedPin.length >= 4 && normalizedPin.all(Char::isDigit)) {
                appContext.getString(R.string.pin_minimum_digits)
            }
        }
        val salt = if (normalizedPin.isEmpty()) {
            ""
        } else {
            ByteArray(16).also(SecureRandom()::nextBytes).toHex()
        }
        val profile = StoredProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            protection = if (normalizedPin.isEmpty()) ProfileProtection.None else ProfileProtection.Pin,
            salt = salt,
            pinHash = if (normalizedPin.isEmpty()) "" else hashPin(normalizedPin, salt),
        )
        writeProfiles(readProfiles() + profile)
        toUiModel(profile)
    }

    fun verifyPin(profileId: String, pin: String): Boolean {
        val profile = readProfiles().firstOrNull { it.id == profileId } ?: return false
        if (profile.protection != ProfileProtection.Pin) return false
        return MessageDigest.isEqual(
            profile.pinHash.hexBytes(),
            hashPin(pin, profile.salt).hexBytes(),
        )
    }

    fun renameProfile(profileId: String, name: String): ProfileUiModel = synchronized(catalogLock) {
        requireAvailableProfile(profileId)
        val normalizedName = name.trim().take(40)
        require(normalizedName.isNotBlank()) { appContext.getString(R.string.enter_profile_name) }
        val profiles = readProfiles()
        val index = profiles.indexOfFirst { it.id == profileId }
        require(index >= 0) { appContext.getString(R.string.unknown_profile) }
        val updated = profiles[index].copy(name = normalizedName, customName = true)
        writeProfiles(profiles.toMutableList().apply { this[index] = updated })
        toUiModel(updated)
    }

    fun setDeviceCredentialProtection(
        profileId: String,
        enabled: Boolean,
    ): ProfileUiModel = synchronized(catalogLock) {
        requireAvailableProfile(profileId)
        val profiles = readProfiles()
        val index = profiles.indexOfFirst { it.id == profileId }
        require(index >= 0) { appContext.getString(R.string.unknown_profile) }
        val updated = profiles[index].copy(
            protection = if (enabled) {
                ProfileProtection.DeviceCredential
            } else {
                ProfileProtection.None
            },
            salt = "",
            pinHash = "",
        )
        writeProfiles(profiles.toMutableList().apply { this[index] = updated })
        toUiModel(updated)
    }

    fun deleteProfile(profileId: String): Boolean {
        val profiles = readProfiles()
        val profile = profiles.firstOrNull { it.id == profileId } ?: return false
        if (profile.builtIn || profileId in setOf(MAIN_ID, PRIVATE_ID) || profileId == activeProfileId()) return false
        synchronized(profileDataLock(appContext, profileId)) {
        synchronized(catalogLock) {
        ensureCatalogReadable()
        // Recheck under the shared locks: a worker commit and a profile switch may have raced
        // with the confirmation dialog. The tombstone and catalog removal are one durable edit.
        if (profileId == activeProfileId() || readProfiles().firstOrNull { it.id == profileId }?.builtIn == true) return false
        val remaining = readProfiles().filterNot { it.id == profileId }
        check(preferences.edit()
            .putString(KEY_PROFILES, encodeProfiles(remaining))
            .putStringSet(KEY_DELETED_PROFILES, deletedProfileIds() + profileId)
            .commit()) { "Could not persist profile deletion" }
        listOf(
            "grayjay_compose_preferences_$profileId",
            "grayjay_compose_library_v2_$profileId",
            "grayjay_compose_watch_progress_v1_$profileId",
            "grayjay_compose_sources_$profileId",
            "grayjoy_home_cache_v1_$profileId",
            "grayjoy_channel_artwork_v1_$profileId",
            "music_classification_$profileId",
        ).forEach { name ->
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        AccountImportJournal(appContext, profileId).discardDeletedProfileJournal()
        OfflineProfileLinks(appContext).clearProfile(profileId)
        }
        }
        return true
    }

    private data class ProfileCatalog(val profiles: List<StoredProfile>, val error: Exception? = null)

    private fun readCatalog(): ProfileCatalog = try {
        val array = JSONArray(preferences.getString(KEY_PROFILES, "[]"))
        var error: Exception? = null
        val profiles = buildList {
            val ids = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                try {
                val json = array.getJSONObject(index)
                val id = json.getString("id")
                require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && ids.add(id)) { "Invalid or duplicate profile identifier" }
                add(
                    StoredProfile(
                        id = id,
                        name = json.optString("name", appContext.getString(R.string.profile_generic)),
                        protection = ProfileProtection.valueOf(json.getString("protection")),
                        builtIn = id == MAIN_ID || id == PRIVATE_ID || json.optString("sourceId") in setOf("youtube", "spotify"),
                        customName = json.optBoolean("customName"),
                        salt = json.optString("salt"),
                        pinHash = json.optString("pinHash"),
                        sourceId = json.optString("sourceId"),
                        iconUrl = json.optString("sourceIconUrl"),
                        accentColor = json.optLong("sourceAccentColor"),
                        hidden = json.optBoolean("hidden"),
                    ),
                )
                } catch (invalid: Exception) { error = invalid }
            }
        }
        if (profiles.none { it.id == MAIN_ID }) error = error ?: IllegalStateException("Main profile is missing")
        ProfileCatalog(profiles, error)
    } catch (error: Exception) { ProfileCatalog(emptyList(), error) }

    private fun readProfiles(): List<StoredProfile> {
        val catalog = readCatalog()
        if (catalog.profiles.any { it.id == MAIN_ID }) return catalog.profiles
        // Keep startup and recovery possible, but never turn unreadable protection into None.
        return catalog.profiles + StoredProfile(MAIN_ID, "Main", ProfileProtection.DeviceCredential, builtIn = true)
    }

    private fun ensureCatalogReadable() {
        readDeletedProfileIds(appContext)
        if (!preferences.contains(KEY_PROFILES)) return
        readCatalog().error?.let { throw ProfileDataUnavailableException(it) }
    }

    private fun deletedProfileIds(): Set<String> = readDeletedProfileIds(appContext)

    private fun writeProfiles(profiles: List<StoredProfile>) = synchronized(catalogLock) {
        ensureCatalogReadable()
        preferences.edit().putString(KEY_PROFILES, encodeProfiles(profiles)).apply()
    }

    private fun encodeProfiles(profiles: List<StoredProfile>): String {
        val array = JSONArray().apply {
            profiles.forEach { profile ->
                put(
                    JSONObject().apply {
                        put("id", profile.id)
                        put("name", profile.name)
                        put("protection", profile.protection.name)
                        put("builtIn", profile.builtIn)
                        put("customName", profile.customName)
                        put("salt", profile.salt)
                        put("pinHash", profile.pinHash)
                        put("sourceId", profile.sourceId)
                        put("sourceIconUrl", profile.iconUrl)
                        put("sourceAccentColor", profile.accentColor)
                        put("hidden", profile.hidden)
                    },
                )
            }
        }
        return array.toString()
    }

    private fun toUiModel(profile: StoredProfile): ProfileUiModel = ProfileUiModel(
        id = profile.id,
        name = when {
            profile.customName -> profile.name
            profile.builtIn && profile.id == MAIN_ID -> appContext.getString(R.string.profile_main)
            profile.builtIn && profile.id == PRIVATE_ID -> appContext.getString(R.string.profile_private)
            else -> profile.name
        },
        protection = profile.protection,
        isBuiltIn = profile.builtIn,
        sourceId = profile.sourceId,
        sourceIconUrl = profile.iconUrl,
        sourceAccentColor = if (profile.sourceId.equals("youtube", ignoreCase = true))
            YOUTUBE_SOURCE_ACCENT else profile.accentColor,
    )

    private data class StoredProfile(
        val id: String,
        val name: String,
        val protection: ProfileProtection,
        val builtIn: Boolean = false,
        val customName: Boolean = false,
        val salt: String = "",
        val pinHash: String = "",
        val sourceId: String = "",
        val iconUrl: String = "",
        val accentColor: Long = 0L,
        val hidden: Boolean = false,
    )

    private companion object {
        const val FILE_NAME = "grayjay_compose_profiles"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_DELETED_PROFILES = "deleted_profile_ids"
        const val MAIN_ID = "main"
        const val PRIVATE_ID = "private"

        fun hashPin(pin: String, salt: String): String = MessageDigest.getInstance("SHA-256")
            .digest(salt.hexBytes() + pin.toByteArray())
            .toHex()

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        fun String.hexBytes(): ByteArray = chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toByte() }
            .toByteArray()
    }
}

internal class ProfileDataUnavailableException(cause: Exception) :
    IllegalStateException("Existing profile data cannot be read; refusing to overwrite it", cause)

internal fun wasProfileDeleted(context: Context, profileId: String): Boolean =
    readDeletedProfileIds(context).contains(profileId)

/** Unknown deletion state cannot authorize writes or replay an old recovery journal. */
private fun readDeletedProfileIds(context: Context): Set<String> = try {
    val stored = context.applicationContext.getSharedPreferences("grayjay_compose_profiles", Context.MODE_PRIVATE)
        .all
    if (!stored.containsKey("deleted_profile_ids")) emptySet() else {
        val value = stored["deleted_profile_ids"]
        require(value is Set<*> && value.all { it is String }) { "Invalid profile deletion metadata" }
        value.filterIsInstance<String>().toSet()
    }
} catch (error: Exception) {
    throw ProfileDataUnavailableException(error)
}

internal class DeletedProfileException : IllegalStateException("This profile has been deleted")
