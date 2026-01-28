package com.salimmeshref.securemessenger.domain.model

import com.salimmeshref.securemessenger.data.local.db.entity.UserEntity

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val publicKey: String? = null
){
    fun toEntity() = UserEntity(
        id = id,
        email = email,
        displayName = displayName,
        publicKey = publicKey,
        avatarUrl = null
    )
}
