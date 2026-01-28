package com.salimmeshref.securemessenger.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salimmeshref.securemessenger.domain.model.Message

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val cipherText: String,
    val iv: String,
    val type: String, // "text", "location", "image"
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationAddress: String? = null,
    val timestamp: Long,
    val status: String, // "pending", "sent", "delivered", "read"
    val isSynced: Boolean = false
) {
    fun toDomain(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            cipherText = cipherText,
            iv = iv,
            type = type,
            locationLat = locationLat,
            locationLng = locationLng,
            locationAddress = locationAddress,
            timestamp = timestamp,
            status = status,
            isSynced = isSynced
        )
    }

    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "conversationId" to conversationId,
            "senderId" to senderId,
            "cipherText" to cipherText,
            "iv" to iv,
            "type" to type,
            "locationLat" to locationLat,
            "locationLng" to locationLng,
            "locationAddress" to locationAddress,
            "timestamp" to timestamp,
            "status" to status
        )
    }
}

