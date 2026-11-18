package com.example.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionStatus(
    val bluetoothScanGranted: Boolean = false,
    val bluetoothAdvertiseGranted: Boolean = false,
    val bluetoothConnectGranted: Boolean = false,
    val recordAudioGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val legacyLocationGranted: Boolean = true
) {
    val allBluetoothGranted: Boolean
        get() = bluetoothScanGranted && bluetoothAdvertiseGranted && bluetoothConnectGranted && legacyLocationGranted

    val readyForCalls: Boolean
        get() = allBluetoothGranted && recordAudioGranted
}

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _status = MutableStateFlow(evaluateStatus())
    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    fun refresh() {
        _status.value = evaluateStatus()
    }

    fun evaluateStatus(): PermissionStatus {
        val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) && hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val advertise = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val connect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH)
        }

        val legacyLocation = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            true
        }

        val audio = hasPermission(Manifest.permission.RECORD_AUDIO)

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        return PermissionStatus(
            bluetoothScanGranted = scan,
            bluetoothAdvertiseGranted = advertise,
            bluetoothConnectGranted = connect,
            recordAudioGranted = audio,
            notificationsGranted = notifications,
            legacyLocationGranted = legacyLocation
        )
    }

    fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun requiredAllPermissions(): Array<String> {
        val list = mutableListOf<String>()
        list.addAll(requiredBluetoothPermissions())
        list.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.distinct().toTypedArray()
    }

    fun hasAudioPermission(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)

    fun hasBluetoothConnectPermission(): Boolean = evaluateStatus().bluetoothConnectGranted

    fun hasBluetoothScanPermission(): Boolean = evaluateStatus().bluetoothScanGranted

    fun hasBluetoothAdvertisePermission(): Boolean = evaluateStatus().bluetoothAdvertiseGranted

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
