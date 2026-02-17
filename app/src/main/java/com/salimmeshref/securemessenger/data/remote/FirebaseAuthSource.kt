package com.salimmeshref.securemessenger.data.remote

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.salimmeshref.securemessenger.domain.model.User
import com.salimmeshref.securemessenger.security.KeyStoreManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val keyStoreManager: KeyStoreManager
) {

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUp(email: String, password: String, displayName: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("User creation failed")

            // Generate key pair for E2E encryption
            val keyPair = keyStoreManager.generateAsymmetricKeyPair(firebaseUser.uid)
            val publicKeyString = Base64.encodeToString(
                keyPair.public.encoded,
                Base64.NO_WRAP
            )

            // Store user data in Firestore
            val userData = hashMapOf(
                "email" to email,
                "displayName" to displayName,
                "publicKey" to publicKeyString,
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(userData)
                .await()

            Result.success(User(
                id = firebaseUser.uid,
                email = email,
                displayName = displayName
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("Sign in failed")

            val userDoc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            // Check if user document exists in Firestore
            if (!userDoc.exists()) {
                // User authenticated but no Firestore profile - sign them out
                auth.signOut()
                throw Exception("User profile not found. Please sign up again.")
            }

            Result.success(User(
                id = firebaseUser.uid,
                email = userDoc.getString("email") ?: "",
                displayName = userDoc.getString("displayName") ?: ""
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("No user signed in")
            val userId = currentUser.uid

            // 1. Delete user document from Firestore
            firestore.collection("users")
                .document(userId)
                .delete()
                .await()

            // 2. Delete user from Firebase Auth
            currentUser.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

