package com.futo.platformplayer.compose.engine

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import java.util.UUID

/** Routes Widevine license challenges through the source plugin when it supplies an executor. */
@UnstableApi
internal class PluginMediaDrmCallback(
    private val delegate: MediaDrmCallback,
    private val requestExecutor: JSRequestExecutor,
    private val licenseUrl: String,
) : MediaDrmCallback by delegate {
    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest,
    ): MediaDrmCallback.Response = MediaDrmCallback.Response(
        requestExecutor.executeRequest("POST", licenseUrl, request.data, emptyMap()),
    )
}
