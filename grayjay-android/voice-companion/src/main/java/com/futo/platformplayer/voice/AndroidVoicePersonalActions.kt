package com.futo.platformplayer.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun contactSimilarity(query: String, name: String): Double {
    val a = answerText(query).take(120); val b = answerText(name).take(120)
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    if (b.split(' ').containsAll(a.split(' '))) return .90
    var prior = IntArray(b.length + 1) { it }
    a.forEachIndexed { i, ac ->
        val row = IntArray(b.length + 1); row[0] = i + 1
        b.forEachIndexed { j, bc -> row[j + 1] = minOf(row[j] + 1, prior[j + 1] + 1, prior[j] + if (ac == bc) 0 else 1) }
        prior = row
    }
    return 1.0 - prior.last().toDouble() / maxOf(a.length, b.length)
}

internal class AndroidVoicePersonalActions(private val context: Context) : VoicePersonalActions {
    override fun callsAllowed() = context.getSharedPreferences("voice", 0).getBoolean("voice_calls", false) &&
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE).all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    override fun messagesAllowed(app: String?) = VoiceMessageListener.enabled(context) &&
        (app == null || context.getSharedPreferences("voice", 0).getBoolean("messages_${app.lowercase()}", true))
    override suspend fun contacts(query: String): List<VoiceContact> = withContext(Dispatchers.IO) {
        check(callsAllowed())
        val candidates = ArrayList<Pair<VoiceContact, Double>>()
        context.contentResolver.query(Phone.CONTENT_URI,
            arrayOf(Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL), null, null, Phone.DISPLAY_NAME)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1).orEmpty()
                val score = contactSimilarity(query, name)
                val number = cursor.getString(2).orEmpty()
                // No USSD, extensions, arbitrary URI or telephone number invented by the model.
                if (score < .65 || !number.matches(Regex("[+0-9 ()\\-]{3,40}"))) continue
                val label = Phone.getTypeLabel(context.resources, cursor.getInt(3), cursor.getString(4)).toString()
                candidates += VoiceContact(cursor.getString(0), name.take(120), number, label) to score
                if (candidates.size > 64) { candidates.sortByDescending { it.second }; candidates.subList(16, candidates.size).clear() }
            }
        }
        val best = candidates.maxOfOrNull { it.second } ?: 0.0
        candidates.filter { it.second >= maxOf(.65, best - .08) }.sortedByDescending { it.second }
            .distinctBy { answerText(it.first.name) to it.first.number.filter(Char::isDigit) }.take(3).map { it.first }
    }
    @android.annotation.SuppressLint("MissingPermission") // callsAllowed rechecks both runtime grants immediately before dialing.
    override suspend fun call(contact: VoiceContact) = withContext(Dispatchers.Main) {
        check(callsAllowed())
        // Resolve the selected row again so removed/changed contacts cannot dial stale data.
        val current = withContext(Dispatchers.IO) {
            context.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.NUMBER), "${Phone._ID} = ?", arrayOf(contact.id), null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
        check(current == contact.number && current.matches(Regex("[+0-9 ()\\-]{3,40}")))
        context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", current, null), Bundle())
    }
}
