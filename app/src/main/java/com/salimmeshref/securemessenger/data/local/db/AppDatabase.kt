package com.salimmeshref.securemessenger.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.salimmeshref.securemessenger.data.local.db.dao.UserDao
import com.salimmeshref.securemessenger.data.local.db.entity.UserEntity

@Database(
    entities = [UserEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}