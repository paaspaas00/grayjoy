package com.futo.platformplayer.states

import android.content.Context
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.models.ImageVariable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID

class StateApp private constructor() {
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var contextOrNull: Context? = null
        private set
    val context: Context get() = requireNotNull(contextOrNull)
    val scopeOrNull: CoroutineScope? get() = backendScope
    val scope: CoroutineScope get() = backendScope
    val sessionId: String = UUID.randomUUID().toString()
    val isMainActive: Boolean get() = contextOrNull != null

    fun attach(context: Context) { contextOrNull = context.applicationContext }
    fun getTempFile(): File = File.createTempFile("grayjay-", ".tmp", context.cacheDir)
    fun handleCaptchaException(client: JSClient, error: ScriptCaptchaRequiredException) = Unit

    companion object { val instance = StateApp() }
}

class StatePlatform private constructor() {
    fun getPlatformIcon(id: String): ImageVariable? = null
    fun getContentClientOrNull(url: String): IPlatformClient? = null
    fun isClientEnabled(id: String): Boolean = false
    companion object { val instance = StatePlatform() }
}

class StatePlugins private constructor() {
    private val scripts = mutableMapOf<String, String>()
    private val descriptors = mutableMapOf<String, SourcePluginDescriptor>()
    fun getScript(id: String): String? = scripts[id]
    fun getPlugin(id: String): SourcePluginDescriptor? = descriptors[id]
    fun register(descriptor: SourcePluginDescriptor, script: String) {
        descriptors[descriptor.config.id] = descriptor
        scripts[descriptor.config.id] = script
    }
    companion object { val instance = StatePlugins() }
}

enum class AnnouncementType { SESSION, SESSION_RECURRING }

class StateAnnouncement private constructor() {
    fun registerAnnouncement(
        id: String,
        title: String,
        message: String,
        type: AnnouncementType,
        time: OffsetDateTime = OffsetDateTime.now(),
    ) = Unit
    companion object { val instance = StateAnnouncement() }
}

class StateDeveloper private constructor() {
    var currentDevID: String? = null
    var devProxy: DevProxySettings? = null
    fun logDevException(devId: String, message: String) = Unit
    fun logDevInfo(devId: String, message: String) = Unit
    fun addDevHttpExchange(exchange: DevHttpExchange) = Unit
    inline fun <reified T> handleDevCall(
        devId: String,
        contextName: String,
        printResult: Boolean = false,
        handle: () -> T,
    ): T = handle()

    data class DevProxySettings(val url: String, val port: Int)
    data class DevHttpRequest(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String,
        val status: Int = 0,
    )
    data class DevHttpExchange(val request: DevHttpRequest, val response: DevHttpRequest)

    companion object {
        const val DEV_ID = "DEV"
        val instance = StateDeveloper()
    }
}
