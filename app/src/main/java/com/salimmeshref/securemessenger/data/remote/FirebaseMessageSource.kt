package com.salimmeshref.securemessenger.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.salimmeshref.securemessenger.data.local.db.entity.MessageEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseMessageSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val CONVERSATIONS_COLLECTION = "conversations"
        private const val MESSAGES_COLLECTION = "messages"
    }

    suspend fun sendMessage(messageData: Map<String, Any?>): Result<Unit> {
        return try {
            val conversationId = messageData["conversationId"] as? String
                ?: throw IllegalArgumentException("conversationId is required")
            val messageId = messageData["id"] as? String
                ?: throw IllegalArgumentException("id is required")

            firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .collection(MESSAGES_COLLECTION)
                .document(messageId)
                .set(messageData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeMessages(conversationId: String): Flow<FirestoreMessage> = callbackFlow {
        val listenerRegistration = firestore.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .collection(MESSAGES_COLLECTION)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val message = FirestoreMessage(
                            id = doc.id,
                            conversationId = doc.getString("conversationId") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            cipherText = doc.getString("cipherText") ?: "",
                            iv = doc.getString("iv") ?: "",
                            type = doc.getString("type") ?: "text",
                            locationLat = doc.getDouble("locationLat"),
                            locationLng = doc.getDouble("locationLng"),
                            locationAddress = doc.getString("locationAddress"),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = doc.getString("status") ?: "sent"
                        )
                        trySend(message)
                    }
                }
            }

        awaitClose { listenerRegistration.remove() }
    }


    suspend fun getMessagesSince(conversationId: String, since: Long): List<FirestoreMessage> {
        return firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereGreaterThan("timestamp", since)
            .orderBy("timestamp")
            .get()
            .await()
            .toObjects(FirestoreMessage::class.java)
    }
}

data class FirestoreMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val cipherText: String,
    val iv: String,
    val type: String,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationAddress: String? = null,
    val timestamp: Long,
    val status: String
) {
    fun toEntity(): MessageEntity {
        return MessageEntity(
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
            isSynced = true
        )
    }
}
