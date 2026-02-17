package com.salimmeshref.securemessenger.domain.model

import android.util.Log
import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import org.json.JSONObject

data class Conversation(
    val id: String,
    val participantIds: String, // JSON array of user IDs
    val encryptedSessionKeys: String, // JSON map: userId -> encryptedSessionKey
    val creatorId: String,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0
){
    fun toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            participantIds = participantIds,
            encryptedSessionKeys = encryptedSessionKeys,
            creatorId = creatorId,
            lastMessagePreview = lastMessagePreview,
            lastMessageAt = lastMessageAt,
            unreadCount = unreadCount
        )
    }

    fun getEncryptedSessionKeyForUser(userId: String): String? {
        return try {
            Log.d("Conversation", "getEncryptedSessionKeyForUser: userId=$userId")
            Log.d("Conversation", "encryptedSessionKeys raw: $encryptedSessionKeys")
            val json = JSONObject(encryptedSessionKeys)
            val keys = json.keys().asSequence().toList()
            Log.d("Conversation", "encryptedSessionKeys contains keys: $keys")
            if (json.has(userId)) {
                val key = json.getString(userId)
                Log.d("Conversation", "Found session key for $userId: ${key.take(30)}...")
                key
            } else {
                Log.e("Conversation", "No session key found for userId=$userId in keys=$keys")
                null
            }
        } catch (e: Exception) {
            Log.e("Conversation", "Error parsing encryptedSessionKeys: ${e.message}")
            null
        }
    }

    fun getParticipantIdsList(): List<String> {
        return try {
            val jsonArray = org.json.JSONArray(participantIds)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
