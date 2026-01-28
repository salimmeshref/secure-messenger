package com.salimmeshref.securemessenger.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salimmeshref.securemessenger.domain.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val displayName: String,
    val publicKey: String?,
    val avatarUrl: String?
){
    fun toDomain() = User(
        id = id,
        email = email,
        displayName = displayName,
        publicKey = publicKey
    )
}