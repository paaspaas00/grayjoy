package com.futo.polycentric.core

import userpackage.Protocol

class Claims {
    companion object {
        //TODO: Update once claim types have been known
        fun hackerNews(id: String): Protocol.Claim {
            return claim(ClaimType.HACKER_NEWS.value, mapOf(0L to id))
        }

        fun youtube(internalId: String, handle: String): Protocol.Claim {
            return claim(ClaimType.YOUTUBE.value, mapOf(
                0L to handle,
                1L to internalId
            ))
        }

        fun odysee(lbryId: String, claimId: String): Protocol.Claim {
            return claim(ClaimType.ODYSEE.value, mapOf(
                0L to lbryId,
                1L to claimId
            ))
        }

        fun rumbleUser(id: String): Protocol.Claim {
            return claim(ClaimType.RUMBLE.value, mapOf(
                0L to id
            ))
        }

        fun rumbleChannel(id: String): Protocol.Claim {
            return claim(ClaimType.RUMBLE.value, mapOf(
                1L to id
            ))
        }

        fun discord(id: String): Protocol.Claim {
            return claim(ClaimType.DISCORD.value, mapOf(0L to id))
        }

        fun instagram(id: String): Protocol.Claim {
            return claim(ClaimType.INSTAGRAM.value, mapOf(0L to id))
        }

        fun twitch(id: String): Protocol.Claim {
            return claim(ClaimType.TWITCH.value, mapOf(0L to id))
        }

        fun url(url: String): Protocol.Claim {
            return claim(ClaimType.URL.value, mapOf(0L to url))
        }

        fun bitcoin(id: String): Protocol.Claim {
            return claim(ClaimType.BITCOIN.value, mapOf(0L to id))
        }

        fun twitter(id: String): Protocol.Claim {
            return claim(ClaimType.TWITTER.value, mapOf(0L to id))
        }

        fun generic(id: String): Protocol.Claim {
            return claim(ClaimType.GENERIC.value, mapOf(0L to id))
        }

        fun github(id: String): Protocol.Claim {
            return claim(ClaimType.GITHUB.value, mapOf(0L to id))
        }

        fun minds(id: String): Protocol.Claim {
            return claim(ClaimType.MINDS.value, mapOf(0L to id))
        }

        fun patreon(id: String): Protocol.Claim {
            return claim(ClaimType.PATREON.value, mapOf(0L to id))
        }

        fun substack(id: String): Protocol.Claim {
            return claim(ClaimType.SUBSTACK.value, mapOf(0L to id))
        }

        fun website(id: String): Protocol.Claim {
            return claim(ClaimType.WEBSITE.value, mapOf(0L to id))
        }

        fun kick(id: String): Protocol.Claim {
            return claim(ClaimType.KICK.value, mapOf(0L to id))
        }

        fun soundcloud(username: String, internalId: String): Protocol.Claim {
            return claim(ClaimType.SOUNDCLOUD.value, mapOf(
                0L to username,
                1L to internalId
            ))
        }

        fun vimeo(id: String): Protocol.Claim {
            return claim(ClaimType.VIMEO.value, mapOf(0L to id))
        }

        fun nebula(id: String): Protocol.Claim {
            return claim(ClaimType.NEBULA.value, mapOf(0L to id))
        }

        fun occupation(id: String): Protocol.Claim {
            return claim(ClaimType.OCCUPATION.value, mapOf(0L to id))
        }

        fun skill(id: String): Protocol.Claim {
            return claim(ClaimType.SKILL.value, mapOf(0L to id))
        }

        fun spotify(id: String): Protocol.Claim {
            return claim(ClaimType.SPOTIFY.value, mapOf(0L to id))
        }

        fun spreadshop(id: String): Protocol.Claim {
            return claim(ClaimType.SPREADSHOP.value, mapOf(0L to id))
        }

        fun polycentric(id: String): Protocol.Claim {
            return claim(ClaimType.POLYCENTRIC.value, mapOf(0L to id))
        }

        fun claim(type: Long, fields: Map<Long, String>): Protocol.Claim{
            return Protocol.Claim.newBuilder()
                .setClaimType(type)
                .addAllClaimFields(fields.map {
                    Protocol.ClaimFieldEntry.newBuilder()
                        .setKey(it.key)
                        .setValue(it.value)
                        .build()
                })
                .build()
        }
    }
}

