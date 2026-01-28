package com.salimmeshref.securemessenger.domain.repository

import com.salimmeshref.securemessenger.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserById(userId: String): User?
    suspend fun getUserByEmail(email: String): User?
    suspend fun searchUsers(query: String): List<User>
    suspend fun updateFcmToken(token: String)
    suspend fun updateLastSeen()
    suspend fun getPublicKey(userId: String): String?
    fun observeUser(userId: String): Flow<User?>
}