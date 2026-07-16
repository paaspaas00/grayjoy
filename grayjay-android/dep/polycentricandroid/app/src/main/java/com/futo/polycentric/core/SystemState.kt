package com.futo.polycentric.core

import com.futo.polycentric.core.serializers.ImageBundleSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import userpackage.Protocol

@Serializable
class SystemState(
    val servers: Array<String>,
    val authorities: Array<String>,
    val processes: Array<Process>,
    val username: String,
    val description: String,
    val store: String,
    @Serializable(with = ImageBundleSerializer::class) val avatar: Protocol.ImageBundle?,
    @Serializable(with = ImageBundleSerializer::class) val banner: Protocol.ImageBundle?,
    var storeData: String,
    var promotion: String,
    var membershipUrls: List<String>,
    var donationDestinations: List<String>,
    @Serializable(with = ImageBundleSerializer::class) val promotionBanner: Protocol.ImageBundle?,
) {
    override fun toString(): String {
        return "(servers: $servers, processes: $processes, username: $username, description: $description, store: $store, avatar: $avatar, banner: $banner)"
    }

    companion object {
        fun fromStorageTypeSystemState(proto: StorageTypeSystemState): SystemState {
            val servers = mutableListOf<String>()
            val authorities = mutableListOf<String>()

            proto.crdtSetItems.forEach { item ->
                if (item.operation == LWWElementSetOperation.ADD) {
                    when (item.contentType) {
                        ContentType.SERVER.value -> {
                            servers.add(item.value.decodeToString())
                        }
                        ContentType.AUTHORITY.value -> {
                            authorities.add(item.value.decodeToString())
                        }
                    }
                }
            }

            val processes = mutableListOf<Process>()
            proto.processes.forEach { processes.add(it) }

            var username = ""
            var description = ""
            var store = ""
            var storeData = ""
            var promotion = ""
            var membershipUrls: List<String> = listOf()
            var donationDestinations: List<String> = listOf()
            var promotionBanner: Protocol.ImageBundle? = null
            var avatar: Protocol.ImageBundle? = null
            var banner: Protocol.ImageBundle? = null
            proto.crdtItems.forEach { item ->
                when (item.contentType) {
                    ContentType.USERNAME.value -> {
                        username = item.value.decodeToString()
                    }
                    ContentType.DESCRIPTION.value -> {
                        description = item.value.decodeToString()
                    }
                    ContentType.STORE.value -> {
                        store = item.value.decodeToString()
                    }
                    ContentType.AVATAR.value -> {
                        avatar = Protocol.ImageBundle.parseFrom(item.value)
                    }
                    ContentType.BANNER.value -> {
                        banner = Protocol.ImageBundle.parseFrom(item.value)
                    }
                    ContentType.STORE_DATA.value -> {
                        storeData = item.value.decodeToString()
                    }
                    ContentType.PROMOTION_BANNER.value -> {
                        promotionBanner = Protocol.ImageBundle.parseFrom(item.value)
                    }
                    ContentType.PROMOTION.value -> {
                        promotion = item.value.decodeToString()
                    }
                    ContentType.MEMBERSHIP_URLS.value -> {
                        val json = item.value.decodeToString()
                        membershipUrls = Json.decodeFromString(json)
                    }
                    ContentType.DONATION_DESTINATIONS.value -> {
                        val json = item.value.decodeToString()
                        donationDestinations = Json.decodeFromString(json)
                    }
                }
            }

            return SystemState(servers.toTypedArray(), authorities.toTypedArray(), processes.toTypedArray(), username, description, store, avatar, banner, storeData, promotion, membershipUrls, donationDestinations, promotionBanner)
        }
    }
}
