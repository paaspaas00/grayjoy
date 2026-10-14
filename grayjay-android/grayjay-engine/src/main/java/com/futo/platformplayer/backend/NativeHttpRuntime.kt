package com.futo.platformplayer.backend

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.curlbind.Libcurl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Lazy native HTTP setup. Trust comes from Android, not a downloaded bundle. */
object NativeHttpRuntime {
    private val lock = Mutex()
    private var initialized = false

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (initialized) return@withLock
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            val certificates = factory.trustManagers.filterIsInstance<X509TrustManager>()
                .flatMap { it.acceptedIssuers.toList() }
            check(certificates.isNotEmpty()) { "Android has no trusted certificate authorities" }
            val file = AtomicFile(File(context.noBackupFilesDir, "native-http-ca.pem"))
            val output = file.startWrite()
            try {
                certificates.forEach { certificate ->
                    val pem = "-----BEGIN CERTIFICATE-----\n" +
                        Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
                            .chunked(64).joinToString("\n") + "\n-----END CERTIFICATE-----\n"
                    output.write(pem.toByteArray(Charsets.US_ASCII))
                }
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
            Libcurl.setDefaultCAPath(file.baseFile.absolutePath)
            initialized = true
        }
    }
}
