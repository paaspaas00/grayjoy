package com.futo.platformplayer

/** Backend-only defaults for legacy HTTP code. The Compose host does not expose
 * legacy developer networking or downloaded-CA settings. */
class Settings private constructor() {
    val browsing = Browsing()

    class Browsing {
        val useDownloadedCABundle: Boolean = false
    }

    companion object {
        val instance = Settings()
    }
}

class SettingsDev private constructor() {
    val developerMode: Boolean = false
    val networking = Networking()

    class Networking {
        val allowAllCertificates: Boolean = false
    }

    companion object {
        val instance = SettingsDev()
    }
}
