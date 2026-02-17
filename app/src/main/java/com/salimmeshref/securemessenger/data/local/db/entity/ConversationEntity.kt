package com.salimmeshref.securemessenger.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salimmeshref.securemessenger.domain.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val participantIds: String, // JSON array of user IDs
    val encryptedSessionKeys: String, // JSON map: userId -> encryptedSessionKey
    val creatorId: String,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0
){
    fun toDomain(): Conversation {
        return Conversation(
            id = id,
            participantIds = participantIds,
            encryptedSessionKeys = encryptedSessionKeys,
            creatorId = creatorId,
            lastMessagePreview = lastMessagePreview,
            lastMessageAt = lastMessageAt,
            unreadCount = unreadCount
        )
    }
}
