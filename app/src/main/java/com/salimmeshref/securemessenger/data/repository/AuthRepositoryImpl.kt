package com.salimmeshref.securemessenger.data.repository

import com.salimmeshref.securemessenger.data.local.db.dao.UserDao
import com.salimmeshref.securemessenger.data.local.prefrences.SecurePreferencesManager
import com.salimmeshref.securemessenger.data.remote.FirebaseAuthSource
import com.salimmeshref.securemessenger.domain.model.User
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthSource: FirebaseAuthSource,
    private val securePreferencesManager: SecurePreferencesManager,
    private val userDao: UserDao
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
        val result = firebaseAuthSource.signUp(email, password, displayName)
        result.onSuccess { user ->
            // Cache user locally
            userDao.insertUser(user.toEntity())
        }
        return result
    }

    override suspend fun signIn(email: String, password: String): Result<User> {
        val result = firebaseAuthSource.signIn(email, password)
        result.onSuccess { user ->
            userDao.insertUser(user.toEntity())
        }
        return result
    }

    override fun signOut() {
        firebaseAuthSource.signOut()
        securePreferencesManager.clearAll()
    }
}