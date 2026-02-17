package com.salimmeshref.securemessenger.domain.repository

import com.salimmeshref.securemessenger.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun signUp(email: String, password: String, displayName: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    fun signOut()
    fun getCurrentUserId(): String?
    suspend fun deleteAccount(): Result<Unit>
}