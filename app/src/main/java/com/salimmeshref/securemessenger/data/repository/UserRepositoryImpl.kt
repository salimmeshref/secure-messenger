package com.salimmeshref.securemessenger.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.salimmeshref.securemessenger.data.local.db.dao.UserDao
import com.salimmeshref.securemessenger.domain.model.User
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import com.salimmeshref.securemessenger.utils.NetworkMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// data/repository/UserRepositoryImpl.kt
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val networkMonitor: NetworkMonitor
) : UserRepository {

    override suspend fun getUserById(userId: String): User? {
        // Try local first
        val localUser = userDao.getUserById(userId)?.toDomain()
        if (localUser != null) return localUser

        // Fetch from Firestore
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val user = doc.toUser()
                user?.toEntity()?.let { userDao.insertUser(it) }
                user
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getUserByEmail(email: String): User? {
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.toUser()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun searchUsers(query: String): List<User> {
        return try {
            // Firestore doesn't support full-text search
            // This searches for displayName starting with query
            val snapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("displayName", query)
                .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
                .limit(20)
                .get()
                .await()

            snapshot.documents.mapNotNull { it.toUser() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun updateFcmToken(token: String) {
        val userId = auth.currentUser?.uid ?: return

        try {
            firestore.collection("users")
                .document(userId)
                .update("fcmToken", token)
                .await()
        } catch (e: Exception) {
            Log.e("UserRepo", "Failed to update FCM token: ${e.message}")
        }
    }

    override suspend fun updateLastSeen() {
        val userId = auth.currentUser?.uid ?: return

        try {
            firestore.collection("users")
                .document(userId)
                .update("lastSeen", FieldValue.serverTimestamp())
                .await()
        } catch (e: Exception) {
            Log.e("UserRepo", "Failed to update lastSeen: ${e.message}")
        }
    }

    override suspend fun getPublicKey(userId: String): String? {
        // Try local cache first
        val localKey = userDao.getUserById(userId)?.publicKey
        if (localKey != null) return localKey

        // Fetch from Firestore
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            doc.getString("publicKey")
        } catch (e: Exception) {
            null
        }
    }

    override fun observeUser(userId: String): Flow<User?> {
        return callbackFlow {
            val listener = firestore.collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    val user = snapshot?.toUser()
                    trySend(user)
                }

            awaitClose { listener.remove() }
        }
    }

    // Extension function to convert Firestore document to User
    private fun DocumentSnapshot.toUser(): User? {
        return try {
            User(
                id = id,
                email = getString("email") ?: return null,
                displayName = getString("displayName") ?: return null,
                publicKey = getString("publicKey")
            )
        } catch (e: Exception) {
            null
        }
    }
}