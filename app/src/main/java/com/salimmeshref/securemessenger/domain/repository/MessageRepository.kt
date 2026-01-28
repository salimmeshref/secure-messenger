package com.salimmeshref.securemessenger.domain.repository

import com.google.firebase.messaging.Constants
import com.salimmeshref.securemessenger.data.local.db.entity.MessageEntity
import com.salimmeshref.securemessenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun getMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, content: String, type: String, senderId:String): Result<Message>
    suspend fun syncMessage(message: MessageEntity)
    suspend fun syncPendingMessages()
    suspend fun observeIncomingMessages(conversationId: String): Flow<Message>
}