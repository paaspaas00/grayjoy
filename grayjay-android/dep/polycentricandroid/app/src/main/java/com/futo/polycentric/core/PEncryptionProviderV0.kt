package com.futo.polycentric.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class PEncryptionProviderV0 {
    private val _keyStore: KeyStore
    private val secretKey: Key? get() = _keyStore.getKey(KEY_ALIAS, null)

    constructor() {
        _keyStore = KeyStore.getInstance(AndroidKeyStore)
        _keyStore.load(null)

        if (!_keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator: KeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
            keyGenerator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false)
                .build())

            keyGenerator.generateKey()
        }
    }

    fun encrypt(decrypted: ByteArray): ByteArray {
        val c: Cipher = Cipher.getInstance(AES_MODE)
        c.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, FIXED_IV))
        return c.doFinal(decrypted)
    }

    fun encrypt(decrypted: String): String {
        return encrypt(decrypted.toByteArray()).toBase64()
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_MODE)
        c.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, FIXED_IV))
        return c.doFinal(encrypted)
    }

    fun decrypt(encrypted: String): String {
        return String(decrypt(encrypted.base64ToByteArray()))
    }

    companion object {
        val instance: PEncryptionProviderV0 = PEncryptionProviderV0()

        private val FIXED_IV = byteArrayOf(12, 43, 127, 2, 99, 22, 6,  78,  24, 53, 8, 101)
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val KEY_ALIAS = "PolycentricCore_Key"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private val TAG = "PEncryptionProviderV0"
        private const val TAG_LENGTH = 128
    }
}