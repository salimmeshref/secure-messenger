package com.salimmeshref.securemessenger.security

import android.util.Base64
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RsaEncryptionManager @Inject constructor() {

    fun encrypt(sessionKey: SecretKey, recipientPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)

        val encryptedKey = cipher.doFinal(sessionKey.encoded)
        return Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
    }

    fun decrypt(encryptedKey: String, privateKey: PrivateKey): SecretKey {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        val keyBytes = cipher.doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
        return SecretKeySpec(keyBytes, "AES")
    }
}