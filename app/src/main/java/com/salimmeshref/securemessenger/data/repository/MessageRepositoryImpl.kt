package com.salimmeshref.securemessenger.data.repository

import android.content.Context
import android.util.Log
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.dao.MessageDao
import com.salimmeshref.securemessenger.data.local.db.entity.MessageEntity
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.data.remote.FirebaseMessageSource
import com.salimmeshref.securemessenger.domain.model.Message
import com.salimmeshref.securemessenger.domain.repository.MessageRepository
import com.salimmeshref.securemessenger.security.E2EEncryptionManager
import com.salimmeshref.securemessenger.utils.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
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
    @ApplicationContext private val context: Context
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
                entities.map { it.toDomain() }
            }
    }

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        type: String,
        senderId:String
    ): Result<Message> {
        return try {
            // Get conversation to retrieve session key
            val conversation = conversationDao.getConversationById(conversationId)
                ?: throw Exception("Conversation not found")

           securePreferencesManager.getPrivateKeyAlias()?.let {

               val sessionKey = e2eEncryptionManager.decryptSessionKey(
                   conversation.encryptedSessionKey, it
               )


               // Encrypt message
               val encryptedData = e2eEncryptionManager.encryptMessage(content, sessionKey)

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

               // 2. Update conversation last message
               conversationDao.updateLastMessage(
                   conversationId = conversationId,
                   preview = "Encrypted message",
                   timestamp = timestamp
               )

               // 3. Try to sync to Firebase
               if (networkMonitor.isOnline.first()) {
                   syncMessage(messageEntity)
               }
               // If offline, message stays in "pending" status and syncs later

               Result.success(messageEntity.toDomain())
           }?:throw Exception("Private key alias not found")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncMessage(message: MessageEntity) {
        try {
            firebaseMessageSource.sendMessage(message.toFirestoreMap())
            messageDao.updateMessageStatus(message.id, "sent", isSynced = true)
        } catch (e: Exception) {
            // Keep as pending, will retry later
            Log.e("MessageRepo", "Failed to sync message: ${e.message}")
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
                entity.toDomain()
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