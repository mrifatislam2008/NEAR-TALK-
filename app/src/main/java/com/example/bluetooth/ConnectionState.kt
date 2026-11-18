package com.example.bluetooth

import android.bluetooth.BluetoothDevice

enum class ConnectionState {
    IDLE,
    SCANNING,
    DEVICE_FOUND,
    REQUESTING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    ERROR
}

data class DiscoveredDevice(
    val deviceId: String,
    val displayName: String,
    val deviceNickname: String,
    val bluetoothAddress: String,
    val rssi: Int,
    val lastSeenAt: Long,
    val bluetoothDevice: BluetoothDevice? = null,
    val isTrusted: Boolean = false,
    val isBlocked: Boolean = false
)

data class PendingConnectionRequest(
    val remoteDeviceId: String,
    val remoteDisplayName: String,
    val remoteNickname: String,
    val bluetoothAddress: String,
    val isTrusted: Boolean
)
