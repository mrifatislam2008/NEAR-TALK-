package com.example.data.repository

import com.example.data.local.CallHistoryDao
import com.example.data.local.entities.CallHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallHistoryRepository @Inject constructor(
    private val callHistoryDao: CallHistoryDao
) {
    val allCalls: Flow<List<CallHistoryEntity>> = callHistoryDao.observeAllCalls()

    fun callsForDevice(deviceId: String): Flow<List<CallHistoryEntity>> =
        callHistoryDao.observeCallsForDevice(deviceId)

    suspend fun recordCall(entry: CallHistoryEntity) {
        callHistoryDao.insertCallRecord(entry)
    }

    suspend fun deleteCall(id: String) {
        callHistoryDao.deleteCallRecord(id)
    }

    suspend fun clearAll() {
        callHistoryDao.clearAll()
    }
}
