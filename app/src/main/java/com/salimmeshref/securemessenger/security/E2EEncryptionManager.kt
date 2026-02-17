package com.salimmeshref.securemessenger.security

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

// here i used a hybrid approach combining RSA and AES for E2E encryption; the message is encrypted with AES (symmetric encryption - fast , efficient , lower computational overhead), and the session key is encrypted with RSA
@Singleton
class E2EEncryptionManager @Inject constructor(private val aesEncryptionManager: AesEncryptionManager, private val rsaEncryptionManager: RsaEncryptionManager, private val keyStoreManager: KeyStoreManager) {

    // Generate per-conversation session key
    fun generateSessionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }

    fun encryptMessage(
        plainText: String,
        sessionKey: SecretKey
    ): EncryptedData {
        return aesEncryptionManager.encrypt(plainText, sessionKey)
    }

    fun decryptMessage(
        encryptedData: EncryptedData,
        sessionKey: SecretKey
    ): String {
        return aesEncryptionManager.decrypt(encryptedData, sessionKey)
    }

    // Called when starting a new conversation
    fun encryptSessionKeyForRecipient(
        sessionKey: SecretKey,
        recipientPublicKey: PublicKey
    ): String {
        return rsaEncryptionManager.encrypt(sessionKey, recipientPublicKey)
    }


    // Decrypt session key received from sender
    fun decryptSessionKey(
        encryptedSessionKey: String,
        privateKeyAlias: String
    ): SecretKey {
        val privateKey = keyStoreManager.getPrivateKey(privateKeyAlias)
            ?: throw IllegalStateException("Private key not found for alias: $privateKeyAlias")
        return rsaEncryptionManager.decrypt(encryptedSessionKey, privateKey)
    }

    // Decode a Base64-encoded public key to PublicKey object
    fun decodePublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    // Create encrypted session keys for multiple participants
    fun createEncryptedSessionKeysForParticipants(
        sessionKey: SecretKey,
        participantPublicKeys: Map<String, String> // userId -> base64PublicKey
    ): Map<String, String> { // userId -> encryptedSessionKey
        return participantPublicKeys.mapValues { (_, base64PublicKey) ->
            val publicKey = decodePublicKey(base64PublicKey)
            encryptSessionKeyForRecipient(sessionKey, publicKey)
        }
    }
}