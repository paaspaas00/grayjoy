package com.futo.polycentric.core

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key

class Ed25519 {
    companion object {
        private var _sodium: SodiumAndroid = SodiumAndroid()
        private var _lazySodium: LazySodiumAndroid = LazySodiumAndroid(_sodium)

        fun verify(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean {
            return _lazySodium.cryptoSignVerifyDetached(signature, data, data.size, publicKey)
        }

        fun sign(data: ByteArray, keyPair: KeyPair): ByteArray {
            val signature = ByteArray(Sign.BYTES)
            if (!_lazySodium.cryptoSignDetached(signature, data, data.size.toLong(), keyPair.secretKey)) {
                throw SodiumException("Failed to sign message.")
            }

            return signature
        }

        fun privateKeyToPublicKey(privateKey: ByteArray): ByteArray {
            if (privateKey.size != 32) {
                throw IllegalArgumentException("Private key must be 32 bytes")
            }

            return _lazySodium.cryptoSignSeedKeypair(privateKey).publicKey.asBytes
        }

        fun generateKeyPair(): KeyPair {
            val kp = _lazySodium.cryptoSignKeypair()
            val publicKey = kp.publicKey.asBytes
            val privateKeyPrivateKeyPart = kp.secretKey.asBytes.slice(IntRange(0, 31)).toByteArray()
            val privateKeyPublicKeyPart = kp.secretKey.asBytes.slice(IntRange(32, 63)).toByteArray()
            if (!privateKeyPublicKeyPart.contentEquals(publicKey)) {
                throw Exception("Expected sodium to append public key at end of private key")
            }

            return KeyPair(PrivateKey(1L, privateKeyPrivateKeyPart), PublicKey(1L, privateKeyPublicKeyPart))
        }

        fun getRandomBytes(count: Int): ByteArray {
            return _lazySodium.randomBytesBuf(count)
        }
    }
}