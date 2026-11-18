package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val deviceNickname: String = "",
    val profileImage: String? = null,
    val lastBluetoothAddress: String? = null,
    val lastConnectedAt: Long = 0L,
    val isTrusted: Boolean = false,
    val isBlocked: Boolean = false
)
