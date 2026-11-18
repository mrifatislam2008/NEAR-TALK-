package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.core.BluetoothManager
import com.example.core.PermissionManager
import com.example.util.Constants
import com.example.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RfcommClient @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val permissionManager: PermissionManager
) {
    @SuppressLint("MissingPermission")
    suspend fun connect(
        device: BluetoothDevice,
        timeoutMs: Long = Constants.CONNECTION_TIMEOUT_MS
    ): Result<BluetoothSocket> = withContext(Dispatchers.IO) {
        if (!permissionManager.hasBluetoothConnectPermission()) {
            return@withContext Result.failure(SecurityException("Bluetooth connect permission denied."))
        }
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is turned off."))
        }

        // Cancel classic discovery if running to prevent RFCOMM bandwidth contention
        bluetoothManager.cancelClassicDiscovery()

        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(Constants.RFCOMM_SERVICE_UUID)
            withTimeout(timeoutMs) {
                socket.connect()
            }
            Logger.i(TAG, "RFCOMM client connected successfully")
            Result.success(socket)
        } catch (e: Exception) {
            Logger.w(TAG, "RFCOMM connect failed: ${e.message}")
            runCatching { socket?.close() }
            val readableError = when (e) {
                is kotlinx.coroutines.TimeoutCancellationException -> IOException("Connection timed out. Ensure the other phone has NEAR TALK open.")
                is IOException -> IOException("Remote device unavailable or declined connection.")
                else -> e
            }
            Result.failure(readableError)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectByAddress(
        address: String,
        timeoutMs: Long = Constants.CONNECTION_TIMEOUT_MS
    ): Result<BluetoothSocket> {
        val adapter = bluetoothManager.adapter
            ?: return Result.failure(IllegalStateException("Bluetooth hardware unavailable."))
        return try {
            val remote = adapter.getRemoteDevice(address)
            connect(remote, timeoutMs)
        } catch (e: IllegalArgumentException) {
            Result.failure(IllegalArgumentException("Invalid Bluetooth device reference."))
        }
    }

    companion object {
        private const val TAG = "RfcommClient"
    }
}
