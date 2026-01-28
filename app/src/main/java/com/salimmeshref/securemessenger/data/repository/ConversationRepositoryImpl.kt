package com.salimmeshref.securemessenger.data.repository

import android.util.Log
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import com.salimmeshref.securemessenger.data.remote.FirebaseConversationSource
import com.salimmeshref.securemessenger.domain.model.Conversation
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val firebaseConversationSource: FirebaseConversationSource,
    private val networkMonitor: NetworkMonitor
) : ConversationRepository {

    override fun getConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override suspend fun getConversationById(conversationId: String): Conversation? {
        return conversationDao.getConversationById(conversationId)?.toDomain()
    }

    override suspend fun createConversation(
        participantIds: String,
        encryptedSessionKey: String
    ): Result<Conversation> {
        return try {
            val conversationId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val conversationEntity = ConversationEntity(
                id = conversationId,
                senderId = participantIds,
                encryptedSessionKey = encryptedSessionKey,
                lastMessagePreview = null,
                lastMessageAt = timestamp,
                unreadCount = 0
            )

            // Save locally first
            conversationDao.insertConversation(conversationEntity)

            // Sync to Firebase if online
            if (networkMonitor.isOnline.first()) {
                syncConversation(conversationEntity)
            }

            Result.success(conversationEntity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLastMessage(
        conversationId: String,
        preview: String,
        timestamp: Long
    ) {
        conversationDao.updateLastMessage(conversationId, preview, timestamp)

        // Sync update to Firebase if online
        if (networkMonitor.isOnline.first()) {
            try {
                firebaseConversationSource.updateConversation(
                    conversationId,
                    mapOf(
                        "lastMessagePreview" to preview,
                        "lastMessageAt" to timestamp
                    )
                )
            } catch (e: Exception) {
                Log.e("ConversationRepo", "Failed to sync last message update: ${e.message}")
            }
        }
    }

    override suspend fun markAsRead(conversationId: String) {
        conversationDao.markAsRead(conversationId)

        // Sync to Firebase if online
        if (networkMonitor.isOnline.first()) {
            try {
                firebaseConversationSource.updateConversation(
                    conversationId,
                    mapOf("unreadCount" to 0)
                )
            } catch (e: Exception) {
                Log.e("ConversationRepo", "Failed to sync mark as read: ${e.message}")
            }
        }
    }

    override suspend fun syncConversation(conversation: ConversationEntity) {
        try {
            val conversationData = mapOf(
                "id" to conversation.id,
                "participantIds" to conversation.senderId,
                "participantIdsList" to conversation.senderId.split(",").map { it.trim() },
                "encryptedSessionKey" to conversation.encryptedSessionKey,
                "lastMessagePreview" to conversation.lastMessagePreview,
                "lastMessageAt" to conversation.lastMessageAt,
                "unreadCount" to conversation.unreadCount
            )
            firebaseConversationSource.createConversation(conversationData)
        } catch (e: Exception) {
            Log.e("ConversationRepo", "Failed to sync conversation: ${e.message}")
        }
    }

    override suspend fun observeRemoteConversations(userId: String): Flow<Conversation> {
        return firebaseConversationSource.observeConversations(userId)
            .map { firestoreConversation ->
                val entity = firestoreConversation.toEntity()
                // Save to local DB
                conversationDao.insertConversation(entity)
                entity.toDomain()
            }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            // Delete from Firebase if online
            if (networkMonitor.isOnline.first()) {
                firebaseConversationSource.deleteConversation(conversationId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}