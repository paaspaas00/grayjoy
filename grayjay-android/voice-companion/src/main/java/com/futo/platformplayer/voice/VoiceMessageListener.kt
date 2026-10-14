package com.futo.platformplayer.voice

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Only fresh messages from explicitly enabled apps; no notification history is persisted. */
internal class VoiceMessageInbox(private val now: () -> Long = System::currentTimeMillis) {
    private val pending = ArrayDeque<IncomingVoiceMessage>()
    private val seen = LinkedHashMap<String, Long>()
    @Synchronized fun offer(message: IncomingVoiceMessage) {
        val time = now()
        seen.entries.removeAll { time - it.value > 120_000 }
        if (time - message.receivedAt !in 0..30_000 || message.key in seen) return
        seen[message.key] = time
        while (seen.size > 128) seen.remove(seen.keys.first())
        pending.addLast(message.copy(sender = message.sender.take(100), text = message.text.take(1800)))
        while (pending.size > 8) pending.removeFirst()
    }
    @Synchronized fun poll(): IncomingVoiceMessage? {
        while (pending.isNotEmpty()) {
            val message = pending.removeFirst()
            if (now() - message.receivedAt in 0..120_000) return message
        }
        return null
    }
    @Synchronized fun remove(notificationKey: String) { pending.removeAll { it.key.startsWith("$notificationKey:") } }
    @Synchronized fun clear() { pending.clear(); seen.clear() }
}

class VoiceMessageListener : NotificationListenerService() {
    companion object {
        internal val inbox = VoiceMessageInbox()
        internal fun accessGranted(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, VoiceMessageListener::class.java))
        internal fun enabled(context: Context): Boolean = context.getSharedPreferences("voice", 0)
            .getBoolean("message_announcements", false) && accessGranted(context)
        internal fun source(packageName: String): String? = when (packageName) {
            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram" -> "Telegram"
            else -> null
        }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!VoiceListeningService.isListening || !enabled(this)) return
        val app = source(sbn.packageName) ?: return
        val prefs = getSharedPreferences("voice", 0)
        if (!prefs.getBoolean("messages_${app.lowercase()}", true)) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 || notification.visibility == Notification.VISIBILITY_SECRET) return
        // No snapshots at connect: announcing old notifications after activation is surprising.
        runCatching {
            val extras = notification.extras
            val messages = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages.orEmpty()
            val latest = messages.lastOrNull()
            val sender = latest?.person?.name?.toString()
                ?: if (notification.category == Notification.CATEGORY_MESSAGE) extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() else null
            val body = latest?.text?.toString()
                ?: if (notification.category == Notification.CATEGORY_MESSAGE) extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() else null
            if (sender.isNullOrBlank() || body.isNullOrBlank() || latest?.person == null && messages.isNotEmpty()) return
            val time = latest?.timestamp?.takeIf { it > 0L } ?: sbn.postTime
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest((sender + "\n" + body).toByteArray())
                .take(12).joinToString("") { "%02x".format(it) }
            inbox.offer(IncomingVoiceMessage("${sbn.key}:$time:$digest", app, sender, body, time))
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { inbox.remove(sbn.key) }
    override fun onListenerDisconnected() { inbox.clear() }
}
