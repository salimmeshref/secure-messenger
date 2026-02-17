package com.salimmeshref.securemessenger.data.repository

import android.content.Context
import android.util.Log
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.dao.MessageDao
import com.salimmeshref.securemessenger.data.local.db.entity.MessageEntity
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.data.remote.FirebaseMessageSource
import com.salimmeshref.securemessenger.domain.model.Message
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.domain.repository.MessageRepository
import com.salimmeshref.securemessenger.security.E2EEncryptionManager
import com.salimmeshref.securemessenger.utils.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.salimmeshref.securemessenger.security.EncryptedData
import java.util.UUID
import javax.crypto.IllegalBlockSizeException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val securePreferencesManager: SecurePreferencesManager,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val firebaseMessageSource: FirebaseMessageSource,
    private val e2eEncryptionManager: E2EEncryptionManager,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context,
    private val conversationRepository: dagger.Lazy<ConversationRepository>
) : MessageRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Auto-sync when network becomes available
        scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    syncPendingMessages()
                }
            }
        }
    }

    override suspend fun getMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesByConversation(conversationId)
            .map { entities ->
                val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
                val privateKeyAlias = securePreferencesManager.getPrivateKeyAlias()
                val currentUserId = securePreferencesManager.getCurrentUserId()

                if (conversation == null) {
                    Log.e("MessageRepo", "Conversation not found in local DB: $conversationId")
                    return@map entities.map { it.toDomain().copy(content = "[Conversation not found]") }
                }
                if (privateKeyAlias == null) {
                    Log.e("MessageRepo", "Private key alias not found in preferences")
                    return@map entities.map { it.toDomain().copy(content = "[Private key not found - please re-login]") }
                }
                if (currentUserId == null) {
                    Log.e("MessageRepo", "Current user ID not found in preferences")
                    return@map entities.map { it.toDomain().copy(content = "[User not authenticated - please re-login]") }
                }

                try {
                    // Get the encrypted session key for the current user (using userId, not keystore alias)
                    val encryptedSessionKey = conversation.getEncryptedSessionKeyForUser(currentUserId)
                    if (encryptedSessionKey.isNullOrBlank()) {
                        Log.e("MessageRepo", "No encrypted session key found for user: $currentUserId")
                        return@map entities.map { it.toDomain().copy(content = "[Session key missing - conversation needs to be recreated]") }
                    }

                    val sessionKey = e2eEncryptionManager.decryptSessionKey(
                        encryptedSessionKey, privateKeyAlias
                    )

                    entities.map { entity ->
                        val decryptedContent = try {
                            e2eEncryptionManager.decryptMessage(
                                EncryptedData(entity.cipherText, entity.iv),
                                sessionKey
                            )
                        } catch (e: Exception) {
                            Log.e("MessageRepo", "Failed to decrypt message: ${e.message}")
                            "[Decryption failed]"
                        }
                        entity.toDomain().copy(content = decryptedContent)
                    }
                } catch (e: IllegalBlockSizeException) {
                    Log.e("MessageRepo", "Key mismatch - IllegalBlockSizeException: ${e.message}")
                    // This happens when the session key was encrypted with a different public key
                    // (e.g., user signed in on a new device where a new key pair was generated)
                    entities.map { it.toDomain().copy(content = "[Encryption key mismatch - this conversation was created on another device]") }
                } catch (e: Exception) {
                    Log.e("MessageRepo", "Failed to decrypt session key: ${e.message}")
                    // Return messages with error indicator - this conversation has invalid keys
                    entities.map { it.toDomain().copy(content = "[Invalid session key - conversation needs to be recreated]") }
                }
            }
            .catch { e ->
                Log.e("MessageRepo", "Flow error in getMessages: ${e.message}", e)
                emit(emptyList())
            }
    }

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        type: String,
        senderId: String
    ): Result<Message> {
        return sendMessageInternal(conversationId, content, type, senderId, isRetry = false)
    }

    private suspend fun sendMessageInternal(
        conversationId: String,
        content: String,
        type: String,
        senderId: String,
        isRetry: Boolean
    ): Result<Message> {
        return try {
            Log.d("MessageRepo", "sendMessage called: conversationId=$conversationId, senderId=$senderId, isRetry=$isRetry")

            // Get conversation to retrieve session key
            val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
                ?: throw Exception("Conversation not found in local DB")

            Log.d("MessageRepo", "Conversation found: id=${conversation.id}")
            Log.d("MessageRepo", "Conversation participantIds: ${conversation.participantIds}")
            Log.d("MessageRepo", "Conversation encryptedSessionKeys: ${conversation.encryptedSessionKeys}")

            val privateKeyAlias = securePreferencesManager.getPrivateKeyAlias()
            val currentUserId = securePreferencesManager.getCurrentUserId()
            Log.d("MessageRepo", "privateKeyAlias from preferences: $privateKeyAlias")
            Log.d("MessageRepo", "currentUserId from preferences: $currentUserId")
            Log.d("MessageRepo", "senderId parameter: $senderId")

            if (privateKeyAlias == null) {
                throw Exception("Private key alias not found - user may need to re-login")
            }

            // Get the encrypted session key for the current user (senderId)
            val encryptedSessionKey = conversation.getEncryptedSessionKeyForUser(senderId)
            Log.d("MessageRepo", "encryptedSessionKey for $senderId: ${encryptedSessionKey?.take(50)}...")
            if (encryptedSessionKey == null) {
                // Log available keys for debugging
                val participants = conversation.getParticipantIdsList()
                Log.e("MessageRepo", "Session key not found! Participants: $participants, looking for: $senderId")
                throw Exception("No encrypted session key found for sender: $senderId. Available participants: $participants")
            }

            val sessionKey = e2eEncryptionManager.decryptSessionKey(
                encryptedSessionKey, privateKeyAlias
            )
            Log.d("MessageRepo", "Session key decrypted successfully")

            // Encrypt message
            val encryptedData = e2eEncryptionManager.encryptMessage(content, sessionKey)
            Log.d("MessageRepo", "Message encrypted successfully")

            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val messageEntity = MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = senderId,
                cipherText = encryptedData.cipherText,
                iv = encryptedData.iv,
                type = type,
                timestamp = timestamp,
                status = "pending",
                isSynced = false
            )

            // 1. Save locally first (instant UI feedback)
            messageDao.insertMessage(messageEntity)
            Log.d("MessageRepo", "Message saved to local DB: $messageId")

            // 2. Update conversation last message preview
            val preview = if (content.length > 50) content.take(50) + "..." else content
            conversationDao.updateLastMessage(
                conversationId = conversationId,
                preview = preview,
                timestamp = timestamp
            )

            // 3. Try to sync to Firebase
            val isOnline = networkMonitor.isOnline.first()
            Log.d("MessageRepo", "Network status: isOnline=$isOnline")
            if (isOnline) {
                syncMessage(messageEntity)
            } else {
                Log.d("MessageRepo", "Offline - message will sync later")
            }

            Result.success(messageEntity.toDomain().copy(content = content))
        } catch (e: IllegalBlockSizeException) {
            Log.e("MessageRepo", "sendMessage failed - key mismatch: ${e.message}", e)
            // Auto-refresh keys and retry once
            if (!isRetry) {
                Log.d("MessageRepo", "Attempting to refresh conversation keys and retry...")
                return refreshKeysAndRetry(conversationId, content, type, senderId)
            }
            Result.failure(Exception("Encryption key mismatch. Keys were refreshed but still failed."))
        } catch (e: Exception) {
            // Check if this is a key decryption failure (could be wrapped exception)
            if (!isRetry && (e.message?.contains("key") == true || e.cause is IllegalBlockSizeException)) {
                Log.d("MessageRepo", "Possible key issue detected, attempting refresh...")
                return refreshKeysAndRetry(conversationId, content, type, senderId)
            }
            Log.e("MessageRepo", "sendMessage failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun refreshKeysAndRetry(
        conversationId: String,
        content: String,
        type: String,
        senderId: String
    ): Result<Message> {
        return try {
            Log.d("MessageRepo", "Refreshing conversation keys for: $conversationId")
            val refreshResult = conversationRepository.get().refreshConversationKeys(conversationId, senderId)
            if (refreshResult.isSuccess) {
                Log.d("MessageRepo", "Keys refreshed successfully, retrying message send...")
                sendMessageInternal(conversationId, content, type, senderId, isRetry = true)
            } else {
                Log.e("MessageRepo", "Failed to refresh keys: ${refreshResult.exceptionOrNull()?.message}")
                Result.failure(Exception("Failed to refresh encryption keys: ${refreshResult.exceptionOrNull()?.message}"))
            }
        } catch (e: Exception) {
            Log.e("MessageRepo", "Error refreshing keys: ${e.message}", e)
            Result.failure(Exception("Error refreshing encryption keys: ${e.message}"))
        }
    }

    override suspend fun syncMessage(message: MessageEntity) {
        try {
            Log.d("MessageRepo", "Syncing message to Firestore: ${message.id}")
            val result = firebaseMessageSource.sendMessage(message.toFirestoreMap())
            if (result.isSuccess) {
                messageDao.updateMessageStatus(message.id, "sent", isSynced = true)
                Log.d("MessageRepo", "Message synced successfully to Firestore: ${message.id}")
            } else {
                Log.e("MessageRepo", "Failed to sync message to Firestore: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            // Keep as pending, will retry later
            Log.e("MessageRepo", "Exception syncing message to Firestore: ${e.message}", e)
        }
    }

    override suspend fun syncPendingMessages() {
        val pendingMessages = messageDao.getUnsyncedMessages()
        pendingMessages.forEach { message ->
            syncMessage(message)
        }
    }

    // Listen for incoming messages from Firestore
    override suspend fun observeIncomingMessages(conversationId: String): Flow<Message> {
        return firebaseMessageSource.observeMessages(conversationId)
            .map { firestoreMessage ->
                val entity = firestoreMessage.toEntity()
                // Save to local DB
                messageDao.insertMessage(entity)

                // Decrypt the message
                val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
                val privateKeyAlias = securePreferencesManager.getPrivateKeyAlias()
                val currentUserId = securePreferencesManager.getCurrentUserId()

                val decryptedContent = if (conversation != null && privateKeyAlias != null && currentUserId != null) {
                    try {
                        // Get the encrypted session key for the current user (using userId, not keystore alias)
                        val encryptedSessionKey = conversation.getEncryptedSessionKeyForUser(currentUserId)
                        if (encryptedSessionKey.isNullOrBlank()) {
                            "[Session key missing]"
                        } else {
                            val sessionKey = e2eEncryptionManager.decryptSessionKey(
                                encryptedSessionKey, privateKeyAlias
                            )
                            e2eEncryptionManager.decryptMessage(
                                EncryptedData(entity.cipherText, entity.iv),
                                sessionKey
                            )
                        }
                    } catch (e: IllegalBlockSizeException) {
                        Log.e("MessageRepo", "Key mismatch on incoming message: ${e.message}")
                        "[Encryption key mismatch - this conversation was created on another device]"
                    } catch (e: Exception) {
                        Log.e("MessageRepo", "Failed to decrypt incoming message: ${e.message}")
                        "[Decryption failed]"
                    }
                } else "[No conversation or key]"

                entity.toDomain().copy(content = decryptedContent)
            }
    }

    override suspend fun syncConversation(conversationId: String) {
        try {
            // 1. Get last sync timestamp for this conversation
            val lastMessage = messageDao.getLastMessageTimestamp(conversationId) ?: 0L

            // 2. Fetch only new messages from Firestore
            val newMessages = firebaseMessageSource.getMessagesSince(
                conversationId = conversationId,
                since = lastMessage
            )

            // 3. Save to local Room database
            if (newMessages.isNotEmpty()) {
                messageDao.insertMessages(newMessages.map { it.toEntity() })
            }
        } catch (e: Exception) {
            Log.e("MessageRepo", "Sync failed: ${e.message}")
        }
    }

}
