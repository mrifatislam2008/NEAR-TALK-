package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.entities.CallHistoryEntity
import com.example.data.local.entities.DeviceEntity
import com.example.data.local.entities.MessageEntity

@Database(
    entities = [
        DeviceEntity::class,
        MessageEntity::class,
        CallHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NearTalkDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun messageDao(): MessageDao
    abstract fun callHistoryDao(): CallHistoryDao

    companion object {
        const val DATABASE_NAME = "neartalk_offline.db"

        // Prepared migration scaffold for future schema upgrades without data loss
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Future non-destructive migrations
            }
        }
    }
}
