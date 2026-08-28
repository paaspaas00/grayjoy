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

    init {
        if (!preferences.contains(KEY_PROFILES)) {
            writeProfiles(
                listOf(
                    StoredProfile(MAIN_ID, "Main", ProfileProtection.None, builtIn = true),
                    StoredProfile(PRIVATE_ID, "Private", ProfileProtection.DeviceCredential, builtIn = true),
                ),
            )
            preferences.edit().putString(KEY_ACTIVE_PROFILE, MAIN_ID).apply()
        }
    }

    fun profiles(): List<ProfileUiModel> = readProfiles().map(::toUiModel)

    fun activeProfileId(): String {
        val profiles = readProfiles()
        val requested = preferences.getString(KEY_ACTIVE_PROFILE, MAIN_ID)
        return requested?.takeIf { id -> profiles.any { it.id == id } } ?: MAIN_ID
    }

    fun setActiveProfile(profileId: String) {
        require(readProfiles().any { it.id == profileId }) {
            appContext.getString(R.string.unknown_profile)
        }
        preferences.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
    }

    fun createProfile(name: String, pin: String? = null): ProfileUiModel {
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
        return toUiModel(profile)
    }

    fun verifyPin(profileId: String, pin: String): Boolean {
        val profile = readProfiles().firstOrNull { it.id == profileId } ?: return false
        if (profile.protection != ProfileProtection.Pin) return false
        return MessageDigest.isEqual(
            profile.pinHash.hexBytes(),
            hashPin(pin, profile.salt).hexBytes(),
        )
    }

    fun renameProfile(profileId: String, name: String): ProfileUiModel {
        val normalizedName = name.trim().take(40)
        require(normalizedName.isNotBlank()) { appContext.getString(R.string.enter_profile_name) }
        val profiles = readProfiles()
        val index = profiles.indexOfFirst { it.id == profileId }
        require(index >= 0) { appContext.getString(R.string.unknown_profile) }
        val updated = profiles[index].copy(name = normalizedName, customName = true)
        writeProfiles(profiles.toMutableList().apply { this[index] = updated })
        return toUiModel(updated)
    }

    fun setDeviceCredentialProtection(
        profileId: String,
        enabled: Boolean,
    ): ProfileUiModel {
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
        return toUiModel(updated)
    }

    fun deleteProfile(profileId: String): Boolean {
        val profiles = readProfiles()
        val profile = profiles.firstOrNull { it.id == profileId } ?: return false
        if (profile.builtIn || profileId == activeProfileId()) return false
        writeProfiles(profiles.filterNot { it.id == profileId })
        listOf(
            "grayjay_compose_preferences_$profileId",
            "grayjay_compose_library_v2_$profileId",
            "grayjay_compose_watch_progress_v1_$profileId",
            "grayjay_compose_sources_$profileId",
            "grayjoy_home_cache_v1_$profileId",
        ).forEach { name ->
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        return true
    }

    private fun readProfiles(): List<StoredProfile> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PROFILES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString("id")
                if (id.isBlank()) continue
                add(
                    StoredProfile(
                        id = id,
                        name = json.optString("name", appContext.getString(R.string.profile_generic)),
                        protection = runCatching {
                            ProfileProtection.valueOf(json.optString("protection"))
                        }.getOrDefault(ProfileProtection.Pin),
                        builtIn = json.optBoolean("builtIn"),
                        customName = json.optBoolean("customName"),
                        salt = json.optString("salt"),
                        pinHash = json.optString("pinHash"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList()).ifEmpty {
        listOf(StoredProfile(MAIN_ID, "Main", ProfileProtection.None, builtIn = true))
    }

    private fun writeProfiles(profiles: List<StoredProfile>) {
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
                    },
                )
            }
        }
        preferences.edit().putString(KEY_PROFILES, array.toString()).apply()
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
    )

    private data class StoredProfile(
        val id: String,
        val name: String,
        val protection: ProfileProtection,
        val builtIn: Boolean = false,
        val customName: Boolean = false,
        val salt: String = "",
        val pinHash: String = "",
    )

    private companion object {
        const val FILE_NAME = "grayjay_compose_profiles"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
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
