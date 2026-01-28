package com.salimmeshref.securemessenger.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.dao.MessageDao
import com.salimmeshref.securemessenger.data.local.db.dao.UserDao
import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import com.salimmeshref.securemessenger.data.local.db.entity.MessageEntity
import com.salimmeshref.securemessenger.data.local.db.entity.UserEntity

@Database(
    entities = [UserEntity::class,MessageEntity::class,ConversationEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
}