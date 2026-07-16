package com.futo.futopay

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class LicenseValidator(publicKey: String) {
    private val _publicPaymentKey: PublicKey;

    init {
        val keyFactory = KeyFactory.getInstance("RSA");
        val publicKeySpec = X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT));
        _publicPaymentKey = keyFactory.generatePublic(publicKeySpec);
    }

    fun validate(licenseKey: String, activationKey: String): Boolean {
        val sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(_publicPaymentKey);
        sig.update(licenseKey.toByteArray());

        return sig.verify(java.util.Base64.getUrlDecoder().decode(activationKey)!!);
    }
}