package com.salimmeshref.securemessenger.di

import com.salimmeshref.securemessenger.data.repository.AuthRepositoryImpl
import com.salimmeshref.securemessenger.data.repository.ConversationRepositoryImpl
import com.salimmeshref.securemessenger.data.repository.MessageRepositoryImpl
import com.salimmeshref.securemessenger.data.repository.UserRepositoryImpl
import com.salimmeshref.securemessenger.domain.repository.AuthRepository
import com.salimmeshref.securemessenger.domain.repository.ConversationRepository
import com.salimmeshref.securemessenger.domain.repository.MessageRepository
import com.salimmeshref.securemessenger.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}