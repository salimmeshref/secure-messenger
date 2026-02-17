package com.salimmeshref.securemessenger.data.repository

import android.util.Log
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.dao.MessageDao
import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.data.remote.FirebaseConversationSource
import com.salimmeshref.securemessenger.domain.model.Conversation
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import com.salimmeshref.securemessenger.security.E2EEncryptionManager
import com.salimmeshref.securemessenger.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val firebaseConversationSource: FirebaseConversationSource,
    private val networkMonitor: NetworkMonitor,
    private val userRepository: UserRepository,
    private val e2eEncryptionManager: E2EEncryptionManager,
    private val securePreferencesManager: SecurePreferencesManager
) : ConversationRepository {

    override fun getConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override suspend fun getConversationById(conversationId: String): Conversation? {
        return conversationDao.getConversationById(conversationId)?.toDomain()
    }

    override fun generateConversationId(userId1: String, userId2: String): String {
        // Deterministic ID: sort user IDs alphabetically and join with underscore
        val sortedIds = listOf(userId1, userId2).sorted()
        return "${sortedIds[0]}_${sortedIds[1]}"
    }

    /**
     * Validates that the current user can decrypt the session key for a conversation.
     * Returns true if decryption succeeds, false if it fails.
     */
    private fun canDecryptSessionKey(conversation: Conversation, currentUserId: String): Boolean {
        val privateKeyAlias = securePreferencesManager.getPrivateKeyAlias()
        if (privateKeyAlias == null) {
            Log.w("ConversationRepo", "No private key alias found")
            return false
        }

        val encryptedSessionKey = conversation.getEncryptedSessionKeyForUser(currentUserId)
        if (encryptedSessionKey.isNullOrBlank()) {
            Log.w("ConversationRepo", "No encrypted session key found for user: $currentUserId")
            return false
        }

        return try {
            e2eEncryptionManager.decryptSessionKey(encryptedSessionKey, privateKeyAlias)
            Log.d("ConversationRepo", "Session key validation successful for user: $currentUserId")
            true
        } catch (e: Exception) {
            Log.w("ConversationRepo", "Session key validation failed: ${e.message}")
            false
        }
    }

    override suspend fun getOrCreateConversation(
        currentUserId: String,
        otherUserId: String
    ): Result<Conversation> {
        // Prevent self-conversation
        if (currentUserId == otherUserId) {
            Log.e("ConversationRepo", "Cannot create conversation with yourself: $currentUserId")
            return Result.failure(Exception("Cannot start a conversation with yourself"))
        }

        return try {
            val conversationId = generateConversationId(currentUserId, otherUserId)

            // 1. Check local DB first
            val localConversation = conversationDao.getConversationById(conversationId)
            if (localConversation != null) {
                val conversation = localConversation.toDomain()
                // Validate session key - if invalid, refresh keys
                if (!canDecryptSessionKey(conversation, currentUserId)) {
                    Log.w("ConversationRepo", "Local conversation has invalid session key, refreshing...")
                    return refreshConversationKeys(conversationId, currentUserId)
                }
                return Result.success(conversation)
            }

            // 2. Check Firestore if online
            if (networkMonitor.isOnline.first()) {
                Log.d("ConversationRepo", "Checking Firestore for conversation: $conversationId")
                val remoteResult = firebaseConversationSource.getConversation(conversationId)
                if (remoteResult.isSuccess) {
                    val remoteConversation = remoteResult.getOrNull()
                    if (remoteConversation != null) {
                        Log.d("ConversationRepo", "Found conversation in Firestore: $conversationId")
                        Log.d("ConversationRepo", "encryptedSessionKeys from Firestore: ${remoteConversation.encryptedSessionKeys}")

                        val conversation = remoteConversation.toEntity().toDomain()

                        // Validate session key - if invalid, refresh keys instead of using corrupt data
                        if (!canDecryptSessionKey(conversation, currentUserId)) {
                            Log.w("ConversationRepo", "Remote conversation has invalid session key, refreshing...")
                            // Save to local first so refreshConversationKeys can find it
                            conversationDao.insertConversation(remoteConversation.toEntity())
                            return refreshConversationKeys(conversationId, currentUserId)
                        }

                        // Save to local DB
                        conversationDao.insertConversation(remoteConversation.toEntity())
                        return Result.success(conversation)
                    }
                }
                Log.d("ConversationRepo", "Conversation not found in Firestore: $conversationId")
            }

            // 3. Create new conversation with key exchange
            // Get public keys for both participants
            Log.d("ConversationRepo", "Creating new conversation: $conversationId")
            Log.d("ConversationRepo", "Fetching public key for currentUser: $currentUserId")
            val currentUserPublicKey = userRepository.getPublicKey(currentUserId)
                ?: throw Exception("Current user's public key not found")
            Log.d("ConversationRepo", "Fetching public key for otherUser: $otherUserId")
            val otherUserPublicKey = userRepository.getPublicKey(otherUserId)
                ?: throw Exception("Other user's public key not found")

            val participantPublicKeys = mapOf(
                currentUserId to currentUserPublicKey,
                otherUserId to otherUserPublicKey
            )
            Log.d("ConversationRepo", "Participant IDs for session keys: ${participantPublicKeys.keys}")

            // Generate session key
            val sessionKey = e2eEncryptionManager.generateSessionKey()

            // Encrypt session key for each participant
            val encryptedSessionKeys = e2eEncryptionManager.createEncryptedSessionKeysForParticipants(
                sessionKey,
                participantPublicKeys
            )
            Log.d("ConversationRepo", "Created encrypted session keys for: ${encryptedSessionKeys.keys}")

            // Create conversation
            createConversation(
                conversationId = conversationId,
                participantIds = listOf(currentUserId, otherUserId),
                encryptedSessionKeys = encryptedSessionKeys,
                creatorId = currentUserId
            )
        } catch (e: Exception) {
            Log.e("ConversationRepo", "Failed to get or create conversation: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createConversation(
        conversationId: String,
        participantIds: List<String>,
        encryptedSessionKeys: Map<String, String>,
        creatorId: String
    ): Result<Conversation> {
        return try {
            val timestamp = System.currentTimeMillis()

            // Convert to JSON strings
            val participantIdsJson = JSONArray(participantIds).toString()
            val encryptedSessionKeysJson = JSONObject(encryptedSessionKeys).toString()

            val conversationEntity = ConversationEntity(
                id = conversationId,
                participantIds = participantIdsJson,
                encryptedSessionKeys = encryptedSessionKeysJson,
                creatorId = creatorId,
                lastMessagePreview = null,
                lastMessageAt = timestamp,
                unreadCount = 0
            )

            // Save locally first
            conversationDao.insertConversation(conversationEntity)

            // Sync to Firebase if online
            if (networkMonitor.isOnline.first()) {
                syncConversation(conversationEntity)
            }

            Result.success(conversationEntity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLastMessage(
        conversationId: String,
        preview: String,
        timestamp: Long
    ) {
        conversationDao.updateLastMessage(conversationId, preview, timestamp)

        // Sync update to Firebase if online
        if (networkMonitor.isOnline.first()) {
            try {
                firebaseConversationSource.updateConversation(
                    conversationId,
                    mapOf(
                        "lastMessagePreview" to preview,
                        "lastMessageAt" to timestamp
                    )
                )
            } catch (e: Exception) {
                Log.e("ConversationRepo", "Failed to sync last message update: ${e.message}")
            }
        }
    }

    override suspend fun markAsRead(conversationId: String) {
        conversationDao.markAsRead(conversationId)

        // Sync to Firebase if online
        if (networkMonitor.isOnline.first()) {
            try {
                firebaseConversationSource.updateConversation(
                    conversationId,
                    mapOf("unreadCount" to 0)
                )
            } catch (e: Exception) {
                Log.e("ConversationRepo", "Failed to sync mark as read: ${e.message}")
            }
        }
    }

    override suspend fun syncConversation(conversation: ConversationEntity) {
        try {
            // Parse participant IDs for the array field Firestore needs for queries
            val participantIdsList = try {
                val jsonArray = JSONArray(conversation.participantIds)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }

            val conversationData = mapOf(
                "id" to conversation.id,
                "participantIds" to conversation.participantIds,
                "participantIdsList" to participantIdsList,
                "encryptedSessionKeys" to conversation.encryptedSessionKeys,
                "creatorId" to conversation.creatorId,
                "lastMessagePreview" to conversation.lastMessagePreview,
                "lastMessageAt" to conversation.lastMessageAt,
                "unreadCount" to conversation.unreadCount
            )
            firebaseConversationSource.createConversation(conversationData)
        } catch (e: Exception) {
            Log.e("ConversationRepo", "Failed to sync conversation: ${e.message}")
        }
    }

    override suspend fun observeRemoteConversations(userId: String): Flow<Conversation> {
        return firebaseConversationSource.observeConversations(userId)
            .map { firestoreConversation ->
                val entity = firestoreConversation.toEntity()
                // Save to local DB
                conversationDao.insertConversation(entity)
                entity.toDomain()
            }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            Log.d("ConversationRepo", "Deleting conversation: $conversationId")

            // 1. Delete messages from local DB first
            messageDao.deleteMessagesForConversation(conversationId)
            Log.d("ConversationRepo", "Deleted messages for conversation: $conversationId")

            // 2. Delete conversation from local DB
            conversationDao.deleteConversation(conversationId)
            Log.d("ConversationRepo", "Deleted conversation from local DB: $conversationId")

            // 3. Delete from Firebase if online
            if (networkMonitor.isOnline.first()) {
                firebaseConversationSource.deleteConversation(conversationId)
                Log.d("ConversationRepo", "Deleted conversation from Firebase: $conversationId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ConversationRepo", "Failed to delete conversation: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun refreshConversationKeys(
        conversationId: String,
        currentUserId: String
    ): Result<Conversation> {
        return try {
            Log.d("ConversationRepo", "Refreshing keys for conversation: $conversationId")

            // Get existing conversation
            val existingConversation = conversationDao.getConversationById(conversationId)?.toDomain()
                ?: throw Exception("Conversation not found")

            val participantIds = existingConversation.getParticipantIdsList()
            Log.d("ConversationRepo", "Participants: $participantIds")

            // Fetch fresh public keys for all participants
            val participantPublicKeys = mutableMapOf<String, String>()
            for (userId in participantIds) {
                val publicKey = userRepository.getPublicKey(userId)
                if (publicKey != null) {
                    participantPublicKeys[userId] = publicKey
                    Log.d("ConversationRepo", "Fetched public key for $userId")
                } else {
                    Log.w("ConversationRepo", "No public key found for $userId")
                }
            }

            if (participantPublicKeys.size != participantIds.size) {
                throw Exception("Could not fetch public keys for all participants")
            }

            // Generate new session key
            val sessionKey = e2eEncryptionManager.generateSessionKey()

            // Encrypt session key for each participant with their current public key
            val encryptedSessionKeys = e2eEncryptionManager.createEncryptedSessionKeysForParticipants(
                sessionKey,
                participantPublicKeys
            )
            Log.d("ConversationRepo", "Created new encrypted session keys for: ${encryptedSessionKeys.keys}")

            // Update conversation
            val encryptedSessionKeysJson = JSONObject(encryptedSessionKeys).toString()
            val updatedEntity = ConversationEntity(
                id = conversationId,
                participantIds = existingConversation.participantIds,
                encryptedSessionKeys = encryptedSessionKeysJson,
                creatorId = existingConversation.creatorId,
                lastMessagePreview = existingConversation.lastMessagePreview,
                lastMessageAt = existingConversation.lastMessageAt,
                unreadCount = existingConversation.unreadCount
            )

            // Save locally
            conversationDao.insertConversation(updatedEntity)

            // Sync to Firebase if online
            if (networkMonitor.isOnline.first()) {
                syncConversation(updatedEntity)
            }

            Log.d("ConversationRepo", "Successfully refreshed keys for conversation: $conversationId")
            Result.success(updatedEntity.toDomain())
        } catch (e: Exception) {
            Log.e("ConversationRepo", "Failed to refresh conversation keys: ${e.message}")
            Result.failure(e)
        }
    }
}
