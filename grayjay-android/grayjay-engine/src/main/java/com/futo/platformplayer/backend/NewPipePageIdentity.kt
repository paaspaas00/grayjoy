package com.futo.platformplayer.backend

import org.schabi.newpipe.extractor.Page
import java.nio.ByteBuffer
import java.security.MessageDigest

/** YouTube continuations often share the same POST URL; the body identifies the actual page. */
internal fun newPipePageIdentity(page: Page): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun bytes(value: ByteArray?) {
        digest.update(ByteBuffer.allocate(4).putInt(value?.size ?: -1).array())
        value?.let(digest::update)
    }
    fun text(value: String?) = bytes(value?.toByteArray(Charsets.UTF_8))
    text(page.url)
    text(page.id)
    text(page.ids?.size?.toString())
    page.ids?.forEach(::text)
    text(page.cookies?.size?.toString())
    page.cookies?.toSortedMap()?.forEach { (key, value) -> text(key); text(value) }
    bytes(page.body)
    return digest.digest().joinToString("") { "%02x".format(it) }
}
