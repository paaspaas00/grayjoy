package com.futo.platformplayer.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Signature-protected revocation also releases the microphone, not just command access. */
class VoiceDisabledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "${BuildConfig.GRAYJOY_PACKAGE}.VOICE_DISABLED")
            context.stopService(Intent(context, VoiceListeningService::class.java))
    }
}
