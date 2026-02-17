package com.salimmeshref.securemessenger.data.repository

import android.util.Base64
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.dao.MessageDao
import com.salimmeshref.securemessenger.data.local.db.dao.UserDao
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.data.remote.FirebaseAuthSource
import com.salimmeshref.securemessenger.domain.model.User
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import com.salimmeshref.securemessenger.security.KeyStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthSource: FirebaseAuthSource,
    private val securePreferencesManager: SecurePreferencesManager,
    private val userDao: UserDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val keyStoreManager: KeyStoreManager,
    private val userRepository: UserRepository
)  : AuthRepository {

    override val currentUser: Flow<User?> = firebaseAuthSource.currentUser
        .map { firebaseUser ->
            firebaseUser?.let {
                userDao.getUserById(it.uid)?.toDomain()
            }
        }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<User> {
        // Get the previously logged-in user ID before signing up
        val previousUserId = securePreferencesManager.getCurrentUserId()

        val result = firebaseAuthSource.signUp(email, password, displayName)
        result.onSuccess { user ->
            // If a different user was on this device, clear their local data
            if (previousUserId != null && previousUserId != user.id) {
                Log.d("AuthRepo", "New user signing up. Previous user: $previousUserId, New: ${user.id}")
                clearLocalDataForUserSwitch(previousUserId)
            }

            // Cache user locally
            userDao.insertUser(user.toEntity())
            // Save user ID securely
            securePreferencesManager.saveCurrentUserId(user.id)
            // Save private key alias (same as user ID, used for decryption)
            securePreferencesManager.savePrivateKeyAlias(user.id)
            // Register FCM token for push notifications
            registerFcmToken()
        }
        return result
    }

    override suspend fun signIn(email: String, password: String): Result<User> {
        // Get the previously logged-in user ID before signing in
        val previousUserId = securePreferencesManager.getCurrentUserId()

        val result = firebaseAuthSource.signIn(email, password)
        result.onSuccess { user ->
            // If a different user is signing in, clear all local data to prevent key mix-ups
            if (previousUserId != null && previousUserId != user.id) {
                Log.d("AuthRepo", "Different user signing in. Previous: $previousUserId, New: ${user.id}")
                clearLocalDataForUserSwitch(previousUserId)
            }

            userDao.insertUser(user.toEntity())
            // Save user ID securely
            securePreferencesManager.saveCurrentUserId(user.id)
            // Save private key alias (same as user ID, used for decryption)
            securePreferencesManager.savePrivateKeyAlias(user.id)

            // Check key pair in KeyStore - regenerate if missing (new device sign-in)
            checkAndRegenerateKeyPairIfNeeded(user.id)
            // Register FCM token for push notifications
            registerFcmToken()
        }
        return result
    }

    /**
     * Clear local data when switching users to prevent E2E encryption key mix-ups.
     * This ensures fresh data sync from Firestore with proper key exchange.
     */
    private suspend fun clearLocalDataForUserSwitch(previousUserId: String) {
        Log.d("AuthRepo", "Clearing local data for user switch")

        // 1. Clear all messages (they were encrypted with previous user's session keys)
        messageDao.deleteAllMessages()
        Log.d("AuthRepo", "Cleared all messages from local DB")

        // 2. Clear all conversations (session keys are user-specific)
        conversationDao.deleteAllConversations()
        Log.d("AuthRepo", "Cleared all conversations from local DB")

        // 3. Clear cached users (except we'll add the new user after)
        userDao.clearAll()
        Log.d("AuthRepo", "Cleared user cache from local DB")

        // 4. Delete the previous user's KeyStore key to prevent accidental use
        keyStoreManager.deleteKeyPair(previousUserId)
        Log.d("AuthRepo", "Deleted previous user's KeyStore key: $previousUserId")
    }

    /**
     * Check if the user's key pair exists in KeyStore.
     * If not (new device sign-in), regenerate keys and update Firestore.
     *
     * NOTE: This will make old conversations unreadable since they were encrypted
     * with the old public key. New conversations will work properly.
     */
    private suspend fun checkAndRegenerateKeyPairIfNeeded(userId: String) {
        val existingKey = keyStoreManager.getPrivateKey(userId)
        if (existingKey == null) {
            Log.w("AuthRepo", "Private key not found for user: $userId")
            Log.w("AuthRepo", "This is a new device sign-in. Regenerating key pair...")

            try {
                // Generate new key pair
                val keyPair = keyStoreManager.generateAsymmetricKeyPair(userId)
                val publicKeyString = Base64.encodeToString(
                    keyPair.public.encoded,
                    Base64.NO_WRAP
                )

                // Update public key in Firestore
                userRepository.updatePublicKey(userId, publicKeyString)

                // Clear old conversations since they can't be decrypted with new keys
                Log.d("AuthRepo", "Clearing old conversations that can't be decrypted...")
                messageDao.deleteAllMessages()
                conversationDao.deleteAllConversations()

                Log.d("AuthRepo", "Key pair regenerated and public key updated in Firestore")
                Log.w("AuthRepo", "Old conversations from other devices are no longer accessible")
            } catch (e: Exception) {
                Log.e("AuthRepo", "Failed to regenerate key pair: ${e.message}", e)
                // Don't throw - allow sign-in to complete, but messaging won't work
            }
        } else {
            Log.d("AuthRepo", "Private key exists for user: $userId")
        }
    }

    private suspend fun registerFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            userRepository.updateFcmToken(token)
            Log.d("AuthRepo", "FCM token registered successfully")
        } catch (e: Exception) {
            Log.e("AuthRepo", "Failed to register FCM token: ${e.message}", e)
        }
    }

    override fun signOut() {
        val userId = securePreferencesManager.getCurrentUserId()
        Log.d("AuthRepo", "Signing out user: $userId")

        firebaseAuthSource.signOut()
        securePreferencesManager.clearAll()

        // Note: We intentionally do NOT delete the KeyStore key here.
        // If the same user signs back in on this device, they'll still have their key.
        // The key will be deleted if a DIFFERENT user signs in (in clearLocalDataForUserSwitch).
        Log.d("AuthRepo", "Sign out complete. KeyStore key preserved for potential re-login.")
    }

    override fun getCurrentUserId(): String? {
        return securePreferencesManager.getCurrentUserId()
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            Log.d("AuthRepo", "Starting account deletion for user: $userId")

            // 1. Delete from Firebase (Auth + Firestore)
            val firebaseResult = firebaseAuthSource.deleteAccount()
            if (firebaseResult.isFailure) {
                throw firebaseResult.exceptionOrNull() ?: Exception("Failed to delete Firebase account")
            }
            Log.d("AuthRepo", "Deleted user from Firebase")

            // 2. Clear all local data
            messageDao.deleteAllMessages()
            Log.d("AuthRepo", "Deleted all messages from local DB")

            conversationDao.deleteAllConversations()
            Log.d("AuthRepo", "Deleted all conversations from local DB")

            userDao.clearAll()
            Log.d("AuthRepo", "Deleted all users from local DB")

            // 3. Delete keys from KeyStore
            if (userId != null) {
                keyStoreManager.deleteKeyPair(userId)
                Log.d("AuthRepo", "Deleted key pair from KeyStore")
            }

            // 4. Clear secure preferences
            securePreferencesManager.clearAll()
            Log.d("AuthRepo", "Cleared secure preferences")

            Log.d("AuthRepo", "Account deletion completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepo", "Failed to delete account: ${e.message}", e)
            Result.failure(e)
        }
    }
}