package com.salimmeshref.securemessenger.security

import android.util.Base64
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RsaEncryptionManager @Inject constructor() {

    // OAEP parameters must match Android KeyStore settings:
    // - Hash: SHA-256 (from KeyProperties.DIGEST_SHA256)
    // - MGF: MGF1 with SHA-1 (Android KeyStore default for OAEP)
    private val oaepParams = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,  // Android KeyStore uses SHA-1 for MGF1 by default
        PSource.PSpecified.DEFAULT
    )

    fun encrypt(sessionKey: SecretKey, recipientPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaepParams)

        val encryptedKey = cipher.doFinal(sessionKey.encoded)
        return Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
    }

    fun decrypt(encryptedKey: String, privateKey: PrivateKey): SecretKey {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams)

        val keyBytes = cipher.doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
        return SecretKeySpec(keyBytes, "AES")
    }
}