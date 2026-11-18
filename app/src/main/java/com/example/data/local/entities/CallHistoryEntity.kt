package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_history",
    indices = [Index(value = ["startTime"])]
)
data class CallHistoryEntity(
    @PrimaryKey val id: String,
    val remoteDeviceId: String,
    val remoteDisplayName: String,
    val callType: String, // OUTGOING, INCOMING
    val startTime: Long,
    val endTime: Long,
    val duration: Long, // seconds
    val callStatus: String // COMPLETED, MISSED, REJECTED, FAILED
)