fun Protocol.Claim.toUrl(key: Long): String? {
    return when (claimType) {
        ClaimType.HACKER_NEWS.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://news.ycombinator.com/user?id=${it.value}" } else null
        ClaimType.YOUTUBE.value -> {
            when (key) {
                0L -> claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://www.youtube.com/channel/${it.value}" }
                1L -> claimFieldsList.firstOrNull { it.key == 1L }?.let { "https://www.youtube.com/${it.value}" }
                else -> null
            }
        }
        ClaimType.ODYSEE.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://odysee.com/${it.value}" } else null
        ClaimType.RUMBLE.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://rumble.com/${it.value}" } else null
        ClaimType.TWITTER.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://twitter.com/${it.value}" } else null
        ClaimType.BITCOIN.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "bitcoin:${it.value}" } else null
        ClaimType.GENERIC.value -> null //TODO: Implement?
        ClaimType.DISCORD.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://discordapp.com/users/${it.value}" } else null
        ClaimType.INSTAGRAM.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://instagram.com/${it.value}" } else null
        ClaimType.GITHUB.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://github.com/${it.value}" } else null
        ClaimType.MINDS.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://www.minds.com/${it.value}" } else null
        ClaimType.PATREON.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://www.patreon.com/${it.value}" } else null
        ClaimType.SUBSTACK.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://${it.value}.substack.com" } else null //TODO: Check this
        ClaimType.TWITCH.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://www.twitch.tv/${it.value}" } else null
        ClaimType.WEBSITE.value -> null //TODO: Implement?
        ClaimType.KICK.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://www.kickstarter.com/profile/${it.value}" } else null //TODO: Check this
        ClaimType.SOUNDCLOUD.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://soundcloud.com/${it.value}" } else null
        ClaimType.VIMEO.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://vimeo.com/${it.value}" } else null
        ClaimType.NEBULA.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://nebula.tv/${it.value}" } else null
        ClaimType.URL.value -> null //TODO: Implement?
        ClaimType.OCCUPATION.value -> null //TODO: Implement?
        ClaimType.SKILL.value -> null //TODO: Implement?
        ClaimType.SPOTIFY.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://open.spotify.com/user/${it.value}" } else null
        ClaimType.SPREADSHOP.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "https://${it.value}.spreadshop.com" } else null //TODO: Check this
        ClaimType.POLYCENTRIC.value -> if (key == 0L) claimFieldsList.firstOrNull { it.key == 0L }?.let { "polycentric://${it.value}" } else null
        else -> null
    }
}

fun Protocol.Claim.toName(): String? {
    return when (claimType) {
        ClaimType.HACKER_NEWS.value -> "HackerNews"
        ClaimType.YOUTUBE.value -> "YouTube"
        ClaimType.ODYSEE.value -> "Odysee"
        ClaimType.RUMBLE.value -> "Rumble"
        ClaimType.TWITTER.value -> "Twitter"
        ClaimType.BITCOIN.value -> "Bitcoin"
        ClaimType.GENERIC.value -> "Generic"
        ClaimType.DISCORD.value -> "Discord"
        ClaimType.INSTAGRAM.value -> "Instagram"
        ClaimType.GITHUB.value -> "GitHub"
        ClaimType.MINDS.value -> "Minds"
        ClaimType.PATREON.value -> "Patreon"
        ClaimType.SUBSTACK.value -> "Substack"
        ClaimType.TWITCH.value -> "Twitch"
        ClaimType.WEBSITE.value -> "Website"
        ClaimType.KICK.value -> "Kick"
        ClaimType.SOUNDCLOUD.value -> "Soundcloud"
        ClaimType.VIMEO.value -> "Vimeo"
        ClaimType.NEBULA.value -> "Nebula"
        ClaimType.URL.value -> "URL"
        ClaimType.OCCUPATION.value -> "Occupation"
        ClaimType.SKILL.value -> "Skill"
        ClaimType.SPOTIFY.value -> "Spotify"
        ClaimType.SPREADSHOP.value -> "Spreadshop"
        ClaimType.POLYCENTRIC.value -> "Polycentric"
        else -> "unknown"
    }
}