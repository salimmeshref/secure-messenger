package com.salimmeshref.securemessenger.security

import java.security.PublicKey
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
}