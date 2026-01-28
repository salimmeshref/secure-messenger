package com.salimmeshref.securemessenger.domain.model

import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity


data class Conversation(
    val id: String,
    val participantIds: String, // JSON array
    val encryptedSessionKey: String,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0
){
    fun toEntity():ConversationEntity{
        return ConversationEntity(
            id = id,
            senderId = participantIds,
            encryptedSessionKey = encryptedSessionKey,
            lastMessagePreview = lastMessagePreview,
            lastMessageAt = lastMessageAt,
            unreadCount = unreadCount
        )
    }
}
