package com.salimmeshref.securemessenger.domain.repository

import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import com.salimmeshref.securemessenger.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversations(): Flow<List<Conversation>>
    suspend fun getConversationById(conversationId: String): Conversation?
    suspend fun createConversation(participantIds: String, encryptedSessionKey: String): Result<Conversation>
    suspend fun updateLastMessage(conversationId: String, preview: String, timestamp: Long)
    suspend fun markAsRead(conversationId: String)
    suspend fun syncConversation(conversation: ConversationEntity)
    suspend fun observeRemoteConversations(userId: String): Flow<Conversation>
    suspend fun deleteConversation(conversationId: String): Result<Unit>
}