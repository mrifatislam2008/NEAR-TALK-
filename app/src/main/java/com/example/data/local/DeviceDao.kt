package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE isBlocked = 0 ORDER BY lastConnectedAt DESC")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isTrusted = 1 AND isBlocked = 0 ORDER BY lastConnectedAt DESC")
    fun observeTrustedDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isBlocked = 1 ORDER BY displayName ASC")
    fun observeBlockedDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    fun observeDeviceById(id: String): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: DeviceEntity)

    @Query("UPDATE devices SET isTrusted = :trusted WHERE id = :id")
    suspend fun setTrusted(id: String, trusted: Boolean)

    @Query("UPDATE devices SET isBlocked = :blocked WHERE id = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("UPDATE devices SET lastConnectedAt = :timestamp, lastBluetoothAddress = :address WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long, address: String?)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteDevice(id: String)
}
