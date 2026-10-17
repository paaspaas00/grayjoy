package com.futo.platformplayer.compose.engine

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.EOFException

internal fun hasRejectedPlaybackUrl(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.take(12).any {
        it is HttpDataSource.InvalidResponseCodeException && it.responseCode in setOf(401, 403, 410)
    }

/** The transport already resumes interrupted responses. Do not multiply that retry budget. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class YoutubePlaybackLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(1) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (generateSequence<Throwable>(info.exception) { it.cause }.take(12).any {
                it is HttpDataSource.HttpDataSourceException || it is EOFException
            }) return C.TIME_UNSET
        return if (info.errorCount > 1) C.TIME_UNSET else super.getRetryDelayMsFor(info)
    }
}
