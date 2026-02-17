package com.salimmeshref.securemessenger.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseConversationSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val CONVERSATIONS_COLLECTION = "conversations"
    }

    suspend fun createConversation(conversationData: Map<String, Any?>): Result<Unit> {
        return try {
            val conversationId = conversationData["id"] as? String
                ?: throw IllegalArgumentException("id is required")

            firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .set(conversationData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateConversation(
        conversationId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return try {
            firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConversation(conversationId: String): Result<FirestoreConversation?> {
        return try {
            val doc = firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .get()
                .await()

            if (doc.exists()) {
                val conversation = FirestoreConversation(
                    id = doc.id,
                    participantIds = doc.getString("participantIds") ?: "[]",
                    encryptedSessionKeys = doc.getString("encryptedSessionKeys") ?: "{}",
                    creatorId = doc.getString("creatorId") ?: "",
                    lastMessagePreview = doc.getString("lastMessagePreview"),
                    lastMessageAt = doc.getLong("lastMessageAt"),
                    unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0
                )
                Result.success(conversation)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<FirestoreConversation> = callbackFlow {
        val listenerRegistration = firestore.collection(CONVERSATIONS_COLLECTION)
            .whereArrayContains("participantIdsList", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val conversation = FirestoreConversation(
                        id = doc.id,
                        participantIds = doc.getString("participantIds") ?: "[]",
                        encryptedSessionKeys = doc.getString("encryptedSessionKeys") ?: "{}",
                        creatorId = doc.getString("creatorId") ?: "",
                        lastMessagePreview = doc.getString("lastMessagePreview"),
                        lastMessageAt = doc.getLong("lastMessageAt"),
                        unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0
                    )
                    trySend(conversation)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    fun observeConversation(conversationId: String): Flow<FirestoreConversation> = callbackFlow {
        val listenerRegistration = firestore.collection(CONVERSATIONS_COLLECTION)
            .document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    if (doc.exists()) {
                        val conversation = FirestoreConversation(
                            id = doc.id,
                            participantIds = doc.getString("participantIds") ?: "[]",
                            encryptedSessionKeys = doc.getString("encryptedSessionKeys") ?: "{}",
                            creatorId = doc.getString("creatorId") ?: "",
                            lastMessagePreview = doc.getString("lastMessagePreview"),
                            lastMessageAt = doc.getLong("lastMessageAt"),
                            unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0
                        )
                        trySend(conversation)
                    }
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            firestore.collection(CONVERSATIONS_COLLECTION)
                .document(conversationId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class FirestoreConversation(
    val id: String,
    val participantIds: String, // JSON array string
    val encryptedSessionKeys: String, // JSON map string
    val creatorId: String,
    val lastMessagePreview: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0
) {
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
}
