package com.futo.platformplayer.compose.pclink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PcLinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PcLinkService.ensureRunning(context)
        }
    }
}
