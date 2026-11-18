package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.CallHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY startTime DESC")
    fun observeAllCalls(): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE remoteDeviceId = :deviceId ORDER BY startTime DESC")
    fun observeCallsForDevice(deviceId: String): Flow<List<CallHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallHistoryEntity)

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteCallRecord(id: String)

    @Query("DELETE FROM call_history")
    suspend fun clearAll()
}
