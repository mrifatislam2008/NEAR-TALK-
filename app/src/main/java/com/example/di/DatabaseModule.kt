package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.CallHistoryDao
import com.example.data.local.DeviceDao
import com.example.data.local.MessageDao
import com.example.data.local.NearTalkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNearTalkDatabase(@ApplicationContext context: Context): NearTalkDatabase {
        return Room.databaseBuilder(
            context,
            NearTalkDatabase::class.java,
            NearTalkDatabase.DATABASE_NAME
        )
            .addMigrations(NearTalkDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideDeviceDao(database: NearTalkDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideMessageDao(database: NearTalkDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideCallHistoryDao(database: NearTalkDatabase): CallHistoryDao = database.callHistoryDao()
}
