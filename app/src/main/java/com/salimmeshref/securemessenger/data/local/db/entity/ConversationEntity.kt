package com.salimmeshref.securemessenger.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salimmeshref.securemessenger.domain.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val senderId:String,
    val encryptedSessionKey: String,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0
){
    fun toDomain(): Conversation {
        return Conversation(
            id = id,
            participantIds = senderId,
            encryptedSessionKey = encryptedSessionKey,
            lastMessagePreview = lastMessagePreview,
            lastMessageAt = lastMessageAt,
            unreadCount = unreadCount
        )
    }
}