package com.salimmeshref.securemessenger.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.salimmeshref.securemessenger.data.local.db.dao.ConversationDao
import com.salimmeshref.securemessenger.data.local.db.dao.MessageDao
import com.salimmeshref.securemessenger.data.local.db.dao.UserDao
import com.salimmeshref.securemessenger.data.local.db.entity.ConversationEntity
import com.salimmeshref.securemessenger.data.local.db.entity.MessageEntity
import com.salimmeshref.securemessenger.data.local.db.entity.UserEntity

@Database(
    entities = [UserEntity::class, MessageEntity::class, ConversationEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with updated schema
                db.execSQL("""
                    CREATE TABLE conversations_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        participantIds TEXT NOT NULL,
                        encryptedSessionKeys TEXT NOT NULL,
                        creatorId TEXT NOT NULL,
                        lastMessagePreview TEXT,
                        lastMessageAt INTEGER,
                        unreadCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Migrate data from old table to new table
                // Old: senderId, encryptedSessionKey
                // New: participantIds (JSON array), encryptedSessionKeys (JSON map), creatorId
                db.execSQL("""
                    INSERT INTO conversations_new (id, participantIds, encryptedSessionKeys, creatorId, lastMessagePreview, lastMessageAt, unreadCount)
                    SELECT
                        id,
                        '["' || senderId || '"]',
                        '{"' || senderId || '":"' || encryptedSessionKey || '"}',
                        senderId,
                        lastMessagePreview,
                        lastMessageAt,
                        unreadCount
                    FROM conversations
                """.trimIndent())

                // Drop old table
                db.execSQL("DROP TABLE conversations")

                // Rename new table to original name
                db.execSQL("ALTER TABLE conversations_new RENAME TO conversations")
            }
        }
    }
}
