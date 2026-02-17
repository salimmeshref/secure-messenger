package com.salimmeshref.securemessenger.domain.repository

import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import com.salimmeshref.securemessenger.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversations(): Flow<List<Conversation>>
    suspend fun getConversationById(conversationId: String): Conversation?
    suspend fun createConversation(
        conversationId: String,
        participantIds: List<String>,
        encryptedSessionKeys: Map<String, String>,
        creatorId: String
    ): Result<Conversation>
    suspend fun getOrCreateConversation(currentUserId: String, otherUserId: String): Result<Conversation>
    fun generateConversationId(userId1: String, userId2: String): String
    suspend fun updateLastMessage(conversationId: String, preview: String, timestamp: Long)
    suspend fun markAsRead(conversationId: String)
    suspend fun syncConversation(conversation: ConversationEntity)
    suspend fun observeRemoteConversations(userId: String): Flow<Conversation>
    suspend fun deleteConversation(conversationId: String): Result<Unit>

    /**
     * Refreshes the session keys for a conversation by generating a new session key
     * and encrypting it with the current public keys of all participants.
     * This is useful when there's a key mismatch (e.g., user signed in on a new device).
     * Note: This will make old messages unreadable as they were encrypted with the old key.
     */
    suspend fun refreshConversationKeys(conversationId: String, currentUserId: String): Result<Conversation>
}
