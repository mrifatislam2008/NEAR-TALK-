package com.example.data.repository

import com.example.data.local.DeviceDao
import com.example.data.local.entities.DeviceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao
) {
    val allDevices: Flow<List<DeviceEntity>> = deviceDao.observeAllDevices()
    val trustedDevices: Flow<List<DeviceEntity>> = deviceDao.observeTrustedDevices()
    val blockedDevices: Flow<List<DeviceEntity>> = deviceDao.observeBlockedDevices()

    suspend fun getDevice(id: String): DeviceEntity? = deviceDao.getDeviceById(id)

    fun observeDevice(id: String): Flow<DeviceEntity?> = deviceDao.observeDeviceById(id)

    suspend fun upsertDevice(device: DeviceEntity) {
        deviceDao.upsertDevice(device)
    }

    suspend fun setTrusted(id: String, trusted: Boolean) {
        deviceDao.setTrusted(id, trusted)
    }

    suspend fun setBlocked(id: String, blocked: Boolean) {
        deviceDao.setBlocked(id, blocked)
    }

    suspend fun updateLastConnected(id: String, timestamp: Long, address: String?) {
        deviceDao.updateLastConnected(id, timestamp, address)
    }

    suspend fun deleteDevice(id: String) {
        deviceDao.deleteDevice(id)
    }
}
